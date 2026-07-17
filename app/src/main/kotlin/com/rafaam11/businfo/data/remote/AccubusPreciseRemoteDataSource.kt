package com.rafaam11.businfo.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.rafaam11.businfo.data.PreciseDataResult
import com.rafaam11.businfo.data.PreciseRosterSnapshot
import com.rafaam11.businfo.data.PreciseVehiclePositionDataSource
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.PreciseVehicleBatch
import com.rafaam11.businfo.domain.PreciseVehiclePosition
import com.rafaam11.businfo.domain.isValidFor
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class AccubusPreciseRemoteDataSource(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val clock: Clock,
) : PreciseVehiclePositionDataSource {
    private val stateLock = Any()
    private val detailSemaphore = Semaphore(MAX_DETAIL_CONCURRENCY)
    private var activeSelection: SelectionKey? = null
    private var roster: List<RosterVehicle> = emptyList()
    private var rosterRefreshedAt: Instant? = null
    private val sessionIdentities = mutableMapOf<String, SessionIdentity>()
    private val positionCursors = mutableMapOf<String, String>()

    override suspend fun refreshRoster(
        selection: FavoriteSelection,
    ): PreciseDataResult<PreciseRosterSnapshot> {
        val url = baseUrl.newBuilder()
            .addPathSegments("realtime/pos")
            .addPathSegment(selection.routeId)
            .addQueryParameter("routeTCd", "")
            .build()
        return when (val response = requestJson(url)) {
            is JsonResult.Failure -> PreciseDataResult.Failure(response.error)
            is JsonResult.Success -> {
                val items = response.root.verifiedBody()
                    ?.takeIf(JsonElement::isJsonArray)
                    ?.asJsonArray
                    ?: return PreciseDataResult.Failure(BusDataError.MalformedResponse)
                val key = SelectionKey(selection.routeId, selection.directionCode)
                val entries = items.mapNotNull { element ->
                    val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
                    val rawId = item.string("crfId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val routeId = item.string("routeId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val direction = item.string("moveDir")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    if (routeId != selection.routeId || direction != selection.directionCode) return@mapNotNull null
                    RosterEntry(
                        rawId = rawId,
                        stopId = item.string("bsId"),
                        stopSequence = item.int("seq"),
                        arrivalState = item.string("arTime"),
                    )
                }
                val parsed = synchronized(stateLock) {
                    if (activeSelection != null && activeSelection != key) {
                        sessionIdentities.clear()
                        positionCursors.clear()
                    }
                    val now = clock.instant()
                    sessionIdentities.entries.removeAll { (_, identity) ->
                        Duration.between(identity.lastSeenAt, now).seconds > SESSION_RETENTION_SECONDS
                    }
                    positionCursors.keys.retainAll(sessionIdentities.keys)
                    val vehicles = entries.map { entry ->
                        val identity = sessionIdentities[entry.rawId]
                            ?.copy(lastSeenAt = now)
                            ?: SessionIdentity(UUID.randomUUID().toString(), now)
                        sessionIdentities[entry.rawId] = identity
                        RosterVehicle(
                            rawId = entry.rawId,
                            sessionKey = identity.sessionKey,
                            stopId = entry.stopId,
                            stopSequence = entry.stopSequence,
                            arrivalState = entry.arrivalState,
                        )
                    }
                    activeSelection = key
                    roster = vehicles
                    rosterRefreshedAt = now
                    vehicles
                }
                PreciseDataResult.Success(PreciseRosterSnapshot(parsed.size, response.serverTime))
            }
        }
    }

    override suspend fun refreshPositions(
        selection: FavoriteSelection,
    ): PreciseDataResult<PreciseVehicleBatch> {
        val key = SelectionKey(selection.routeId, selection.directionCode)
        val rosterState = synchronized(stateLock) {
            if (activeSelection == key) roster.toList() to rosterRefreshedAt else null
        } ?: return PreciseDataResult.Failure(BusDataError.ServiceUnavailable)
        val refreshedAt = rosterState.second
            ?: return PreciseDataResult.Failure(BusDataError.ServiceUnavailable)
        if (Duration.between(refreshedAt, clock.instant()).seconds > ROSTER_RETENTION_SECONDS) {
            return PreciseDataResult.Failure(BusDataError.ServiceUnavailable)
        }
        val snapshot = rosterState.first
        val results = coroutineScope {
            snapshot.map { vehicle ->
                async {
                    detailSemaphore.withPermit { loadDetail(vehicle, selection) }
                }
            }.awaitAll()
        }
        val positions = results.mapNotNull { (it as? DetailResult.Success)?.position }
        val receivedAt = results.mapNotNull { (it as? DetailResult.Success)?.receivedAt }.maxOrNull()
            ?: clock.instant()
        return PreciseDataResult.Success(
            PreciseVehicleBatch(
                positions = positions,
                rosterCount = snapshot.size,
                failureCount = results.count { it is DetailResult.Failure },
                receivedAt = receivedAt,
                rosterSessionKeys = snapshot.map(RosterVehicle::sessionKey).toSet(),
            ),
        )
    }

    override fun closeSession() {
        synchronized(stateLock) {
            activeSelection = null
            roster = emptyList()
            rosterRefreshedAt = null
            sessionIdentities.clear()
            positionCursors.clear()
        }
    }

    private suspend fun loadDetail(
        vehicle: RosterVehicle,
        selection: FavoriteSelection,
    ): DetailResult {
        val cursor = synchronized(stateLock) { positionCursors[vehicle.rawId] }
        val url = baseUrl.newBuilder()
            .addPathSegments("realtime/vhcPos")
            .addPathSegment(vehicle.rawId)
            .apply { cursor?.let { addQueryParameter("posNo", it) } }
            .build()
        val response = requestJson(url)
        if (response !is JsonResult.Success) return DetailResult.Failure
        val body = response.root.verifiedBody()
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?: return DetailResult.Failure
        val longitude = body.double("xPos") ?: return DetailResult.Failure
        val latitude = body.double("yPos") ?: return DetailResult.Failure
        val gpsTime = body.string("gpsTm") ?: return DetailResult.Failure
        val observedAt = parseGpsInstant(gpsTime, response.serverTime) ?: return DetailResult.Failure
        val position = PreciseVehiclePosition(
            sessionKey = vehicle.sessionKey,
            routeId = body.string("routeId") ?: return DetailResult.Failure,
            moveDirection = body.string("moveDir") ?: return DetailResult.Failure,
            stopId = vehicle.stopId,
            stopSequence = vehicle.stopSequence,
            point = GeoPoint(longitude, latitude),
            observedAt = observedAt,
            // Accubus encodes a full 360-degree bearing in 0..179 half-degree units.
            heading = body.float("heading")?.let { encoded -> (encoded * 2f).normalizeHeading() },
            arrivalState = vehicle.arrivalState,
        )
        if (!position.isValidFor(selection, response.serverTime)) return DetailResult.Failure
        val nextCursor = body.string("posNo")
        if (nextCursor != null) {
            synchronized(stateLock) {
                positionCursors[vehicle.rawId] = nextCursor
            }
        }
        return DetailResult.Success(position, response.serverTime)
    }

    private suspend fun requestJson(url: HttpUrl): JsonResult = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).get().build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resume(JsonResult.Failure(BusDataError.NetworkUnavailable))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { value ->
                        if (!value.isSuccessful) {
                            JsonResult.Failure(BusDataError.ServiceUnavailable)
                        } else {
                            val body = value.body ?: return@use JsonResult.Failure(BusDataError.MalformedResponse)
                            val root = JsonParser.parseReader(body.charStream())
                            val serverTime = value.header("Date")?.let(::parseHttpDate) ?: clock.instant()
                            JsonResult.Success(root, serverTime)
                        }
                    }
                } catch (_: IOException) {
                    JsonResult.Failure(BusDataError.NetworkUnavailable)
                } catch (_: JsonParseException) {
                    JsonResult.Failure(BusDataError.MalformedResponse)
                }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }

    private fun JsonElement.verifiedBody(): JsonElement? {
        if (!isJsonObject) return null
        val header = asJsonObject.get("header")?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return null
        if (header.boolean("success") != true || header.string("resultCode") != SUCCESS_CODE) return null
        return asJsonObject.get("body")?.takeUnless(JsonElement::isJsonNull)
    }

    private fun parseGpsInstant(value: String, serverTime: Instant): Instant? {
        val digits = value.filter(Char::isDigit).padStart(6, '0')
        if (digits.length != 6) return null
        val time = runCatching {
            LocalTime.of(digits.substring(0, 2).toInt(), digits.substring(2, 4).toInt(), digits.substring(4, 6).toInt())
        }.getOrNull() ?: return null
        val serverDate = serverTime.atZone(DAEGU_ZONE).toLocalDate()
        return listOf(serverDate.minusDays(1), serverDate, serverDate.plusDays(1))
            .map { date -> LocalDateTime.of(date, time).atZone(DAEGU_ZONE).toInstant() }
            .minByOrNull { candidate -> kotlin.math.abs(candidate.epochSecond - serverTime.epochSecond) }
    }

    private fun parseHttpDate(value: String): Instant? = runCatching {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
    }.getOrNull()

    private fun Float.normalizeHeading(): Float = ((this % 360f) + 360f) % 360f

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.runCatching { asString }?.getOrNull()

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.runCatching { asInt }?.getOrNull()

    private fun JsonObject.double(name: String): Double? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.runCatching { asDouble }?.getOrNull()?.takeIf(Double::isFinite)

    private fun JsonObject.float(name: String): Float? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.runCatching { asFloat }?.getOrNull()?.takeIf(Float::isFinite)

    private fun JsonObject.boolean(name: String): Boolean? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.runCatching { asBoolean }?.getOrNull()

    private data class SelectionKey(val routeId: String, val directionCode: String)

    private data class SessionIdentity(val sessionKey: String, val lastSeenAt: Instant)

    private data class RosterEntry(
        val rawId: String,
        val stopId: String?,
        val stopSequence: Int?,
        val arrivalState: String?,
    )

    private data class RosterVehicle(
        val rawId: String,
        val sessionKey: String,
        val stopId: String?,
        val stopSequence: Int?,
        val arrivalState: String?,
    )

    private sealed interface JsonResult {
        data class Success(val root: JsonElement, val serverTime: Instant) : JsonResult
        data class Failure(val error: BusDataError) : JsonResult
    }

    private sealed interface DetailResult {
        data class Success(val position: PreciseVehiclePosition, val receivedAt: Instant) : DetailResult
        data object Failure : DetailResult
    }

    private companion object {
        const val SUCCESS_CODE = "0000"
        const val MAX_DETAIL_CONCURRENCY = 4
        const val ROSTER_RETENTION_SECONDS = 30L
        const val SESSION_RETENTION_SECONDS = 30L
        val DAEGU_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

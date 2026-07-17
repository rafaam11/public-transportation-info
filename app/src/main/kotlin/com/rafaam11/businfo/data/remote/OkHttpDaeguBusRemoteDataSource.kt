package com.rafaam11.businfo.data.remote

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.ArrivalEstimate
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.RouteLink
import com.rafaam11.businfo.domain.RouteNode
import com.rafaam11.businfo.domain.VehicleSnapshot
import com.rafaam11.businfo.domain.hasPlausibleDaeguPosition
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OkHttpDaeguBusRemoteDataSource(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    @Suppress("UNUSED_PARAMETER") clock: Clock,
) : DaeguBusRemoteDataSource {
    private val gson = Gson()

    override suspend fun validateKey(serviceKey: String): RemoteResult<Unit> =
        when (val result = request("getBasic02", serviceKey, emptyMap())) {
            is RemoteResult.Success -> if (result.value.isJsonObject) {
                RemoteResult.Success(Unit)
            } else {
                RemoteResult.Failure(BusDataError.MalformedResponse)
            }
            is RemoteResult.Failure -> result
        }

    override suspend fun vehicles(
        serviceKey: String,
        routeId: String,
    ): RemoteResult<List<VehicleSnapshot>> =
        when (val result = request("getPos02", serviceKey, mapOf("routeId" to routeId))) {
            is RemoteResult.Success -> parseVehicles(routeId, result.value)
            is RemoteResult.Failure -> result
        }

    override suspend fun routes(serviceKey: String): RemoteResult<List<RouteSummary>> =
        when (val result = request("getBasic02", serviceKey, emptyMap())) {
            is RemoteResult.Success -> parseRoutes(result.value)
            is RemoteResult.Failure -> result
        }

    override suspend fun routeStops(serviceKey: String, routeId: String): RemoteResult<List<RouteStop>> =
        when (val result = request("getBs02", serviceKey, mapOf("routeId" to routeId))) {
            is RemoteResult.Success -> parseRouteStops(routeId, result.value)
            is RemoteResult.Failure -> result
        }

    override suspend fun basicNodes(serviceKey: String): RemoteResult<List<RouteNode>> =
        when (val result = request("getBasic02", serviceKey, emptyMap())) {
            is RemoteResult.Success -> parseBasicNodes(result.value)
            is RemoteResult.Failure -> result
        }

    override suspend fun routeLinks(serviceKey: String, routeId: String): RemoteResult<List<RouteLink>> =
        when (val result = request("getLink02", serviceKey, mapOf("routeId" to routeId))) {
            is RemoteResult.Success -> parseRouteLinks(routeId, result.value)
            is RemoteResult.Failure -> result
        }

    override suspend fun arrivals(
        serviceKey: String,
        stopId: String,
        routeNo: String,
    ): RemoteResult<List<ArrivalEstimate>> =
        when (val result = request("getRealtime02", serviceKey, mapOf("bsId" to stopId, "routeNo" to routeNo))) {
            is RemoteResult.Success -> parseArrivals(routeNo, result.value)
            is RemoteResult.Failure -> result
        }

    private suspend fun request(
        endpoint: String,
        serviceKey: String,
        parameters: Map<String, String>,
    ): RemoteResult<JsonElement> {
        val urlBuilder = baseUrl.newBuilder()
            .addPathSegment(endpoint)
            .addQueryParameter("serviceKey", serviceKey)
        parameters.forEach { (name, value) -> urlBuilder.addQueryParameter(name, value) }
        val call = client.newCall(Request.Builder().url(urlBuilder.build()).get().build())
        return call.awaitParsedResponse()
    }

    private fun parseResponse(response: Response): RemoteResult<JsonElement> {
        if (!response.isSuccessful) {
            return RemoteResult.Failure(BusDataError.ServiceUnavailable)
        }
        val responseBody = response.body
            ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
        val root = JsonParser.parseReader(responseBody.charStream())
        if (!hasVerifiedEnvelopeTypes(root)) {
            return RemoteResult.Failure(BusDataError.MalformedResponse)
        }
        val envelope = gson.fromJson(root, Envelope::class.java)
            ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
        val header = envelope.header
            ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
        val body = envelope.body
            ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
        val code = header.resultCode
            ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
        val message = header.resultMsg
            ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
        val success = header.success
            ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
        if (!success || code != SUCCESS_CODE) {
            return RemoteResult.Failure(classify(code, message))
        }
        val items = body.items
            ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
        if (items.isJsonNull) {
            return RemoteResult.Failure(BusDataError.MalformedResponse)
        }
        return RemoteResult.Success(items)
    }

    private fun parseVehicles(expectedRouteId: String, items: JsonElement): RemoteResult<List<VehicleSnapshot>> {
        if (!items.isJsonArray) return RemoteResult.Failure(BusDataError.MalformedResponse)
        val array = items.asJsonArray
        if (array.isEmpty) return RemoteResult.Success(emptyList())
        val vehicles = array.mapNotNull(::parseVehicle).filter { vehicle ->
            vehicle.routeId == expectedRouteId && vehicle.hasPlausibleDaeguPosition()
        }
        return if (vehicles.isEmpty()) {
            RemoteResult.Failure(BusDataError.MalformedResponse)
        } else {
            RemoteResult.Success(vehicles)
        }
    }

    private fun parseRoutes(items: JsonElement): RemoteResult<List<RouteSummary>> {
        val routeArray = items.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?.get("route")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
            ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
        val routes = routeArray.mapNotNull { element ->
            val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            RouteSummary(
                routeId = item.string("routeId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                routeNo = item.string("routeNo")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                startName = item.string("stNm").orEmpty(),
                endName = item.string("edNm").orEmpty(),
                directionNote = item.string("dirRouteNote"),
                reverseDirectionNote = item.string("ndirRouteNote"),
            )
        }
        return if (routeArray.size() > 0 && routes.isEmpty()) {
            RemoteResult.Failure(BusDataError.MalformedResponse)
        } else RemoteResult.Success(routes)
    }

    private fun parseRouteStops(routeId: String, items: JsonElement): RemoteResult<List<RouteStop>> {
        if (!items.isJsonArray) return RemoteResult.Failure(BusDataError.MalformedResponse)
        val stops = items.asJsonArray.mapNotNull { element ->
            val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            RouteStop(
                routeId = routeId,
                stopId = item.string("bsId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                stopName = item.string("bsNm")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                moveDirection = item.string("moveDir")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                sequence = item.int("seq") ?: return@mapNotNull null,
                longitude = item.double("xPos") ?: return@mapNotNull null,
                latitude = item.double("yPos") ?: return@mapNotNull null,
            )
        }
        return if (items.asJsonArray.size() > 0 && stops.isEmpty()) {
            RemoteResult.Failure(BusDataError.MalformedResponse)
        } else RemoteResult.Success(stops.sortedWith(compareBy(RouteStop::moveDirection, RouteStop::sequence)))
    }

    private fun parseBasicNodes(items: JsonElement): RemoteResult<List<RouteNode>> {
        val array = items.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?.get("node")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
            ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
        val nodes = array.mapNotNull { element ->
            val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            RouteNode(
                nodeId = item.string("nodeId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                longitude = item.double("xPos") ?: return@mapNotNull null,
                latitude = item.double("yPos") ?: return@mapNotNull null,
            )
        }
        return if (array.size() > 0 && nodes.isEmpty()) {
            RemoteResult.Failure(BusDataError.MalformedResponse)
        } else {
            RemoteResult.Success(nodes)
        }
    }

    private fun parseRouteLinks(routeId: String, items: JsonElement): RemoteResult<List<RouteLink>> {
        if (!items.isJsonArray) return RemoteResult.Failure(BusDataError.MalformedResponse)
        val array = items.asJsonArray
        val links = array.mapNotNull { element ->
            val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            RouteLink(
                routeId = routeId,
                linkId = item.string("linkId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                moveDirection = item.string("moveDir")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                sequence = item.int("linkSeq") ?: return@mapNotNull null,
                startNodeId = item.string("stNode")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                endNodeId = item.string("edNode")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
            )
        }
        return if (array.size() > 0 && links.isEmpty()) {
            RemoteResult.Failure(BusDataError.MalformedResponse)
        } else {
            RemoteResult.Success(links.sortedWith(compareBy(RouteLink::moveDirection, RouteLink::sequence)))
        }
    }

    private fun parseArrivals(routeNo: String, items: JsonElement): RemoteResult<List<ArrivalEstimate>> {
        if (!items.isJsonArray) return RemoteResult.Failure(BusDataError.MalformedResponse)
        val arrivals = items.asJsonArray.flatMap { routeElement ->
            val routeItem = routeElement.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@flatMap emptyList()
            if (routeItem.string("routeNo") != routeNo) return@flatMap emptyList()
            val list = routeItem.get("arrList")?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: return@flatMap emptyList()
            list.mapNotNull { arrivalElement ->
                val item = arrivalElement.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
                if (item.string("routeNo") != routeNo) return@mapNotNull null
                ArrivalEstimate(
                    stopGap = item.int("bsGap") ?: return@mapNotNull null,
                    arrivalSeconds = item.int("arrTime") ?: return@mapNotNull null,
                    state = item.string("arrState"),
                    moveDirection = item.string("moveDir").orEmpty(),
                )
            }
        }.sortedBy(ArrivalEstimate::arrivalSeconds)
        return RemoteResult.Success(arrivals)
    }

    private suspend fun Call.awaitParsedResponse(): RemoteResult<JsonElement> =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(RemoteResult.Failure(BusDataError.NetworkUnavailable))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use {
                            if (!continuation.isActive) return
                            parseResponse(it)
                        }
                    } catch (_: IOException) {
                        RemoteResult.Failure(BusDataError.NetworkUnavailable)
                    } catch (_: JsonParseException) {
                        RemoteResult.Failure(BusDataError.MalformedResponse)
                    } catch (exception: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                        return
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }

    private fun hasVerifiedEnvelopeTypes(root: JsonElement): Boolean {
        if (!root.isJsonObject) return false
        val header = root.asJsonObject.get("header")?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return false
        val body = root.asJsonObject.get("body")?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return false
        val resultCode = header.get("resultCode")?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive ?: return false
        val resultMsg = header.get("resultMsg")?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive ?: return false
        val success = header.get("success")?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive ?: return false
        val items = body.get("items") ?: return false
        return resultCode.isString && resultMsg.isString && success.isBoolean && !items.isJsonNull
    }

    private fun parseVehicle(element: JsonElement): VehicleSnapshot? {
        if (!element.isJsonObject) return null
        val item = element.asJsonObject
        val routeId = item.string("routeId")?.takeIf(String::isNotBlank) ?: return null
        val longitude = item.double("xPos") ?: return null
        val latitude = item.double("yPos") ?: return null
        return VehicleSnapshot(
            routeId = routeId,
            routeNo = item.string("routeNo").orEmpty(),
            moveDirection = item.string("moveDir").orEmpty(),
            stopId = item.string("bsId"),
            stopSequence = item.int("seq"),
            latitude = latitude,
            longitude = longitude,
            arrivalState = item.string("arTime"),
            busTypeCode2 = item.string("busTCd2"),
            busTypeCode3 = item.string("busTCd3"),
        )
    }

    private fun classify(code: String, message: String): BusDataError = when {
        code == "9003" -> BusDataError.InvalidCredential
        message.contains("한도") || message.contains("초과") -> BusDataError.RateLimited
        else -> BusDataError.ServiceUnavailable
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.runCatching { asString }?.getOrNull()

    private fun JsonObject.double(name: String): Double? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.runCatching { asDouble }?.getOrNull()?.takeIf(Double::isFinite)

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.runCatching { asInt }?.getOrNull()

    private data class Envelope(
        val header: Header?,
        val body: Body?,
    )

    private data class Header(
        val resultCode: String?,
        val resultMsg: String?,
        val success: Boolean?,
    )

    private data class Body(val items: JsonElement?)

    private companion object {
        const val SUCCESS_CODE = "0000"
    }
}

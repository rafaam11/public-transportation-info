package com.rafaam11.businfo.data.remote

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.VehicleSnapshot
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

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
            is RemoteResult.Success -> parseVehicles(result.value)
            is RemoteResult.Failure -> result
        }

    private suspend fun request(
        endpoint: String,
        serviceKey: String,
        parameters: Map<String, String>,
    ): RemoteResult<JsonElement> = try {
        withContext(Dispatchers.IO) {
            val urlBuilder = baseUrl.newBuilder()
                .addPathSegment(endpoint)
                .addQueryParameter("serviceKey", serviceKey)
            parameters.forEach { (name, value) -> urlBuilder.addQueryParameter(name, value) }
            val call = client.newCall(Request.Builder().url(urlBuilder.build()).get().build())

            call.execute().use { response ->
                ensureActive()
                if (!response.isSuccessful) {
                    return@withContext RemoteResult.Failure(BusDataError.ServiceUnavailable)
                }
                val responseBody = response.body
                    ?: return@withContext RemoteResult.Failure(BusDataError.MalformedResponse)
                val envelope = gson.fromJson(responseBody.charStream(), Envelope::class.java)
                    ?: return@withContext RemoteResult.Failure(BusDataError.MalformedResponse)
                val header = envelope.header
                    ?: return@withContext RemoteResult.Failure(BusDataError.MalformedResponse)
                val body = envelope.body
                    ?: return@withContext RemoteResult.Failure(BusDataError.MalformedResponse)
                val code = header.resultCode
                    ?: return@withContext RemoteResult.Failure(BusDataError.MalformedResponse)
                val message = header.resultMsg
                    ?: return@withContext RemoteResult.Failure(BusDataError.MalformedResponse)
                val success = header.success
                    ?: return@withContext RemoteResult.Failure(BusDataError.MalformedResponse)
                if (!success || code != SUCCESS_CODE) {
                    return@withContext RemoteResult.Failure(classify(code, message))
                }
                val items = body.items
                    ?: return@withContext RemoteResult.Failure(BusDataError.MalformedResponse)
                if (items.isJsonNull) {
                    return@withContext RemoteResult.Failure(BusDataError.MalformedResponse)
                }
                RemoteResult.Success(items)
            }
        }
    } catch (_: IOException) {
        RemoteResult.Failure(BusDataError.NetworkUnavailable)
    } catch (_: JsonParseException) {
        RemoteResult.Failure(BusDataError.MalformedResponse)
    }

    private fun parseVehicles(items: JsonElement): RemoteResult<List<VehicleSnapshot>> {
        if (!items.isJsonArray) return RemoteResult.Failure(BusDataError.MalformedResponse)
        return RemoteResult.Success(items.asJsonArray.mapNotNull(::parseVehicle))
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

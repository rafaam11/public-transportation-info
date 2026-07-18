package com.rafaam11.businfo.data.remote

import com.google.gson.Gson
import com.rafaam11.businfo.data.PlaceSearchDataSource
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.PlaceResult
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume

class OkHttpPlaceSearchDataSource(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl?,
) : PlaceSearchDataSource {
    private val gson = Gson()

    override suspend fun search(query: String): Result<List<PlaceResult>> {
        val origin = baseUrl ?: return Result.failure(PlaceSearchUnavailableException())
        val url = origin.newBuilder().addPathSegments("v1/places").addQueryParameter("q", query).build()
        return client.newCall(Request.Builder().url(url).get().build()).await().mapCatching { response ->
            response.use {
                if (!it.isSuccessful) throw PlaceSearchUnavailableException()
                val body = it.body?.charStream() ?: throw PlaceSearchUnavailableException()
                val payload = gson.fromJson(body, PlacePayload::class.java) ?: throw PlaceSearchUnavailableException()
                payload.items.orEmpty().mapNotNull(PlaceItem::toDomain)
            }
        }
    }

    private suspend fun Call.await(): Result<Response> = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(Result.success(response)) else response.close()
            }
        })
    }

    private data class PlacePayload(val items: List<PlaceItem>?)
    private data class PlaceItem(
        val name: String?,
        val category: String?,
        val address: String?,
        val roadAddress: String?,
        val latitude: Double?,
        val longitude: Double?,
    ) {
        fun toDomain(): PlaceResult? {
            val safeName = name?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val lat = latitude?.takeIf { it.isFinite() && it in 35.3..36.3 } ?: return null
            val lng = longitude?.takeIf { it.isFinite() && it in 128.0..129.2 } ?: return null
            return PlaceResult(safeName, category.orEmpty(), address.orEmpty(), roadAddress.orEmpty(), GeoPoint(lng, lat))
        }
    }
}

class PlaceSearchUnavailableException : IOException("Place search is unavailable")

package com.rafaam11.businfo.probe

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class ProbeResponse(val statusCode: Int, val body: String, val requestSummary: String)

class DaeguApiProbe(
    private val baseUrl: HttpUrl = "https://apis.data.go.kr/6270000/dbmsapi02/".toHttpUrl(),
    private val serviceKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun execute(command: ProbeCommand): ProbeResponse {
        val url = baseUrl.newBuilder()
            .addPathSegment(command.endpoint)
            .addQueryParameter("serviceKey", serviceKey)
            .apply {
                command.parameters.forEach { (name, value) -> addQueryParameter(name, value) }
            }
            .build()
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            return ProbeResponse(
                statusCode = response.code,
                body = requireNotNull(response.body).string(),
                requestSummary = url.newBuilder()
                    .removeAllQueryParameters("serviceKey")
                    .addQueryParameter("serviceKey", "[redacted]")
                    .build()
                    .toString(),
            )
        }
    }
}

package com.rafaam11.businfo.probe

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class ProbeResponse(val statusCode: Int, val body: String, val requestSummary: String)

class DaeguApiProbe(
    private val baseUrl: HttpUrl = "https://apis.data.go.kr/6270000/dbmsapi02/".toHttpUrl(),
    serviceKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val serviceKey = serviceKey.trim()

    init {
        require(this.serviceKey.isNotEmpty()) { "DAEGU_BUS_SERVICE_KEY is blank" }
    }

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
                requestSummary = sanitizedRequestSummary(command),
            )
        }
    }

    private fun sanitizedRequestSummary(command: ProbeCommand): String = baseUrl.newBuilder()
        .addPathSegment(command.endpoint)
        .addQueryParameter("serviceKey", "[redacted]")
        .apply {
            command.parameters.forEach { (name, value) ->
                addQueryParameter(name, if (SensitiveNamePolicy.isSensitive(name)) "[redacted]" else value)
            }
        }
        .build()
        .toString()
}

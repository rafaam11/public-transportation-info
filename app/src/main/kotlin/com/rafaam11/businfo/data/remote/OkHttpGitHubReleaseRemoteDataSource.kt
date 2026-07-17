package com.rafaam11.businfo.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.rafaam11.businfo.domain.BusDataError
import java.io.IOException
import java.time.Clock
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OkHttpGitHubReleaseRemoteDataSource(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    @Suppress("UNUSED_PARAMETER") clock: Clock,
) : GitHubReleaseDataSource {
    override suspend fun latestRelease(): RemoteResult<GitHubRelease?> {
        val url = baseUrl.newBuilder().addPathSegments("releases/latest").build()
        val request = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "daegu-bus-android")
            .get()
            .build()
        return client.newCall(request).awaitRelease()
    }

    private suspend fun Call.awaitRelease(): RemoteResult<GitHubRelease?> =
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
                        response.use(::parseResponse)
                    } catch (_: IOException) {
                        RemoteResult.Failure(BusDataError.NetworkUnavailable)
                    } catch (_: JsonParseException) {
                        RemoteResult.Failure(BusDataError.MalformedResponse)
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }

    private fun parseResponse(response: Response): RemoteResult<GitHubRelease?> {
        if (response.code == 404) return RemoteResult.Success(null)
        if (!response.isSuccessful) {
            if (response.code == 403 && response.header("X-RateLimit-Remaining") == "0") {
                return RemoteResult.Failure(BusDataError.RateLimited)
            }
            return RemoteResult.Failure(BusDataError.ServiceUnavailable)
        }
        val body = response.body ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
        val root = JsonParser.parseReader(body.charStream())
        if (!root.isJsonObject) return RemoteResult.Failure(BusDataError.MalformedResponse)
        val json = root.asJsonObject
        val tagName = json.string("tag_name")?.takeIf(String::isNotBlank)
            ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
        val htmlUrl = json.string("html_url").orEmpty()
        val assetsArray = json.get("assets")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
            ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
        val assets = assetsArray.mapNotNull { element ->
            val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            val name = item.string("name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val downloadUrl = item.string("browser_download_url")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            GitHubReleaseAsset(name, downloadUrl)
        }
        val apkAsset = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
        return RemoteResult.Success(GitHubRelease(tagName = tagName, htmlUrl = htmlUrl, assets = listOf(apkAsset)))
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.runCatching { asString }?.getOrNull()
}

package com.rafaam11.businfo.data.remote

import com.rafaam11.businfo.domain.BusDataError
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpGitHubReleaseRemoteDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var source: OkHttpGitHubReleaseRemoteDataSource

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        source = OkHttpGitHubReleaseRemoteDataSource(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
            clock = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `successful response maps tag and apk asset`() = runTest {
        server.enqueue(MockResponse().setBody(RELEASE_WITH_APK))

        val result = source.latestRelease()

        assertTrue(result is RemoteResult.Success)
        val release = (result as RemoteResult.Success).value!!
        assertEquals("v0.6.0", release.tagName)
        assertEquals("https://github.com/rafaam11/public-transportation-info/releases/tag/v0.6.0", release.htmlUrl)
        assertEquals("daegu-bus-0.6.0.apk", release.assets.single().name)
        assertEquals("https://github.com/rafaam11/public-transportation-info/releases/download/v0.6.0/daegu-bus-0.6.0.apk", release.assets.single().downloadUrl)
        assertEquals("daegu-bus-android", server.takeRequest().getHeader("User-Agent"))
    }

    @Test fun `404 means no published release yet`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        assertEquals(RemoteResult.Success(null), source.latestRelease())
    }

    @Test fun `rate limited 403 is classified separately from other failures`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setHeader("X-RateLimit-Remaining", "0"))

        assertEquals(RemoteResult.Failure(BusDataError.RateLimited), source.latestRelease())
    }

    @Test fun `server error is service unavailable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(RemoteResult.Failure(BusDataError.ServiceUnavailable), source.latestRelease())
    }

    @Test fun `release without an apk asset is malformed`() = runTest {
        server.enqueue(MockResponse().setBody(RELEASE_WITHOUT_APK))

        val result = source.latestRelease()

        assertTrue(result is RemoteResult.Failure)
        assertEquals(BusDataError.MalformedResponse, (result as RemoteResult.Failure).error)
    }

    @Test fun `missing tag name is malformed`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))

        val result = source.latestRelease()

        assertTrue(result is RemoteResult.Failure)
        assertEquals(BusDataError.MalformedResponse, (result as RemoteResult.Failure).error)
    }

    private companion object {
        const val RELEASE_WITH_APK = """
            {
              "tag_name": "v0.6.0",
              "html_url": "https://github.com/rafaam11/public-transportation-info/releases/tag/v0.6.0",
              "assets": [
                {
                  "name": "daegu-bus-0.6.0.apk",
                  "browser_download_url": "https://github.com/rafaam11/public-transportation-info/releases/download/v0.6.0/daegu-bus-0.6.0.apk"
                }
              ]
            }
        """

        const val RELEASE_WITHOUT_APK = """
            {
              "tag_name": "v0.6.0",
              "html_url": "https://github.com/rafaam11/public-transportation-info/releases/tag/v0.6.0",
              "assets": []
            }
        """
    }
}

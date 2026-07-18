package com.rafaam11.businfo.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpPlaceSearchDataSourceTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test fun `search encodes query and parses worker contract`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"items":[{"name":"동대구역","category":"기차역","address":"대구 동구","roadAddress":"동대구로 550","latitude":35.879612,"longitude":128.62792}]}
        """.trimIndent()).addHeader("Content-Type", "application/json"))
        val source = OkHttpPlaceSearchDataSource(client(), server.url("/"))

        val result = source.search("동대구 역").getOrThrow()

        assertEquals("동대구역", result.single().name)
        assertEquals(35.879612, result.single().point.latitude, 0.0)
        assertEquals("동대구 역", server.takeRequest().requestUrl?.queryParameter("q"))
    }

    @Test fun `non success worker response is a failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{\"error\":\"rate_limited\"}"))
        val source = OkHttpPlaceSearchDataSource(client(), server.url("/"))

        assertTrue(source.search("수성못").isFailure)
    }

    private fun client() = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()
}

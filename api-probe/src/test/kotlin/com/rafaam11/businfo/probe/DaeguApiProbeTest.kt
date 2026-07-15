package com.rafaam11.businfo.probe

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaeguApiProbeTest {
    @Test
    fun serviceKeyIsSentButNeverRendered() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"items\":[]}"))
            server.start()

            val result = DaeguApiProbe(server.url("/"), "secret-value").execute(
                ProbeCommand("getPos02", mapOf("routeId" to "123")),
            )

            assertTrue(server.takeRequest().requestUrl!!.queryParameter("serviceKey") == "secret-value")
            assertFalse(result.requestSummary.contains("secret-value"))
        }
    }
}

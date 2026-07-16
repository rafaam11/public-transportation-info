package com.rafaam11.businfo.probe

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

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

    @Test
    fun rejectsBlankServiceKeyBeforeNetwork() {
        MockWebServer().use { server ->
            server.start()

            assertThrows(IllegalArgumentException::class.java) {
                DaeguApiProbe(server.url("/"), "   ")
            }
            assertTrue(server.requestCount == 0)
        }
    }

    @Test
    fun sensitiveRuntimeQueryValuesAreAbsentFromSummaryAndReport() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"items\":[]}"))
            server.start()
            val response = DaeguApiProbe(server.url("/"), "local-key").execute(
                ProbeCommand("getPos02", mapOf("token" to "runtime-secret")),
            )
            val projectDir = Files.createTempDirectory("api-probe-report")

            val report = ProbeReportWriter.write(
                ProbeCommand("getPos02", mapOf("token" to "runtime-secret")),
                response,
                "$.items | array | []",
                projectDir,
            )

            assertFalse(response.requestSummary.contains("local-key"))
            assertFalse(response.requestSummary.contains("runtime-secret"))
            assertFalse(Files.readString(report).contains("local-key"))
            assertFalse(Files.readString(report).contains("runtime-secret"))
        }
    }

    @Test
    fun exposedParametersCannotBeMutatedBeforeRequest() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"items\":[]}"))
            server.start()
            val command = ProbeCommand(
                "getPos02",
                linkedMapOf("routeId" to "123", "stopId" to "456"),
            )
            @Suppress("UNCHECKED_CAST")
            val exposed = command.parameters as MutableMap<String, String>

            assertThrows(UnsupportedOperationException::class.java) { exposed["routeId"] = "   " }
            assertThrows(UnsupportedOperationException::class.java) { exposed["Service-Key"] = "injected-secret" }
            assertThrows(UnsupportedOperationException::class.java) { exposed["unexpected"] = "unexpected-value" }

            DaeguApiProbe(server.url("/"), "local-key").execute(command)
            val requestUrl = server.takeRequest().requestUrl!!
            assertTrue(requestUrl.queryParameter("routeId") == "123")
            assertTrue(requestUrl.queryParameter("stopId") == "456")
            assertTrue(requestUrl.queryParameter("Service-Key") == null)
            assertTrue(requestUrl.queryParameter("unexpected") == null)
        }
    }
}

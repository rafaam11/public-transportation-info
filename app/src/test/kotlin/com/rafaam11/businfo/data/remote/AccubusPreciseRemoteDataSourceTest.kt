package com.rafaam11.businfo.data.remote

import com.rafaam11.businfo.data.PreciseDataResult
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteSelection
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccubusPreciseRemoteDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var source: AccubusPreciseRemoteDataSource
    private lateinit var clock: MutableClock
    private val selection = FavoriteSelection(
        CommuteSlot.MORNING,
        "route",
        "급행8-1",
        "0",
        "검단동 방면",
        "target",
        "효동초등학교건너",
    )

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        clock = MutableClock(Instant.parse("2026-07-17T11:20:46Z"))
        source = AccubusPreciseRemoteDataSource(
            client = OkHttpClient(),
            baseUrl = server.url("/dbms_web_api/"),
            clock = clock,
        )
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `roster filters direction and detail exposes only opaque confirmed GPS`() = runTest {
        server.enqueue(json(roster(item("secret-crf-1", "0"), item("secret-crf-2", "1"))))
        server.enqueue(json(detail("secret-crf-1", "0", "202045", 128.611, 35.811, 45)))

        val roster = source.refreshRoster(selection) as PreciseDataResult.Success
        val batch = (source.refreshPositions(selection) as PreciseDataResult.Success).value

        assertEquals(1, roster.value.vehicleCount)
        assertEquals(1, batch.rosterCount)
        assertEquals(0, batch.failureCount)
        assertEquals(1, batch.positions.size)
        val position = batch.positions.single()
        assertEquals(128.611, position.point.longitude, 0.000001)
        assertEquals(35.811, position.point.latitude, 0.000001)
        assertEquals(Instant.parse("2026-07-17T11:20:45Z"), position.observedAt)
        assertEquals(90f, position.heading)
        assertNotEquals("secret-crf-1", position.sessionKey)
        assertFalse(position.sessionKey.contains("secret-crf-1"))
        assertEquals("/dbms_web_api/realtime/pos/route?routeTCd=", server.takeRequest().path)
        assertEquals("/dbms_web_api/realtime/vhcPos/secret-crf-1", server.takeRequest().path)
    }

    @Test fun `one failed vehicle detail preserves successful positions`() = runTest {
        server.enqueue(json(roster(item("crf-a", "0"), item("crf-b", "0"))))
        server.enqueue(json(detail("crf-a", "0", "202045", 128.611, 35.811, 90)))
        server.enqueue(MockResponse().setResponseCode(503))

        source.refreshRoster(selection)
        val batch = (source.refreshPositions(selection) as PreciseDataResult.Success).value

        assertEquals(2, batch.rosterCount)
        assertEquals(1, batch.failureCount)
        assertEquals(1, batch.positions.size)
    }

    @Test fun `session key stays stable until session is closed`() = runTest {
        server.enqueue(json(roster(item("crf-a", "0"))))
        server.enqueue(json(detail("crf-a", "0", "202045", 128.611, 35.811, 90)))
        source.refreshRoster(selection)
        val first = (source.refreshPositions(selection) as PreciseDataResult.Success).value.positions.single().sessionKey

        server.enqueue(json(roster(item("crf-a", "0"))))
        server.enqueue(json(detail("crf-a", "0", "202046", 128.612, 35.812, 90)))
        source.refreshRoster(selection)
        val second = (source.refreshPositions(selection) as PreciseDataResult.Success).value.positions.single().sessionKey
        assertEquals(first, second)
        repeat(3) { server.takeRequest() }
        assertEquals(
            "/dbms_web_api/realtime/vhcPos/crf-a?posNo=12",
            server.takeRequest().path,
        )

        source.closeSession()
        server.enqueue(json(roster(item("crf-a", "0"))))
        server.enqueue(json(detail("crf-a", "0", "202046", 128.612, 35.812, 90)))
        source.refreshRoster(selection)
        val third = (source.refreshPositions(selection) as PreciseDataResult.Success).value.positions.single().sessionKey
        assertNotEquals(first, third)
    }

    @Test fun `temporary roster omission does not change opaque session key`() = runTest {
        server.enqueue(json(roster(item("crf-a", "0"))))
        server.enqueue(json(detail("crf-a", "0", "202045", 128.611, 35.811, 90)))
        source.refreshRoster(selection)
        val first = (source.refreshPositions(selection) as PreciseDataResult.Success)
            .value.positions.single().sessionKey

        server.enqueue(json(roster()))
        source.refreshRoster(selection)
        clock.advanceSeconds(15)

        server.enqueue(json(roster(item("crf-a", "0"))))
        server.enqueue(json(detail("crf-a", "0", "202046", 128.612, 35.812, 90)))
        source.refreshRoster(selection)
        val returned = (source.refreshPositions(selection) as PreciseDataResult.Success)
            .value.positions.single().sessionKey

        assertEquals(first, returned)
    }

    @Test fun `malformed and mismatched detail is counted as failure`() = runTest {
        server.enqueue(json(roster(item("crf-a", "0"), item("crf-b", "0"))))
        server.enqueue(json(detail("crf-a", "1", "202045", 128.611, 35.811, 90)))
        server.enqueue(json(detail("crf-b", "0", "202045", 127.0, 35.811, 90)))
        source.refreshRoster(selection)

        val result = source.refreshPositions(selection) as PreciseDataResult.Success

        assertTrue(result.value.positions.isEmpty())
        assertEquals(2, result.value.failureCount)
    }

    @Test fun `roster older than thirty seconds is not used for detail requests`() = runTest {
        server.enqueue(json(roster(item("crf-a", "0"))))
        server.enqueue(json(detail("crf-a", "0", "202116", 128.611, 35.811, 90)))
        source.refreshRoster(selection)
        clock.advanceSeconds(31)

        val result = source.refreshPositions(selection)

        assertTrue(result is PreciseDataResult.Failure)
        assertEquals(1, server.requestCount)
    }

    @Test fun `detail requests never exceed four concurrent calls`() = runTest {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path?.contains("/realtime/pos/") == true) {
                    return json(roster(*(1..6).map { item("crf-$it", "0") }.toTypedArray()))
                }
                val nowActive = active.incrementAndGet()
                maximum.accumulateAndGet(nowActive, ::maxOf)
                Thread.sleep(50)
                active.decrementAndGet()
                return json(detail("ignored", "0", "202045", 128.611, 35.811, 90))
            }
        }

        source.refreshRoster(selection)
        val batch = (source.refreshPositions(selection) as PreciseDataResult.Success).value

        assertEquals(6, batch.positions.size)
        assertTrue(maximum.get() in 2..4)
    }

    @Test fun `vehicles that already passed target never trigger detail requests`() = runTest {
        server.enqueue(json(roster(item("approaching", "0", 4), item("passed", "0", 6))))
        server.enqueue(json(detail("approaching", "0", "202045", 128.611, 35.811, 90)))
        source.configureTargetStopSequence(5)

        source.refreshRoster(selection)
        val batch = (source.refreshPositions(selection) as PreciseDataResult.Success).value

        assertEquals(1, batch.rosterCount)
        assertEquals(1, batch.positions.size)
        assertEquals(2, server.requestCount)
        assertTrue(server.takeRequest().path!!.contains("/realtime/pos/"))
        assertTrue(server.takeRequest().path!!.contains("/realtime/vhcPos/approaching"))
    }

    private fun json(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setHeader("Date", "Fri, 17 Jul 2026 11:20:46 GMT")
        .setBody(body)

    private fun roster(vararg items: String) = envelope("[${items.joinToString(",")}]")

    private fun item(crfId: String, direction: String, sequence: Int = 5) = """
        {"crfId":"$crfId","routeId":"route","moveDir":"$direction","arTime":"곧 도착","seq":$sequence,"bsId":"nearby"}
    """.trimIndent()

    private fun detail(
        crfId: String,
        direction: String,
        gpsTime: String,
        longitude: Double,
        latitude: Double,
        heading: Int,
    ) = envelope(
        """{"posNo":12,"crfId":"$crfId","routeId":"route","xPos":$longitude,"yPos":$latitude,"heading":$heading,"speed":20,"moveDir":"$direction","gpsTm":"$gpsTime","vertexList":[]}""",
    )

    private fun envelope(body: String) =
        """{"header":{"success":true,"resultCode":"0000","resultMsg":"성공"},"body":$body}"""

    private class MutableClock(private var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
        fun advanceSeconds(seconds: Long) { current = current.plusSeconds(seconds) }
    }
}

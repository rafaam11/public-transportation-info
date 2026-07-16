package com.rafaam11.businfo.data.remote

import com.rafaam11.businfo.domain.BusDataError
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpDaeguBusRemoteDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var source: OkHttpDaeguBusRemoteDataSource

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        source = OkHttpDaeguBusRemoteDataSource(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
            clock = Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun successfulValidationUsesBasicEndpoint() = runTest {
        server.enqueue(MockResponse().setBody(BASIC_SUCCESS))

        assertEquals(RemoteResult.Success(Unit), source.validateKey("secret"))
        val requestUrl = server.takeRequest().requestUrl!!
        assertEquals("/getBasic02", requestUrl.encodedPath)
        assertEquals(setOf("serviceKey"), requestUrl.queryParameterNames)
        assertTrue(requestUrl.queryParameterNames.contains("serviceKey"))
    }

    @Test fun vehicleResponseMapsVerifiedCoordinatesAndQuery() = runTest {
        server.enqueue(MockResponse().setBody(VEHICLE_SUCCESS))

        val result = source.vehicles("secret", "3000814001")

        assertTrue(result is RemoteResult.Success)
        val vehicle = (result as RemoteResult.Success).value.single()
        assertEquals("3000814001", vehicle.routeId)
        assertEquals("814", vehicle.routeNo)
        assertEquals("0", vehicle.moveDirection)
        assertEquals("7011000100", vehicle.stopId)
        assertEquals(12, vehicle.stopSequence)
        assertEquals(35.8, vehicle.latitude, 0.0)
        assertEquals(128.6, vehicle.longitude, 0.0)
        assertEquals("45", vehicle.arrivalState)
        assertEquals("1", vehicle.busTypeCode2)
        assertEquals("2", vehicle.busTypeCode3)
        val requestUrl = server.takeRequest().requestUrl!!
        assertEquals(setOf("serviceKey", "routeId"), requestUrl.queryParameterNames)
        assertTrue(requestUrl.queryParameterNames.contains("serviceKey"))
        assertEquals("3000814001", requestUrl.queryParameter("routeId"))
    }

    @Test fun emptyVehicleItemsReturnsEmptyList() = runTest {
        server.enqueue(MockResponse().setBody(EMPTY_SUCCESS))

        assertEquals(RemoteResult.Success(emptyList<Any>()), source.vehicles("secret", "3000814001"))
    }

    @Test fun partiallyValidVehicleItemsDiscardOnlyInvalidRows() = runTest {
        server.enqueue(MockResponse().setBody(successEnvelope("[$VALID_VEHICLE,{\"routeId\":\"\"}]")))

        val result = source.vehicles("secret", "3000814001")

        assertTrue(result is RemoteResult.Success)
        assertEquals("3000814001", (result as RemoteResult.Success).value.single().routeId)
    }

    @Test fun whollyInvalidNonemptyVehicleItemsAreMalformed() = runTest {
        server.enqueue(MockResponse().setBody(successEnvelope("[{\"routeId\":\"\"},{\"xPos\":128.6}]")))

        assertEquals(
            RemoteResult.Failure(BusDataError.MalformedResponse),
            source.vehicles("secret", "3000814001"),
        )
    }

    @Test fun cancellingCoroutineCancelsRealCallAndFreesDispatcherForNextRequest() = runBlocking {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 1
            maxRequestsPerHost = 1
        }
        val cancellableSource = OkHttpDaeguBusRemoteDataSource(
            client = OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .callTimeout(10, TimeUnit.SECONDS)
                .build(),
            baseUrl = server.url("/"),
            clock = Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC),
        )
        server.enqueue(MockResponse().setHeadersDelay(3, TimeUnit.SECONDS).setBody(BASIC_SUCCESS))
        server.enqueue(MockResponse().setBody(BASIC_SUCCESS))

        val first = launch(Dispatchers.Default) { cancellableSource.validateKey("cancelled") }
        server.takeRequest(2, TimeUnit.SECONDS) ?: error("first request was not received")
        val cancelStartedAt = System.nanoTime()
        first.cancelAndJoin()
        val cancelElapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cancelStartedAt)
        val second = async(Dispatchers.Default) { cancellableSource.validateKey("next") }
        val secondRequest = server.takeRequest(2, TimeUnit.SECONDS)

        assertTrue("cancellation took $cancelElapsedMillis ms", cancelElapsedMillis < 2_000)
        assertTrue("second request stayed queued behind cancelled call", secondRequest != null)
        assertEquals(RemoteResult.Success(Unit), second.await())
    }

    @Test fun cancellingDuringResponseBodyCancelsCallAndFreesDispatcherForNextRequest() = runBlocking {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 1
            maxRequestsPerHost = 1
        }
        val callCancelled = AtomicBoolean(false)
        val bodyCancellableSource = OkHttpDaeguBusRemoteDataSource(
            client = OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .eventListener(object : EventListener() {
                    override fun canceled(call: Call) {
                        callCancelled.set(true)
                    }
                })
                .callTimeout(10, TimeUnit.SECONDS)
                .build(),
            baseUrl = server.url("/"),
            clock = Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC),
        )
        val slowBody = successEnvelope("{\"padding\":\"${"x".repeat(32 * 1024)}\"}")
        server.enqueue(
            MockResponse()
                .setBody(slowBody)
                .throttleBody(1_024, 250, TimeUnit.MILLISECONDS),
        )
        server.enqueue(MockResponse().setBody(BASIC_SUCCESS))

        val first = launch(Dispatchers.Default) { bodyCancellableSource.validateKey("cancelled") }
        server.takeRequest(2, TimeUnit.SECONDS) ?: error("body-delayed request was not received")
        delay(300)
        val cancelStartedAt = System.nanoTime()
        first.cancelAndJoin()
        val cancelElapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cancelStartedAt)
        val second = async(Dispatchers.Default) { bodyCancellableSource.validateKey("next") }
        val secondRequest = server.takeRequest(2, TimeUnit.SECONDS)

        assertTrue("body cancellation took $cancelElapsedMillis ms", cancelElapsedMillis < 2_000)
        assertTrue("OkHttp call was not cancelled during body consumption", callCancelled.get())
        assertTrue("second request stayed queued behind body consumption", secondRequest != null)
        assertEquals(RemoteResult.Success(Unit), second.await())
    }

    @Test fun sensitiveVehicleFieldIsDiscardedAtParsingBoundary() = runTest {
        server.enqueue(MockResponse().setBody(VEHICLE_SUCCESS))

        val vehicle = (source.vehicles("secret", "3000814001") as RemoteResult.Success).value.single()
        val fields = vehicle::class.java.declaredFields.map { it.name }

        assertFalse(fields.any { it.contains("vhc", ignoreCase = true) || it.contains("vehicleNo", ignoreCase = true) })
    }

    @Test fun http200WithFailureHeaderIsNotSuccess() = runTest {
        server.enqueue(MockResponse().setBody(FAILURE_HEADER))

        assertEquals(RemoteResult.Failure(BusDataError.InvalidCredential), source.validateKey("secret"))
    }

    @Test fun nullItemsIsMalformedResponse() = runTest {
        server.enqueue(MockResponse().setBody(NULL_ITEMS))

        assertEquals(RemoteResult.Failure(BusDataError.MalformedResponse), source.validateKey("secret"))
    }

    @Test fun basicItemsMustBeAnObject() = runTest {
        listOf("\"not-an-object\"", "42", "[]").forEach { items ->
            server.enqueue(MockResponse().setBody(successEnvelope(items)))

            assertEquals(RemoteResult.Failure(BusDataError.MalformedResponse), source.validateKey("secret"))
        }
    }

    @Test fun requiredHeaderScalarsMustHaveVerifiedJsonTypes() = runTest {
        listOf(
            headerEnvelope(resultCode = "0", resultMsg = "\"success\"", success = "true"),
            headerEnvelope(resultCode = "\"0000\"", resultMsg = "true", success = "true"),
            headerEnvelope(resultCode = "\"0000\"", resultMsg = "\"success\"", success = "\"true\""),
        ).forEach { response ->
            server.enqueue(MockResponse().setBody(response))

            assertEquals(RemoteResult.Failure(BusDataError.MalformedResponse), source.validateKey("secret"))
        }
    }

    private companion object {
        const val BASIC_SUCCESS = """
            {"header":{"resultCode":"0000","resultMsg":"success","success":true},
             "body":{"totalCount":0,"items":{"route":[],"bs":[],"node":[]}}}
        """
        const val VEHICLE_SUCCESS = """
            {"header":{"resultCode":"0000","resultMsg":"success","success":true},
             "body":{"totalCount":1,"items":[{"routeId":"3000814001","routeNo":"814","moveDir":"0",
             "arTime":"45","seq":12,"bsId":"7011000100","xPos":128.6,"yPos":35.8,
             "busTCd2":"1","busTCd3":"2","vhcNo2":"sensitive-vehicle-id"}]}}
        """
        const val VALID_VEHICLE = """{"routeId":"3000814001","routeNo":"814","moveDir":"0","arTime":"45","seq":12,"bsId":"7011000100","xPos":128.6,"yPos":35.8,"busTCd2":"1","busTCd3":"2"}"""
        const val EMPTY_SUCCESS = """
            {"header":{"resultCode":"0000","resultMsg":"success","success":true},
             "body":{"totalCount":0,"items":[]}}
        """
        const val FAILURE_HEADER = """
            {"header":{"resultCode":"9003","resultMsg":"error","success":false},
             "body":{"totalCount":0,"items":[]}}
        """
        const val NULL_ITEMS = """
            {"header":{"resultCode":"0000","resultMsg":"success","success":true},
             "body":{"totalCount":0,"items":null}}
        """

        fun successEnvelope(items: String) = """
            {"header":{"resultCode":"0000","resultMsg":"success","success":true},
             "body":{"totalCount":0,"items":$items}}
        """

        fun headerEnvelope(resultCode: String, resultMsg: String, success: String) = """
            {"header":{"resultCode":$resultCode,"resultMsg":$resultMsg,"success":$success},
             "body":{"totalCount":0,"items":{"route":[],"bs":[],"node":[]}}}
        """
    }
}

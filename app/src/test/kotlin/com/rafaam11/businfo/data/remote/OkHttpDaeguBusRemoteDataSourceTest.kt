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

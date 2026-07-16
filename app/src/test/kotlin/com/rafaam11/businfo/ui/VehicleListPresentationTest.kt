package com.rafaam11.businfo.ui

import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleListPresentationTest {
    @Test
    fun invalidCredentialGivesKeyAction() {
        assertEquals(
            "API 키를 확인할 수 없습니다. 키를 다시 입력해 주세요.",
            BusDataError.InvalidCredential.userMessage(),
        )
    }

    @Test
    fun networkFailureHasRetryableCopy() {
        assertEquals(
            "네트워크에 연결할 수 없습니다. 마지막 정상 데이터는 유지됩니다.",
            BusDataError.NetworkUnavailable.userMessage(),
        )
    }

    @Test
    fun serviceFailureHasRetryableCopy() {
        assertEquals(
            "버스 정보 서비스를 사용할 수 없습니다. 잠시 후 새로고침해 주세요.",
            BusDataError.ServiceUnavailable.userMessage(),
        )
    }

    @Test
    fun malformedResponseHasRetryableCopy() {
        assertEquals(
            "버스 정보를 읽을 수 없습니다. 잠시 후 새로고침해 주세요.",
            BusDataError.MalformedResponse.userMessage(),
        )
    }

    @Test
    fun rateLimitHasWaitAction() {
        assertEquals(
            "요청 한도를 초과했습니다. 잠시 기다린 뒤 새로고침해 주세요.",
            BusDataError.RateLimited.userMessage(),
        )
    }

    @Test
    fun vehicleTextContainsDirectionAndSequenceWithoutIdentifiers() {
        val vehicle = VehicleSnapshot(
            routeId = "secret-route-id",
            routeNo = "814",
            moveDirection = "범물 방향",
            stopId = "secret-stop-id",
            stopSequence = 17,
            latitude = 35.8714,
            longitude = 128.6014,
            arrivalState = null,
            busTypeCode2 = null,
            busTypeCode3 = null,
        )

        val text = vehicle.primaryText()

        assertTrue(text.contains("범물 방향"))
        assertTrue(text.contains("17"))
        assertFalse(text.contains("secret-route-id"))
        assertFalse(text.contains("secret-stop-id"))
    }
}

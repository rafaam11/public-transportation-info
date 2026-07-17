package com.rafaam11.businfo.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreciseVehiclePositionTest {
    private val now = Instant.parse("2026-07-17T12:00:30Z")
    private val selection = FavoriteSelection(
        CommuteSlot.MORNING,
        "route",
        "급행8-1",
        "0",
        "검단동 방면",
        "target",
        "효동초등학교건너",
    )

    @Test fun `freshness is classified per vehicle observation time`() {
        assertEquals(PrecisePositionFreshness.CURRENT, position(now.minusSeconds(15)).freshnessAt(now))
        assertEquals(PrecisePositionFreshness.DELAYED, position(now.minusSeconds(16)).freshnessAt(now))
        assertEquals(PrecisePositionFreshness.DELAYED, position(now.minusSeconds(30)).freshnessAt(now))
        assertEquals(PrecisePositionFreshness.HIDDEN, position(now.minusSeconds(31)).freshnessAt(now))
    }

    @Test fun `position validation rejects future invalid bounds and selection mismatch`() {
        assertTrue(position(now).isValidFor(selection, now))
        assertTrue(position(now.plusSeconds(5)).isValidFor(selection, now))
        assertFalse(position(now.plusSeconds(6)).isValidFor(selection, now))
        assertFalse(position(now).copy(point = GeoPoint(127.9, 35.8)).isValidFor(selection, now))
        assertFalse(position(now).copy(routeId = "other").isValidFor(selection, now))
        assertFalse(position(now).copy(moveDirection = "1").isValidFor(selection, now))
        assertFalse(position(now).copy(sessionKey = "").isValidFor(selection, now))
    }

    private fun position(observedAt: Instant) = PreciseVehiclePosition(
        sessionKey = "session-1",
        routeId = "route",
        moveDirection = "0",
        stopId = "nearby",
        stopSequence = 5,
        point = GeoPoint(128.61, 35.81),
        observedAt = observedAt,
        heading = 90f,
        arrivalState = "곧 도착",
    )
}

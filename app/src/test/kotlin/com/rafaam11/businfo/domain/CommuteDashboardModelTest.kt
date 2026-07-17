package com.rafaam11.businfo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommuteDashboardModelTest {
    @Test
    fun `direction label prefers API note`() {
        val stops = listOf(
            RouteStop("r", "s1", "출발", "0", 1, 128.0, 35.0),
            RouteStop("r", "s2", "종점", "0", 2, 128.1, 35.1),
        )

        assertEquals("범물동 방면", directionLabel("0", "범물동 방면", stops))
    }

    @Test
    fun `direction label falls back to last stop`() {
        val stops = listOf(
            RouteStop("r", "s1", "출발", "1", 1, 128.0, 35.0),
            RouteStop("r", "s2", "대구대정문 건너", "1", 77, 128.1, 35.1),
        )

        assertEquals("대구대정문 건너 방면", directionLabel("1", "", stops))
    }

    @Test
    fun `arrival primary text reflects stop gap`() {
        assertEquals("도착 임박", ArrivalEstimate(0, 20, "곧 도착").primaryText())
        assertEquals("1정거장 전", ArrivalEstimate(1, 80, null).primaryText())
        assertEquals("7정거장 전", ArrivalEstimate(7, 487, "9분").primaryText())
    }

    @Test
    fun `arrival secondary text prefers state and otherwise rounds minutes up`() {
        assertEquals("9분", ArrivalEstimate(7, 487, "9분").secondaryText())
        assertEquals("곧 도착", ArrivalEstimate(0, 59, null).secondaryText())
        assertEquals("2분", ArrivalEstimate(2, 61, null).secondaryText())
    }

    @Test
    fun `negative stop gap is treated as a sentinel rather than an imminent arrival`() {
        assertEquals("운행종료", ArrivalEstimate(-1, 100001, "운행종료").primaryText())
        assertEquals("정보 없음", ArrivalEstimate(-1, 100001, null).primaryText())
        assertNull(ArrivalEstimate(-1, 100001, "운행종료").secondaryText())
        assertNull(ArrivalEstimate(-1, 100001, null).secondaryText())
    }
}

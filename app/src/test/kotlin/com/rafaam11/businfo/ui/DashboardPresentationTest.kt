package com.rafaam11.businfo.ui

import com.rafaam11.businfo.domain.ArrivalEstimate
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteDashboardSnapshot
import com.rafaam11.businfo.domain.FavoriteSelection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardPresentationTest {
    @Test fun `dashboard always contains morning and evening in fixed order`() {
        val cards = dashboardCards(emptyList(), emptyMap(), emptySet())

        assertEquals(listOf(CommuteSlot.MORNING, CommuteSlot.EVENING), cards.map { it.slot })
        assertTrue(cards.all { it is DashboardCardUiState.Empty })
    }

    @Test fun `configured card carries two persisted arrivals and error independently`() {
        val selection = FavoriteSelection(
            CommuteSlot.MORNING, "route", "814", "0", "범물동 방면", "stop", "효동초등학교건너",
        )
        val snapshot = FavoriteDashboardSnapshot(
            selection,
            listOf(ArrivalEstimate(1, 60, "1분"), ArrivalEstimate(4, 300, "5분")),
            Instant.parse("2026-07-17T12:00:00Z"),
        )

        val cards = dashboardCards(listOf(snapshot), mapOf(CommuteSlot.MORNING to "네트워크 오류"), setOf(CommuteSlot.MORNING))
        val morning = cards.first() as DashboardCardUiState.Configured

        assertEquals(2, morning.snapshot.arrivals.size)
        assertEquals("네트워크 오류", morning.errorMessage)
        assertTrue(morning.refreshing)
        assertTrue(cards[1] is DashboardCardUiState.Empty)
    }
}

package com.rafaam11.businfo.widget

import com.rafaam11.businfo.domain.BusDataError
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStatusFormatterTest {
    private val now = Instant.parse("2026-07-17T12:00:00Z")

    @Test fun `failure age formats seconds deterministically`() {
        assertEquals("갱신 실패 · 45초 전", widgetStatusLabel(failedAt(now.minusSeconds(45)), now))
    }

    @Test fun `failure age formats whole minutes deterministically`() {
        assertEquals("갱신 실패 · 2분 전", widgetStatusLabel(failedAt(now.minusSeconds(125)), now))
    }

    @Test fun `future failure clock skew clamps to zero seconds`() {
        assertEquals("갱신 실패 · 0초 전", widgetStatusLabel(failedAt(now.plusSeconds(30)), now))
    }

    private fun failedAt(at: Instant) = CommuteWidgetUiState(
        appWidgetId = 42,
        slot = null,
        routeNo = null,
        routeTypeCode = null,
        stopName = null,
        directionLabel = null,
        primaryText = "",
        secondaryText = null,
        fetchedAt = null,
        refreshError = BusDataError.ServiceUnavailable,
        refreshErrorAt = at.toEpochMilli(),
        isRefreshing = false,
        requiresConfiguration = false,
    )
}

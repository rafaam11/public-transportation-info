package com.rafaam11.businfo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.PreciseSourceHealth
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class RealtimeMapScreenTest {
    @get:Rule val compose = createComposeRule()

    private val selection = FavoriteSelection(
        CommuteSlot.MORNING,
        "route",
        "급행8-1",
        "0",
        "검단동 방면",
        "stop",
        "효동초등학교건너",
    )
    private val observedAt = Instant.parse("2026-07-17T12:00:00Z")
    private val normalState = RealtimeMapUiState(
        selection = selection,
        visibleVehicles = listOf(
            MapVehicleUi(
                "session:0", GeoPoint(128.61, 35.81), "동대구역", 5, 2, null,
                observedAt, 0, false, 90f,
            ),
        ),
        totalOperatingCount = 3,
        hiddenVehicleCount = 2,
        preciseSourceHealth = PreciseSourceHealth.HEALTHY,
    )

    @Test
    fun normalStateShowsRouteSummaryAndSelectedDirectionVehicles() {
        compose.setContent {
            MaterialTheme {
                RealtimeMapScreen(
                    state = normalState,
                    onBack = {},
                    onRetry = {},
                    onVehicleSelected = {},
                    onFitRoute = {},
                    mapContent = { _, _ -> Text("가짜 지도") },
                )
            }
        }

        compose.onNodeWithText("가짜 지도").assertIsDisplayed()
        compose.onNodeWithText("급행8-1 · 검단동 방면").assertIsDisplayed()
        compose.onNodeWithText("내 정류장 · 효동초등학교건너").assertIsDisplayed()
        compose.onNodeWithText("전체 운행 3대 · 초정밀 위치 1대").assertIsDisplayed()
        compose.onNodeWithText("GPS 0초 전").assertIsDisplayed()
    }

    @Test
    fun staleStateExplainsWhyMarkersAreHidden() {
        val staleState = normalState.copy(
            visibleVehicles = emptyList(),
            hiddenVehicleCount = 3,
            preciseSourceHealth = PreciseSourceHealth.DELAYED,
        )

        compose.setContent {
            MaterialTheme {
                RealtimeMapScreen(staleState, {}, {}, {}, {}, mapContent = { _, _ -> })
            }
        }

        compose.onNodeWithText("초정밀 위치 연결이 지연되고 있습니다").assertIsDisplayed()
    }

    @Test
    fun successfulEmptyStateShowsNoOperatingVehicles() {
        val emptyState = normalState.copy(
            totalOperatingCount = 0,
            visibleVehicles = emptyList(),
            hiddenVehicleCount = 0,
        )

        compose.setContent {
            MaterialTheme {
                RealtimeMapScreen(emptyState, {}, {}, {}, {}, mapContent = { _, _ -> })
            }
        }

        compose.onNodeWithText("현재 운행 차량 없음").assertIsDisplayed()
    }

    @Test
    fun delayedStateShowsTheAgeOfTheLastConfirmedPosition() {
        val delayedState = normalState.copy(
            visibleVehicles = listOf(normalState.visibleVehicles.single().copy(ageSeconds = 22, delayed = true)),
            preciseSourceHealth = PreciseSourceHealth.PARTIAL,
        )

        compose.setContent {
            MaterialTheme {
                RealtimeMapScreen(delayedState, {}, {}, {}, {}, mapContent = { _, _ -> })
            }
        }

        compose.onNodeWithText("GPS 지연 · 22초 전").assertIsDisplayed()
    }
}

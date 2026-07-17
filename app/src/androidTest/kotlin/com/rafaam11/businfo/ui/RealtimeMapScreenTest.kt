package com.rafaam11.businfo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.DataFreshness
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.VehicleSnapshot
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
    private val vehicle = VehicleSnapshot(
        "route", "급행8-1", "0", "nearby", 5, 35.81, 128.61, null, null, null,
    )
    private val batch = VehicleBatch.from(
        listOf(vehicle),
        Instant.parse("2026-07-17T12:00:00Z"),
    )
    private val normalState = RealtimeMapUiState(
        selection = selection,
        vehicleBatch = batch,
        visibleVehicles = listOf(
            MapVehicleUi("snapshot:0", GeoPoint(128.61, 35.81), "동대구역", 5, 2, null),
        ),
        freshness = DataFreshness.FRESH,
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
    }

    @Test
    fun staleStateExplainsWhyMarkersAreHidden() {
        val staleState = normalState.copy(
            visibleVehicles = emptyList(),
            freshness = DataFreshness.STALE,
        )

        compose.setContent {
            MaterialTheme {
                RealtimeMapScreen(staleState, {}, {}, {}, {}, mapContent = { _, _ -> })
            }
        }

        compose.onNodeWithText("위치 정보가 지연되고 있습니다").assertIsDisplayed()
    }

    @Test
    fun successfulEmptyStateShowsNoOperatingVehicles() {
        val emptyState = normalState.copy(
            vehicleBatch = VehicleBatch.from(
                emptyList(),
                Instant.parse("2026-07-17T12:00:00Z"),
            ),
            visibleVehicles = emptyList(),
        )

        compose.setContent {
            MaterialTheme {
                RealtimeMapScreen(emptyState, {}, {}, {}, {}, mapContent = { _, _ -> })
            }
        }

        compose.onNodeWithText("현재 운행 차량 없음").assertIsDisplayed()
    }
}

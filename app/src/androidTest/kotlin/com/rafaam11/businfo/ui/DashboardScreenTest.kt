package com.rafaam11.businfo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.rafaam11.businfo.domain.ArrivalEstimate
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteDashboardSnapshot
import com.rafaam11.businfo.domain.FavoriteSelection
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyDashboardShowsBothFixedSlots() {
        compose.setContent {
            MaterialTheme {
                DashboardScreen(
                    cards = listOf(DashboardCardUiState.Empty(CommuteSlot.MORNING), DashboardCardUiState.Empty(CommuteSlot.EVENING)),
                    onAdd = {}, onOpen = {}, onEdit = {}, onRefresh = {}, onClearKey = {},
                )
            }
        }

        compose.onNodeWithText("출근").assertIsDisplayed()
        compose.onNodeWithText("퇴근").assertIsDisplayed()
        compose.onAllNodesWithText("버스 추가", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test fun configuredCardPrioritizesFirstVehicleAndShowsSecond() {
        val selection = FavoriteSelection(
            CommuteSlot.MORNING, "route", "814", "0", "범물동 방면", "stop", "효동초등학교건너",
        )
        val card = DashboardCardUiState.Configured(
            CommuteSlot.MORNING,
            FavoriteDashboardSnapshot(selection, listOf(
                ArrivalEstimate(1, 60, "1분", "0"), ArrivalEstimate(4, 300, "5분", "0"),
            ), Instant.parse("2026-07-17T12:00:00Z")),
            refreshing = false,
            errorMessage = null,
        )
        compose.setContent {
            MaterialTheme {
                DashboardScreen(listOf(card, DashboardCardUiState.Empty(CommuteSlot.EVENING)), {}, {}, {}, {}, {})
            }
        }

        compose.onNodeWithText("1정거장 전").assertIsDisplayed()
        compose.onNodeWithText("다음 차량 · 4정거장 전 · 5분").assertIsDisplayed()
        compose.onNodeWithText("효동초등학교건너").assertIsDisplayed()
    }
}

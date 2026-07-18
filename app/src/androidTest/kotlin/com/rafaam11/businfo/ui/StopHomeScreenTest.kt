package com.rafaam11.businfo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class StopHomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun singleHomeShowsUnifiedSearchNearbyAndFavoriteEmptyState() {
        compose.setContent {
            MaterialTheme {
                StopHomeScreen(
                    state = StopHomeUiState(catalogPreparing = false),
                    updateState = UpdateUiState.Idle,
                    locationGranted = false,
                    placeSearchConfigured = false,
                    onSearch = {}, onNearby = {}, onStop = {}, onPlace = {}, onRoute = {},
                    onBackFromRoute = {}, onBackFromNearby = {}, onFavorite = {}, onDeleteFavorite = {}, onMoveFavorite = { _, _ -> },
                    onToggleReorder = {}, onRefreshStop = { _, _ -> }, onRefreshCatalog = {},
                    onChangeKey = {}, onCheckUpdate = {}, onDownloadUpdate = {}, onInstallUpdate = {},
                    onOpenReleases = {}, onConsumeMessage = {},
                )
            }
        }

        compose.onNodeWithText("버스 · 정류장 · 장소 검색").assertIsDisplayed()
        compose.onNodeWithText("내 주변").assertIsDisplayed()
        compose.onNodeWithText("즐겨찾는 정류장").assertIsDisplayed()
        compose.onNodeWithText("첫 정류장을 저장해 보세요").assertIsDisplayed()
    }
}

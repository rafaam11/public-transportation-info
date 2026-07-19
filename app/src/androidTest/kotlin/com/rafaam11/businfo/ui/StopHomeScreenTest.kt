package com.rafaam11.businfo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rafaam11.businfo.data.GroupedSearchResult
import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.StopCatalogItem
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
                    onBackFromRoute = {}, onBackFromNearby = {}, onToggleFavorite = {}, onDeleteFavorite = {}, onMoveFavorite = { _, _ -> },
                    onToggleReorder = {}, onRefreshStop = { _, _ -> }, onRefreshCatalog = {},
                    onChangeKey = {}, onCheckUpdate = {}, onDownloadUpdate = {}, onInstallUpdate = {},
                    onOpenReleases = {},
                )
            }
        }

        compose.onNodeWithText("버스 · 정류장 · 장소 검색").assertIsDisplayed()
        compose.onNodeWithText("내 주변").assertIsDisplayed()
        compose.onNodeWithText("즐겨찾는 정류장").assertIsDisplayed()
        compose.onNodeWithText("첫 정류장을 저장해 보세요").assertIsDisplayed()
    }

    @Test fun searchResultReflectsFavoriteStateAndOffersClearAction() {
        val stop = StopCatalogItem("s1", "동대구역건너", 128.62, 35.87)
        val favorite = FavoriteStop(FavoriteStopId("f1"), stop.stopId, stop.stopName, GeoPoint(128.62, 35.87), 0)
        setHomeContent(
            StopHomeUiState(
                query = "동대구",
                searchResult = GroupedSearchResult(emptyList(), listOf(stop), emptyList()),
                favorites = listOf(favorite),
                catalogPreparing = false,
            ),
        )

        compose.onNodeWithText("★ 저장됨").assertIsDisplayed()
        compose.onNodeWithContentDescription("검색어 지우기").assertIsDisplayed()
        compose.onNodeWithContentDescription("동대구역건너 즐겨찾기 해제").assertIsDisplayed()
    }

    @Test fun reorderDisablesDirectionsThatCannotMove() {
        val first = FavoriteStop(FavoriteStopId("f1"), "s1", "첫 정류장", GeoPoint(128.6, 35.8), 0)
        val second = FavoriteStop(FavoriteStopId("f2"), "s2", "둘째 정류장", GeoPoint(128.7, 35.9), 1)
        setHomeContent(StopHomeUiState(favorites = listOf(first, second), reorderMode = true, catalogPreparing = false))

        compose.onAllNodesWithContentDescription("위로 이동")[0].assertIsNotEnabled()
        compose.onAllNodesWithContentDescription("아래로 이동")[1].assertIsNotEnabled()
        compose.onNodeWithText("순서 편집 중 · 화살표로 즐겨찾기 순서를 바꾼 뒤 메뉴에서 편집을 끝내세요").assertIsDisplayed()
    }

    private fun setHomeContent(state: StopHomeUiState) {
        compose.setContent {
            MaterialTheme {
                StopHomeScreen(
                    state = state,
                    updateState = UpdateUiState.Idle,
                    locationGranted = false,
                    placeSearchConfigured = false,
                    onSearch = {}, onNearby = {}, onStop = {}, onPlace = {}, onRoute = {},
                    onBackFromRoute = {}, onBackFromNearby = {}, onToggleFavorite = {}, onDeleteFavorite = {}, onMoveFavorite = { _, _ -> },
                    onToggleReorder = {}, onRefreshStop = { _, _ -> }, onRefreshCatalog = {},
                    onChangeKey = {}, onCheckUpdate = {}, onDownloadUpdate = {}, onInstallUpdate = {},
                    onOpenReleases = {},
                )
            }
        }
    }
}

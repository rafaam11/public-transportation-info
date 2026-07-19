package com.rafaam11.businfo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppFeedbackHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun favoriteRemovalShowsUndoAndResolvesTheMatchingEvent() {
        var resolution: Pair<Long, Boolean>? = null
        val event = UiFeedbackEvent.FavoriteRemoved(7L, "동대구역 즐겨찾기를 해제했습니다", "동대구역")

        compose.setContent {
            MaterialTheme {
                Box {
                    AppFeedbackHost(listOf(event)) { id, actionPerformed ->
                        resolution = id to actionPerformed
                    }
                }
            }
        }

        compose.onNodeWithText(event.message).assertIsDisplayed()
        compose.onNodeWithText("실행 취소").performClick()

        compose.runOnIdle { assertEquals(7L to true, resolution) }
    }
}

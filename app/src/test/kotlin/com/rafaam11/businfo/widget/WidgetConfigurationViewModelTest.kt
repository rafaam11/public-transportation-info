package com.rafaam11.businfo.widget

import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import com.rafaam11.businfo.domain.GeoPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetConfigurationViewModelTest {
    private val favorite = FavoriteStop(
        FavoriteStopId("favorite-1"), "stop-1", "동대구역건너", GeoPoint(128.62, 35.87), 0,
    )

    @Test fun `selection exposes binding progress and ignores duplicate taps`() = runTest {
        val release = CompletableDeferred<Unit>()
        var bindCalls = 0
        val viewModel = WidgetConfigurationViewModel(
            bindFavorite = { bindCalls++; release.await() },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.choose(favorite)
        viewModel.choose(favorite)
        runCurrent()

        assertEquals(favorite.id, viewModel.uiState.value.bindingFavoriteId)
        assertEquals(1, bindCalls)

        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(favorite.id, viewModel.uiState.value.completedFavoriteId)
        assertEquals(null, viewModel.uiState.value.bindingFavoriteId)
    }

    @Test fun `binding failure returns to retryable state`() = runTest {
        val viewModel = WidgetConfigurationViewModel(
            bindFavorite = { error("database unavailable") },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.choose(favorite)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBinding)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("다시"))
        assertEquals(null, viewModel.uiState.value.completedFavoriteId)
    }
}

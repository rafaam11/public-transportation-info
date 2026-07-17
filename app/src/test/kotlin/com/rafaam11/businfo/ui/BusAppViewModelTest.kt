package com.rafaam11.businfo.ui

import com.rafaam11.businfo.data.CredentialGateway
import com.rafaam11.businfo.data.DashboardDataSource
import com.rafaam11.businfo.data.DirectionOption
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteDashboardSnapshot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.VehicleLoadResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BusAppViewModelTest {
    @Test fun `missing key starts at key entry`() = runTest {
        val viewModel = BusAppViewModel(FakeCredential(false), FakeDashboard(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppUiState.NeedsKey)
    }

    @Test fun `saved key opens dashboard and refreshes both slots once`() = runTest {
        val dashboard = FakeDashboard()
        val viewModel = BusAppViewModel(FakeCredential(true), dashboard, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        val ready = viewModel.uiState.value as AppUiState.Ready
        assertEquals(2, ready.cards.size)
        assertEquals(1, dashboard.refreshAllCalls)
    }

    @Test fun `valid submitted key opens dashboard`() = runTest {
        val credential = FakeCredential(false)
        val viewModel = BusAppViewModel(credential, FakeDashboard(), StandardTestDispatcher(testScheduler))

        viewModel.submitKey("key")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppUiState.Ready)
        assertTrue(credential.hasKey)
    }

    @Test fun `failed replacement validation preserves old key`() = runTest {
        val credential = FakeCredential(true, validationError = BusDataError.InvalidCredential)
        val viewModel = BusAppViewModel(credential, FakeDashboard(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.beginKeyChange()
        viewModel.submitKey("bad replacement")
        advanceUntilIdle()

        val state = viewModel.uiState.value as AppUiState.NeedsKey
        assertTrue(state.changeMode)
        assertEquals(BusDataError.InvalidCredential, state.error)
        assertTrue(credential.hasKey)
        assertFalse(credential.clearCalled)
    }

    @Test fun `catalog retry forces a new basic sync`() = runTest {
        val dashboard = FakeDashboard()
        val viewModel = BusAppViewModel(FakeCredential(true), dashboard, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.retryCatalog()
        advanceUntilIdle()

        assertTrue(dashboard.catalogForces.last())
    }

    @Test fun `saving a stop copies the official route type to the favorite`() = runTest {
        val dashboard = FakeDashboard()
        val viewModel = BusAppViewModel(FakeCredential(true), dashboard, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        viewModel.openSetup(CommuteSlot.MORNING)
        advanceUntilIdle()
        viewModel.selectRoute(RouteSummary("route", "급행8-1", "유곡리", "검단동", null, null, "1"))
        advanceUntilIdle()
        viewModel.selectDirection(DirectionOption("0", "유곡리 방면", emptyList()))

        viewModel.saveStop(RouteStop("route", "stop", "진천역", "0", 1, 128.5, 35.8))
        advanceUntilIdle()

        assertEquals("1", dashboard.savedFavorite?.routeTypeCode)
    }

    private class FakeCredential(
        var hasKey: Boolean,
        var validationError: BusDataError? = null,
    ) : CredentialGateway {
        var clearCalled = false
        override fun savedKeyExists() = hasKey
        override suspend fun validateAndSave(key: String): BusDataError? {
            if (validationError == null) hasKey = true
            return validationError
        }
        override fun clearKey() { clearCalled = true; hasKey = false }
    }

    private class FakeDashboard : DashboardDataSource {
        val snapshots = MutableStateFlow<List<FavoriteDashboardSnapshot>>(emptyList())
        var refreshAllCalls = 0
        var savedFavorite: FavoriteSelection? = null
        val catalogForces = mutableListOf<Boolean>()
        override fun observeDashboard() = snapshots
        override suspend fun ensureRouteCatalog(force: Boolean): BusDataError? { catalogForces += force; return null }
        override suspend fun searchRoutes(query: String) = emptyList<RouteSummary>()
        override suspend fun directions(route: RouteSummary, force: Boolean) = Result.success(emptyList<DirectionOption>())
        override suspend fun saveFavorite(selection: FavoriteSelection) { savedFavorite = selection }
        override suspend fun deleteFavorite(slot: CommuteSlot) = Unit
        override suspend fun favorite(slot: CommuteSlot): FavoriteSelection? = null
        override suspend fun refreshFavorite(slot: CommuteSlot) = null
        override suspend fun refreshAll(): Map<CommuteSlot, BusDataError?> { refreshAllCalls++; return emptyMap() }
        override suspend fun refreshRouteVehicles(slot: CommuteSlot): VehicleLoadResult =
            VehicleLoadResult.Failure(BusDataError.ServiceUnavailable, null)
        override suspend fun routeSummary(routeId: String): RouteSummary? = null
        override suspend fun cachedDirections(route: RouteSummary) = emptyList<DirectionOption>()
    }
}

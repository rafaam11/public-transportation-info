package com.rafaam11.businfo.widget

import com.rafaam11.businfo.data.DashboardDataSource
import com.rafaam11.businfo.data.DirectionOption
import com.rafaam11.businfo.domain.ArrivalEstimate
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteDashboardSnapshot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.VehicleLoadResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommuteWidgetRepositoryTest {
    private val now = Instant.parse("2026-07-17T12:00:00Z")
    private val dashboard = FakeDashboard()
    private val preferences = FakeWidgetPreferences()
    private val repository = CommuteWidgetRepository(
        dashboard,
        preferences,
        Clock.fixed(now, ZoneOffset.UTC),
    )
    private val favorite = FavoriteSelection(
        slot = CommuteSlot.MORNING,
        routeId = "route-1",
        routeNo = "급행8-1",
        directionCode = "0",
        directionLabel = "범물동 방면",
        stopId = "stop-1",
        stopName = "효동초등학교건너",
        routeTypeCode = "1",
    )

    @Test fun `configured favorite without arrival shows empty message`() = runTest {
        preferences.saveSlot(WIDGET_ID, CommuteSlot.MORNING)
        dashboard.snapshots.value = listOf(snapshot(arrivals = emptyList(), fetchedAt = null))

        val state = repository.state(WIDGET_ID, now)

        assertEquals("급행8-1", state.routeNo)
        assertEquals("1", state.routeTypeCode)
        assertEquals(CommuteSlot.MORNING, state.slot)
        assertEquals("아직 받은 정보 없음", state.primaryText)
        assertFalse(state.requiresConfiguration)
    }

    @Test fun `configured favorite maps first and second arrivals`() = runTest {
        preferences.saveSlot(WIDGET_ID, CommuteSlot.MORNING)
        dashboard.snapshots.value = listOf(snapshot(
            arrivals = listOf(
                ArrivalEstimate(0, 20, "곧 도착"),
                ArrivalEstimate(2, 120, "2분"),
            ),
            fetchedAt = now.minusSeconds(30),
        ))

        val state = repository.state(WIDGET_ID, now)

        assertEquals("급행8-1", state.routeNo)
        assertEquals("도착 임박", state.primaryText)
        assertEquals("곧 도착", state.secondaryText)
        assertEquals("1", state.routeTypeCode)
        assertEquals(CommuteSlot.MORNING, state.slot)
        assertEquals(now.minusSeconds(30), state.fetchedAt)
    }

    @Test fun `missing slot requires configuration`() = runTest {
        val state = repository.state(WIDGET_ID, now)

        assertTrue(state.requiresConfiguration)
        assertNull(state.slot)
    }

    @Test fun `deleted configured favorite requires reconfiguration`() = runTest {
        preferences.saveSlot(WIDGET_ID, CommuteSlot.MORNING)

        val state = repository.state(WIDGET_ID, now)

        assertTrue(state.requiresConfiguration)
        assertEquals(CommuteSlot.MORNING, state.slot)
    }

    @Test fun `successful refresh clears error and retains room backed state`() = runTest {
        preferences.saveSlot(WIDGET_ID, CommuteSlot.MORNING)
        preferences.saveError(WIDGET_ID, BusDataError.ServiceUnavailable, now.minusSeconds(60).toEpochMilli())
        dashboard.snapshots.value = listOf(snapshot(
            arrivals = listOf(ArrivalEstimate(0, 20, "곧 도착")),
            fetchedAt = now,
        ))

        val result = repository.refresh(WIDGET_ID)

        assertEquals(WidgetRefreshResult.Success, result)
        assertEquals(listOf(CommuteSlot.MORNING), dashboard.refreshedSlots)
        assertNull(preferences.errorState(WIDGET_ID))
        assertEquals("도착 임박", repository.state(WIDGET_ID, now).primaryText)
    }

    @Test fun `failed refresh stores error and keeps prior snapshot`() = runTest {
        preferences.saveSlot(WIDGET_ID, CommuteSlot.MORNING)
        val prior = snapshot(
            arrivals = listOf(ArrivalEstimate(0, 20, "곧 도착")),
            fetchedAt = now.minusSeconds(60),
        )
        dashboard.snapshots.value = listOf(prior)
        dashboard.refreshError = BusDataError.ServiceUnavailable

        val result = repository.refresh(WIDGET_ID)
        val state = repository.state(WIDGET_ID, now)

        assertEquals(WidgetRefreshResult.Failed(BusDataError.ServiceUnavailable), result)
        assertEquals("급행8-1", state.routeNo)
        assertEquals("도착 임박", state.primaryText)
        assertEquals(BusDataError.ServiceUnavailable, state.refreshError)
        assertEquals(now.toEpochMilli(), state.refreshErrorAt)
        assertEquals(listOf(prior), dashboard.snapshots.value)
    }

    @Test fun `missing API key is exposed as refresh failure`() = runTest {
        preferences.saveSlot(WIDGET_ID, CommuteSlot.MORNING)
        dashboard.snapshots.value = listOf(snapshot(emptyList(), null))
        dashboard.refreshError = BusDataError.InvalidCredential

        val result = repository.refresh(WIDGET_ID)

        assertEquals(WidgetRefreshResult.Failed(BusDataError.InvalidCredential), result)
        assertEquals(BusDataError.InvalidCredential, repository.state(WIDGET_ID, now).refreshError)
    }

    @Test fun `refresh without configured slot does not call dashboard`() = runTest {
        assertEquals(WidgetRefreshResult.RequiresConfiguration, repository.refresh(WIDGET_ID))
        assertTrue(dashboard.refreshedSlots.isEmpty())
    }

    @Test fun `concurrent refresh taps for same widget make one network call`() = runTest {
        preferences.saveSlot(WIDGET_ID, CommuteSlot.MORNING)
        dashboard.snapshots.value = listOf(snapshot(emptyList(), null))
        dashboard.refreshGate = CompletableDeferred()
        val started = CompletableDeferred<Unit>()

        val first = async { repository.refresh(WIDGET_ID) { started.complete(Unit) } }
        started.await()
        assertTrue(repository.state(WIDGET_ID, now).isRefreshing)
        val second = repository.refresh(WIDGET_ID)
        dashboard.refreshGate!!.complete(Unit)

        assertEquals(WidgetRefreshResult.AlreadyRunning, second)
        assertEquals(WidgetRefreshResult.Success, first.await())
        assertEquals(1, dashboard.refreshedSlots.size)
        assertFalse(repository.state(WIDGET_ID, now).isRefreshing)
    }

    @Test fun `clear removes widget preferences and process state`() = runTest {
        preferences.saveSlot(WIDGET_ID, CommuteSlot.MORNING)
        preferences.saveError(WIDGET_ID, BusDataError.ServiceUnavailable, now.toEpochMilli())

        repository.clear(WIDGET_ID)

        assertNull(preferences.slot(WIDGET_ID))
        assertNull(preferences.errorState(WIDGET_ID))
        assertTrue(repository.state(WIDGET_ID, now).requiresConfiguration)
        assertFalse(repository.state(WIDGET_ID, now).isRefreshing)
    }

    @Test fun `clear during failed refresh prevents deleted error from being recreated`() = runTest {
        preferences.saveSlot(WIDGET_ID, CommuteSlot.MORNING)
        dashboard.refreshError = BusDataError.ServiceUnavailable
        dashboard.refreshGate = CompletableDeferred()
        val started = CompletableDeferred<Unit>()
        val refresh = async { repository.refresh(WIDGET_ID) { started.complete(Unit) } }
        started.await()

        repository.clear(WIDGET_ID)
        dashboard.refreshGate!!.complete(Unit)

        assertEquals(WidgetRefreshResult.Failed(BusDataError.ServiceUnavailable), refresh.await())
        assertNull(preferences.errorState(WIDGET_ID))
        assertFalse(repository.state(WIDGET_ID, now).isRefreshing)

        preferences.saveSlot(WIDGET_ID, CommuteSlot.MORNING)
        dashboard.refreshError = null
        assertEquals(WidgetRefreshResult.Success, repository.refresh(WIDGET_ID))
        assertEquals(2, dashboard.refreshedSlots.size)
    }

    @Test fun `deletion during startup invalidates old lease without duplicating recreated refresh`() = runTest {
        preferences.saveSlot(WIDGET_ID, CommuteSlot.MORNING)
        dashboard.refreshGate = CompletableDeferred()
        val oldStartupEntered = CompletableDeferred<Unit>()
        val releaseOldStartup = CompletableDeferred<Unit>()
        var startupCount = 0
        val lifecycleRepository = CommuteWidgetRepository(
            dashboard,
            preferences,
            Clock.fixed(now, ZoneOffset.UTC),
            beforeRefreshStartup = {
                startupCount++
                if (startupCount == 1) {
                    oldStartupEntered.complete(Unit)
                    releaseOldStartup.await()
                }
            },
        )
        val oldRefresh = async { lifecycleRepository.refresh(WIDGET_ID) }
        oldStartupEntered.await()

        lifecycleRepository.clear(WIDGET_ID)
        preferences.saveSlot(WIDGET_ID, CommuteSlot.MORNING)
        val newStarted = CompletableDeferred<Unit>()
        val newRefresh = async { lifecycleRepository.refresh(WIDGET_ID) { newStarted.complete(Unit) } }
        newStarted.await()
        releaseOldStartup.complete(Unit)

        assertEquals(WidgetRefreshResult.RequiresConfiguration, oldRefresh.await())
        assertEquals(1, dashboard.refreshedSlots.size)
        dashboard.refreshGate!!.complete(Unit)
        assertEquals(WidgetRefreshResult.Success, newRefresh.await())
        assertNull(preferences.errorState(WIDGET_ID))
        assertFalse(lifecycleRepository.state(WIDGET_ID, now).isRefreshing)
    }

    private fun snapshot(arrivals: List<ArrivalEstimate>, fetchedAt: Instant?) =
        FavoriteDashboardSnapshot(favorite, arrivals, fetchedAt)

    private class FakeWidgetPreferences : WidgetPreferenceGateway {
        private val slots = mutableMapOf<Int, CommuteSlot>()
        private val errors = mutableMapOf<Int, WidgetRefreshError>()

        override fun slot(appWidgetId: Int) = slots[appWidgetId]
        override fun saveSlot(appWidgetId: Int, slot: CommuteSlot) { slots[appWidgetId] = slot }
        override fun errorState(appWidgetId: Int) = errors[appWidgetId]
        override fun saveError(appWidgetId: Int, error: BusDataError?, atEpochMillis: Long?) {
            if (error == null || atEpochMillis == null) errors.remove(appWidgetId)
            else errors[appWidgetId] = WidgetRefreshError(error, atEpochMillis)
        }
        override fun clear(appWidgetId: Int) { slots.remove(appWidgetId); errors.remove(appWidgetId) }
    }

    private class FakeDashboard : DashboardDataSource {
        val snapshots = MutableStateFlow<List<FavoriteDashboardSnapshot>>(emptyList())
        val refreshedSlots = mutableListOf<CommuteSlot>()
        var refreshError: BusDataError? = null
        var refreshGate: CompletableDeferred<Unit>? = null

        override fun observeDashboard() = snapshots
        override suspend fun refreshFavorite(slot: CommuteSlot): BusDataError? {
            refreshedSlots += slot
            refreshGate?.await()
            return refreshError
        }
        override suspend fun ensureRouteCatalog(force: Boolean) = unsupported<BusDataError?>()
        override suspend fun searchRoutes(query: String) = unsupported<List<RouteSummary>>()
        override suspend fun directions(route: RouteSummary, force: Boolean) = unsupported<Result<List<DirectionOption>>>()
        override suspend fun saveFavorite(selection: FavoriteSelection) = unsupported<Unit>()
        override suspend fun deleteFavorite(slot: CommuteSlot) = unsupported<Unit>()
        override suspend fun favorite(slot: CommuteSlot) = unsupported<FavoriteSelection?>()
        override suspend fun refreshAll() = unsupported<Map<CommuteSlot, BusDataError?>>()
        override suspend fun refreshRouteVehicles(slot: CommuteSlot) = unsupported<VehicleLoadResult>()
        override suspend fun routeSummary(routeId: String) = unsupported<RouteSummary?>()
        override suspend fun cachedDirections(route: RouteSummary) = unsupported<List<DirectionOption>>()

        private fun <T> unsupported(): T = error("Unexpected dashboard call")
    }

    private companion object {
        const val WIDGET_ID = 42
    }
}

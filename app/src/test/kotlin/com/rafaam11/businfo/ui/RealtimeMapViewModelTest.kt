package com.rafaam11.businfo.ui

import com.rafaam11.businfo.data.DashboardDataSource
import com.rafaam11.businfo.data.DirectionOption
import com.rafaam11.businfo.data.PreciseDataResult
import com.rafaam11.businfo.data.PreciseRosterSnapshot
import com.rafaam11.businfo.data.PreciseVehiclePositionDataSource
import com.rafaam11.businfo.data.RouteGeometryDataSource
import com.rafaam11.businfo.data.RouteMapLoadResult
import com.rafaam11.businfo.data.VehiclePositionDataSource
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteDashboardSnapshot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.PreciseSourceHealth
import com.rafaam11.businfo.domain.PreciseVehicleBatch
import com.rafaam11.businfo.domain.PreciseVehiclePosition
import com.rafaam11.businfo.domain.RouteGeometry
import com.rafaam11.businfo.domain.RouteSegment
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.VehicleLoadResult
import com.rafaam11.businfo.domain.VehicleSnapshot
import com.rafaam11.businfo.ui.map.MapAuthMonitor
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeMapViewModelTest {
    private val start = Instant.parse("2026-07-17T12:00:00Z")
    private val selection = FavoriteSelection(
        CommuteSlot.MORNING, "route", "급행8-1", "0", "검단동 방면", "target", "효동초등학교건너",
    )
    private val stops = listOf(
        RouteStop("route", "nearby", "동대구역", "0", 5, 128.61, 35.81),
        RouteStop("route", "target", "효동초등학교건너", "0", 8, 128.64, 35.84),
    )

    @Test fun `visible map polls precise positions every three seconds and summaries every fifteen`() = runTest {
        val fixture = fixture(testScheduler)
        fixture.viewModel.setVisible(true)
        fixture.viewModel.open(CommuteSlot.MORNING)
        runCurrent()

        assertEquals(1, fixture.summary.calls)
        assertEquals(1, fixture.precise.rosterCalls)
        assertEquals(1, fixture.precise.positionCalls)

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(2, fixture.precise.positionCalls)
        assertEquals(1, fixture.summary.calls)
        assertEquals(1, fixture.precise.rosterCalls)

        advanceTimeBy(12_000)
        runCurrent()
        assertEquals(2, fixture.summary.calls)
        assertEquals(2, fixture.precise.rosterCalls)
        fixture.viewModel.close()
    }

    @Test fun `public stop coordinates are never mapped and precise vehicle hides after thirty seconds`() = runTest {
        val fixture = fixture(testScheduler)
        fixture.viewModel.setVisible(true)
        fixture.viewModel.open(CommuteSlot.MORNING)
        runCurrent()

        val first = fixture.viewModel.uiState.value
        assertEquals(128.615, first.visibleVehicles.single().point.longitude, 0.000001)
        assertFalse(first.visibleVehicles.any { it.point.longitude == 128.61 })

        fixture.precise.positionResults += PreciseDataResult.Failure(BusDataError.NetworkUnavailable)
        fixture.clock.advanceSeconds(31)
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(fixture.viewModel.uiState.value.visibleVehicles.isEmpty())
        assertEquals(1, fixture.viewModel.uiState.value.hiddenVehicleCount)
        fixture.viewModel.close()
    }

    @Test fun `partial detail failure retains last confirmed vehicle independently`() = runTest {
        val fixture = fixture(testScheduler)
        fixture.precise.defaultPositions = preciseBatch(
            positions = listOf(precise("one", start), precise("two", start).copy(point = GeoPoint(128.62, 35.82))),
            rosterCount = 2,
        )
        fixture.viewModel.setVisible(true)
        fixture.viewModel.open(CommuteSlot.MORNING)
        runCurrent()
        assertEquals(2, fixture.viewModel.uiState.value.visibleVehicles.size)

        fixture.precise.positionResults += PreciseDataResult.Success(
            preciseBatch(
                listOf(precise("one", start.plusSeconds(3))),
                rosterCount = 2,
                failureCount = 1,
                rosterSessionKeys = setOf("one", "two"),
            ),
        )
        fixture.clock.advanceSeconds(3)
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals(2, fixture.viewModel.uiState.value.visibleVehicles.size)
        assertEquals(PreciseSourceHealth.PARTIAL, fixture.viewModel.uiState.value.preciseSourceHealth)
        fixture.viewModel.close()
    }

    @Test fun `vehicle removed from current roster is removed without waiting for GPS expiry`() = runTest {
        val fixture = fixture(testScheduler)
        fixture.precise.defaultPositions = preciseBatch(
            positions = listOf(precise("one", start), precise("two", start)),
            rosterCount = 2,
        )
        fixture.viewModel.setVisible(true)
        fixture.viewModel.open(CommuteSlot.MORNING)
        runCurrent()
        assertEquals(2, fixture.viewModel.uiState.value.visibleVehicles.size)

        fixture.precise.positionResults += PreciseDataResult.Success(
            preciseBatch(
                positions = listOf(precise("one", start.plusSeconds(3))),
                rosterCount = 1,
                rosterSessionKeys = setOf("one"),
            ),
        )
        fixture.clock.advanceSeconds(3)
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals(listOf("one"), fixture.viewModel.uiState.value.visibleVehicles.map(MapVehicleUi::key))
        fixture.viewModel.close()
    }

    @Test fun `source heading and per vehicle age are mapped without geometry inference`() = runTest {
        val fixture = fixture(testScheduler)
        fixture.viewModel.setVisible(true)
        fixture.viewModel.open(CommuteSlot.MORNING)
        runCurrent()

        val vehicle = fixture.viewModel.uiState.value.visibleVehicles.single()
        assertEquals(90f, vehicle.headingDegrees)
        assertEquals(0L, vehicle.ageSeconds)
        assertFalse(vehicle.delayed)
        fixture.viewModel.close()
    }

    @Test fun `background cancels all polling and close clears precise session`() = runTest {
        val fixture = fixture(testScheduler)
        fixture.viewModel.setVisible(true)
        fixture.viewModel.open(CommuteSlot.MORNING)
        runCurrent()
        fixture.viewModel.setVisible(false)

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, fixture.summary.calls)
        assertEquals(1, fixture.precise.rosterCalls)
        assertEquals(1, fixture.precise.positionCalls)

        fixture.viewModel.close()
        assertEquals(1, fixture.precise.closeCalls)
    }

    @Test fun `all detail failures back off from six to fifteen seconds`() = runTest {
        val fixture = fixture(testScheduler)
        repeat(3) { fixture.precise.positionResults += PreciseDataResult.Failure(BusDataError.NetworkUnavailable) }
        fixture.viewModel.setVisible(true)
        fixture.viewModel.open(CommuteSlot.MORNING)
        runCurrent()
        assertEquals(1, fixture.precise.positionCalls)

        advanceTimeBy(5_999)
        runCurrent()
        assertEquals(1, fixture.precise.positionCalls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, fixture.precise.positionCalls)

        advanceTimeBy(14_999)
        runCurrent()
        assertEquals(2, fixture.precise.positionCalls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, fixture.precise.positionCalls)
        fixture.viewModel.close()
    }

    private fun fixture(scheduler: TestCoroutineScheduler): Fixture {
        val clock = MutableClock(start)
        val summary = FakeSummary(clock)
        val precise = FakePrecise(clock)
        return Fixture(
            RealtimeMapViewModel(
                FakeDashboard(selection),
                FakeGeometry(geometry(), stops),
                summary,
                precise,
                MapAuthMonitor(),
                clock,
                StandardTestDispatcher(scheduler),
            ),
            summary,
            precise,
            clock,
        )
    }

    private fun geometry() = RouteGeometry(
        "route", "0", listOf(RouteSegment(listOf("a", "b"), listOf(GeoPoint(128.60, 35.80), GeoPoint(128.65, 35.85)))), start,
    )

    private fun summaryVehicle(time: Instant) = VehicleSnapshot(
        "route", "급행8-1", "0", "nearby", 5, 35.81, 128.61, time.toString(), null, null,
    )

    private fun precise(key: String, observedAt: Instant) = PreciseVehiclePosition(
        key, "route", "0", "nearby", 5, GeoPoint(128.615, 35.815), observedAt, 90f, "곧 도착",
    )

    private fun preciseBatch(
        positions: List<PreciseVehiclePosition>,
        rosterCount: Int,
        failureCount: Int = 0,
        rosterSessionKeys: Set<String> = positions.map(PreciseVehiclePosition::sessionKey).toSet(),
    ) = PreciseVehicleBatch(positions, rosterCount, failureCount, start, rosterSessionKeys)

    private inner class FakeSummary(private val clock: Clock) : VehiclePositionDataSource {
        var calls = 0
        override suspend fun refresh(selection: FavoriteSelection): VehicleLoadResult {
            calls++
            return VehicleLoadResult.Success(VehicleBatch.from(listOf(summaryVehicle(clock.instant())), clock.instant()))
        }
    }

    private inner class FakePrecise(private val clock: Clock) : PreciseVehiclePositionDataSource {
        var rosterCalls = 0
        var positionCalls = 0
        var closeCalls = 0
        var defaultPositions = preciseBatch(listOf(precise("one", start)), 1)
        val positionResults = ArrayDeque<PreciseDataResult<PreciseVehicleBatch>>()

        override suspend fun refreshRoster(selection: FavoriteSelection): PreciseDataResult<PreciseRosterSnapshot> {
            rosterCalls++
            return PreciseDataResult.Success(PreciseRosterSnapshot(defaultPositions.rosterCount, clock.instant()))
        }

        override suspend fun refreshPositions(selection: FavoriteSelection): PreciseDataResult<PreciseVehicleBatch> {
            positionCalls++
            return if (positionResults.isEmpty()) {
                PreciseDataResult.Success(defaultPositions.copy(receivedAt = clock.instant()))
            } else {
                positionResults.removeFirst()
            }
        }

        override fun closeSession() { closeCalls++ }
    }

    private class FakeGeometry(
        private val geometry: RouteGeometry,
        private val stops: List<RouteStop>,
    ) : RouteGeometryDataSource {
        override suspend fun load(selection: FavoriteSelection, force: Boolean) =
            RouteMapLoadResult.Success(geometry, stops, null)
    }

    private class FakeDashboard(private val favorite: FavoriteSelection) : DashboardDataSource {
        override fun observeDashboard() = MutableStateFlow<List<FavoriteDashboardSnapshot>>(emptyList())
        override suspend fun ensureRouteCatalog(force: Boolean): BusDataError? = null
        override suspend fun searchRoutes(query: String) = emptyList<RouteSummary>()
        override suspend fun directions(route: RouteSummary, force: Boolean) = Result.success(emptyList<DirectionOption>())
        override suspend fun saveFavorite(selection: FavoriteSelection) = Unit
        override suspend fun deleteFavorite(slot: CommuteSlot) = Unit
        override suspend fun favorite(slot: CommuteSlot) = favorite
        override suspend fun refreshFavorite(slot: CommuteSlot): BusDataError? = null
        override suspend fun refreshAll() = emptyMap<CommuteSlot, BusDataError?>()
        override suspend fun refreshRouteVehicles(slot: CommuteSlot) = VehicleLoadResult.Failure(BusDataError.ServiceUnavailable, null)
        override suspend fun routeSummary(routeId: String): RouteSummary? = null
        override suspend fun cachedDirections(route: RouteSummary) = emptyList<DirectionOption>()
    }

    private class MutableClock(private var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
        fun advanceSeconds(seconds: Long) { current = current.plusSeconds(seconds) }
    }

    private data class Fixture(
        val viewModel: RealtimeMapViewModel,
        val summary: FakeSummary,
        val precise: FakePrecise,
        val clock: MutableClock,
    )
}

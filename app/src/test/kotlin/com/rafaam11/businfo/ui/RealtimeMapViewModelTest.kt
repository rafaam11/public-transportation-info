package com.rafaam11.businfo.ui

import com.rafaam11.businfo.data.DashboardDataSource
import com.rafaam11.businfo.data.DirectionOption
import com.rafaam11.businfo.data.RouteGeometryDataSource
import com.rafaam11.businfo.data.RouteMapLoadResult
import com.rafaam11.businfo.data.VehiclePositionDataSource
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.DataFreshness
import com.rafaam11.businfo.domain.FavoriteDashboardSnapshot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.RouteGeometry
import com.rafaam11.businfo.domain.RouteSegment
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.VehicleLoadResult
import com.rafaam11.businfo.domain.VehicleSnapshot
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

    @Test fun `opening visible map polls immediately then after eight seconds`() = runTest {
        val fixture = fixture(testScheduler)
        fixture.viewModel.setVisible(true)
        fixture.viewModel.open(CommuteSlot.MORNING)
        runCurrent()
        assertEquals(1, fixture.vehicles.calls)

        advanceTimeBy(7_999)
        runCurrent()
        assertEquals(1, fixture.vehicles.calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, fixture.vehicles.calls)
        fixture.viewModel.close()
    }

    @Test fun `background visibility cancels polling`() = runTest {
        val fixture = fixture(testScheduler)
        fixture.viewModel.setVisible(true)
        fixture.viewModel.open(CommuteSlot.MORNING)
        runCurrent()
        fixture.viewModel.setVisible(false)

        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(1, fixture.vehicles.calls)
        fixture.viewModel.close()
    }

    @Test fun `freshness ticker hides vehicles after thirty seconds`() = runTest {
        val fixture = fixture(testScheduler)
        fixture.viewModel.setVisible(true)
        fixture.viewModel.open(CommuteSlot.MORNING)
        runCurrent()
        assertEquals(1, fixture.viewModel.uiState.value.visibleVehicles.size)

        fixture.clock.advanceSeconds(31)
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(fixture.viewModel.uiState.value.visibleVehicles.isEmpty())
        assertEquals(DataFreshness.STALE, fixture.viewModel.uiState.value.freshness)
        fixture.viewModel.close()
    }

    @Test fun `authentication failure stops future polls`() = runTest {
        val fixture = fixture(testScheduler, mutableListOf(VehicleLoadResult.Failure(BusDataError.InvalidCredential, null)))
        fixture.viewModel.setVisible(true)
        fixture.viewModel.open(CommuteSlot.MORNING)
        runCurrent()

        advanceTimeBy(120_000)
        runCurrent()

        assertEquals(1, fixture.vehicles.calls)
        fixture.viewModel.close()
    }

    @Test fun `vehicle mapper derives remaining stops without a persistent identifier`() {
        val batch = VehicleBatch.from(listOf(vehicle(start)), start)

        val result = mapVehicles(selection, stops, batch).single()

        assertEquals(3, result.remainingStops)
        assertEquals("동대구역", result.stopName)
        assertTrue(result.key.startsWith(start.toEpochMilli().toString()))
    }

    private fun fixture(
        scheduler: TestCoroutineScheduler,
        results: MutableList<VehicleLoadResult> = mutableListOf(),
    ): Fixture {
        val clock = MutableClock(start)
        val vehicles = FakeVehicles(clock, results)
        return Fixture(
            RealtimeMapViewModel(
                FakeDashboard(selection),
                FakeGeometry(geometry(), stops),
                vehicles,
                clock,
                StandardTestDispatcher(scheduler),
            ),
            vehicles,
            clock,
        )
    }

    private fun geometry() = RouteGeometry(
        "route", "0", listOf(RouteSegment(listOf("a", "b"), listOf(GeoPoint(128.60, 35.80), GeoPoint(128.65, 35.85)))), start,
    )

    private fun vehicle(fetchedAt: Instant) = VehicleSnapshot(
        "route", "급행8-1", "0", "nearby", 5, 35.81, 128.61, fetchedAt.toString(), null, null,
    )

    private inner class FakeVehicles(
        private val clock: Clock,
        val results: MutableList<VehicleLoadResult>,
    ) : VehiclePositionDataSource {
        var calls = 0
        override suspend fun refresh(selection: FavoriteSelection): VehicleLoadResult {
            calls++
            return if (results.isNotEmpty()) results.removeAt(0) else {
                VehicleLoadResult.Success(VehicleBatch.from(listOf(vehicle(clock.instant())), clock.instant()))
            }
        }
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
        val vehicles: FakeVehicles,
        val clock: MutableClock,
    )
}

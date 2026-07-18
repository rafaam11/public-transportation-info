package com.rafaam11.businfo.ui

import com.rafaam11.businfo.data.GroupedSearchResult
import com.rafaam11.businfo.data.PreciseDataResult
import com.rafaam11.businfo.data.PreciseRosterSnapshot
import com.rafaam11.businfo.data.PreciseVehiclePositionDataSource
import com.rafaam11.businfo.data.PreciseVehicleSessionFactory
import com.rafaam11.businfo.data.StopSearchGateway
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.NearbyStopResult
import com.rafaam11.businfo.domain.PreciseVehicleBatch
import com.rafaam11.businfo.domain.PreciseVehiclePosition
import com.rafaam11.businfo.domain.RouteDirectionKey
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.StopArrivalGroup
import com.rafaam11.businfo.domain.StopArrivalSnapshot
import com.rafaam11.businfo.domain.StopCatalogItem
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StopRealtimeMapViewModelTest {
    private val stop = StopCatalogItem("target", "효동초등학교건너", 128.61, 35.81)
    private val groups = listOf(
        StopArrivalGroup(RouteDirectionKey("r1", "0"), "814", emptyList()),
        StopArrivalGroup(RouteDirectionKey("r2", "1"), "급행1", emptyList()),
    )

    @Test fun `highlighted route polls at three seconds while other route polls at eight`() = runTest {
        val sessions = mutableListOf<FakePrecise>()
        val viewModel = StopRealtimeMapViewModel(
            FakeSearch(), PreciseVehicleSessionFactory { FakePrecise().also(sessions::add) },
            Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC),
            StandardTestDispatcher(testScheduler),
        )

        viewModel.open(stop, groups)
        runCurrent()
        assertEquals(listOf(1, 1), sessions.map { it.positionCalls })
        assertEquals(listOf(5, 5), sessions.map { it.targetSequence })

        advanceTimeBy(8_100)
        runCurrent()

        assertEquals(3, sessions[0].positionCalls)
        assertEquals(2, sessions[1].positionCalls)
        viewModel.close()
    }

    @Test fun `a failed route does not hide another routes confirmed vehicle`() = runTest {
        var index = 0
        val viewModel = StopRealtimeMapViewModel(
            FakeSearch(), PreciseVehicleSessionFactory {
                FakePrecise(failPositions = index++ == 0)
            },
            MutableClock(Instant.parse("2026-07-18T00:00:00Z")),
            StandardTestDispatcher(testScheduler),
        )

        viewModel.open(stop, groups)
        runCurrent()

        assertEquals(1, viewModel.uiState.value.vehicles.size)
        assertTrue(viewModel.uiState.value.routeErrors.contains(groups.first().key))
        viewModel.close()
    }

    @Test fun `closing map ignores a late non cancellable position response`() = runTest {
        val late = CompletableDeferred<PreciseDataResult<PreciseVehicleBatch>>()
        val viewModel = StopRealtimeMapViewModel(
            FakeSearch(), PreciseVehicleSessionFactory {
                object : FakePrecise() {
                    override suspend fun refreshPositions(selection: FavoriteSelection) =
                        withContext(NonCancellable) { late.await() }
                }
            },
            Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC),
            StandardTestDispatcher(testScheduler),
        )
        viewModel.open(stop, groups.take(1))
        runCurrent()

        viewModel.close()
        val observedAt = Instant.parse("2026-07-18T00:00:00Z")
        late.complete(
            PreciseDataResult.Success(
                PreciseVehicleBatch(
                    listOf(PreciseVehiclePosition("late", "r1", "0", "before", 4, GeoPoint(128.6, 35.8), observedAt, 0f, null)),
                    1,
                    0,
                    observedAt,
                ),
            ),
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.vehicles.isEmpty())
    }

    private class FakeSearch : StopSearchGateway {
        override suspend fun ensureStopCatalog(force: Boolean) = Result.success(Unit)
        override suspend fun search(rawQuery: String) = GroupedSearchResult(emptyList(), emptyList(), emptyList())
        override suspend fun refreshArrivals(stopId: String, force: Boolean) = Result.success(
            StopArrivalSnapshot(stopId, emptyList(), Instant.EPOCH),
        )
        override suspend fun nearby(origin: GeoPoint) = NearbyStopResult(emptyList(), 500)
        override suspend fun routeStops(routeId: String) = Result.success(listOf(
            RouteStop(routeId, "before", "이전", if (routeId == "r1") "0" else "1", 4, 128.60, 35.80),
            RouteStop(routeId, "target", "목표", if (routeId == "r1") "0" else "1", 5, 128.61, 35.81),
        ))
        override suspend fun todayApiCallCount() = 0
    }

    private open class FakePrecise(private val failPositions: Boolean = false) : PreciseVehiclePositionDataSource {
        var targetSequence: Int? = null
        var positionCalls = 0
        override fun configureTargetStopSequence(sequence: Int?) { targetSequence = sequence }
        override suspend fun refreshRoster(selection: FavoriteSelection) = PreciseDataResult.Success(
            PreciseRosterSnapshot(1, Instant.parse("2026-07-18T00:00:00Z")),
        )
        override suspend fun refreshPositions(selection: FavoriteSelection): PreciseDataResult<PreciseVehicleBatch> {
            positionCalls++
            if (failPositions) return PreciseDataResult.Failure(com.rafaam11.businfo.domain.BusDataError.NetworkUnavailable)
            val position = PreciseVehiclePosition(
                "${selection.routeId}-vehicle", selection.routeId, selection.directionCode, "before", 4,
                GeoPoint(128.60, 35.80), Instant.parse("2026-07-18T00:00:00Z"), 90f, "1분",
            )
            return PreciseDataResult.Success(PreciseVehicleBatch(listOf(position), 1, 0, position.observedAt))
        }
        override fun closeSession() = Unit
    }

    private class MutableClock(private var value: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = value
    }
}

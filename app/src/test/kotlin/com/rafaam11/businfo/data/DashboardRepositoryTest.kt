package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.credential.CredentialStore
import com.rafaam11.businfo.data.local.BusLocalDataSource
import com.rafaam11.businfo.data.remote.DaeguBusRemoteDataSource
import com.rafaam11.businfo.data.remote.RemoteResult
import com.rafaam11.businfo.domain.ArrivalEstimate
import com.rafaam11.businfo.domain.ArrivalSnapshot
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteDashboardSnapshot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.VehicleSnapshot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardRepositoryTest {
    private val now = Instant.parse("2026-07-17T12:00:00Z")
    private val credentials = MemoryCredential("key")
    private val local = FakeLocal()
    private val remote = FakeRemote()
    private val repository = DashboardRepository(credentials, remote, local, Clock.fixed(now, ZoneOffset.UTC))
    private val favorite = FavoriteSelection(
        CommuteSlot.MORNING, "route", "814", "0", "범물동 방면", "stop", "효동초등학교건너",
    )

    @Test fun `route catalog inside 24 hours does not call network`() = runTest {
        local.routes += RouteSummary("route", "814", "대구대", "범물동", null, null)
        local.syncTimes[DashboardRepository.ROUTE_CATALOG_SYNC_KEY] = now.minusSeconds(86_399)

        assertEquals(null, repository.ensureRouteCatalog())
        assertEquals(0, remote.routeCalls)
    }

    @Test fun `refresh favorite stores only matching direction and top two arrivals`() = runTest {
        local.favorites[CommuteSlot.MORNING] = favorite
        remote.arrivalResult = RemoteResult.Success(listOf(
            ArrivalEstimate(4, 300, "5분", "1"),
            ArrivalEstimate(7, 487, "9분", "0"),
            ArrivalEstimate(2, 120, "2분", "0"),
            ArrivalEstimate(1, 60, "1분", "0"),
        ))

        assertEquals(null, repository.refreshFavorite(CommuteSlot.MORNING))

        assertEquals(listOf(60, 120), local.arrivals[CommuteSlot.MORNING]!!.arrivals.map { it.arrivalSeconds })
        assertEquals(now, local.arrivals[CommuteSlot.MORNING]!!.fetchedAt)
    }

    @Test fun `failed refresh keeps persisted arrival snapshot`() = runTest {
        local.favorites[CommuteSlot.MORNING] = favorite
        val prior = ArrivalSnapshot(listOf(ArrivalEstimate(2, 120, "2분", "0")), now.minusSeconds(600))
        local.arrivals[CommuteSlot.MORNING] = prior
        remote.arrivalResult = RemoteResult.Failure(BusDataError.NetworkUnavailable)

        assertEquals(BusDataError.NetworkUnavailable, repository.refreshFavorite(CommuteSlot.MORNING))
        assertEquals(prior, local.arrivals[CommuteSlot.MORNING])
    }

    @Test fun `successful empty arrival replaces prior snapshot`() = runTest {
        local.favorites[CommuteSlot.MORNING] = favorite
        local.arrivals[CommuteSlot.MORNING] = ArrivalSnapshot(listOf(ArrivalEstimate(2, 120, "2분")), now.minusSeconds(600))
        remote.arrivalResult = RemoteResult.Success(emptyList())

        repository.refreshFavorite(CommuteSlot.MORNING)

        assertTrue(local.arrivals[CommuteSlot.MORNING]!!.arrivals.isEmpty())
        assertEquals(now, local.arrivals[CommuteSlot.MORNING]!!.fetchedAt)
    }

    private class MemoryCredential(private var key: String?) : CredentialStore {
        override fun read() = key
        override fun write(serviceKey: String) { key = serviceKey }
        override fun clear() { key = null }
    }

    private class FakeRemote : DaeguBusRemoteDataSource {
        var routeCalls = 0
        var arrivalResult: RemoteResult<List<ArrivalEstimate>> = RemoteResult.Success(emptyList())
        override suspend fun validateKey(serviceKey: String) = RemoteResult.Success(Unit)
        override suspend fun routes(serviceKey: String): RemoteResult<List<RouteSummary>> {
            routeCalls++
            return RemoteResult.Success(emptyList())
        }
        override suspend fun arrivals(serviceKey: String, stopId: String, routeNo: String) = arrivalResult
        override suspend fun vehicles(serviceKey: String, routeId: String) = RemoteResult.Success(emptyList<VehicleSnapshot>())
    }

    private class FakeLocal : BusLocalDataSource {
        val routes = mutableListOf<RouteSummary>()
        val favorites = mutableMapOf<CommuteSlot, FavoriteSelection>()
        val arrivals = mutableMapOf<CommuteSlot, ArrivalSnapshot>()
        val syncTimes = mutableMapOf<String, Instant>()
        override fun observeDashboard(): Flow<List<FavoriteDashboardSnapshot>> = flowOf(emptyList())
        override suspend fun routes() = routes.toList()
        override suspend fun replaceRoutes(routes: List<RouteSummary>) { this.routes.apply { clear(); addAll(routes) } }
        override suspend fun routeStops(routeId: String) = emptyList<RouteStop>()
        override suspend fun replaceRouteStops(routeId: String, stops: List<RouteStop>) = Unit
        override suspend fun favorite(slot: CommuteSlot) = favorites[slot]
        override suspend fun saveFavorite(selection: FavoriteSelection) { favorites[selection.slot] = selection }
        override suspend fun deleteFavorite(slot: CommuteSlot) { favorites.remove(slot); arrivals.remove(slot) }
        override suspend fun saveArrival(slot: CommuteSlot, snapshot: ArrivalSnapshot) { arrivals[slot] = snapshot }
        override suspend fun syncTime(key: String) = syncTimes[key]
        override suspend fun saveSyncTime(key: String, instant: Instant) { syncTimes[key] = instant }
        override suspend fun vehicleBatch(routeId: String): VehicleBatch? = null
        override suspend fun saveVehicleBatch(routeId: String, batch: VehicleBatch) = Unit
    }
}

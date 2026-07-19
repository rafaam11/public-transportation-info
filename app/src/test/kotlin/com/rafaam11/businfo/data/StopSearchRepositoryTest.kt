package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.credential.CredentialStore
import com.rafaam11.businfo.data.local.BusLocalDataSource
import com.rafaam11.businfo.data.local.StopCenteredLocalDataSource
import com.rafaam11.businfo.data.remote.DaeguBusRemoteDataSource
import com.rafaam11.businfo.data.remote.RemoteResult
import com.rafaam11.businfo.domain.ArrivalSnapshot
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteDashboardSnapshot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.PlaceResult
import com.rafaam11.businfo.domain.RouteGeometry
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.StopArrival
import com.rafaam11.businfo.domain.StopArrivalSnapshot
import com.rafaam11.businfo.domain.StopCatalogItem
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.VehicleSnapshot
import com.rafaam11.businfo.domain.WidgetBinding
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StopSearchRepositoryTest {
    private val now = Instant.parse("2026-07-18T09:00:00Z")
    private val stop = StopCatalogItem("stop-1", "동대구역건너", 128.628, 35.880)
    private val local = FakeStopLocal()
    private val routes = FakeBusLocal().apply {
        routeValues += RouteSummary("route-814", "814", "대구대", "범물동", null, null)
    }
    private val remote = FakeRemote()
    private val places = FakePlaceSearch()
    private val calls = MemoryApiCallCounter()
    private val repository = StopSearchRepository(
        credentials = MemoryCredential("key"), remote = remote, stopLocal = local, routeLocal = routes,
        placeSearch = places, callCounter = calls, clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test fun `concurrent catalog preparation performs one network request`() = runTest {
        remote.catalogResult = RemoteResult.Success(listOf(stop))

        List(4) { async { repository.ensureStopCatalog() } }.awaitAll()

        assertEquals(1, remote.catalogCalls)
        assertEquals(listOf(stop), local.stopValues)
        assertEquals(1, calls.count(now))
    }

    @Test fun `grouped search combines local buses stops and remote places`() = runTest {
        local.stopValues += stop
        places.results = listOf(PlaceResult("동대구역", "기차역", "대구 동구", "동대구로 550", GeoPoint(128.628, 35.880)))

        val result = repository.search("동대구")

        assertEquals(emptyList<RouteSummary>(), result.routes)
        assertEquals(listOf(stop), result.stops)
        assertEquals("동대구역", result.places.single().name)
        assertEquals(listOf("동대구"), places.queries)
    }

    @Test fun `arrival refresh groups all route directions and reuses fresh snapshot`() = runTest {
        remote.arrivalResult = RemoteResult.Success(listOf(
            StopArrival("r1", "814", "0", 2, 120, "2분"),
            StopArrival("r1", "814", "0", 5, 360, "6분"),
            StopArrival("r2", "급행1", "1", 1, 60, "1분"),
        ))

        val first = repository.refreshArrivals(stop.stopId)
        val second = repository.refreshArrivals(stop.stopId)

        assertEquals(listOf("급행1", "814"), first.getOrThrow().groups.map { it.routeNo })
        assertEquals(first, second)
        assertEquals(1, remote.arrivalCalls)
        assertEquals(1, calls.count(now))
    }

    private class MemoryCredential(private var key: String?) : CredentialStore {
        override fun read() = key
        override fun write(serviceKey: String) { key = serviceKey }
        override fun clear() { key = null }
    }

    private class FakeRemote : DaeguBusRemoteDataSource {
        var catalogCalls = 0
        var arrivalCalls = 0
        var catalogResult: RemoteResult<List<StopCatalogItem>> = RemoteResult.Success(emptyList())
        var arrivalResult: RemoteResult<List<StopArrival>> = RemoteResult.Success(emptyList())
        override suspend fun validateKey(serviceKey: String) = RemoteResult.Success(Unit)
        override suspend fun stopCatalog(serviceKey: String): RemoteResult<List<StopCatalogItem>> {
            catalogCalls++
            return catalogResult
        }
        override suspend fun stopArrivals(serviceKey: String, stopId: String): RemoteResult<List<StopArrival>> {
            arrivalCalls++
            return arrivalResult
        }
        override suspend fun vehicles(serviceKey: String, routeId: String) = RemoteResult.Success(emptyList<VehicleSnapshot>())
    }

    private class FakePlaceSearch : PlaceSearchDataSource {
        var results = emptyList<PlaceResult>()
        val queries = mutableListOf<String>()
        override suspend fun search(query: String): Result<List<PlaceResult>> {
            queries += query
            return Result.success(results)
        }
    }

    private class FakeStopLocal : StopCenteredLocalDataSource {
        val stopValues = mutableListOf<StopCatalogItem>()
        private val arrivals = mutableMapOf<String, MutableStateFlow<StopArrivalSnapshot?>>()
        override fun observeFavoriteStops(): Flow<List<FavoriteStop>> = flowOf(emptyList())
        override suspend fun favoriteStop(id: FavoriteStopId): FavoriteStop? = null
        override suspend fun favoriteStopByStopId(stopId: String): FavoriteStop? = null
        override suspend fun favoriteStopCount() = 0
        override suspend fun saveFavoriteStop(stop: FavoriteStop) = Unit
        override suspend fun removeFavoriteStop(id: FavoriteStopId) = null
        override suspend fun restoreFavoriteStop(snapshot: com.rafaam11.businfo.domain.FavoriteRemovalSnapshot) = Unit
        override suspend fun replaceStopCatalog(stops: List<StopCatalogItem>) { stopValues.clear(); stopValues.addAll(stops) }
        override suspend fun stops() = stopValues.toList()
        override suspend fun searchStops(query: String, limit: Int) = stopValues.filter {
            it.stopName.contains(query, ignoreCase = true) || it.stopId.contains(query, ignoreCase = true)
        }.take(limit)
        override fun observeStopArrival(stopId: String): Flow<StopArrivalSnapshot?> = arrivals.getOrPut(stopId) { MutableStateFlow(null) }
        override suspend fun stopArrival(stopId: String) = arrivals[stopId]?.value
        override suspend fun saveStopArrival(snapshot: StopArrivalSnapshot) {
            arrivals.getOrPut(snapshot.stopId) { MutableStateFlow(null) }.value = snapshot
        }
        override fun observeWidgetBinding(appWidgetId: Int): Flow<WidgetBinding?> = flowOf(null)
        override suspend fun widgetBinding(appWidgetId: Int): WidgetBinding? = null
        override suspend fun saveWidgetBinding(binding: WidgetBinding) = Unit
        override suspend fun deleteWidgetBinding(appWidgetId: Int) = Unit
    }

    private class FakeBusLocal : BusLocalDataSource {
        val routeValues = mutableListOf<RouteSummary>()
        override fun observeDashboard(): Flow<List<FavoriteDashboardSnapshot>> = flowOf(emptyList())
        override suspend fun routes() = routeValues.toList()
        override suspend fun replaceRoutes(routes: List<RouteSummary>) { routeValues.clear(); routeValues.addAll(routes) }
        override suspend fun routeStops(routeId: String) = emptyList<RouteStop>()
        override suspend fun replaceRouteStops(routeId: String, stops: List<RouteStop>) = Unit
        override suspend fun favorite(slot: CommuteSlot): FavoriteSelection? = null
        override suspend fun saveFavorite(selection: FavoriteSelection) = Unit
        override suspend fun deleteFavorite(slot: CommuteSlot) = Unit
        override suspend fun saveArrival(slot: CommuteSlot, snapshot: ArrivalSnapshot) = Unit
        override suspend fun syncTime(key: String): Instant? = null
        override suspend fun saveSyncTime(key: String, instant: Instant) = Unit
        override suspend fun vehicleBatch(routeId: String): VehicleBatch? = null
        override suspend fun saveVehicleBatch(routeId: String, batch: VehicleBatch) = Unit
        override suspend fun routeGeometry(routeId: String, moveDirection: String): RouteGeometry? = null
        override suspend fun saveRouteGeometry(geometry: RouteGeometry) = Unit
    }
}

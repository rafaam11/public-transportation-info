package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.credential.CredentialStore
import com.rafaam11.businfo.data.local.BusLocalDataSource
import com.rafaam11.businfo.data.remote.DaeguBusRemoteDataSource
import com.rafaam11.businfo.data.remote.RemoteResult
import com.rafaam11.businfo.domain.ArrivalSnapshot
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteDashboardSnapshot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.RouteGeometry
import com.rafaam11.businfo.domain.RouteLink
import com.rafaam11.businfo.domain.RouteNode
import com.rafaam11.businfo.domain.RouteSegment
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
import org.junit.Test

class RouteGeometryRepositoryTest {
    private val now = Instant.parse("2026-07-17T12:00:00Z")
    private val selection = FavoriteSelection(
        CommuteSlot.MORNING, "route", "급행8-1", "0", "검단동 방면", "stop", "효동초등학교건너",
    )
    private val stops = listOf(RouteStop("route", "stop", "효동초등학교건너", "0", 8, 128.61, 35.81))
    private val local = FakeLocal(stops = stops)
    private val remote = FakeRemote()
    private val repository = RouteGeometryRepository(
        MemoryCredential("key"), remote, local, Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test fun `fresh geometry cache avoids node and link requests`() = runTest {
        local.geometry = geometry(now.minusSeconds(86_399))

        val result = repository.load(selection)

        assertEquals(RouteMapLoadResult.Success(local.geometry!!, stops, null), result)
        assertEquals(0, remote.basicNodeCalls)
        assertEquals(0, remote.routeLinkCalls)
    }

    @Test fun `failed geometry refresh retains cache and reports warning`() = runTest {
        local.geometry = geometry(now.minusSeconds(86_401))
        remote.nodeResult = RemoteResult.Failure(BusDataError.NetworkUnavailable)

        val result = repository.load(selection) as RouteMapLoadResult.Success

        assertEquals(local.geometry, result.geometry)
        assertEquals(BusDataError.NetworkUnavailable, result.warning)
    }

    @Test fun `remote link data is assembled and cached for selected direction`() = runTest {
        remote.nodeResult = RemoteResult.Success(listOf(
            RouteNode("a", 128.60, 35.80), RouteNode("b", 128.61, 35.81),
        ))
        remote.linkResult = RemoteResult.Success(listOf(
            RouteLink("route", "link", "0", 1, "a", "b"),
            RouteLink("route", "reverse", "1", 1, "b", "a"),
        ))

        val result = repository.load(selection) as RouteMapLoadResult.Success

        assertEquals(listOf(GeoPoint(128.60, 35.80), GeoPoint(128.61, 35.81)), result.geometry.segments.single().points)
        assertEquals(result.geometry, local.geometry)
        assertEquals("0", result.geometry.moveDirection)
    }

    private fun geometry(fetchedAt: Instant) = RouteGeometry(
        "route",
        "0",
        listOf(RouteSegment(listOf("a", "b"), listOf(GeoPoint(128.60, 35.80), GeoPoint(128.61, 35.81)))),
        fetchedAt,
    )

    private class MemoryCredential(private val key: String?) : CredentialStore {
        override fun read() = key
        override fun write(serviceKey: String) = Unit
        override fun clear() = Unit
    }

    private class FakeRemote : DaeguBusRemoteDataSource {
        var basicNodeCalls = 0
        var routeLinkCalls = 0
        var nodeResult: RemoteResult<List<RouteNode>> = RemoteResult.Success(emptyList())
        var linkResult: RemoteResult<List<RouteLink>> = RemoteResult.Success(emptyList())
        override suspend fun validateKey(serviceKey: String) = RemoteResult.Success(Unit)
        override suspend fun vehicles(serviceKey: String, routeId: String) = RemoteResult.Success(emptyList<VehicleSnapshot>())
        override suspend fun basicNodes(serviceKey: String): RemoteResult<List<RouteNode>> { basicNodeCalls++; return nodeResult }
        override suspend fun routeLinks(serviceKey: String, routeId: String): RemoteResult<List<RouteLink>> { routeLinkCalls++; return linkResult }
    }

    private class FakeLocal(private val stops: List<RouteStop>) : BusLocalDataSource {
        var geometry: RouteGeometry? = null
        override fun observeDashboard(): Flow<List<FavoriteDashboardSnapshot>> = flowOf(emptyList())
        override suspend fun routes() = emptyList<RouteSummary>()
        override suspend fun replaceRoutes(routes: List<RouteSummary>) = Unit
        override suspend fun routeStops(routeId: String) = stops
        override suspend fun replaceRouteStops(routeId: String, stops: List<RouteStop>) = Unit
        override suspend fun favorite(slot: CommuteSlot): FavoriteSelection? = null
        override suspend fun saveFavorite(selection: FavoriteSelection) = Unit
        override suspend fun deleteFavorite(slot: CommuteSlot) = Unit
        override suspend fun saveArrival(slot: CommuteSlot, snapshot: ArrivalSnapshot) = Unit
        override suspend fun syncTime(key: String): Instant? = null
        override suspend fun saveSyncTime(key: String, instant: Instant) = Unit
        override suspend fun vehicleBatch(routeId: String): VehicleBatch? = null
        override suspend fun saveVehicleBatch(routeId: String, batch: VehicleBatch) = Unit
        override suspend fun routeGeometry(routeId: String, moveDirection: String) = geometry
        override suspend fun saveRouteGeometry(geometry: RouteGeometry) { this.geometry = geometry }
    }
}

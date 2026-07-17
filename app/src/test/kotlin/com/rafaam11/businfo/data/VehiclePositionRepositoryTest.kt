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
import com.rafaam11.businfo.domain.RouteGeometry
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.VehicleLoadResult
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

class VehiclePositionRepositoryTest {
    private val now = Instant.parse("2026-07-17T12:00:00Z")
    private val selection = FavoriteSelection(
        CommuteSlot.MORNING, "route", "급행8-1", "0", "검단동 방면", "stop", "효동초등학교건너",
    )
    private val local = FakeLocal()
    private val remote = FakeRemote()
    private val repository = VehiclePositionRepository(
        MemoryCredential("key"), remote, local, Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test fun `successful empty response replaces prior vehicle state`() = runTest {
        local.batch = VehicleBatch.from(listOf(vehicle("0")), now.minusSeconds(60))
        remote.result = RemoteResult.Success(emptyList())

        val result = repository.refresh(selection) as VehicleLoadResult.Success

        assertTrue(result.batch.vehicles.isEmpty())
        assertEquals(now, result.batch.fetchedAt)
        assertTrue(local.batch!!.vehicles.isEmpty())
    }

    @Test fun `vehicle refresh returns only selected direction`() = runTest {
        remote.result = RemoteResult.Success(listOf(vehicle("0"), vehicle("1")))

        val result = repository.refresh(selection) as VehicleLoadResult.Success

        assertEquals(listOf("0"), result.batch.vehicles.map(VehicleSnapshot::moveDirection))
        assertEquals(2, local.batch!!.vehicles.size)
    }

    @Test fun `failed refresh retains only selected direction cache`() = runTest {
        local.batch = VehicleBatch.from(listOf(vehicle("0"), vehicle("1")), now.minusSeconds(20))
        remote.result = RemoteResult.Failure(BusDataError.NetworkUnavailable)

        val result = repository.refresh(selection) as VehicleLoadResult.Failure

        assertEquals(listOf("0"), result.retained!!.vehicles.map(VehicleSnapshot::moveDirection))
    }

    @Test fun `nonempty response without a valid requested route retains cache and fails`() = runTest {
        local.batch = VehicleBatch.from(listOf(vehicle("0")), now.minusSeconds(20))
        remote.result = RemoteResult.Success(listOf(
            vehicle("0").copy(routeId = "another-route"),
            vehicle("0").copy(latitude = 0.0, longitude = 0.0),
        ))

        val result = repository.refresh(selection) as VehicleLoadResult.Failure

        assertEquals(BusDataError.MalformedResponse, result.error)
        assertEquals(1, result.retained!!.vehicles.size)
        assertEquals(now.minusSeconds(20), local.batch!!.fetchedAt)
    }

    @Test fun `failed refresh never exposes implausible cached coordinates`() = runTest {
        local.batch = VehicleBatch.from(
            listOf(vehicle("0").copy(latitude = 0.0, longitude = 0.0)),
            now.minusSeconds(20),
        )
        remote.result = RemoteResult.Failure(BusDataError.NetworkUnavailable)

        val result = repository.refresh(selection) as VehicleLoadResult.Failure

        assertTrue(result.retained!!.vehicles.isEmpty())
    }

    private fun vehicle(direction: String) = VehicleSnapshot(
        "route", "급행8-1", direction, "stop", 5, 35.81, 128.61, null, null, null,
    )

    private class MemoryCredential(private val key: String?) : CredentialStore {
        override fun read() = key
        override fun write(serviceKey: String) = Unit
        override fun clear() = Unit
    }

    private class FakeRemote : DaeguBusRemoteDataSource {
        var result: RemoteResult<List<VehicleSnapshot>> = RemoteResult.Success(emptyList())
        override suspend fun validateKey(serviceKey: String) = RemoteResult.Success(Unit)
        override suspend fun vehicles(serviceKey: String, routeId: String) = result
    }

    private class FakeLocal : BusLocalDataSource {
        var batch: VehicleBatch? = null
        override fun observeDashboard(): Flow<List<FavoriteDashboardSnapshot>> = flowOf(emptyList())
        override suspend fun routes() = emptyList<RouteSummary>()
        override suspend fun replaceRoutes(routes: List<RouteSummary>) = Unit
        override suspend fun routeStops(routeId: String) = emptyList<RouteStop>()
        override suspend fun replaceRouteStops(routeId: String, stops: List<RouteStop>) = Unit
        override suspend fun favorite(slot: CommuteSlot): FavoriteSelection? = null
        override suspend fun saveFavorite(selection: FavoriteSelection) = Unit
        override suspend fun deleteFavorite(slot: CommuteSlot) = Unit
        override suspend fun saveArrival(slot: CommuteSlot, snapshot: ArrivalSnapshot) = Unit
        override suspend fun syncTime(key: String): Instant? = null
        override suspend fun saveSyncTime(key: String, instant: Instant) = Unit
        override suspend fun vehicleBatch(routeId: String) = batch
        override suspend fun saveVehicleBatch(routeId: String, batch: VehicleBatch) { this.batch = batch }
        override suspend fun routeGeometry(routeId: String, moveDirection: String): RouteGeometry? = null
        override suspend fun saveRouteGeometry(geometry: RouteGeometry) = Unit
    }
}

package com.rafaam11.businfo.data.local

import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomBusLocalDataSourceTest {
    @Test fun favoriteRouteTypeRoundTrips() = runTest {
        val local = RoomBusLocalDataSource(FakeBusDao())
        val selection = FavoriteSelection(
            CommuteSlot.MORNING, "route", "급행8-1", "0", "유곡리 방면", "stop", "진천역", "1",
        )

        local.saveFavorite(selection)

        assertEquals("1", local.favorite(CommuteSlot.MORNING)?.routeTypeCode)
    }

    private class FakeBusDao : BusDao() {
        private val favorites = mutableMapOf<String, FavoriteEntity>()
        private val favoriteFlow = MutableStateFlow<List<FavoriteEntity>>(emptyList())

        override suspend fun routes() = emptyList<RouteEntity>()
        override suspend fun insertRoutes(routes: List<RouteEntity>) = Unit
        override suspend fun clearRoutes() = Unit
        override suspend fun backfillFavoriteRouteTypes() = Unit
        override suspend fun routeStops(routeId: String) = emptyList<RouteStopEntity>()
        override suspend fun insertRouteStops(stops: List<RouteStopEntity>) = Unit
        override suspend fun clearRouteStops(routeId: String) = Unit
        override fun observeFavorites(): Flow<List<FavoriteEntity>> = favoriteFlow
        override suspend fun favorite(slot: String) = favorites[slot]
        override suspend fun saveFavorite(favorite: FavoriteEntity) {
            favorites[favorite.slot] = favorite
            favoriteFlow.value = favorites.values.toList()
        }
        override suspend fun deleteFavoriteRow(slot: String) {
            favorites.remove(slot)
            favoriteFlow.value = favorites.values.toList()
        }
        override fun observeArrivals(): Flow<List<ArrivalSnapshotEntity>> = MutableStateFlow(emptyList())
        override suspend fun saveArrival(snapshot: ArrivalSnapshotEntity) = Unit
        override suspend fun deleteArrival(slot: String) = Unit
        override suspend fun sync(key: String): SyncEntity? = null
        override suspend fun saveSync(sync: SyncEntity) = Unit
        override suspend fun vehicleSnapshot(routeId: String): VehicleSnapshotEntity? = null
        override suspend fun saveVehicleSnapshot(snapshot: VehicleSnapshotEntity) = Unit
        override suspend fun routeGeometry(routeId: String, moveDirection: String): RouteGeometryEntity? = null
        override suspend fun saveRouteGeometry(geometry: RouteGeometryEntity) = Unit
    }
}

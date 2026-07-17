package com.rafaam11.businfo.data.local

import com.rafaam11.businfo.domain.ArrivalSnapshot
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteDashboardSnapshot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.VehicleBatch
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface BusLocalDataSource {
    fun observeDashboard(): Flow<List<FavoriteDashboardSnapshot>>
    suspend fun routes(): List<RouteSummary>
    suspend fun replaceRoutes(routes: List<RouteSummary>)
    suspend fun routeStops(routeId: String): List<RouteStop>
    suspend fun replaceRouteStops(routeId: String, stops: List<RouteStop>)
    suspend fun favorite(slot: CommuteSlot): FavoriteSelection?
    suspend fun saveFavorite(selection: FavoriteSelection)
    suspend fun deleteFavorite(slot: CommuteSlot)
    suspend fun saveArrival(slot: CommuteSlot, snapshot: ArrivalSnapshot)
    suspend fun syncTime(key: String): Instant?
    suspend fun saveSyncTime(key: String, instant: Instant)
    suspend fun vehicleBatch(routeId: String): VehicleBatch?
    suspend fun saveVehicleBatch(routeId: String, batch: VehicleBatch)
}

package com.rafaam11.businfo.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rafaam11.businfo.domain.ArrivalEstimate
import com.rafaam11.businfo.domain.ArrivalSnapshot
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteDashboardSnapshot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.VehicleSnapshot
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomBusLocalDataSource(
    private val dao: BusDao,
    private val gson: Gson = Gson(),
) : BusLocalDataSource {
    override fun observeDashboard(): Flow<List<FavoriteDashboardSnapshot>> =
        combine(dao.observeFavorites(), dao.observeArrivals()) { favorites, snapshots ->
            val arrivalsBySlot = snapshots.associateBy(ArrivalSnapshotEntity::slot)
            favorites.map { favorite ->
                val arrival = arrivalsBySlot[favorite.slot]
                FavoriteDashboardSnapshot(
                    selection = favorite.toDomain(),
                    arrivals = arrival?.let { gson.fromJson(it.arrivalsJson, ARRIVAL_LIST_TYPE) } ?: emptyList(),
                    fetchedAt = arrival?.let { Instant.ofEpochMilli(it.fetchedAtEpochMillis) },
                )
            }.sortedBy { it.selection.slot.ordinal }
        }

    override suspend fun routes() = dao.routes().map { it.toDomain() }
    override suspend fun replaceRoutes(routes: List<RouteSummary>) = dao.replaceRoutes(routes.map { it.toEntity() })
    override suspend fun routeStops(routeId: String) = dao.routeStops(routeId).map { it.toDomain() }
    override suspend fun replaceRouteStops(routeId: String, stops: List<RouteStop>) =
        dao.replaceRouteStops(routeId, stops.map { it.toEntity() })
    override suspend fun favorite(slot: CommuteSlot) = dao.favorite(slot.name)?.toDomain()
    override suspend fun saveFavorite(selection: FavoriteSelection) = dao.saveFavorite(selection.toEntity())
    override suspend fun deleteFavorite(slot: CommuteSlot) = dao.deleteFavorite(slot.name)
    override suspend fun saveArrival(slot: CommuteSlot, snapshot: ArrivalSnapshot) = dao.saveArrival(
        ArrivalSnapshotEntity(slot.name, gson.toJson(snapshot.arrivals), snapshot.fetchedAt.toEpochMilli()),
    )
    override suspend fun syncTime(key: String) = dao.sync(key)?.let { Instant.ofEpochMilli(it.fetchedAtEpochMillis) }
    override suspend fun saveSyncTime(key: String, instant: Instant) = dao.saveSync(SyncEntity(key, instant.toEpochMilli()))
    override suspend fun vehicleBatch(routeId: String): VehicleBatch? = dao.vehicleSnapshot(routeId)?.let {
        VehicleBatch.from(gson.fromJson(it.vehiclesJson, VEHICLE_LIST_TYPE), Instant.ofEpochMilli(it.fetchedAtEpochMillis))
    }
    override suspend fun saveVehicleBatch(routeId: String, batch: VehicleBatch) = dao.saveVehicleSnapshot(
        VehicleSnapshotEntity(routeId, gson.toJson(batch.vehicles), batch.fetchedAt.toEpochMilli()),
    )

    private fun RouteEntity.toDomain() = RouteSummary(routeId, routeNo, startName, endName, directionNote, reverseDirectionNote)
    private fun RouteSummary.toEntity() = RouteEntity(routeId, routeNo, startName, endName, directionNote, reverseDirectionNote)
    private fun RouteStopEntity.toDomain() = RouteStop(routeId, stopId, stopName, moveDirection, sequence, longitude, latitude)
    private fun RouteStop.toEntity() = RouteStopEntity(routeId, stopId, stopName, moveDirection, sequence, longitude, latitude)
    private fun FavoriteEntity.toDomain() = FavoriteSelection(
        CommuteSlot.valueOf(slot), routeId, routeNo, directionCode, directionLabel, stopId, stopName,
    )
    private fun FavoriteSelection.toEntity() = FavoriteEntity(
        slot.name, routeId, routeNo, directionCode, directionLabel, stopId, stopName,
    )

    private companion object {
        val ARRIVAL_LIST_TYPE = object : TypeToken<List<ArrivalEstimate>>() {}.type
        val VEHICLE_LIST_TYPE = object : TypeToken<List<VehicleSnapshot>>() {}.type
    }
}

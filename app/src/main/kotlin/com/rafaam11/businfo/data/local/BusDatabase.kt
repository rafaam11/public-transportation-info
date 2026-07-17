package com.rafaam11.businfo.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "routes", primaryKeys = ["routeId"])
data class RouteEntity(
    val routeId: String,
    val routeNo: String,
    val startName: String,
    val endName: String,
    val directionNote: String?,
    val reverseDirectionNote: String?,
)

@Entity(tableName = "route_stops", primaryKeys = ["routeId", "moveDirection", "sequence"])
data class RouteStopEntity(
    val routeId: String,
    val stopId: String,
    val stopName: String,
    val moveDirection: String,
    val sequence: Int,
    val longitude: Double,
    val latitude: Double,
)

@Entity(tableName = "favorites", primaryKeys = ["slot"])
data class FavoriteEntity(
    val slot: String,
    val routeId: String,
    val routeNo: String,
    val directionCode: String,
    val directionLabel: String,
    val stopId: String,
    val stopName: String,
)

@Entity(tableName = "arrival_snapshots", primaryKeys = ["slot"])
data class ArrivalSnapshotEntity(val slot: String, val arrivalsJson: String, val fetchedAtEpochMillis: Long)

@Entity(tableName = "vehicle_snapshots", primaryKeys = ["routeId"])
data class VehicleSnapshotEntity(val routeId: String, val vehiclesJson: String, val fetchedAtEpochMillis: Long)

@Entity(tableName = "sync_metadata", primaryKeys = ["syncKey"])
data class SyncEntity(val syncKey: String, val fetchedAtEpochMillis: Long)

@Entity(tableName = "route_geometries", primaryKeys = ["routeId", "moveDirection"])
data class RouteGeometryEntity(
    val routeId: String,
    val moveDirection: String,
    val segmentsJson: String,
    val fetchedAtEpochMillis: Long,
)

@Dao
abstract class BusDao {
    @Query("SELECT * FROM routes") abstract suspend fun routes(): List<RouteEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertRoutes(routes: List<RouteEntity>)
    @Query("DELETE FROM routes") abstract suspend fun clearRoutes()
    @Transaction open suspend fun replaceRoutes(routes: List<RouteEntity>) { clearRoutes(); insertRoutes(routes) }

    @Query("SELECT * FROM route_stops WHERE routeId = :routeId ORDER BY moveDirection, sequence")
    abstract suspend fun routeStops(routeId: String): List<RouteStopEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertRouteStops(stops: List<RouteStopEntity>)
    @Query("DELETE FROM route_stops WHERE routeId = :routeId") abstract suspend fun clearRouteStops(routeId: String)
    @Transaction open suspend fun replaceRouteStops(routeId: String, stops: List<RouteStopEntity>) {
        clearRouteStops(routeId); insertRouteStops(stops)
    }

    @Query("SELECT * FROM favorites") abstract fun observeFavorites(): Flow<List<FavoriteEntity>>
    @Query("SELECT * FROM favorites WHERE slot = :slot") abstract suspend fun favorite(slot: String): FavoriteEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun saveFavorite(favorite: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE slot = :slot") abstract suspend fun deleteFavoriteRow(slot: String)

    @Query("SELECT * FROM arrival_snapshots") abstract fun observeArrivals(): Flow<List<ArrivalSnapshotEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun saveArrival(snapshot: ArrivalSnapshotEntity)
    @Query("DELETE FROM arrival_snapshots WHERE slot = :slot") abstract suspend fun deleteArrival(slot: String)
    @Transaction open suspend fun deleteFavorite(slot: String) { deleteFavoriteRow(slot); deleteArrival(slot) }

    @Query("SELECT * FROM sync_metadata WHERE syncKey = :key") abstract suspend fun sync(key: String): SyncEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun saveSync(sync: SyncEntity)

    @Query("SELECT * FROM vehicle_snapshots WHERE routeId = :routeId")
    abstract suspend fun vehicleSnapshot(routeId: String): VehicleSnapshotEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun saveVehicleSnapshot(snapshot: VehicleSnapshotEntity)

    @Query("SELECT * FROM route_geometries WHERE routeId = :routeId AND moveDirection = :moveDirection")
    abstract suspend fun routeGeometry(routeId: String, moveDirection: String): RouteGeometryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveRouteGeometry(geometry: RouteGeometryEntity)
}

@Database(
    entities = [RouteEntity::class, RouteStopEntity::class, FavoriteEntity::class, ArrivalSnapshotEntity::class,
        VehicleSnapshotEntity::class, SyncEntity::class, RouteGeometryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class BusDatabase : RoomDatabase() {
    abstract fun dao(): BusDao
}

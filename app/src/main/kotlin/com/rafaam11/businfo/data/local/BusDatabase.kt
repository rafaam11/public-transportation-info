package com.rafaam11.businfo.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Index
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
    val routeTypeCode: String?,
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
    val routeTypeCode: String?,
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

@Entity(
    tableName = "stops",
    indices = [Index("stopName"), Index(value = ["longitude", "latitude"])],
)
data class StopCatalogEntity(
    @androidx.room.PrimaryKey val stopId: String,
    val stopName: String,
    val longitude: Double,
    val latitude: Double,
)

@Entity(
    tableName = "favorite_stops",
    indices = [Index(value = ["stopId"], unique = true), Index("sortOrder")],
)
data class FavoriteStopEntity(
    @androidx.room.PrimaryKey val id: String,
    val stopId: String,
    val stopName: String,
    val longitude: Double,
    val latitude: Double,
    val sortOrder: Int,
)

@Entity(
    tableName = "favorite_pinned_routes",
    primaryKeys = ["favoriteStopId", "routeId", "moveDirection"],
    indices = [Index("favoriteStopId"), Index(value = ["favoriteStopId", "sortOrder"])],
)
data class FavoritePinnedRouteEntity(
    val favoriteStopId: String,
    val routeId: String,
    val moveDirection: String,
    val routeNo: String,
    val directionLabel: String,
    val sortOrder: Int,
    val routeTypeCode: String?,
)

@Entity(tableName = "stop_arrival_snapshots")
data class StopArrivalSnapshotEntity(
    @androidx.room.PrimaryKey val stopId: String,
    val groupsJson: String,
    val fetchedAtEpochMillis: Long,
)

@Entity(tableName = "widget_bindings", indices = [Index("favoriteStopId")])
data class WidgetBindingEntity(
    @androidx.room.PrimaryKey val appWidgetId: Int,
    val favoriteStopId: String,
    val configuredAtEpochMillis: Long,
)

@Dao
abstract class BusDao {
    @Query("SELECT * FROM routes") abstract suspend fun routes(): List<RouteEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertRoutes(routes: List<RouteEntity>)
    @Query("DELETE FROM routes") abstract suspend fun clearRoutes()
    @Query("""UPDATE favorites
        SET routeTypeCode = (SELECT routeTypeCode FROM routes WHERE routes.routeId = favorites.routeId)
        WHERE routeTypeCode IS NULL""")
    abstract suspend fun backfillFavoriteRouteTypes()
    @Transaction open suspend fun replaceRoutes(routes: List<RouteEntity>) {
        clearRoutes()
        insertRoutes(routes)
        backfillFavoriteRouteTypes()
    }

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

@Dao
abstract class StopCenteredDao {
    @Query("SELECT * FROM stops ORDER BY stopName") abstract suspend fun stops(): List<StopCatalogEntity>
    @Query("SELECT * FROM stops WHERE stopName LIKE '%' || :query || '%' OR stopId LIKE '%' || :query || '%' ORDER BY stopName LIMIT :limit")
    abstract suspend fun searchStops(query: String, limit: Int): List<StopCatalogEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertStops(stops: List<StopCatalogEntity>)
    @Query("DELETE FROM stops") abstract suspend fun clearStops()
    @Transaction open suspend fun replaceStops(stops: List<StopCatalogEntity>) { clearStops(); insertStops(stops) }

    @Query("SELECT * FROM favorite_stops ORDER BY sortOrder, id")
    abstract fun observeFavoriteStops(): Flow<List<FavoriteStopEntity>>
    @Query("SELECT * FROM favorite_stops WHERE id = :id") abstract suspend fun favoriteStop(id: String): FavoriteStopEntity?
    @Query("SELECT * FROM favorite_stops WHERE stopId = :stopId")
    abstract suspend fun favoriteStopByStopId(stopId: String): FavoriteStopEntity?
    @Query("SELECT COUNT(*) FROM favorite_stops") abstract suspend fun favoriteStopCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun saveFavoriteStop(stop: FavoriteStopEntity)
    @Query("DELETE FROM favorite_stops WHERE id = :id") abstract suspend fun deleteFavoriteStopRow(id: String)

    @Query("SELECT * FROM favorite_pinned_routes WHERE favoriteStopId = :favoriteStopId ORDER BY sortOrder")
    abstract suspend fun pinnedRoutes(favoriteStopId: String): List<FavoritePinnedRouteEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPinnedRoutes(routes: List<FavoritePinnedRouteEntity>)
    @Query("DELETE FROM favorite_pinned_routes WHERE favoriteStopId = :favoriteStopId")
    abstract suspend fun clearPinnedRoutes(favoriteStopId: String)
    @Transaction open suspend fun replacePinnedRoutes(favoriteStopId: String, routes: List<FavoritePinnedRouteEntity>) {
        clearPinnedRoutes(favoriteStopId); insertPinnedRoutes(routes)
    }
    @Transaction open suspend fun saveFavoriteWithPinned(
        stop: FavoriteStopEntity,
        routes: List<FavoritePinnedRouteEntity>,
    ) {
        saveFavoriteStop(stop)
        replacePinnedRoutes(stop.id, routes)
    }

    @Query("SELECT * FROM stop_arrival_snapshots WHERE stopId = :stopId")
    abstract fun observeStopArrival(stopId: String): Flow<StopArrivalSnapshotEntity?>
    @Query("SELECT * FROM stop_arrival_snapshots WHERE stopId = :stopId")
    abstract suspend fun stopArrival(stopId: String): StopArrivalSnapshotEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveStopArrival(snapshot: StopArrivalSnapshotEntity)

    @Query("SELECT * FROM widget_bindings WHERE appWidgetId = :appWidgetId")
    abstract fun observeWidgetBinding(appWidgetId: Int): Flow<WidgetBindingEntity?>
    @Query("SELECT * FROM widget_bindings WHERE appWidgetId = :appWidgetId")
    abstract suspend fun widgetBinding(appWidgetId: Int): WidgetBindingEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveWidgetBinding(binding: WidgetBindingEntity)
    @Query("DELETE FROM widget_bindings WHERE appWidgetId = :appWidgetId")
    abstract suspend fun deleteWidgetBinding(appWidgetId: Int)
    @Query("DELETE FROM widget_bindings WHERE favoriteStopId = :favoriteStopId")
    abstract suspend fun deleteWidgetBindingsForFavorite(favoriteStopId: String)

    @Transaction open suspend fun deleteStopCenteredFavorite(id: String) {
        deleteWidgetBindingsForFavorite(id)
        clearPinnedRoutes(id)
        deleteFavoriteStopRow(id)
    }
}

@Database(
    entities = [RouteEntity::class, RouteStopEntity::class, FavoriteEntity::class, ArrivalSnapshotEntity::class,
        VehicleSnapshotEntity::class, SyncEntity::class, RouteGeometryEntity::class, StopCatalogEntity::class,
        FavoriteStopEntity::class, FavoritePinnedRouteEntity::class, StopArrivalSnapshotEntity::class,
        WidgetBindingEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class BusDatabase : RoomDatabase() {
    abstract fun dao(): BusDao
    abstract fun stopCenteredDao(): StopCenteredDao
}

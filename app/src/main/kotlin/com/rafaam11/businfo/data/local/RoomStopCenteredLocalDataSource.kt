package com.rafaam11.businfo.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.PinnedRoute
import com.rafaam11.businfo.domain.RouteDirectionKey
import com.rafaam11.businfo.domain.StopArrivalGroup
import com.rafaam11.businfo.domain.StopArrivalSnapshot
import com.rafaam11.businfo.domain.StopCatalogItem
import com.rafaam11.businfo.domain.WidgetBinding
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomStopCenteredLocalDataSource(
    private val dao: StopCenteredDao,
    private val gson: Gson = Gson(),
) : StopCenteredLocalDataSource {
    override fun observeFavoriteStops(): Flow<List<FavoriteStop>> = dao.observeFavoriteStops().map { entities ->
        entities.map { entity -> entity.toDomain(dao.pinnedRoutes(entity.id)) }
    }

    override suspend fun favoriteStop(id: FavoriteStopId): FavoriteStop? =
        dao.favoriteStop(id.value)?.let { it.toDomain(dao.pinnedRoutes(it.id)) }

    override suspend fun favoriteStopByStopId(stopId: String): FavoriteStop? =
        dao.favoriteStopByStopId(stopId)?.let { it.toDomain(dao.pinnedRoutes(it.id)) }

    override suspend fun favoriteStopCount(): Int = dao.favoriteStopCount()

    override suspend fun saveFavoriteStop(stop: FavoriteStop) = dao.saveFavoriteWithPinned(
        stop.toEntity(),
        stop.pinnedRoutes.map { it.toEntity() },
    )

    override suspend fun deleteFavoriteStop(id: FavoriteStopId) = dao.deleteStopCenteredFavorite(id.value)

    override suspend fun replaceStopCatalog(stops: List<StopCatalogItem>) = dao.replaceStops(stops.map { it.toEntity() })
    override suspend fun stops(): List<StopCatalogItem> = dao.stops().map { it.toDomain() }
    override suspend fun searchStops(query: String, limit: Int): List<StopCatalogItem> =
        dao.searchStops(query.trim(), limit).map { it.toDomain() }

    override fun observeStopArrival(stopId: String): Flow<StopArrivalSnapshot?> =
        dao.observeStopArrival(stopId).map { it?.toDomain() }

    override suspend fun stopArrival(stopId: String): StopArrivalSnapshot? = dao.stopArrival(stopId)?.toDomain()

    override suspend fun saveStopArrival(snapshot: StopArrivalSnapshot) = dao.saveStopArrival(
        StopArrivalSnapshotEntity(
            snapshot.stopId,
            gson.toJson(snapshot.groups),
            snapshot.fetchedAt.toEpochMilli(),
        ),
    )

    override fun observeWidgetBinding(appWidgetId: Int): Flow<WidgetBinding?> =
        dao.observeWidgetBinding(appWidgetId).map { it?.toDomain() }

    override suspend fun widgetBinding(appWidgetId: Int): WidgetBinding? = dao.widgetBinding(appWidgetId)?.toDomain()

    override suspend fun saveWidgetBinding(binding: WidgetBinding) = dao.saveWidgetBinding(
        WidgetBindingEntity(binding.appWidgetId, binding.favoriteStopId.value, binding.configuredAt.toEpochMilli()),
    )

    override suspend fun deleteWidgetBinding(appWidgetId: Int) = dao.deleteWidgetBinding(appWidgetId)

    private fun FavoriteStopEntity.toDomain(routes: List<FavoritePinnedRouteEntity>) = FavoriteStop(
        FavoriteStopId(id),
        stopId,
        stopName,
        GeoPoint(longitude, latitude),
        sortOrder,
        routes.map { it.toDomain() },
    )

    private fun FavoriteStop.toEntity() = FavoriteStopEntity(
        id.value, stopId, stopName, point.longitude, point.latitude, sortOrder,
    )

    private fun FavoritePinnedRouteEntity.toDomain() = PinnedRoute(
        FavoriteStopId(favoriteStopId),
        RouteDirectionKey(routeId, moveDirection),
        routeNo,
        directionLabel,
        sortOrder,
        routeTypeCode,
    )

    private fun PinnedRoute.toEntity() = FavoritePinnedRouteEntity(
        favoriteStopId.value,
        key.routeId,
        key.moveDirection,
        routeNo,
        directionLabel,
        sortOrder,
        routeTypeCode,
    )

    private fun StopCatalogItem.toEntity() = StopCatalogEntity(stopId, stopName, longitude, latitude)
    private fun StopCatalogEntity.toDomain() = StopCatalogItem(stopId, stopName, longitude, latitude)

    private fun StopArrivalSnapshotEntity.toDomain() = StopArrivalSnapshot(
        stopId,
        gson.fromJson(groupsJson, STOP_ARRIVAL_GROUPS_TYPE),
        Instant.ofEpochMilli(fetchedAtEpochMillis),
    )

    private fun WidgetBindingEntity.toDomain() = WidgetBinding(
        appWidgetId,
        FavoriteStopId(favoriteStopId),
        Instant.ofEpochMilli(configuredAtEpochMillis),
    )

    private companion object {
        val STOP_ARRIVAL_GROUPS_TYPE = object : TypeToken<List<StopArrivalGroup>>() {}.type
    }
}

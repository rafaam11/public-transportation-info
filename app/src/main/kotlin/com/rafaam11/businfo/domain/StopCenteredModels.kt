package com.rafaam11.businfo.domain

import java.time.Instant
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@JvmInline
value class FavoriteStopId(val value: String) {
    init {
        require(value.isNotBlank())
    }

    companion object {
        fun create(): FavoriteStopId = FavoriteStopId(UUID.randomUUID().toString())
    }
}

data class RouteDirectionKey(
    val routeId: String,
    val moveDirection: String,
)

data class PinnedRoute(
    val favoriteStopId: FavoriteStopId,
    val key: RouteDirectionKey,
    val routeNo: String,
    val directionLabel: String,
    val sortOrder: Int,
    val routeTypeCode: String? = null,
)

data class FavoriteStop(
    val id: FavoriteStopId,
    val stopId: String,
    val stopName: String,
    val point: GeoPoint,
    val sortOrder: Int,
    val pinnedRoutes: List<PinnedRoute> = emptyList(),
)

data class StopCatalogItem(
    val stopId: String,
    val stopName: String,
    val longitude: Double,
    val latitude: Double,
) {
    val point: GeoPoint get() = GeoPoint(longitude, latitude)
}

data class StopArrival(
    val routeId: String,
    val routeNo: String,
    val moveDirection: String,
    val stopGap: Int,
    val arrivalSeconds: Int,
    val state: String?,
) {
    val key: RouteDirectionKey get() = RouteDirectionKey(routeId, moveDirection)
}

data class StopArrivalGroup(
    val key: RouteDirectionKey,
    val routeNo: String,
    val arrivals: List<StopArrival>,
) {
    val soonestArrivalSeconds: Int? get() = arrivals.minOfOrNull(StopArrival::arrivalSeconds)
}

data class StopArrivalSnapshot(
    val stopId: String,
    val groups: List<StopArrivalGroup>,
    val fetchedAt: Instant,
)

data class NearbyStop(
    val stop: StopCatalogItem,
    val distanceMeters: Int,
)

data class NearbyStopResult(
    val stops: List<NearbyStop>,
    val radiusMeters: Int,
)

data class PlaceResult(
    val name: String,
    val category: String,
    val address: String,
    val roadAddress: String,
    val point: GeoPoint,
)

data class WidgetBinding(
    val appWidgetId: Int,
    val favoriteStopId: FavoriteStopId,
    val configuredAt: Instant,
)

fun List<StopArrival>.groupByRouteDirection(): List<StopArrivalGroup> =
    groupBy(StopArrival::key)
        .map { (key, values) ->
            StopArrivalGroup(key, values.first().routeNo, values.sortedBy(StopArrival::arrivalSeconds))
        }
        .sortedWith(compareBy(nullsLast()) { it.soonestArrivalSeconds })

fun FavoriteStop.routesForHome(
    groups: List<StopArrivalGroup>,
    limit: Int,
): List<StopArrivalGroup> {
    require(limit >= 0)
    if (pinnedRoutes.isEmpty()) return groups.take(limit)
    val groupsByKey = groups.associateBy(StopArrivalGroup::key)
    return pinnedRoutes.sortedBy(PinnedRoute::sortOrder).mapNotNull { groupsByKey[it.key] }.take(limit)
}

fun selectNearbyStops(
    origin: GeoPoint,
    stops: Collection<StopCatalogItem>,
    primaryRadiusMeters: Int = 500,
    expandedRadiusMeters: Int = 1_000,
    minimumPrimaryCount: Int = 5,
    limit: Int = 10,
): NearbyStopResult {
    val measured = stops.asSequence()
        .map { stop -> NearbyStop(stop, distanceMeters(origin, stop.point)) }
        .sortedBy(NearbyStop::distanceMeters)
        .toList()
    val primary = measured.filter { it.distanceMeters <= primaryRadiusMeters }
    val radius = if (primary.size >= minimumPrimaryCount) primaryRadiusMeters else expandedRadiusMeters
    return NearbyStopResult(measured.filter { it.distanceMeters <= radius }.take(limit), radius)
}

private fun distanceMeters(first: GeoPoint, second: GeoPoint): Int {
    val latitudeDistance = Math.toRadians(second.latitude - first.latitude)
    val longitudeDistance = Math.toRadians(second.longitude - first.longitude)
    val firstLatitude = Math.toRadians(first.latitude)
    val secondLatitude = Math.toRadians(second.latitude)
    val haversine = sin(latitudeDistance / 2).let { it * it } +
        cos(firstLatitude) * cos(secondLatitude) * sin(longitudeDistance / 2).let { it * it }
    return (EARTH_RADIUS_METERS * 2 * asin(sqrt(haversine))).roundToInt()
}

private const val EARTH_RADIUS_METERS = 6_371_000.0

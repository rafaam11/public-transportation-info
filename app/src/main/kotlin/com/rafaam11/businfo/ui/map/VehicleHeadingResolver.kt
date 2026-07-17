package com.rafaam11.businfo.ui.map

import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.RouteGeometry
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

object VehicleHeadingResolver {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun resolve(
        point: GeoPoint,
        geometry: RouteGeometry,
        maxDistanceMeters: Double = 80.0,
    ): Float? {
        val originLatRadians = Math.toRadians(point.latitude)
        var nearestDistanceSquared = Double.POSITIVE_INFINITY
        var nearestAngle: Float? = null

        geometry.segments.forEach { segment ->
            segment.points.zipWithNext().forEach segmentPair@{ (a, b) ->
                val aEast = localEastMeters(point, a, originLatRadians)
                val aNorth = localNorthMeters(point, a)
                val bEast = localEastMeters(point, b, originLatRadians)
                val bNorth = localNorthMeters(point, b)
                val east = bEast - aEast
                val north = bNorth - aNorth
                val segmentLengthSquared = east * east + north * north
                if (segmentLengthSquared == 0.0) return@segmentPair

                val projection = (-(aEast * east + aNorth * north) / segmentLengthSquared)
                    .coerceIn(0.0, 1.0)
                val projectedEast = aEast + projection * east
                val projectedNorth = aNorth + projection * north
                val distanceSquared = projectedEast * projectedEast + projectedNorth * projectedNorth
                if (distanceSquared < nearestDistanceSquared) {
                    nearestDistanceSquared = distanceSquared
                    val bearing = Math.toDegrees(atan2(east, north))
                    nearestAngle = (((bearing - 90.0) % 360.0 + 360.0) % 360.0).toFloat()
                }
            }
        }

        return nearestAngle?.takeIf { sqrt(nearestDistanceSquared) <= maxDistanceMeters }
    }

    private fun localEastMeters(origin: GeoPoint, point: GeoPoint, originLatRadians: Double): Double =
        Math.toRadians(point.longitude - origin.longitude) * EARTH_RADIUS_METERS * cos(originLatRadians)

    private fun localNorthMeters(origin: GeoPoint, point: GeoPoint): Double =
        Math.toRadians(point.latitude - origin.latitude) * EARTH_RADIUS_METERS
}

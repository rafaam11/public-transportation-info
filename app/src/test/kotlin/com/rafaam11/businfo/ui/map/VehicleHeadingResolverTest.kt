package com.rafaam11.businfo.ui.map

import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.RouteGeometry
import com.rafaam11.businfo.domain.RouteSegment
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleHeadingResolverTest {
    private val resolver = VehicleHeadingResolver

    @Test
    fun resolvesEastboundSegmentFromGeometryOrder() {
        val vehicle = GeoPoint(longitude = 128.6005, latitude = 35.8701)
        val eastbound = geometry(
            GeoPoint(128.6000, 35.8700),
            GeoPoint(128.6010, 35.8700),
        )

        assertEquals(0f, resolver.resolve(vehicle, eastbound, 80.0)!!, 0.5f)
    }

    @Test
    fun reversingGeometryOrderReversesHeading() {
        val vehicle = GeoPoint(longitude = 128.6005, latitude = 35.8701)
        val westbound = geometry(
            GeoPoint(128.6010, 35.8700),
            GeoPoint(128.6000, 35.8700),
        )

        assertEquals(180f, resolver.resolve(vehicle, westbound, 80.0)!!, 0.5f)
    }

    @Test
    fun resolvesNorthboundSegment() {
        val vehicle = GeoPoint(longitude = 128.6001, latitude = 35.8705)
        val northbound = geometry(
            GeoPoint(128.6000, 35.8700),
            GeoPoint(128.6000, 35.8710),
        )

        assertEquals(270f, resolver.resolve(vehicle, northbound, 80.0)!!, 0.5f)
    }

    @Test
    fun curvedRouteUsesNearestSegment() {
        val vehicle = GeoPoint(longitude = 128.6011, latitude = 35.8705)
        val curved = geometry(
            GeoPoint(128.6000, 35.8700),
            GeoPoint(128.6010, 35.8700),
            GeoPoint(128.6010, 35.8710),
        )

        assertEquals(270f, resolver.resolve(vehicle, curved, 80.0)!!, 0.5f)
    }

    @Test
    fun vehicleBeyondMaximumDistanceHasNoHeading() {
        val farVehicle = GeoPoint(longitude = 128.6005, latitude = 35.8718)
        val eastbound = geometry(
            GeoPoint(128.6000, 35.8700),
            GeoPoint(128.6010, 35.8700),
        )

        assertNull(resolver.resolve(farVehicle, eastbound, 80.0))
    }

    private fun geometry(vararg points: GeoPoint): RouteGeometry = RouteGeometry(
        routeId = "route-1",
        moveDirection = "0",
        segments = listOf(RouteSegment(nodeIds = points.indices.map(Int::toString), points = points.toList())),
        fetchedAt = Instant.EPOCH,
    )
}

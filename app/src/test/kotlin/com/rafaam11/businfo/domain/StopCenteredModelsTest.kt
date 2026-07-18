package com.rafaam11.businfo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopCenteredModelsTest {
    private val origin = GeoPoint(longitude = 128.601, latitude = 35.871)

    @Test fun `nearby stops use five hundred metres when at least five exist`() {
        val stops = (1..6).map { index ->
            StopCatalogItem("stop-$index", "정류장 $index", 128.601, 35.871 + index * 0.0003)
        } + StopCatalogItem("far", "먼 정류장", 128.601, 35.88)

        val result = selectNearbyStops(origin, stops)

        assertEquals(500, result.radiusMeters)
        assertEquals(6, result.stops.size)
        assertTrue(result.stops.zipWithNext().all { (a, b) -> a.distanceMeters <= b.distanceMeters })
    }

    @Test fun `nearby stops expand to one kilometre when fewer than five are close`() {
        val stops = listOf(
            StopCatalogItem("near-1", "가까운 정류장", 128.601, 35.872),
            StopCatalogItem("near-2", "가까운 정류장 2", 128.601, 35.873),
            StopCatalogItem("expanded", "확장 정류장", 128.601, 35.877),
        )

        val result = selectNearbyStops(origin, stops)

        assertEquals(1_000, result.radiusMeters)
        assertEquals(listOf("near-1", "near-2", "expanded"), result.stops.map { it.stop.stopId })
    }

    @Test fun `stop arrivals are grouped by route and direction with soonest route first`() {
        val arrivals = listOf(
            StopArrival("r1", "814", "0", 2, 180, "3분"),
            StopArrival("r2", "급행5", "1", 1, 60, "곧 도착"),
            StopArrival("r1", "814", "0", 7, 600, "10분"),
        )

        val groups = arrivals.groupByRouteDirection()

        assertEquals(listOf("r2", "r1"), groups.map { it.key.routeId })
        assertEquals(listOf(180, 600), groups[1].arrivals.map { it.arrivalSeconds })
    }

    @Test fun `pinned routes win over automatic soonest routes`() {
        val favorite = FavoriteStop(
            id = FavoriteStopId("favorite"),
            stopId = "stop",
            stopName = "진천역",
            point = origin,
            sortOrder = 0,
            pinnedRoutes = listOf(
                PinnedRoute(FavoriteStopId("favorite"), RouteDirectionKey("r1", "0"), "814", "범물동", 0),
            ),
        )
        val groups = listOf(
            StopArrivalGroup(RouteDirectionKey("r2", "1"), "급행5", listOf(StopArrival("r2", "급행5", "1", 1, 60, null))),
            StopArrivalGroup(RouteDirectionKey("r1", "0"), "814", listOf(StopArrival("r1", "814", "0", 2, 180, null))),
        )

        assertEquals(listOf("r1"), favorite.routesForHome(groups, limit = 2).map { it.key.routeId })
    }
}

package com.rafaam11.businfo.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteGeometryAssemblerTest {
    private val nodes = listOf(
        RouteNode("a", 128.60, 35.80),
        RouteNode("b", 128.61, 35.81),
        RouteNode("c", 128.62, 35.82),
        RouteNode("x", 128.70, 35.90),
        RouteNode("y", 128.71, 35.91),
    )

    @Test fun `sorts links and reverses endpoints to preserve continuity`() {
        val result = RouteGeometryAssembler.assemble(
            "route", "0", nodes,
            listOf(
                RouteLink("route", "l2", "0", 2, "c", "b"),
                RouteLink("route", "l1", "0", 1, "a", "b"),
            ),
            Instant.EPOCH,
        )

        assertEquals(listOf("a", "b", "c"), result.segments.single().nodeIds)
    }

    @Test fun `splits disconnected links instead of drawing a bridge`() {
        val result = RouteGeometryAssembler.assemble(
            "route", "0", nodes,
            listOf(
                RouteLink("route", "l1", "0", 1, "a", "b"),
                RouteLink("route", "l2", "0", 2, "x", "y"),
            ),
            Instant.EPOCH,
        )

        assertEquals(listOf(listOf("a", "b"), listOf("x", "y")), result.segments.map { it.nodeIds })
    }

    @Test fun `drops links whose endpoint coordinates are missing`() {
        val result = RouteGeometryAssembler.assemble(
            "route", "0", nodes,
            listOf(RouteLink("route", "broken", "0", 1, "a", "missing")),
            Instant.EPOCH,
        )

        assertEquals(emptyList<RouteSegment>(), result.segments)
    }
}

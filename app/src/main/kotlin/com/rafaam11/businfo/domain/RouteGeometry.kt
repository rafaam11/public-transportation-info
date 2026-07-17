package com.rafaam11.businfo.domain

import java.time.Instant

data class GeoPoint(val longitude: Double, val latitude: Double)

data class RouteNode(
    val nodeId: String,
    val longitude: Double,
    val latitude: Double,
)

data class RouteLink(
    val routeId: String,
    val linkId: String,
    val moveDirection: String,
    val sequence: Int,
    val startNodeId: String,
    val endNodeId: String,
)

data class RouteSegment(
    val nodeIds: List<String>,
    val points: List<GeoPoint>,
)

data class RouteGeometry(
    val routeId: String,
    val moveDirection: String,
    val segments: List<RouteSegment>,
    val fetchedAt: Instant,
)

object RouteGeometryAssembler {
    fun assemble(
        routeId: String,
        moveDirection: String,
        nodes: Collection<RouteNode>,
        links: Collection<RouteLink>,
        fetchedAt: Instant,
    ): RouteGeometry {
        val nodeById = nodes.associateBy(RouteNode::nodeId)
        val completed = mutableListOf<List<String>>()
        var current = mutableListOf<String>()

        links.asSequence()
            .filter { it.routeId == routeId && it.moveDirection == moveDirection }
            .sortedBy(RouteLink::sequence)
            .forEach { link ->
                if (nodeById[link.startNodeId] == null || nodeById[link.endNodeId] == null) return@forEach
                if (current.isEmpty()) {
                    current = mutableListOf(link.startNodeId, link.endNodeId)
                } else {
                    when (current.last()) {
                        link.startNodeId -> current += link.endNodeId
                        link.endNodeId -> current += link.startNodeId
                        else -> {
                            completed += current
                            current = mutableListOf(link.startNodeId, link.endNodeId)
                        }
                    }
                }
            }
        if (current.size >= 2) completed += current

        val segments = completed.filter { it.size >= 2 }.map { nodeIds ->
            RouteSegment(
                nodeIds = nodeIds,
                points = nodeIds.map { id ->
                    nodeById.getValue(id).let { GeoPoint(it.longitude, it.latitude) }
                },
            )
        }
        return RouteGeometry(routeId, moveDirection, segments, fetchedAt)
    }
}

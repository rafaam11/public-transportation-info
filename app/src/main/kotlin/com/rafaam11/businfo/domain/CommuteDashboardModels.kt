package com.rafaam11.businfo.domain

import java.time.Instant
import kotlin.math.ceil

enum class CommuteSlot(val label: String) {
    MORNING("출근"),
    EVENING("퇴근"),
}

data class RouteSummary(
    val routeId: String,
    val routeNo: String,
    val startName: String,
    val endName: String,
    val directionNote: String?,
    val reverseDirectionNote: String?,
    val routeTypeCode: String? = null,
)

data class RouteStop(
    val routeId: String,
    val stopId: String,
    val stopName: String,
    val moveDirection: String,
    val sequence: Int,
    val longitude: Double,
    val latitude: Double,
)

data class FavoriteSelection(
    val slot: CommuteSlot,
    val routeId: String,
    val routeNo: String,
    val directionCode: String,
    val directionLabel: String,
    val stopId: String,
    val stopName: String,
    val routeTypeCode: String? = null,
)

data class ArrivalEstimate(
    val stopGap: Int,
    val arrivalSeconds: Int,
    val state: String?,
    val moveDirection: String = "",
) {
    fun primaryText(): String = when {
        stopGap < 0 -> state?.takeIf(String::isNotBlank) ?: "정보 없음"
        stopGap == 0 -> "도착 임박"
        stopGap == 1 -> "1정거장 전"
        else -> "${stopGap}정거장 전"
    }

    fun secondaryText(): String? = when {
        stopGap < 0 -> null
        else -> state?.takeIf(String::isNotBlank) ?: when {
            arrivalSeconds < 60 -> "곧 도착"
            else -> "${ceil(arrivalSeconds / 60.0).toInt()}분"
        }
    }
}

data class ArrivalSnapshot(
    val arrivals: List<ArrivalEstimate>,
    val fetchedAt: Instant,
)

data class FavoriteDashboardSnapshot(
    val selection: FavoriteSelection,
    val arrivals: List<ArrivalEstimate>,
    val fetchedAt: Instant?,
)

fun directionLabel(directionCode: String, apiNote: String?, stops: List<RouteStop>): String =
    apiNote?.trim()?.takeIf(String::isNotEmpty)
        ?: stops.filter { it.moveDirection == directionCode }
            .maxByOrNull(RouteStop::sequence)
            ?.let { "${it.stopName} 방면" }
        ?: "방향 $directionCode"

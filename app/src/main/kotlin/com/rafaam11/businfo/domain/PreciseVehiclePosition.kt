package com.rafaam11.businfo.domain

import java.time.Duration
import java.time.Instant

data class PreciseVehiclePosition(
    val sessionKey: String,
    val routeId: String,
    val moveDirection: String,
    val stopId: String?,
    val stopSequence: Int?,
    val point: GeoPoint,
    val observedAt: Instant,
    val heading: Float?,
    val arrivalState: String?,
)

data class PreciseVehicleBatch(
    val positions: List<PreciseVehiclePosition>,
    val rosterCount: Int,
    val failureCount: Int,
    val receivedAt: Instant,
    val rosterSessionKeys: Set<String> = positions.map(PreciseVehiclePosition::sessionKey).toSet(),
) {
    val successCount: Int get() = positions.size
}

enum class PrecisePositionFreshness { CURRENT, DELAYED, HIDDEN }

enum class PreciseSourceHealth { HEALTHY, PARTIAL, DELAYED, UNAVAILABLE }

fun PreciseVehiclePosition.freshnessAt(now: Instant): PrecisePositionFreshness {
    val ageSeconds = Duration.between(observedAt, now).seconds.coerceAtLeast(0)
    return when {
        ageSeconds <= 15 -> PrecisePositionFreshness.CURRENT
        ageSeconds <= 30 -> PrecisePositionFreshness.DELAYED
        else -> PrecisePositionFreshness.HIDDEN
    }
}

fun PreciseVehiclePosition.isValidFor(selection: FavoriteSelection, now: Instant): Boolean =
    sessionKey.isNotBlank() &&
        routeId == selection.routeId &&
        moveDirection == selection.directionCode &&
        point.longitude in 128.0..129.2 &&
        point.latitude in 35.3..36.3 &&
        !observedAt.isAfter(now.plusSeconds(5)) &&
        (heading == null || heading.isFinite())

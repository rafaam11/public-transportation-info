package com.rafaam11.businfo.ui

import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.DataFreshness
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.PrecisePositionFreshness
import com.rafaam11.businfo.domain.PreciseSourceHealth
import com.rafaam11.businfo.domain.PreciseVehicleBatch
import com.rafaam11.businfo.domain.RouteGeometry
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.freshnessAt
import java.time.Duration
import java.time.Instant

data class MapVehicleUi(
    val key: String,
    val point: GeoPoint,
    val stopName: String,
    val stopSequence: Int?,
    val remainingStops: Int?,
    val arrivalState: String?,
    val observedAt: Instant,
    val ageSeconds: Long,
    val delayed: Boolean,
    val headingDegrees: Float?,
)

data class RealtimeMapUiState(
    val selection: FavoriteSelection? = null,
    val geometry: RouteGeometry? = null,
    val stops: List<RouteStop> = emptyList(),
    val vehicleBatch: VehicleBatch? = null,
    val preciseBatch: PreciseVehicleBatch? = null,
    val visibleVehicles: List<MapVehicleUi> = emptyList(),
    val totalOperatingCount: Int? = null,
    val delayedVehicleCount: Int = 0,
    val hiddenVehicleCount: Int = 0,
    val preciseSourceHealth: PreciseSourceHealth = PreciseSourceHealth.UNAVAILABLE,
    val freshness: DataFreshness = DataFreshness.UNAVAILABLE,
    val dataAgeSeconds: Long? = null,
    val loadingGeometry: Boolean = false,
    val geometryError: BusDataError? = null,
    val vehicleError: BusDataError? = null,
    val preciseError: BusDataError? = null,
    val mapErrorCode: String? = null,
    val selectedVehicleKey: String? = null,
)

fun mapVehicles(
    selection: FavoriteSelection,
    stops: List<RouteStop>,
    batch: PreciseVehicleBatch,
    now: Instant,
): List<MapVehicleUi> {
    val stopById = stops.associateBy(RouteStop::stopId)
    val targetSequence = stops.firstOrNull { it.stopId == selection.stopId }?.sequence
    return batch.positions
        .filter { it.freshnessAt(now) != PrecisePositionFreshness.HIDDEN }
        .sortedBy { it.stopSequence ?: Int.MAX_VALUE }
        .map { vehicle ->
        val stop = vehicle.stopId?.let(stopById::get)
        val ageSeconds = Duration.between(vehicle.observedAt, now).seconds.coerceAtLeast(0)
        MapVehicleUi(
            key = vehicle.sessionKey,
            point = vehicle.point,
            stopName = stop?.stopName ?: "정류장 정보 없음",
            stopSequence = vehicle.stopSequence,
            remainingStops = vehicle.stopSequence?.let { sequence -> targetSequence?.minus(sequence) },
            arrivalState = vehicle.arrivalState,
            observedAt = vehicle.observedAt,
            ageSeconds = ageSeconds,
            delayed = vehicle.freshnessAt(now) == PrecisePositionFreshness.DELAYED,
            headingDegrees = vehicle.heading,
        )
    }
}

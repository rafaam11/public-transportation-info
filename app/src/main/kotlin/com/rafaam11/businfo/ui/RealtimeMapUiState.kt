package com.rafaam11.businfo.ui

import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.DataFreshness
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.RouteGeometry
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.VehicleBatch

data class MapVehicleUi(
    val key: String,
    val point: GeoPoint,
    val stopName: String,
    val stopSequence: Int?,
    val remainingStops: Int?,
    val arrivalState: String?,
)

data class RealtimeMapUiState(
    val selection: FavoriteSelection? = null,
    val geometry: RouteGeometry? = null,
    val stops: List<RouteStop> = emptyList(),
    val vehicleBatch: VehicleBatch? = null,
    val visibleVehicles: List<MapVehicleUi> = emptyList(),
    val freshness: DataFreshness = DataFreshness.UNAVAILABLE,
    val loadingGeometry: Boolean = false,
    val geometryError: BusDataError? = null,
    val vehicleError: BusDataError? = null,
    val mapErrorCode: String? = null,
    val selectedVehicleKey: String? = null,
)

fun mapVehicles(
    selection: FavoriteSelection,
    stops: List<RouteStop>,
    batch: VehicleBatch,
): List<MapVehicleUi> {
    val stopById = stops.associateBy(RouteStop::stopId)
    val targetSequence = stops.firstOrNull { it.stopId == selection.stopId }?.sequence
    return batch.vehicles.sortedBy { it.stopSequence ?: Int.MAX_VALUE }.mapIndexed { index, vehicle ->
        val stop = vehicle.stopId?.let(stopById::get)
        MapVehicleUi(
            key = listOf(
                batch.fetchedAt.toEpochMilli(), index, vehicle.stopSequence, vehicle.latitude, vehicle.longitude,
            ).joinToString(":"),
            point = GeoPoint(vehicle.longitude, vehicle.latitude),
            stopName = stop?.stopName ?: "정류장 정보 없음",
            stopSequence = vehicle.stopSequence,
            remainingStops = vehicle.stopSequence?.let { sequence -> targetSequence?.minus(sequence) },
            arrivalState = vehicle.arrivalState,
        )
    }
}

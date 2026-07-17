package com.rafaam11.businfo.ui.map

import android.graphics.Color
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Align
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.PathOverlay
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.RouteGeometry
import com.rafaam11.businfo.domain.RouteSegment
import com.rafaam11.businfo.ui.MapVehicleUi
import com.rafaam11.businfo.ui.RealtimeMapUiState

class NaverMapOverlayController(
    private val markerRenderer: BusMarkerRenderer = BusMarkerRenderer(),
    private val iconCache: BusMarkerIconCache = BusMarkerIconCache(markerRenderer),
) {
    private val paths = mutableListOf<PathOverlay>()
    private val stopMarkers = mutableListOf<Marker>()
    private val vehicleMarkers = mutableListOf<Marker>()
    private var initialFitPending = true
    private var lastFitRequest = 0

    fun render(
        map: NaverMap,
        state: RealtimeMapUiState,
        onVehicleSelected: (String) -> Unit,
        density: Float,
    ) {
        clear(paths)
        clear(stopMarkers)

        state.geometry?.segments.orEmpty()
            .filter { it.points.size >= 2 }
            .forEach { segment ->
                paths += PathOverlay().apply {
                    coords = segment.points.map { point -> point.toLatLng() }
                    color = Color.rgb(0, 91, 172)
                    width = 10
                    this.map = map
                }
            }

        state.stops.forEach { stop ->
            stopMarkers += Marker().apply {
                position = LatLng(stop.latitude, stop.longitude)
                captionText = stop.stopName
                iconTintColor = if (stop.stopId == state.selection?.stopId) {
                    Color.rgb(229, 57, 53)
                } else {
                    Color.rgb(117, 117, 117)
                }
                this.map = map
            }
        }

        ensureVehicleMarkerCount(state.visibleVehicles.size)
        val routeNo = state.selection?.routeNo.orEmpty()
        val palette = RoutePaletteResolver.resolve(state.selection?.routeTypeCode)
        val routeFallback = markerRenderer.captionFallback(routeNo, density)
        state.visibleVehicles.sortedBy(MapVehicleUi::key).forEachIndexed { index, vehicle ->
            val marker = vehicleMarkers[index]
            val selected = vehicle.key == state.selectedVehicleKey
            marker.position = vehicle.point.toLatLng()
            marker.icon = iconCache.icon(routeNo, palette, selected, density)
            marker.angle = state.geometry?.let { VehicleHeadingResolver.resolve(vehicle.point, it) } ?: 0f
            marker.isFlat = true
            marker.isCaptionPerspectiveEnabled = false
            marker.setCaptionAligns(Align.Bottom)
            marker.captionText = arrivalCaption(vehicle.remainingStops, routeFallback)
            marker.tag = vehicle.key
            marker.zIndex = if (vehicle.key == state.selectedVehicleKey) 20 else 10
            marker.setOnClickListener { overlay ->
                onVehicleSelected(overlay.tag as String)
                true
            }
            marker.map = map
        }
    }

    fun fitRouteOnceOrOnRequest(
        map: NaverMap,
        state: RealtimeMapUiState,
        fitRouteRequest: Int,
    ) {
        val points = state.geometry.orEmptyPoints() + state.visibleVehicles.map(MapVehicleUi::point)
        val initialDataReady = state.vehicleBatch != null || state.vehicleError != null
        if (
            points.isNotEmpty() &&
            ((initialFitPending && initialDataReady) || fitRouteRequest != lastFitRequest)
        ) {
            val builder = LatLngBounds.Builder()
            points.forEach { builder.include(it.toLatLng()) }
            map.moveCamera(CameraUpdate.fitBounds(builder.build(), 64))
            initialFitPending = false
            lastFitRequest = fitRouteRequest
        }
    }

    fun clear() {
        clear(paths)
        clear(stopMarkers)
        clear(vehicleMarkers)
        iconCache.evictAll()
    }

    private fun ensureVehicleMarkerCount(count: Int) {
        while (vehicleMarkers.size < count) vehicleMarkers += Marker()
        while (vehicleMarkers.size > count) {
            vehicleMarkers.removeAt(vehicleMarkers.lastIndex).map = null
        }
    }

    private fun arrivalCaption(remainingStops: Int?, routeFallback: String?): String {
        val arrival = remainingStops?.let { remaining ->
            when {
                remaining > 0 -> "${remaining}정거장 전"
                remaining == 0 -> "도착"
                else -> "통과"
            }
        }.orEmpty()
        return listOf(arrival, routeFallback.orEmpty()).filter(String::isNotEmpty).joinToString(" · ")
    }

    private fun <T> clear(overlays: MutableList<T>) where T : com.naver.maps.map.overlay.Overlay {
        overlays.forEach { it.map = null }
        overlays.clear()
    }

    private fun RouteGeometry?.orEmptyPoints(): List<GeoPoint> =
        this?.segments.orEmpty().flatMap(RouteSegment::points)

    private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
}

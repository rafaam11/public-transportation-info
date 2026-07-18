package com.rafaam11.businfo.ui.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapOptions
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.Align
import com.rafaam11.businfo.domain.StopCatalogItem
import com.rafaam11.businfo.domain.RouteDirectionKey
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.ui.StopMapVehicleUi
import com.rafaam11.businfo.domain.RouteStop

data class InitialStopCamera(val latitude: Double, val longitude: Double, val zoom: Double)

fun initialStopCamera(stop: StopCatalogItem): InitialStopCamera =
    InitialStopCamera(stop.latitude, stop.longitude, 16.0)

@Composable
fun NaverStopMap(
    stop: StopCatalogItem,
    vehicles: List<StopMapVehicleUi> = emptyList(),
    highlightedRoute: RouteDirectionKey? = null,
    nearbyStops: List<StopCatalogItem> = emptyList(),
    highlightedStops: List<RouteStop> = emptyList(),
    fitRouteRequest: Int = 0,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val camera = remember(stop.stopId) { initialStopCamera(stop) }
    val savedState = rememberSaveable(stop.stopId) { Bundle() }
    val mapView = remember(stop.stopId) {
        val options = NaverMapOptions().camera(
            CameraPosition(LatLng(camera.latitude, camera.longitude), camera.zoom),
        )
        MapView(context, options).also { it.onCreate(savedState) }
    }
    var map by remember(stop.stopId) { mutableStateOf<NaverMap?>(null) }
    val stopMarker = remember(stop.stopId) { Marker() }
    val vehicleMarkers = remember(stop.stopId) { mutableListOf<Marker>() }
    val nearbyStopMarkers = remember(stop.stopId) { mutableListOf<Marker>() }
    val busIcons = remember(stop.stopId) { BusMarkerIconCache() }

    DisposableEffect(mapView, lifecycle) {
        var started = false
        var resumed = false
        fun start() { if (!started) { mapView.onStart(); started = true } }
        fun resume() { if (!resumed) { start(); mapView.onResume(); resumed = true } }
        fun pause() { if (resumed) { mapView.onPause(); resumed = false } }
        fun stopMap() { if (started) { pause(); mapView.onStop(); started = false } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stopMap()
                else -> Unit
            }
        }
        val callbacks = object : ComponentCallbacks2 {
            override fun onLowMemory() = mapView.onLowMemory()
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) mapView.onLowMemory()
            }
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
        }
        lifecycle.addObserver(observer)
        context.applicationContext.registerComponentCallbacks(callbacks)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        mapView.getMapAsync { map = it }
        onDispose {
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(callbacks)
            stopMarker.map = null
            vehicleMarkers.forEach { it.map = null }
            vehicleMarkers.clear()
            nearbyStopMarkers.forEach { it.map = null }
            nearbyStopMarkers.clear()
            busIcons.evictAll()
            stopMap()
            mapView.onSaveInstanceState(savedState)
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
    LaunchedEffect(map, stop) {
        stopMarker.position = LatLng(stop.latitude, stop.longitude)
        stopMarker.captionText = stop.stopName
        stopMarker.iconTintColor = Color.rgb(11, 122, 90)
        stopMarker.zIndex = 20
        stopMarker.map = map
    }
    LaunchedEffect(map, vehicles, highlightedRoute) {
        while (vehicleMarkers.size < vehicles.size) vehicleMarkers += Marker()
        while (vehicleMarkers.size > vehicles.size) {
            vehicleMarkers.removeAt(vehicleMarkers.lastIndex).map = null
        }
        vehicles.sortedBy(StopMapVehicleUi::key).forEachIndexed { index, vehicle ->
            vehicleMarkers[index].apply {
                position = LatLng(vehicle.point.latitude, vehicle.point.longitude)
                captionText = listOfNotNull(
                    vehicle.routeNo,
                    vehicle.remainingStops?.let { if (it == 0) "도착" else "${it}정거장 전" },
                ).joinToString(" · ")
                anchor = PointF(0.5f, 0.5f)
                icon = busIcons.icon(
                    vehicle.routeNo,
                    RoutePalette(routeColor(vehicle.routeNo), Color.WHITE),
                    vehicle.routeKey == highlightedRoute,
                    density,
                )
                angle = vehicle.heading?.let { ((it - 90f) % 360f + 360f) % 360f } ?: 0f
                isFlat = true
                isCaptionPerspectiveEnabled = false
                setCaptionAligns(Align.Bottom)
                alpha = if (vehicle.delayed) 0.55f else 1f
                zIndex = if (vehicle.routeKey == highlightedRoute) 15 else 10
                this.map = map
            }
        }
    }
    LaunchedEffect(map, nearbyStops) {
        while (nearbyStopMarkers.size < nearbyStops.size) nearbyStopMarkers += Marker()
        while (nearbyStopMarkers.size > nearbyStops.size) {
            nearbyStopMarkers.removeAt(nearbyStopMarkers.lastIndex).map = null
        }
        nearbyStops.forEachIndexed { index, nearbyStop ->
            nearbyStopMarkers[index].apply {
                position = LatLng(nearbyStop.latitude, nearbyStop.longitude)
                captionText = nearbyStop.stopName
                iconTintColor = Color.rgb(21, 87, 192)
                zIndex = 10
                this.map = map
            }
        }
    }
    LaunchedEffect(map, fitRouteRequest) {
        if (fitRouteRequest > 0 && highlightedStops.isNotEmpty()) {
            val bounds = LatLngBounds.Builder().apply {
                highlightedStops.forEach { include(LatLng(it.latitude, it.longitude)) }
            }.build()
            map?.moveCamera(CameraUpdate.fitBounds(bounds, 64))
        }
    }
}

@Composable
fun NaverNearbyMap(origin: GeoPoint, stops: List<StopCatalogItem>) {
    NaverStopMap(
        stop = StopCatalogItem(
            "current-location:${origin.longitude}:${origin.latitude}",
            "내 위치",
            origin.longitude,
            origin.latitude,
        ),
        nearbyStops = stops,
    )
}

private fun routeColor(routeNo: String): Int {
    val palette = intArrayOf(
        Color.rgb(21, 87, 192),
        Color.rgb(11, 122, 90),
        Color.rgb(217, 92, 43),
        Color.rgb(116, 70, 157),
        Color.rgb(0, 126, 167),
    )
    return palette[(routeNo.hashCode() and Int.MAX_VALUE) % palette.size]
}

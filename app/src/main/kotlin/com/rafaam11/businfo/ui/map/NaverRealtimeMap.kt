package com.rafaam11.businfo.ui.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
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
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.rafaam11.businfo.ui.RealtimeMapUiState

@Composable
fun NaverRealtimeMap(
    state: RealtimeMapUiState,
    onVehicleSelected: (String) -> Unit,
    fitRouteRequest: Int,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val savedState = rememberSaveable { Bundle() }
    val mapView = remember { MapView(context).also { it.onCreate(savedState) } }
    val density = context.resources.displayMetrics.density
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }
    val controller = remember { NaverMapOverlayController() }

    DisposableEffect(mapView, lifecycle) {
        var started = false
        var resumed = false

        fun start() {
            if (!started) {
                mapView.onStart()
                started = true
            }
        }

        fun resume() {
            if (!resumed) {
                start()
                mapView.onResume()
                resumed = true
            }
        }

        fun pause() {
            if (resumed) {
                mapView.onPause()
                resumed = false
            }
        }

        fun stop() {
            if (started) {
                pause()
                mapView.onStop()
                started = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        val callbacks = object : ComponentCallbacks2 {
            override fun onLowMemory() = mapView.onLowMemory()

            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    mapView.onLowMemory()
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit
        }

        lifecycle.addObserver(observer)
        context.applicationContext.registerComponentCallbacks(callbacks)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        mapView.getMapAsync { naverMap = it }

        onDispose {
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(callbacks)
            controller.clear()
            stop()
            mapView.onSaveInstanceState(savedState)
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
    LaunchedEffect(
        naverMap,
        state.geometry,
        state.stops,
        state.visibleVehicles,
        state.selectedVehicleKey,
        state.selection,
        density,
    ) {
        naverMap?.let { controller.render(it, state, onVehicleSelected, density) }
    }
    LaunchedEffect(naverMap, state.geometry, state.vehicleBatch, state.vehicleError, fitRouteRequest) {
        naverMap?.let { controller.fitRouteOnceOrOnRequest(it, state, fitRouteRequest) }
    }
}

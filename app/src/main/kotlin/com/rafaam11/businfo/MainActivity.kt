package com.rafaam11.businfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.naver.maps.map.NaverMapSdk
import com.rafaam11.businfo.ui.BusAppViewModel
import com.rafaam11.businfo.ui.RealtimeMapViewModel
import java.time.Clock

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = AppGraph(applicationContext)
        NaverMapSdk.getInstance(applicationContext).setOnAuthFailedListener { exception ->
            graph.mapAuthMonitor.report(exception.errorCode)
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
                modelClass.isAssignableFrom(BusAppViewModel::class.java) -> {
                    BusAppViewModel(graph.credentialRepository, graph.dashboardRepository) as T
                }
                modelClass.isAssignableFrom(RealtimeMapViewModel::class.java) -> {
                    RealtimeMapViewModel(
                        graph.dashboardRepository,
                        graph.routeGeometryRepository,
                        graph.vehiclePositionRepository,
                        graph.mapAuthMonitor,
                        Clock.systemUTC(),
                    ) as T
                }
                else -> error("Unsupported ViewModel ${modelClass.name}")
            }
        }
        val provider = ViewModelProvider(this, factory)
        val busViewModel = provider[BusAppViewModel::class.java]
        val realtimeMapViewModel = provider[RealtimeMapViewModel::class.java]

        setContent { BusInfoApp(busViewModel, realtimeMapViewModel) }
    }
}

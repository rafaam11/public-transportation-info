package com.rafaam11.businfo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.naver.maps.map.NaverMapSdk
import com.rafaam11.businfo.ui.BusAppViewModel
import com.rafaam11.businfo.ui.RealtimeMapViewModel
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.widget.KeySettingsRequestStore
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val openMapSlot = MutableStateFlow<CommuteSlot?>(null)
    private val openKeySettings = MutableStateFlow(false)
    private val keySettingsRequests by lazy { KeySettingsRequestStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readNavigationExtras(intent)
        val graph = AppGraph.get(applicationContext)
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
                        graph.preciseVehiclePositionDataSource,
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

        setContent {
            val mapSlot by openMapSlot.collectAsState()
            val keySettings by openKeySettings.collectAsState()
            BusInfoApp(
                viewModel = busViewModel,
                realtimeMapViewModel = realtimeMapViewModel,
                openMapSlot = mapSlot,
                onOpenMapSlotConsumed = {
                    intent.removeExtra(EXTRA_OPEN_MAP_SLOT)
                    openMapSlot.value = null
                },
                openKeySettings = keySettings,
                onOpenKeySettingsConsumed = {
                    openKeySettings.value = false
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readNavigationExtras(intent)
    }

    private fun readNavigationExtras(intent: Intent?) {
        openMapSlot.value = intent?.getStringExtra(EXTRA_OPEN_MAP_SLOT)?.let { name ->
            runCatching { CommuteSlot.valueOf(name) }.getOrNull()
        }
        openKeySettings.value = keySettingsRequests.consume()
    }

    companion object {
        const val EXTRA_OPEN_MAP_SLOT = "com.rafaam11.businfo.extra.OPEN_MAP_SLOT"
    }
}

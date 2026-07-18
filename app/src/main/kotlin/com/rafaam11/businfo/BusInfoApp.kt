package com.rafaam11.businfo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rafaam11.businfo.data.location.CurrentLocationDataSource
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.StopCatalogItem
import com.rafaam11.businfo.ui.AppUiState
import com.rafaam11.businfo.ui.BusAppViewModel
import com.rafaam11.businfo.ui.KeyEntryScreen
import com.rafaam11.businfo.ui.StopDetailScreen
import com.rafaam11.businfo.ui.StopHomeScreen
import com.rafaam11.businfo.ui.StopHomeViewModel
import com.rafaam11.businfo.ui.StopRealtimeMapViewModel
import com.rafaam11.businfo.ui.map.NaverStopMap
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun BusInfoApp(
    viewModel: BusAppViewModel,
    stopHomeViewModel: StopHomeViewModel,
    stopRealtimeMapViewModel: StopRealtimeMapViewModel,
    currentLocation: CurrentLocationDataSource,
    openFavoriteStopId: String? = null,
    onOpenFavoriteStopConsumed: () -> Unit = {},
    openMapSlot: CommuteSlot? = null,
    onOpenMapSlotConsumed: () -> Unit = {},
    openSetupSlot: CommuteSlot? = null,
    onOpenSetupSlotConsumed: () -> Unit = {},
    openKeySettings: Boolean = false,
    onOpenKeySettingsConsumed: () -> Unit = {},
    onInstallUpdate: (File) -> Unit = {},
) {
    val appState by viewModel.uiState.collectAsState()
    val homeState by stopHomeViewModel.uiState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val stopMapState by stopRealtimeMapViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedStop by remember { mutableStateOf<StopCatalogItem?>(null) }
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        )
    }
    fun loadNearby() {
        scope.launch {
            currentLocation.current().onSuccess { stopHomeViewModel.showNearby(it) }
                .onFailure { stopHomeViewModel.locationDenied() }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        locationGranted = grants.values.any { it }
        if (locationGranted) loadNearby() else stopHomeViewModel.locationDenied()
    }
    val appReady = appState is AppUiState.Ready
    LaunchedEffect(appReady) {
        if (appReady) stopHomeViewModel.prepareCatalog()
    }

    LaunchedEffect(openKeySettings) {
        if (openKeySettings) {
            viewModel.beginKeyChange()
            onOpenKeySettingsConsumed()
        }
    }
    LaunchedEffect(openMapSlot, openSetupSlot) {
        if (openMapSlot != null) onOpenMapSlotConsumed()
        if (openSetupSlot != null) onOpenSetupSlotConsumed()
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1557C0),
            onPrimary = Color.White,
            secondary = Color(0xFF0B7A5A),
            background = Color(0xFFF2F5F7),
            onBackground = Color(0xFF16202A),
            surface = Color.White,
            onSurface = Color(0xFF16202A),
            error = Color(0xFFB3261E),
        ),
    ) {
        when (val current = appState) {
            AppUiState.Starting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is AppUiState.NeedsKey -> KeyEntryScreen(current, viewModel::submitKey)
            is AppUiState.Ready -> {
                val nav = rememberNavController()
                LaunchedEffect(openFavoriteStopId, homeState.favorites) {
                    val favorite = homeState.favorites.firstOrNull { it.id.value == openFavoriteStopId }
                    if (favorite != null) {
                        selectedStop = StopCatalogItem(
                            favorite.stopId,
                            favorite.stopName,
                            favorite.point.longitude,
                            favorite.point.latitude,
                        )
                        nav.navigate("stop") { launchSingleTop = true }
                        onOpenFavoriteStopConsumed()
                    }
                }
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        StopHomeScreen(
                            state = homeState,
                            updateState = updateState,
                            locationGranted = locationGranted,
                            placeSearchConfigured = BuildConfig.PLACE_SEARCH_BASE_URL.isNotBlank(),
                            onSearch = { stopHomeViewModel.clearNearby(); stopHomeViewModel.search(it) },
                            onNearby = {
                                stopHomeViewModel.clearNearby()
                                if (locationGranted) loadNearby() else permissionLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                                )
                            },
                            onStop = { stop -> selectedStop = stop; nav.navigate("stop") },
                            onPlace = { place -> stopHomeViewModel.showNearby(place.point, "${place.name} 주변 정류장") },
                            onRoute = stopHomeViewModel::selectRoute,
                            onBackFromRoute = stopHomeViewModel::clearSelectedRoute,
                            onBackFromNearby = stopHomeViewModel::clearNearby,
                            onFavorite = stopHomeViewModel::addFavorite,
                            onDeleteFavorite = { stopHomeViewModel.deleteFavorite(it.id) },
                            onMoveFavorite = { favorite, offset -> stopHomeViewModel.moveFavorite(favorite.id, offset) },
                            onToggleReorder = stopHomeViewModel::toggleReorderMode,
                            onRefreshStop = stopHomeViewModel::refreshStop,
                            onRefreshCatalog = stopHomeViewModel::refreshCatalog,
                            onChangeKey = viewModel::beginKeyChange,
                            onCheckUpdate = viewModel::checkForUpdatesOnce,
                            onDownloadUpdate = viewModel::downloadUpdate,
                            onInstallUpdate = onInstallUpdate,
                            onOpenReleases = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
                            },
                            onConsumeMessage = stopHomeViewModel::consumeMessage,
                        )
                    }
                    composable("stop") {
                        val stop = selectedStop
                        if (stop == null) {
                            LaunchedEffect(Unit) { nav.popBackStack() }
                        } else {
                            val lifecycleOwner = LocalLifecycleOwner.current
                            val favorite = homeState.favorites.firstOrNull { it.stopId == stop.stopId }
                            val arrivalGroups = homeState.arrivals[stop.stopId]?.groups.orEmpty()
                            val arrivalRouteKeys = arrivalGroups.map { it.key }
                            StopDetailScreen(
                                stop = stop,
                                snapshot = homeState.arrivals[stop.stopId],
                                isFavorite = favorite != null,
                                pinnedRoutes = favorite?.pinnedRoutes?.map { it.key }?.toSet().orEmpty(),
                                onBack = nav::popBackStack,
                                onFavorite = { stopHomeViewModel.addFavorite(stop) },
                                onTogglePinnedRoute = { group -> stopHomeViewModel.togglePinnedRoute(stop.stopId, group) },
                                onRefresh = { stopHomeViewModel.refreshStop(stop.stopId, force = true) },
                                highlightedRoute = stopMapState.highlightedRoute,
                                routeErrors = stopMapState.routeErrors.keys,
                                onHighlightRoute = stopRealtimeMapViewModel::highlight,
                                canFitRoute = stopMapState.highlightedRoute in stopMapState.routeStops,
                                mapContent = { mapStop, fitRouteRequest ->
                                    NaverStopMap(
                                        stop = mapStop,
                                        vehicles = stopMapState.vehicles,
                                        highlightedRoute = stopMapState.highlightedRoute,
                                        highlightedStops = stopMapState.highlightedRoute?.let(stopMapState.routeStops::get).orEmpty(),
                                        fitRouteRequest = fitRouteRequest,
                                    )
                                },
                            )
                            DisposableEffect(stop.stopId, arrivalRouteKeys, lifecycleOwner) {
                                val observer = LifecycleEventObserver { _, event ->
                                    when (event) {
                                        Lifecycle.Event.ON_START -> stopRealtimeMapViewModel.open(stop, arrivalGroups)
                                        Lifecycle.Event.ON_STOP -> stopRealtimeMapViewModel.close()
                                        else -> Unit
                                    }
                                }
                                lifecycleOwner.lifecycle.addObserver(observer)
                                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                    stopRealtimeMapViewModel.open(stop, arrivalGroups)
                                }
                                onDispose {
                                    lifecycleOwner.lifecycle.removeObserver(observer)
                                    stopRealtimeMapViewModel.close()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val RELEASES_URL = "https://github.com/rafaam11/public-transportation-info/releases"

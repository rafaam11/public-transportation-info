package com.rafaam11.businfo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.ui.AppUiState
import com.rafaam11.businfo.ui.BusAppViewModel
import com.rafaam11.businfo.ui.DashboardScreen
import com.rafaam11.businfo.ui.KeyEntryScreen
import com.rafaam11.businfo.ui.RouteDetailScreen
import com.rafaam11.businfo.ui.SetupScreen
import com.rafaam11.businfo.ui.userMessage

@Composable
fun BusInfoApp(viewModel: BusAppViewModel) {
    val state by viewModel.uiState.collectAsState()
    val setup by viewModel.setupState.collectAsState()
    val detail by viewModel.detailState.collectAsState()
    val colors = lightColorScheme(
        primary = Color(0xFF005BAC),
        onPrimary = Color(0xFFF4F7F2),
        secondary = Color(0xFF1E73BE),
        background = Color(0xFFF4F7F2),
        onBackground = Color(0xFF17212B),
        surface = Color(0xFFF4F7F2),
        onSurface = Color(0xFF17212B),
        error = Color(0xFFB3261E),
    )
    MaterialTheme(colorScheme = colors) {
        when (val current = state) {
            AppUiState.Starting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is AppUiState.NeedsKey -> KeyEntryScreen(current, viewModel::submitKey)
            is AppUiState.Ready -> {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "dashboard") {
                    composable("dashboard") {
                        DashboardScreen(
                            cards = current.cards,
                            onAdd = { nav.navigate("setup/${it.name}") },
                            onOpen = { nav.navigate("detail/${it.name}") },
                            onEdit = { nav.navigate("setup/${it.name}") },
                            onRefresh = viewModel::refreshAll,
                            onClearKey = viewModel::clearKey,
                            catalogPreparing = current.catalogPreparing,
                            catalogError = current.catalogError?.userMessage(),
                            onRetryCatalog = viewModel::retryCatalog,
                        )
                    }
                    composable("setup/{slot}") { entry ->
                        val slot = CommuteSlot.valueOf(entry.arguments?.getString("slot")!!)
                        androidx.compose.runtime.LaunchedEffect(slot) { viewModel.openSetup(slot) }
                        SetupScreen(
                            state = setup, onBack = nav::popBackStack, onSearch = viewModel::searchRoutes,
                            onRoute = viewModel::selectRoute, onDirection = viewModel::selectDirection,
                            onStop = { viewModel.saveStop(it); nav.popBackStack() },
                            onDelete = { viewModel.deleteFavorite(slot); nav.popBackStack() },
                        )
                    }
                    composable("detail/{slot}") { entry ->
                        val slot = CommuteSlot.valueOf(entry.arguments?.getString("slot")!!)
                        androidx.compose.runtime.LaunchedEffect(slot) { viewModel.loadDetail(slot) }
                        RouteDetailScreen(detail, nav::popBackStack) { viewModel.loadDetail(slot) }
                    }
                }
            }
        }
    }
}

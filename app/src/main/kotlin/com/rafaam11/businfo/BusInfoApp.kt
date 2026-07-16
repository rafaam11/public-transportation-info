package com.rafaam11.businfo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.rafaam11.businfo.ui.VehicleListScreen
import com.rafaam11.businfo.ui.VehicleListViewModel

@Composable
fun BusInfoApp(viewModel: VehicleListViewModel) {
    val state by viewModel.uiState.collectAsState()
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
        VehicleListScreen(
            state = state,
            onSubmitKey = viewModel::submitKey,
            onRefresh = viewModel::refresh,
            onClearKey = viewModel::clearKey,
        )
    }
}

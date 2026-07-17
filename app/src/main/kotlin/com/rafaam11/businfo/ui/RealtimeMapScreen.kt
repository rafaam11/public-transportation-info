package com.rafaam11.businfo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rafaam11.businfo.domain.DataFreshness

const val REALTIME_MAP_SURFACE_TAG = "realtime_map_surface"
const val REALTIME_MAP_SHEET_TAG = "realtime_map_sheet"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealtimeMapScreen(
    state: RealtimeMapUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onVehicleSelected: (String) -> Unit,
    onFitRoute: () -> Unit,
    mapContent: @Composable (
        RealtimeMapUiState,
        (String) -> Unit,
    ) -> Unit,
) {
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
    )
    BottomSheetScaffold(
        sheetPeekHeight = 132.dp,
        sheetContent = {
            RealtimeMapSheet(
                state = state,
                onRetry = onRetry,
                onVehicleSelected = onVehicleSelected,
            )
        },
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        scaffoldState = rememberBottomSheetScaffoldState(sheetState),
        topBar = {
            TopAppBar(
                title = {
                    Text(state.selection?.let { "${it.routeNo} 실시간" } ?: "실시간 버스")
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("뒤로") }
                },
                actions = {
                    TextButton(onClick = onFitRoute) { Text("노선 전체 보기") }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(REALTIME_MAP_SURFACE_TAG),
        ) {
            when {
                state.mapErrorCode != null -> MapBlockingError(
                    "네이버 지도를 인증할 수 없습니다. 코드 ${state.mapErrorCode}",
                    onRetry,
                )
                state.geometry == null && state.geometryError != null -> {
                    MapBlockingError(state.geometryError.userMessage(), onRetry)
                }
                else -> {
                    mapContent(state, onVehicleSelected)
                    if (state.loadingGeometry) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}

@Composable
private fun RealtimeMapSheet(
    state: RealtimeMapUiState,
    onRetry: () -> Unit,
    onVehicleSelected: (String) -> Unit,
) {
    val selection = state.selection
    val statusText = when {
        state.vehicleBatch?.vehicles?.isEmpty() == true && state.vehicleError == null -> {
            "현재 운행 차량 없음"
        }
        state.freshness == DataFreshness.STALE -> "위치 정보가 지연되고 있습니다"
        state.vehicleError != null -> state.vehicleError.userMessage()
        else -> "운행 차량 ${state.visibleVehicles.size}대"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag(REALTIME_MAP_SHEET_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            selection?.let { "${it.routeNo} · ${it.directionLabel}" } ?: "노선 준비 중",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        selection?.let { Text("내 정류장 · ${it.stopName}") }
        Text(statusText)
        state.dataAgeSeconds?.let { ageSeconds ->
            val freshnessLabel = when (state.freshness) {
                DataFreshness.FRESH -> "정상"
                DataFreshness.DELAYED -> "지연"
                DataFreshness.STALE -> "오래됨"
                DataFreshness.UNAVAILABLE -> "수신 전"
            }
            Text("$freshnessLabel · ${ageSeconds}초 전")
        }
        if (state.geometry != null && state.geometryError != null) {
            Text(
                "캐시된 노선선을 표시하고 있습니다",
                color = MaterialTheme.colorScheme.error,
            )
        }
        state.visibleVehicles.forEach { vehicle ->
            val remaining = when {
                vehicle.remainingStops == null -> "남은 정류장 확인 불가"
                vehicle.remainingStops > 0 -> "${vehicle.remainingStops}정거장 전"
                vehicle.remainingStops == 0 -> "내 정류장 도착"
                else -> "내 정류장 통과"
            }
            Card(
                onClick = { onVehicleSelected(vehicle.key) },
                colors = CardDefaults.cardColors(
                    containerColor = if (vehicle.key == state.selectedVehicleKey) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(vehicle.stopName, fontWeight = FontWeight.SemiBold)
                    Text(remaining)
                }
            }
        }
        if (state.vehicleError != null || state.geometryError != null) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("다시 시도")
            }
        }
    }
}

@Composable
private fun MapBlockingError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("다시 시도") }
    }
}

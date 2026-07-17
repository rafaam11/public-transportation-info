package com.rafaam11.businfo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.DataFreshness
import com.rafaam11.businfo.domain.FreshnessPolicy
import com.rafaam11.businfo.domain.RouteStop
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    cards: List<DashboardCardUiState>,
    onAdd: (CommuteSlot) -> Unit,
    onOpen: (CommuteSlot) -> Unit,
    onEdit: (CommuteSlot) -> Unit,
    onRefresh: () -> Unit,
    onClearKey: () -> Unit,
    catalogPreparing: Boolean = false,
    catalogError: String? = null,
    onRetryCatalog: () -> Unit = {},
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("내 버스", fontWeight = FontWeight.Bold) }, actions = {
            TextButton(onClick = onClearKey) { Text("API 키 변경") }
        }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (catalogPreparing) item { StatusBanner("노선 정보를 준비하는 중입니다") }
            catalogError?.let {
                item { DashboardErrorBanner(it) }
                item { OutlinedButton(onClick = onRetryCatalog, modifier = Modifier.fillMaxWidth()) { Text("노선 정보 다시 받기") } }
            }
            items(cards, key = { it.slot.name }) { card ->
                when (card) {
                    is DashboardCardUiState.Empty -> EmptyCommuteCard(card.slot) { onAdd(card.slot) }
                    is DashboardCardUiState.Configured -> ConfiguredCommuteCard(card, onOpen, onEdit)
                }
            }
            item { Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("전체 새로고침") } }
        }
    }
}

@Composable
private fun EmptyCommuteCard(slot: CommuteSlot, onAdd: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(slot.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("자주 타는 노선과 승차 정류장을 설정하세요")
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("버스 추가") }
        }
    }
}

@Composable
private fun ConfiguredCommuteCard(
    card: DashboardCardUiState.Configured,
    onOpen: (CommuteSlot) -> Unit,
    onEdit: (CommuteSlot) -> Unit,
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = Instant.now() } }
    val snapshot = card.snapshot
    val freshness = FreshnessPolicy.classify(snapshot.fetchedAt, now)
    val elapsed = snapshot.fetchedAt?.let { Duration.between(it, now).seconds.coerceAtLeast(0) }
    val first = snapshot.arrivals.firstOrNull()
    val second = snapshot.arrivals.getOrNull(1)
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(card.slot) },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(card.slot.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { onEdit(card.slot) }) { Text("편집") }
            }
            Text("${snapshot.selection.routeNo}번 · ${snapshot.selection.directionLabel}", color = MaterialTheme.colorScheme.primary)
            Text(snapshot.selection.stopName, style = MaterialTheme.typography.titleMedium)
            if (card.refreshing && first == null) CircularProgressIndicator()
            else if (first == null) Text("현재 도착 예정 차량 없음", style = MaterialTheme.typography.headlineSmall)
            else {
                Text(first.primaryText(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(first.secondaryText(), style = MaterialTheme.typography.titleLarge)
            }
            second?.let { Text("다음 차량 · ${it.primaryText()} · ${it.secondaryText()}") }
            Text(freshnessLabel(freshness, elapsed), color = freshnessColor(freshness), style = MaterialTheme.typography.labelLarge)
            card.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyEntryScreen(state: AppUiState.NeedsKey, onSubmit: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text("대구 버스 API 연결") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("공공데이터 API 키 입력", style = MaterialTheme.typography.headlineSmall)
            Text("data.go.kr에서 발급받은 일반 인증키를 입력하세요.")
            state.error?.let { DashboardErrorBanner(it.userMessage()) }
            OutlinedTextField(
                value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API 키") },
                singleLine = true, enabled = !state.submitting, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Button(onClick = { onSubmit(key) }, modifier = Modifier.fillMaxWidth(), enabled = key.isNotBlank() && !state.submitting) {
                Text(if (state.submitting) "키 확인 중" else "키 저장하고 시작")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    state: SetupUiState,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onRoute: (com.rafaam11.businfo.domain.RouteSummary) -> Unit,
    onDirection: (com.rafaam11.businfo.data.DirectionOption) -> Unit,
    onStop: (RouteStop) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("${state.slot.label} 카드를 삭제할까요?") },
            text = { Text("저장된 도착정보도 함께 삭제됩니다. 노선 캐시는 유지됩니다.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
    Scaffold(topBar = { TopAppBar(title = { Text("${state.slot.label} 버스 설정") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("뒤로") }
    }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.existing?.let { existing ->
                item { StatusBanner("현재 설정 · ${existing.routeNo}번 · ${existing.stopName}") }
                item { OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("이 카드 삭제") } }
            }
            state.error?.let { item { DashboardErrorBanner(it.userMessage()) } }
            if (state.selectedRoute == null) {
                item {
                    OutlinedTextField(
                        value = state.query, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(),
                        label = { Text("노선번호 또는 기·종점 검색") }, singleLine = true,
                    )
                }
                items(state.routes, key = { it.routeId }) { route ->
                    SelectionRow("${route.routeNo}번", "${route.startName} ↔ ${route.endName}") { onRoute(route) }
                }
            } else if (state.selectedDirection == null) {
                item { Text("방향을 선택하세요", style = MaterialTheme.typography.titleLarge) }
                if (!state.loading && state.directions.isEmpty()) item { Text("방향 정보를 찾지 못했습니다. 노선을 다시 선택해 주세요.") }
                items(state.directions, key = { it.code }) { direction ->
                    SelectionRow(direction.label, "${direction.stops.size}개 정류장") { onDirection(direction) }
                }
            } else {
                item { Text("승차 정류장을 선택하세요", style = MaterialTheme.typography.titleLarge) }
                items(state.selectedDirection.stops, key = { "${it.stopId}:${it.sequence}" }) { stop ->
                    SelectionRow(stop.stopName, "${stop.sequence}번째 정류장") { onStop(stop) }
                }
            }
            if (state.loading) item { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun SelectionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle) }
    }
}

@Composable private fun StatusBanner(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))) {
        Text(text, Modifier.fillMaxWidth().padding(14.dp))
    }
}

@Composable private fun DashboardErrorBanner(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f))) {
        Text(text, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.error)
    }
}

private fun freshnessLabel(freshness: DataFreshness, elapsed: Long?): String {
    val label = when (freshness) {
        DataFreshness.FRESH -> "최신"
        DataFreshness.DELAYED -> "지연"
        DataFreshness.STALE -> "오래됨"
        DataFreshness.UNAVAILABLE -> "조회 전"
    }
    return elapsed?.let { "$label · ${it}초 전" } ?: label
}

@Composable private fun freshnessColor(freshness: DataFreshness) = when (freshness) {
    DataFreshness.FRESH -> MaterialTheme.colorScheme.primary
    DataFreshness.DELAYED -> Color(0xFFA86000)
    DataFreshness.STALE -> MaterialTheme.colorScheme.error
    DataFreshness.UNAVAILABLE -> MaterialTheme.colorScheme.onSurface
}

package com.rafaam11.businfo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.DataFreshness
import com.rafaam11.businfo.domain.FreshnessPolicy
import com.rafaam11.businfo.domain.VehicleSnapshot
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

private val DaeguBlue = Color(0xFF005BAC)
private val TransitBlue = Color(0xFF1E73BE)
private val MapPaper = Color(0xFFF4F7F2)
private val DarkInk = Color(0xFF17212B)
private val DelayedAmber = Color(0xFFA86000)
private val StaleRed = Color(0xFFB3261E)

fun BusDataError.userMessage(): String = when (this) {
    BusDataError.InvalidCredential -> "API 키를 확인할 수 없습니다. 키를 다시 입력해 주세요."
    BusDataError.NetworkUnavailable -> "네트워크에 연결할 수 없습니다. 마지막 정상 데이터는 유지됩니다."
    BusDataError.ServiceUnavailable -> "버스 정보 서비스를 사용할 수 없습니다. 잠시 후 새로고침해 주세요."
    BusDataError.MalformedResponse -> "버스 정보를 읽을 수 없습니다. 잠시 후 새로고침해 주세요."
    BusDataError.RateLimited -> "요청 한도를 초과했습니다. 잠시 기다린 뒤 새로고침해 주세요."
}

fun VehicleSnapshot.primaryText(): String = buildString {
    append("$moveDirection · ${stopSequence?.let { "정류장 $it" } ?: "정류장 순서 미확인"}")
    append("\n정류장 ID ${stopId ?: "미확인"}")
    append("\n도착 정보 ${arrivalState ?: "없음"}")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleListScreen(
    state: VehicleListUiState,
    onSubmitKey: (String) -> Unit,
    onRefresh: () -> Unit,
    onClearKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MapPaper,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("814번 실시간 차량", fontWeight = FontWeight.Bold)
                        Text("대구 시내버스", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MapPaper,
                    titleContentColor = DarkInk,
                ),
            )
        },
    ) { innerPadding ->
        when (state) {
            VehicleListUiState.Starting -> LoadingPanel(
                label = "저장된 설정을 확인하는 중입니다",
                modifier = Modifier.padding(innerPadding),
            )
            is VehicleListUiState.NeedsKey -> KeyPanel(
                state = state,
                onSubmitKey = onSubmitKey,
                modifier = Modifier.padding(innerPadding),
            )
            is VehicleListUiState.Content -> ContentPanel(
                state = state,
                onRefresh = onRefresh,
                onClearKey = onClearKey,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun KeyPanel(
    state: VehicleListUiState.NeedsKey,
    onSubmitKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var key by remember { mutableStateOf("") }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("공공데이터 API 키 입력", style = MaterialTheme.typography.headlineSmall, color = DarkInk)
        }
        item {
            Text(
                "data.go.kr에서 발급받은 일반 인증키를 입력하면 814번 차량을 조회합니다.",
                style = MaterialTheme.typography.bodyLarge,
                color = DarkInk,
            )
        }
        state.error?.let { error ->
            item { ErrorBanner(error.userMessage()) }
        }
        item {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.submitting,
                label = { Text("API 키") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        }
        item {
            Button(
                onClick = { onSubmitKey(key) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.submitting && key.isNotBlank(),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MapPaper,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (state.submitting) "키 확인 중" else "키 저장하고 조회")
            }
        }
    }
}

@Composable
private fun ContentPanel(
    state: VehicleListUiState.Content,
    onRefresh: () -> Unit,
    onClearKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = Instant.now()
        }
    }

    val batch = state.batch
    val freshness = FreshnessPolicy.classify(batch?.fetchedAt, now)
    val elapsedSeconds = batch?.fetchedAt?.let {
        Duration.between(it, now).seconds.coerceAtLeast(0)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ServiceStrip(
                count = batch?.vehicles?.size,
                freshness = freshness,
                elapsedSeconds = elapsedSeconds,
            )
        }
        state.error?.let { error ->
            item { ErrorBanner(error.userMessage()) }
        }
        if (batch == null && state.refreshing) {
            item { LoadingPanel("814번 차량을 불러오는 중입니다") }
        } else if (batch == null) {
            item {
                Text(
                    "차량 정보를 불러오지 못했습니다. 새로고침해 주세요.",
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = DarkInk,
                )
            }
        } else if (batch.vehicles.isEmpty()) {
            item {
                Text(
                    "현재 운행 차량 없음",
                    modifier = Modifier.padding(vertical = 28.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkInk,
                )
            }
        } else {
            items(batch.vehicles) { vehicle -> VehicleRow(vehicle) }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                    enabled = !state.refreshing,
                ) {
                    if (state.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MapPaper,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.refreshing) "조회 중" else "새로고침")
                }
                OutlinedButton(onClick = onClearKey, modifier = Modifier.weight(1f)) {
                    Text("API 키 변경")
                }
            }
        }
    }
}

@Composable
private fun ServiceStrip(count: Int?, freshness: DataFreshness, elapsedSeconds: Long?) {
    val (label, color) = when (freshness) {
        DataFreshness.FRESH -> "최신" to TransitBlue
        DataFreshness.DELAYED -> "지연" to DelayedAmber
        DataFreshness.STALE -> "오래됨" to StaleRed
        DataFreshness.UNAVAILABLE -> "조회 전" to DarkInk
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(count?.let { "${it}대 운행" } ?: "차량 확인 중", color = DarkInk, fontWeight = FontWeight.Bold)
        Text(
            if (elapsedSeconds == null) label else "$label · ${elapsedSeconds}초 전",
            color = color,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StaleRed.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            color = StaleRed,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LoadingPanel(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = DaeguBlue)
        Text(label, color = DarkInk)
    }
}

@Composable
private fun VehicleRow(vehicle: VehicleSnapshot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(4.dp).height(14.dp).background(TransitBlue))
                Box(Modifier.size(14.dp).background(DaeguBlue, CircleShape))
                Box(Modifier.width(4.dp).height(14.dp).background(TransitBlue))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(vehicle.primaryText(), style = MaterialTheme.typography.titleMedium, color = DarkInk)
                Text(
                    "${vehicle.latitude}, ${vehicle.longitude}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = DarkInk.copy(alpha = 0.72f),
                )
            }
            Text(
                vehicle.stopSequence?.toString() ?: "—",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                color = DaeguBlue,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

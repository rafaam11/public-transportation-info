package com.rafaam11.businfo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.NearbyStop
import com.rafaam11.businfo.domain.PlaceResult
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.RouteDirectionKey
import com.rafaam11.businfo.domain.StopArrival
import com.rafaam11.businfo.domain.StopArrivalGroup
import com.rafaam11.businfo.domain.StopArrivalSnapshot
import com.rafaam11.businfo.domain.StopCatalogItem
import com.rafaam11.businfo.domain.routesForHome
import java.time.Duration
import java.time.Instant
import java.io.File
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.rafaam11.businfo.ui.map.NaverNearbyMap

private val TransitBlue = Color(0xFF1557C0)
private val TransitGreen = Color(0xFF0B7A5A)
private val Ink = Color(0xFF16202A)
private val Mist = Color(0xFFF2F5F7)

@Composable
fun StopHomeScreen(
    state: StopHomeUiState,
    updateState: UpdateUiState,
    locationGranted: Boolean,
    placeSearchConfigured: Boolean,
    onSearch: (String) -> Unit,
    onNearby: () -> Unit,
    onStop: (StopCatalogItem) -> Unit,
    onPlace: (PlaceResult) -> Unit,
    onRoute: (RouteSummary) -> Unit,
    onBackFromRoute: () -> Unit,
    onBackFromNearby: () -> Unit,
    onToggleFavorite: (StopCatalogItem) -> Unit,
    onDeleteFavorite: (FavoriteStop) -> Unit,
    onMoveFavorite: (FavoriteStop, Int) -> Unit,
    onToggleReorder: () -> Unit,
    onRefreshStop: (String, Boolean) -> Unit,
    onRefreshCatalog: () -> Unit,
    onChangeKey: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: (File) -> Unit,
    onOpenReleases: () -> Unit,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            StopDrawer(
                favorites = state.favorites.size,
                reorderMode = state.reorderMode,
                locationGranted = locationGranted,
                placeSearchConfigured = placeSearchConfigured,
                apiCallCount = state.apiCallCount,
                catalogPreparing = state.catalogPreparing,
                updateState = updateState,
                onToggleReorder = { onToggleReorder(); scope.launch { drawer.close() } },
                onRefreshCatalog = onRefreshCatalog,
                onChangeKey = { onChangeKey(); scope.launch { drawer.close() } },
                onCheckUpdate = onCheckUpdate,
                onDownloadUpdate = onDownloadUpdate,
                onInstallUpdate = onInstallUpdate,
                onOpenReleases = onOpenReleases,
            )
        },
    ) {
        Scaffold(
            containerColor = Mist,
            topBar = {
                StopSearchBar(
                    query = state.query,
                    onQuery = onSearch,
                    onMenu = { scope.launch { drawer.open() } },
                    onNearby = onNearby,
                )
            },
        ) { padding ->
            when {
                state.selectedRoute != null -> RouteStopsContent(
                    route = state.selectedRoute,
                    stops = state.routeStops,
                    loading = state.routeStopsLoading,
                    onBack = onBackFromRoute,
                    onStop = onStop,
                    modifier = Modifier.padding(padding),
                )
                state.nearbyLoading -> Box(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                ) {
                    LinearStatus(state.nearbyLoadingMessage ?: "주변 정류장을 찾는 중")
                }
                state.nearby != null -> NearbyContent(
                    title = state.nearbyTitle.orEmpty(),
                    radiusMeters = state.nearby.radiusMeters,
                    stops = state.nearby.stops,
                    origin = state.nearbyOrigin,
                    onBack = onBackFromNearby,
                    onStop = onStop,
                    favorites = state.favorites,
                    busyStopIds = state.favoriteMutatingStopIds + state.pendingRemovalStopIds,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier.padding(padding),
                )
                state.query.isNotBlank() -> SearchContent(
                    state = state,
                    onRoute = onRoute,
                    onStop = onStop,
                    onToggleFavorite = onToggleFavorite,
                    onPlace = onPlace,
                    modifier = Modifier.padding(padding),
                )
                else -> FavoriteHomeContent(
                    state = state,
                    onStop = onStop,
                    onDelete = onDeleteFavorite,
                    onMove = onMoveFavorite,
                    onRefreshStop = onRefreshStop,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun StopSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    onMenu: () -> Unit,
    onNearby: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Surface(color = Color.White, shadowElevation = 5.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                )
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onMenu,
                modifier = Modifier.semantics { contentDescription = "메뉴 열기" },
                contentPadding = PaddingValues(8.dp),
            ) {
                Text("☰", style = MaterialTheme.typography.headlineSmall, color = Ink)
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                placeholder = { Text("버스 · 정류장 · 장소 검색", maxLines = 1) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                trailingIcon = if (query.isNotBlank()) {
                    {
                        TextButton(
                            onClick = { onQuery(""); focusManager.clearFocus() },
                            modifier = Modifier.semantics { contentDescription = "검색어 지우기" },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) { Text("지우기") }
                    }
                } else null,
            )
            Button(
                onClick = onNearby,
                colors = ButtonDefaults.buttonColors(containerColor = TransitGreen),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) { Text("내 주변") }
        }
    }
}

@Composable
private fun FavoriteHomeContent(
    state: StopHomeUiState,
    onStop: (StopCatalogItem) -> Unit,
    onDelete: (FavoriteStop) -> Unit,
    onMove: (FavoriteStop, Int) -> Unit,
    onRefreshStop: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("즐겨찾는 정류장", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Ink)
                Text("정류장을 열면 도착 버스와 초정밀 위치를 함께 볼 수 있어요", color = Color(0xFF5A6672))
            }
        }
        if (state.catalogPreparing) item { LinearStatus("대구 정류장 정보를 준비하고 있습니다") }
        if (state.reorderMode) item {
            Surface(color = TransitBlue.copy(alpha = 0.09f), shape = RoundedCornerShape(14.dp)) {
                Text(
                    "순서 편집 중 · 화살표로 즐겨찾기 순서를 바꾼 뒤 메뉴에서 편집을 끝내세요",
                    Modifier.fillMaxWidth().padding(14.dp),
                    color = TransitBlue,
                )
            }
        }
        if (state.favorites.isEmpty()) {
            item {
                EmptyFavoriteCard()
            }
        }
        val orderedFavorites = state.favorites.sortedBy(FavoriteStop::sortOrder)
        itemsIndexed(orderedFavorites, key = { _, favorite -> favorite.id.value }) { index, favorite ->
            FavoriteStopCard(
                favorite = favorite,
                snapshot = state.arrivals[favorite.stopId],
                reorderMode = state.reorderMode,
                canMoveUp = index > 0,
                canMoveDown = index < orderedFavorites.lastIndex,
                onOpen = {
                    onStop(StopCatalogItem(favorite.stopId, favorite.stopName, favorite.point.longitude, favorite.point.latitude))
                },
                onDelete = { onDelete(favorite) },
                onMove = { onMove(favorite, it) },
                onRefresh = { force -> onRefreshStop(favorite.stopId, force) },
            )
        }
    }
}

@Composable
private fun FavoriteStopCard(
    favorite: FavoriteStop,
    snapshot: StopArrivalSnapshot?,
    reorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onRefresh: (Boolean) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(favorite.stopId) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var force = false
            while (true) {
                onRefresh(force)
                force = true
                delay(8_000)
            }
        }
    }
    val groups = favorite.routesForHome(snapshot?.groups.orEmpty(), 3)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(TransitGreen, CircleShape))
                Spacer(Modifier.width(9.dp))
                Text(favorite.stopName, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                if (reorderMode) {
                    TextButton(
                        onClick = { onMove(-1) },
                        enabled = canMoveUp,
                        modifier = Modifier.semantics { contentDescription = "위로 이동" },
                    ) { Text("↑") }
                    TextButton(
                        onClick = { onMove(1) },
                        enabled = canMoveDown,
                        modifier = Modifier.semantics { contentDescription = "아래로 이동" },
                    ) { Text("↓") }
                } else TextButton(onClick = onDelete) { Text("삭제", color = Color(0xFF7A4A42)) }
            }
            if (snapshot == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(9.dp))
                    Text("도착정보 확인 중", color = Color(0xFF5A6672))
                }
            } else if (groups.isEmpty()) {
                Text("현재 도착 예정 버스가 없습니다", color = Color(0xFF5A6672))
            } else groups.forEach { ArrivalRow(it) }
            snapshot?.let { Text(ageLabel(it.fetchedAt), style = MaterialTheme.typography.labelMedium, color = Color(0xFF687480)) }
        }
    }
}

@Composable
private fun ArrivalRow(group: StopArrivalGroup) {
    val first = group.arrivals.firstOrNull()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RouteBadge(group.routeNo)
        Spacer(Modifier.width(10.dp))
        Text(directionName(group.key.moveDirection), Modifier.weight(1f), color = Color(0xFF64707B), maxLines = 1)
        Text(first?.arrivalLabel().orEmpty(), fontWeight = FontWeight.ExtraBold, color = Ink)
    }
}

@Composable
private fun SearchContent(
    state: StopHomeUiState,
    onRoute: (RouteSummary) -> Unit,
    onStop: (StopCatalogItem) -> Unit,
    onToggleFavorite: (StopCatalogItem) -> Unit,
    onPlace: (PlaceResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (state.searching) item { LinearStatus("버스·정류장·장소를 함께 찾는 중") }
        if (!state.searching && state.searchResult.placeSearchFailed) {
            item { LinearStatus("장소 검색에 연결하지 못했습니다. 버스·정류장 결과는 계속 사용할 수 있습니다") }
        }
        if (!state.searching && state.searchResult.routes.isEmpty() && state.searchResult.stops.isEmpty() && state.searchResult.places.isEmpty()) {
            item { EmptyResult("검색 결과가 없습니다") }
        }
        if (state.searchResult.routes.isNotEmpty()) {
            item { SectionTitle("버스", state.searchResult.routes.size) }
            items(state.searchResult.routes, key = { it.routeId }) { route ->
                ResultCard(onClick = { onRoute(route) }) {
                    RouteBadge(route.routeNo)
                    Spacer(Modifier.width(12.dp))
                    Text("${route.startName} ↔ ${route.endName}", Modifier.weight(1f), maxLines = 2)
                }
            }
        }
        if (state.searchResult.stops.isNotEmpty()) {
            item { SectionTitle("정류장", state.searchResult.stops.size) }
            items(state.searchResult.stops, key = { it.stopId }) { stop ->
                StopResultCard(
                    stop = stop,
                    distance = null,
                    isFavorite = state.favorites.any { it.stopId == stop.stopId },
                    busy = stop.stopId in state.favoriteMutatingStopIds || stop.stopId in state.pendingRemovalStopIds,
                    onStop = onStop,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
        if (state.searchResult.places.isNotEmpty()) {
            item { SectionTitle("장소", state.searchResult.places.size) }
            items(state.searchResult.places, key = { "${it.name}:${it.point.longitude}:${it.point.latitude}" }) { place ->
                ResultCard(onClick = { onPlace(place) }) {
                    Text("⌖", color = TransitGreen, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(place.name, fontWeight = FontWeight.Bold)
                        Text(place.roadAddress.ifBlank { place.address }, color = Color(0xFF65717D), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("주변 보기", color = TransitGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NearbyContent(
    title: String,
    radiusMeters: Int,
    stops: List<NearbyStop>,
    origin: GeoPoint?,
    onBack: () -> Unit,
    onStop: (StopCatalogItem) -> Unit,
    favorites: List<FavoriteStop>,
    busyStopIds: Set<String>,
    onToggleFavorite: (StopCatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← 이전 화면") }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("${radiusMeters}m 안의 가까운 정류장 ${stops.size}개", color = Color(0xFF65717D))
        }
        origin?.let { point ->
            item {
                Box(Modifier.fillMaxWidth().height(260.dp)) {
                    NaverNearbyMap(point, stops.map(NearbyStop::stop))
                }
            }
        }
        if (stops.isEmpty()) item { EmptyResult("주변에서 정류장을 찾지 못했습니다") }
        items(stops, key = { it.stop.stopId }) { nearby ->
            StopResultCard(
                stop = nearby.stop,
                distance = "${nearby.distanceMeters}m",
                isFavorite = favorites.any { it.stopId == nearby.stop.stopId },
                busy = nearby.stop.stopId in busyStopIds,
                onStop = onStop,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

@Composable
private fun RouteStopsContent(
    route: RouteSummary,
    stops: List<RouteStop>,
    loading: Boolean,
    onBack: () -> Unit,
    onStop: (StopCatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← 검색 결과") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RouteBadge(route.routeNo)
                Spacer(Modifier.width(10.dp))
                Text("경유 정류장", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
            Text("${route.startName} ↔ ${route.endName}", color = Color(0xFF65717D))
        }
        if (loading) item { LinearStatus("경유 정류장을 불러오는 중") }
        items(stops, key = { "${it.moveDirection}:${it.sequence}" }) { stop ->
            ResultCard(onClick = { onStop(StopCatalogItem(stop.stopId, stop.stopName, stop.longitude, stop.latitude)) }) {
                Text(stop.sequence.toString().padStart(2, '0'), color = TransitBlue, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stop.stopName, fontWeight = FontWeight.Bold)
                    Text(directionName(stop.moveDirection), color = Color(0xFF65717D))
                }
            }
        }
    }
}

@Composable
private fun StopResultCard(
    stop: StopCatalogItem,
    distance: String?,
    isFavorite: Boolean,
    busy: Boolean,
    onStop: (StopCatalogItem) -> Unit,
    onToggleFavorite: (StopCatalogItem) -> Unit,
) {
    ResultCard(onClick = { onStop(stop) }) {
        Box(Modifier.size(34.dp).background(TransitGreen.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Text("정", color = TransitGreen, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stop.stopName, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(distance, stop.stopId.takeIf(String::isNotBlank)).joinToString(" · "), color = Color(0xFF65717D))
        }
        TextButton(
            onClick = { onToggleFavorite(stop) },
            enabled = !busy,
            modifier = Modifier.semantics {
                contentDescription = if (isFavorite) "${stop.stopName} 즐겨찾기 해제" else "${stop.stopName} 즐겨찾기 저장"
            },
        ) {
            Text(
                when {
                    busy -> "처리 중"
                    isFavorite -> "★ 저장됨"
                    else -> "☆ 저장"
                },
            )
        }
    }
}

@Composable
private fun StopDrawer(
    favorites: Int,
    reorderMode: Boolean,
    locationGranted: Boolean,
    placeSearchConfigured: Boolean,
    apiCallCount: Int,
    catalogPreparing: Boolean,
    updateState: UpdateUiState,
    onToggleReorder: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onChangeKey: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: (File) -> Unit,
    onOpenReleases: () -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = Color.White) {
        Column(Modifier.width(310.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("대구 버스", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = TransitBlue)
            Text("정류장을 중심으로 더 빠르게", color = Color(0xFF65717D))
            Spacer(Modifier.height(14.dp))
            DrawerRow(if (reorderMode) "순서 편집 끝내기" else "즐겨찾기 순서", "$favorites / 20", onToggleReorder)
            DrawerRow("API 키 설정", "공공데이터 연결", onChangeKey)
            DrawerInfoRow("위치 권한", if (locationGranted) "허용됨" else "내 주변에서 요청")
            DrawerRow(
                "정류장 캐시 갱신",
                if (catalogPreparing) "갱신 중" else "대구 전체 정류장",
                onRefreshCatalog,
                enabled = !catalogPreparing,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("진단", style = MaterialTheme.typography.labelLarge, color = Color(0xFF65717D))
            DrawerInfoRow("오늘 API 호출", "${apiCallCount}회")
            DrawerInfoRow("장소 검색", if (placeSearchConfigured) "연결됨" else "운영 주소 미설정")
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            DrawerRow("공지사항", "GitHub Releases", onOpenReleases)
            DrawerRow("업데이트 확인", updateLabel(updateState), onCheckUpdate, enabled = updateState != UpdateUiState.Checking)
            if (updateState is UpdateUiState.Available) {
                when (val download = updateState.download) {
                    DownloadUiState.NotStarted -> DrawerRow("새 버전 다운로드", updateState.info.tagName, onDownloadUpdate)
                    DownloadUiState.Downloading -> DrawerRow("새 버전 다운로드", "진행 중", {}, enabled = false)
                    is DownloadUiState.Downloaded -> DrawerRow(
                        "업데이트 설치",
                        download.file.name,
                        onClick = { onInstallUpdate(download.file) },
                    )
                    is DownloadUiState.Failed -> DrawerRow("다운로드 다시 시도", download.message, onDownloadUpdate)
                }
            }
        }
    }
}

@Composable
private fun DrawerRow(title: String, detail: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) Ink else Color(0xFF8A949E))
        Text(detail, color = Color(0xFF65717D), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DrawerInfoRow(title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color(0xFF52606D))
        Text(detail, color = Color(0xFF65717D), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ResultCard(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, content = content) }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Text("$title  $count", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Ink)
}

@Composable
private fun RouteBadge(routeNo: String) {
    Surface(color = TransitBlue, shape = RoundedCornerShape(9.dp)) {
        Text(routeNo, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun LinearStatus(text: String) {
    Surface(color = TransitBlue.copy(alpha = 0.09f), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(text)
        }
    }
}

@Composable
private fun EmptyFavoriteCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("첫 정류장을 저장해 보세요", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("상단 검색이나 내 주변에서 ☆ 저장을 누르면 됩니다", color = Color(0xFF65717D))
        }
    }
}

@Composable
private fun EmptyResult(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { Text(text, color = Color(0xFF65717D)) }
}

private fun StopArrival.arrivalLabel(): String = when {
    stopGap == 0 -> "도착 임박"
    arrivalSeconds < 60 -> "곧 도착"
    else -> "${ceil(arrivalSeconds / 60.0).toInt()}분 · ${stopGap}정거장"
}

private fun directionName(code: String): String = when (code) {
    "0" -> "정방향"
    "1" -> "역방향"
    else -> "$code 방향"
}

private fun ageLabel(at: Instant): String {
    val seconds = Duration.between(at, Instant.now()).seconds.coerceAtLeast(0)
    return if (seconds < 60) "${seconds}초 전 갱신" else "${seconds / 60}분 전 갱신"
}

private fun updateLabel(state: UpdateUiState): String = when (state) {
    UpdateUiState.Checking -> "확인 중"
    is UpdateUiState.Available -> "${state.info.tagName} 사용 가능"
    is UpdateUiState.Failed -> "확인 실패"
    UpdateUiState.UpToDate -> "최신 버전"
    UpdateUiState.Idle -> "직접 확인"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopDetailScreen(
    stop: StopCatalogItem,
    snapshot: StopArrivalSnapshot?,
    isFavorite: Boolean,
    favoriteBusy: Boolean,
    manualRefreshing: Boolean,
    pinnedRoutes: Set<RouteDirectionKey>,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onTogglePinnedRoute: (StopArrivalGroup) -> Unit,
    onAutoRefresh: () -> Unit,
    onManualRefresh: () -> Unit,
    highlightedRoute: RouteDirectionKey?,
    routeErrors: Set<RouteDirectionKey>,
    onHighlightRoute: (RouteDirectionKey) -> Unit,
    canFitRoute: Boolean,
    mapContent: @Composable (StopCatalogItem, Int) -> Unit,
) {
    var fitRouteRequest by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(stop.stopId) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                onAutoRefresh()
                delay(8_000)
            }
        }
    }
    Scaffold(
        containerColor = Mist,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stop.stopName, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(stop.stopId, style = MaterialTheme.typography.labelSmall, color = Color(0xFF65717D))
                    }
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "뒤로 가기" },
                    ) { Text("←") }
                },
                actions = {
                    TextButton(
                        onClick = onFavorite,
                        enabled = !favoriteBusy,
                        modifier = Modifier.semantics {
                            contentDescription = if (isFavorite) "${stop.stopName} 즐겨찾기 해제" else "${stop.stopName} 즐겨찾기 저장"
                        },
                    ) { Text(if (favoriteBusy) "처리 중" else if (isFavorite) "★ 저장됨" else "☆ 저장") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Box(Modifier.fillMaxWidth().height(300.dp)) { mapContent(stop, fitRouteRequest) } }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("도착 예정", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text("이 정류장을 향해 오는 모든 노선", color = Color(0xFF65717D))
                    }
                    OutlinedButton(onClick = onManualRefresh, enabled = !manualRefreshing) {
                        if (manualRefreshing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("갱신 중")
                        } else {
                            Text("새로고침")
                        }
                    }
                }
            }
            if (canFitRoute) item {
                OutlinedButton(
                    onClick = { fitRouteRequest++ },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { Text("강조 노선 전체 보기") }
            }
            if (snapshot == null) item { Box(Modifier.padding(horizontal = 16.dp)) { LinearStatus("도착정보를 불러오는 중") } }
            if (snapshot != null && snapshot.groups.isEmpty()) item { EmptyResult("현재 도착 예정 버스가 없습니다") }
            snapshot?.groups?.let { groups ->
                items(groups, key = { "${it.key.routeId}:${it.key.moveDirection}" }) { group ->
                    StopArrivalCard(
                        group = group,
                        selected = group.key == highlightedRoute,
                        pinned = group.key in pinnedRoutes,
                        pinBusy = favoriteBusy,
                        partialError = group.key in routeErrors,
                        onClick = { onHighlightRoute(group.key) },
                        onTogglePin = { onTogglePinnedRoute(group) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item { Text(ageLabel(snapshot.fetchedAt), Modifier.padding(horizontal = 16.dp), color = Color(0xFF65717D)) }
            }
        }
    }
}

@Composable
private fun StopArrivalCard(
    group: StopArrivalGroup,
    selected: Boolean,
    pinned: Boolean,
    pinBusy: Boolean,
    partialError: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) TransitBlue.copy(alpha = 0.09f) else Color.White),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RouteBadge(group.routeNo)
                Spacer(Modifier.width(10.dp))
                Text(directionName(group.key.moveDirection), color = Color(0xFF65717D))
                Spacer(Modifier.weight(1f))
                if (selected) Text("지도 강조", color = TransitBlue, fontWeight = FontWeight.Bold)
                TextButton(onClick = onTogglePin, enabled = !pinBusy) {
                    Text(if (pinBusy) "처리 중" else if (pinned) "★ 고정됨" else "☆ 홈 고정")
                }
            }
            group.arrivals.take(2).forEachIndexed { index, arrival ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (index == 0) "첫 번째 버스" else "다음 버스", color = Color(0xFF65717D))
                    Text(arrival.arrivalLabel(), fontWeight = FontWeight.ExtraBold)
                }
            }
            if (partialError) Text("초정밀 위치 일부를 불러오지 못했습니다", color = MaterialTheme.colorScheme.error)
        }
    }
}

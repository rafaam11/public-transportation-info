package com.rafaam11.businfo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafaam11.businfo.data.FavoriteStopRepository
import com.rafaam11.businfo.data.GroupedSearchResult
import com.rafaam11.businfo.data.SaveFavoriteResult
import com.rafaam11.businfo.data.StopSearchGateway
import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.NearbyStopResult
import com.rafaam11.businfo.domain.PinnedRoute
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.StopArrivalGroup
import com.rafaam11.businfo.domain.StopArrivalSnapshot
import com.rafaam11.businfo.domain.StopCatalogItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StopHomeUiState(
    val favorites: List<FavoriteStop> = emptyList(),
    val arrivals: Map<String, StopArrivalSnapshot> = emptyMap(),
    val query: String = "",
    val searchResult: GroupedSearchResult = GroupedSearchResult(emptyList(), emptyList(), emptyList()),
    val searching: Boolean = false,
    val nearby: NearbyStopResult? = null,
    val nearbyOrigin: GeoPoint? = null,
    val nearbyTitle: String? = null,
    val selectedRoute: RouteSummary? = null,
    val routeStops: List<RouteStop> = emptyList(),
    val routeStopsLoading: Boolean = false,
    val catalogPreparing: Boolean = true,
    val apiCallCount: Int = 0,
    val reorderMode: Boolean = false,
    val message: String? = null,
)

class StopHomeViewModel(
    private val favorites: FavoriteStopRepository,
    private val searchGateway: StopSearchGateway,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StopHomeUiState())
    val uiState: StateFlow<StopHomeUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var catalogJob: Job? = null
    private var routeRequestGeneration = 0L

    init {
        viewModelScope.launch(dispatcher) {
            favorites.observeFavorites().collect { values ->
                _uiState.value = _uiState.value.copy(favorites = values)
                values.forEach { favorite ->
                    val cached = runCatching { searchGateway.cachedArrivals(favorite.stopId) }.getOrNull()
                        ?: return@forEach
                    val current = _uiState.value.arrivals[favorite.stopId]
                    if (current == null || cached.fetchedAt.isAfter(current.fetchedAt)) {
                        _uiState.value = _uiState.value.copy(
                            arrivals = _uiState.value.arrivals + (favorite.stopId to cached),
                        )
                    }
                }
            }
        }
        prepareCatalog()
    }

    fun prepareCatalog() {
        if (catalogJob?.isActive == true) return
        _uiState.value = _uiState.value.copy(catalogPreparing = true)
        catalogJob = viewModelScope.launch(dispatcher) {
            val result = searchGateway.ensureStopCatalog()
            _uiState.value = _uiState.value.copy(
                catalogPreparing = false,
                apiCallCount = searchGateway.todayApiCallCount(),
                message = result.exceptionOrNull()?.let { "정류장 정보를 준비하지 못했습니다" },
            )
        }
    }

    fun search(query: String) {
        routeRequestGeneration++
        _uiState.value = _uiState.value.copy(
            query = query,
            searching = query.isNotBlank(),
            selectedRoute = null,
            nearby = null,
            nearbyOrigin = null,
            nearbyTitle = null,
        )
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchResult = GroupedSearchResult(emptyList(), emptyList(), emptyList()), searching = false,
            )
            return
        }
        searchJob = viewModelScope.launch(dispatcher) {
            delay(250)
            val result = searchGateway.search(query)
            _uiState.value = _uiState.value.copy(
                searchResult = result, searching = false, apiCallCount = searchGateway.todayApiCallCount(),
            )
        }
    }

    fun selectRoute(route: RouteSummary) {
        val requestGeneration = ++routeRequestGeneration
        _uiState.value = _uiState.value.copy(selectedRoute = route, routeStops = emptyList(), routeStopsLoading = true)
        viewModelScope.launch(dispatcher) {
            val result = searchGateway.routeStops(route.routeId)
            if (requestGeneration != routeRequestGeneration) return@launch
            _uiState.value = _uiState.value.copy(
                routeStops = result.getOrDefault(emptyList()),
                routeStopsLoading = false,
                apiCallCount = searchGateway.todayApiCallCount(),
                message = result.exceptionOrNull()?.let { "노선 정류장을 불러오지 못했습니다" },
            )
        }
    }

    fun clearSelectedRoute() {
        routeRequestGeneration++
        _uiState.value = _uiState.value.copy(selectedRoute = null, routeStops = emptyList())
    }

    fun showNearby(origin: GeoPoint, title: String = "내 주변 정류장") {
        routeRequestGeneration++
        _uiState.value = _uiState.value.copy(selectedRoute = null, routeStops = emptyList())
        viewModelScope.launch(dispatcher) {
            val nearby = searchGateway.nearby(origin)
            _uiState.value = _uiState.value.copy(nearby = nearby, nearbyOrigin = origin, nearbyTitle = title)
        }
    }

    fun clearNearby() {
        _uiState.value = _uiState.value.copy(nearby = null, nearbyOrigin = null, nearbyTitle = null)
    }

    fun locationDenied() {
        _uiState.value = _uiState.value.copy(message = "위치 권한 없이도 검색과 지도를 계속 사용할 수 있습니다")
    }

    fun refreshStop(stopId: String, force: Boolean = false) {
        viewModelScope.launch(dispatcher) {
            val result = searchGateway.refreshArrivals(stopId, force)
            result.onSuccess { snapshot ->
                _uiState.value = _uiState.value.copy(
                    arrivals = _uiState.value.arrivals + (stopId to snapshot),
                    apiCallCount = searchGateway.todayApiCallCount(),
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(message = "도착정보 갱신에 실패해 마지막 정보를 유지합니다")
            }
        }
    }

    fun refreshCatalog() {
        catalogJob?.cancel()
        _uiState.value = _uiState.value.copy(catalogPreparing = true)
        catalogJob = viewModelScope.launch(dispatcher) {
            val result = searchGateway.ensureStopCatalog(force = true)
            _uiState.value = _uiState.value.copy(
                catalogPreparing = false,
                apiCallCount = searchGateway.todayApiCallCount(),
                message = if (result.isSuccess) "정류장 캐시를 갱신했습니다" else "정류장 캐시 갱신에 실패했습니다",
            )
        }
    }

    fun addFavorite(stop: StopCatalogItem) {
        val current = _uiState.value.favorites
        val favorite = FavoriteStop(
            id = FavoriteStopId.create(), stopId = stop.stopId, stopName = stop.stopName,
            point = GeoPoint(stop.longitude, stop.latitude), sortOrder = current.size,
        )
        viewModelScope.launch(dispatcher) {
            val message = when (favorites.save(favorite)) {
                SaveFavoriteResult.Saved -> "${stop.stopName}을 즐겨찾기에 추가했습니다"
                SaveFavoriteResult.AlreadyExists -> "이미 즐겨찾는 정류장입니다"
                SaveFavoriteResult.LimitReached -> "즐겨찾기는 최대 20개까지 저장할 수 있습니다"
            }
            _uiState.value = _uiState.value.copy(message = message)
        }
    }

    fun deleteFavorite(id: FavoriteStopId) {
        viewModelScope.launch(dispatcher) { favorites.delete(id) }
    }

    fun togglePinnedRoute(stopId: String, group: StopArrivalGroup) {
        val favorite = _uiState.value.favorites.firstOrNull { it.stopId == stopId }
        if (favorite == null) {
            _uiState.value = _uiState.value.copy(message = "먼저 정류장을 즐겨찾기에 저장해 주세요")
            return
        }
        val alreadyPinned = favorite.pinnedRoutes.any { it.key == group.key }
        val routes = if (alreadyPinned) {
            favorite.pinnedRoutes.filterNot { it.key == group.key }
                .mapIndexed { index, route -> route.copy(sortOrder = index) }
        } else {
            favorite.pinnedRoutes + PinnedRoute(
                favoriteStopId = favorite.id,
                key = group.key,
                routeNo = group.routeNo,
                directionLabel = directionLabel(group.key.moveDirection),
                sortOrder = favorite.pinnedRoutes.size,
            )
        }
        viewModelScope.launch(dispatcher) {
            favorites.save(favorite.copy(pinnedRoutes = routes))
            _uiState.value = _uiState.value.copy(
                message = if (alreadyPinned) {
                    "${group.routeNo} 노선 고정을 해제했습니다"
                } else {
                    "${group.routeNo} 노선을 홈과 위젯에 고정했습니다"
                },
            )
        }
    }

    fun toggleReorderMode() {
        _uiState.value = _uiState.value.copy(reorderMode = !_uiState.value.reorderMode)
    }

    fun moveFavorite(id: FavoriteStopId, offset: Int) {
        if (offset == 0) return
        val ordered = _uiState.value.favorites.sortedBy(FavoriteStop::sortOrder).toMutableList()
        val from = ordered.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + offset).coerceIn(0, ordered.lastIndex)
        if (from == to) return
        val moved = ordered.removeAt(from)
        ordered.add(to, moved)
        viewModelScope.launch(dispatcher) {
            ordered.forEachIndexed { index, favorite -> favorites.save(favorite.copy(sortOrder = index)) }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun directionLabel(code: String): String = when (code) {
        "0" -> "정방향"
        "1" -> "역방향"
        else -> "$code 방향"
    }
}

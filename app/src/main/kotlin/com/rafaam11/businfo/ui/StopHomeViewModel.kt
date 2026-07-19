package com.rafaam11.businfo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafaam11.businfo.data.FavoriteStopRepository
import com.rafaam11.businfo.data.GroupedSearchResult
import com.rafaam11.businfo.data.SaveFavoriteResult
import com.rafaam11.businfo.data.StopSearchGateway
import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import com.rafaam11.businfo.domain.FavoriteRemovalSnapshot
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
    val nearbyLoading: Boolean = false,
    val nearbyLoadingMessage: String? = null,
    val nearbyOrigin: GeoPoint? = null,
    val nearbyTitle: String? = null,
    val selectedRoute: RouteSummary? = null,
    val routeStops: List<RouteStop> = emptyList(),
    val routeStopsLoading: Boolean = false,
    val catalogPreparing: Boolean = true,
    val apiCallCount: Int = 0,
    val reorderMode: Boolean = false,
    val reorderDirty: Boolean = false,
    val favoriteMutatingStopIds: Set<String> = emptySet(),
    val pendingRemovalStopIds: Set<String> = emptySet(),
    val manualRefreshingStopIds: Set<String> = emptySet(),
    val feedbackEvents: List<UiFeedbackEvent> = emptyList(),
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
    private var currentLocationRequestGeneration = 0L
    private var activeCurrentLocationRequestId: Long? = null
    private var feedbackSequence = 0L
    private var catalogPreparationFailureEventId: Long? = null
    private val pendingRemovals = mutableMapOf<Long, FavoriteRemovalSnapshot>()

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
            )
            if (result.isFailure) {
                val queuedFailureId = catalogPreparationFailureEventId
                    ?.takeIf { id -> _uiState.value.feedbackEvents.any { it.id == id } }
                if (queuedFailureId == null) {
                    catalogPreparationFailureEventId = enqueueNotice("정류장 정보를 준비하지 못했습니다")
                }
            } else {
                catalogPreparationFailureEventId?.let { resolveFeedback(it, actionPerformed = false) }
                catalogPreparationFailureEventId = null
            }
        }
    }

    fun search(query: String) {
        activeCurrentLocationRequestId = null
        routeRequestGeneration++
        _uiState.value = _uiState.value.copy(
            query = query,
            searching = query.isNotBlank(),
            selectedRoute = null,
            nearby = null,
            nearbyLoading = false,
            nearbyLoadingMessage = null,
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
        activeCurrentLocationRequestId = null
        val requestGeneration = ++routeRequestGeneration
        _uiState.value = _uiState.value.copy(selectedRoute = route, routeStops = emptyList(), routeStopsLoading = true)
        viewModelScope.launch(dispatcher) {
            val result = searchGateway.routeStops(route.routeId)
            if (requestGeneration != routeRequestGeneration) return@launch
            _uiState.value = _uiState.value.copy(
                routeStops = result.getOrDefault(emptyList()),
                routeStopsLoading = false,
                apiCallCount = searchGateway.todayApiCallCount(),
            )
            if (result.isFailure) enqueueNotice("노선 정류장을 불러오지 못했습니다")
        }
    }

    fun clearSelectedRoute() {
        routeRequestGeneration++
        _uiState.value = _uiState.value.copy(selectedRoute = null, routeStops = emptyList())
    }

    fun showNearby(origin: GeoPoint, title: String = "내 주변 정류장") {
        activeCurrentLocationRequestId = null
        val requestGeneration = ++routeRequestGeneration
        _uiState.value = _uiState.value.copy(
            selectedRoute = null,
            routeStops = emptyList(),
            nearbyLoading = true,
            nearbyLoadingMessage = "주변 정류장을 찾는 중",
        )
        viewModelScope.launch(dispatcher) {
            runCatching { searchGateway.nearby(origin) }
                .onSuccess { nearby ->
                    if (requestGeneration != routeRequestGeneration) return@launch
                    _uiState.value = _uiState.value.copy(
                        nearby = nearby,
                        nearbyLoading = false,
                        nearbyLoadingMessage = null,
                        nearbyOrigin = origin,
                        nearbyTitle = title,
                    )
                }
                .onFailure {
                    if (requestGeneration == routeRequestGeneration) nearbyLookupUnavailable()
                }
        }
    }

    fun showNearbyFromCurrentLocation(requestId: Long, origin: GeoPoint) {
        if (activeCurrentLocationRequestId != requestId) return
        activeCurrentLocationRequestId = null
        showNearby(origin)
    }

    fun beginNearby(): Long {
        val requestId = ++currentLocationRequestGeneration
        activeCurrentLocationRequestId = requestId
        routeRequestGeneration++
        _uiState.value = _uiState.value.copy(
            selectedRoute = null,
            routeStops = emptyList(),
            nearby = null,
            nearbyLoading = true,
            nearbyLoadingMessage = "현재 위치를 확인하는 중",
            nearbyOrigin = null,
            nearbyTitle = null,
        )
        return requestId
    }

    fun pendingCurrentLocationRequestId(): Long? = activeCurrentLocationRequestId

    fun cancelCurrentLocationRequest(requestId: Long? = null) {
        val activeRequestId = activeCurrentLocationRequestId ?: return
        if (requestId != null && requestId != activeRequestId) return
        activeCurrentLocationRequestId = null
        _uiState.value = _uiState.value.copy(
            nearbyLoading = false,
            nearbyLoadingMessage = null,
        )
    }

    fun clearNearby() {
        activeCurrentLocationRequestId = null
        _uiState.value = _uiState.value.copy(
            nearby = null,
            nearbyLoading = false,
            nearbyLoadingMessage = null,
            nearbyOrigin = null,
            nearbyTitle = null,
        )
    }

    fun locationPermissionDenied(requestId: Long) {
        if (activeCurrentLocationRequestId != requestId) return
        activeCurrentLocationRequestId = null
        _uiState.value = _uiState.value.copy(
            nearbyLoading = false,
            nearbyLoadingMessage = null,
        )
        enqueueNotice("위치 권한 없이도 검색과 지도를 계속 사용할 수 있습니다")
    }

    fun locationUnavailable(requestId: Long) {
        if (activeCurrentLocationRequestId != requestId) return
        activeCurrentLocationRequestId = null
        _uiState.value = _uiState.value.copy(
            nearbyLoading = false,
            nearbyLoadingMessage = null,
        )
        enqueueNotice("현재 위치를 확인하지 못했습니다. 위치 서비스를 확인한 뒤 다시 시도해 주세요")
    }

    private fun nearbyLookupUnavailable() {
        _uiState.value = _uiState.value.copy(
            nearbyLoading = false,
            nearbyLoadingMessage = null,
        )
        enqueueNotice("주변 정류장 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요")
    }

    fun refreshStop(stopId: String, force: Boolean = false) {
        viewModelScope.launch(dispatcher) {
            val result = runCatching { searchGateway.refreshArrivals(stopId, force) }
                .getOrElse { Result.failure(it) }
            result.onSuccess { snapshot ->
                _uiState.value = _uiState.value.copy(
                    arrivals = _uiState.value.arrivals + (stopId to snapshot),
                    apiCallCount = searchGateway.todayApiCallCount(),
                )
            }
        }
    }

    fun refreshStopManually(stopId: String) {
        if (stopId in _uiState.value.manualRefreshingStopIds) return
        _uiState.value = _uiState.value.copy(
            manualRefreshingStopIds = _uiState.value.manualRefreshingStopIds + stopId,
        )
        viewModelScope.launch(dispatcher) {
            val result = runCatching { searchGateway.refreshArrivals(stopId, force = true) }
                .getOrElse { Result.failure(it) }
            result.onSuccess { snapshot ->
                _uiState.value = _uiState.value.copy(
                    arrivals = _uiState.value.arrivals + (stopId to snapshot),
                    apiCallCount = searchGateway.todayApiCallCount(),
                )
                enqueueNotice("도착정보를 갱신했습니다")
            }.onFailure {
                enqueueNotice("도착정보 갱신에 실패했습니다. 마지막 정보를 유지합니다")
            }
            _uiState.value = _uiState.value.copy(
                manualRefreshingStopIds = _uiState.value.manualRefreshingStopIds - stopId,
            )
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
            )
            enqueueNotice(if (result.isSuccess) "정류장 캐시를 갱신했습니다" else "정류장 캐시 갱신에 실패했습니다")
        }
    }

    fun addFavorite(stop: StopCatalogItem) {
        if (stop.stopId in _uiState.value.favoriteMutatingStopIds || stop.stopId in _uiState.value.pendingRemovalStopIds) return
        val current = _uiState.value.favorites
        if (current.size + pendingRemovals.size >= MAXIMUM_FAVORITES) {
            enqueueNotice("즐겨찾기는 최대 20개까지 저장할 수 있습니다")
            return
        }
        val favorite = FavoriteStop(
            id = FavoriteStopId.create(), stopId = stop.stopId, stopName = stop.stopName,
            point = GeoPoint(stop.longitude, stop.latitude), sortOrder = current.size,
        )
        _uiState.value = _uiState.value.copy(
            favoriteMutatingStopIds = _uiState.value.favoriteMutatingStopIds + stop.stopId,
        )
        viewModelScope.launch(dispatcher) {
            val message = runCatching { favorites.save(favorite) }.fold(
                onSuccess = { result -> when (result) {
                    SaveFavoriteResult.Saved -> "${stop.stopName} 정류장을 즐겨찾기에 추가했습니다"
                    SaveFavoriteResult.AlreadyExists -> "이미 즐겨찾는 정류장입니다"
                    SaveFavoriteResult.LimitReached -> "즐겨찾기는 최대 20개까지 저장할 수 있습니다"
                } },
                onFailure = { "${stop.stopName} 정류장을 즐겨찾기에 저장하지 못했습니다" },
            )
            _uiState.value = _uiState.value.copy(
                favoriteMutatingStopIds = _uiState.value.favoriteMutatingStopIds - stop.stopId,
            )
            enqueueNotice(message)
        }
    }

    fun deleteFavorite(id: FavoriteStopId) {
        val favorite = _uiState.value.favorites.firstOrNull { it.id == id } ?: return
        if (favorite.stopId in _uiState.value.favoriteMutatingStopIds) return
        _uiState.value = _uiState.value.copy(
            favoriteMutatingStopIds = _uiState.value.favoriteMutatingStopIds + favorite.stopId,
        )
        viewModelScope.launch(dispatcher) {
            val removal = runCatching { favorites.remove(id) }.getOrNull()
            _uiState.value = _uiState.value.copy(
                favoriteMutatingStopIds = _uiState.value.favoriteMutatingStopIds - favorite.stopId,
            )
            if (removal == null) {
                enqueueNotice("${favorite.stopName} 즐겨찾기를 삭제하지 못했습니다")
                return@launch
            }
            val eventId = nextFeedbackId()
            pendingRemovals[eventId] = removal
            _uiState.value = _uiState.value.copy(
                pendingRemovalStopIds = _uiState.value.pendingRemovalStopIds + favorite.stopId,
                feedbackEvents = _uiState.value.feedbackEvents + UiFeedbackEvent.FavoriteRemoved(
                    id = eventId,
                    message = "${favorite.stopName} 즐겨찾기를 해제했습니다",
                    stopName = favorite.stopName,
                ),
            )
        }
    }

    fun toggleFavorite(stop: StopCatalogItem) {
        val favorite = _uiState.value.favorites.firstOrNull { it.stopId == stop.stopId }
        if (favorite == null) addFavorite(stop) else deleteFavorite(favorite.id)
    }

    fun resolveFeedback(eventId: Long, actionPerformed: Boolean) {
        val event = _uiState.value.feedbackEvents.firstOrNull { it.id == eventId } ?: return
        _uiState.value = _uiState.value.copy(
            feedbackEvents = _uiState.value.feedbackEvents.filterNot { it.id == eventId },
        )
        if (event !is UiFeedbackEvent.FavoriteRemoved) return
        val removal = pendingRemovals.remove(eventId) ?: return
        if (!actionPerformed) {
            _uiState.value = _uiState.value.copy(
                pendingRemovalStopIds = _uiState.value.pendingRemovalStopIds - removal.favorite.stopId,
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            favoriteMutatingStopIds = _uiState.value.favoriteMutatingStopIds + removal.favorite.stopId,
        )
        viewModelScope.launch(dispatcher) {
            runCatching { favorites.restore(removal) }
                .onFailure { enqueueNotice("${removal.favorite.stopName} 즐겨찾기를 복원하지 못했습니다") }
            _uiState.value = _uiState.value.copy(
                favoriteMutatingStopIds = _uiState.value.favoriteMutatingStopIds - removal.favorite.stopId,
                pendingRemovalStopIds = _uiState.value.pendingRemovalStopIds - removal.favorite.stopId,
            )
        }
    }

    fun togglePinnedRoute(stopId: String, group: StopArrivalGroup) {
        val favorite = _uiState.value.favorites.firstOrNull { it.stopId == stopId }
        if (favorite == null) {
            enqueueNotice("먼저 정류장을 즐겨찾기에 저장해 주세요")
            return
        }
        if (stopId in _uiState.value.favoriteMutatingStopIds) return
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
        _uiState.value = _uiState.value.copy(
            favoriteMutatingStopIds = _uiState.value.favoriteMutatingStopIds + stopId,
        )
        viewModelScope.launch(dispatcher) {
            val saved = runCatching { favorites.save(favorite.copy(pinnedRoutes = routes)) }.isSuccess
            _uiState.value = _uiState.value.copy(
                favoriteMutatingStopIds = _uiState.value.favoriteMutatingStopIds - stopId,
            )
            enqueueNotice(
                if (!saved) {
                    "${group.routeNo} 노선 고정 상태를 변경하지 못했습니다"
                } else if (alreadyPinned) {
                    "${group.routeNo} 노선 고정을 해제했습니다"
                } else {
                    "${group.routeNo} 노선을 홈과 위젯에 고정했습니다"
                },
            )
        }
    }

    fun toggleReorderMode() {
        val state = _uiState.value
        val finishingChangedSession = state.reorderMode && state.reorderDirty
        _uiState.value = state.copy(
            reorderMode = !state.reorderMode,
            reorderDirty = if (state.reorderMode) false else state.reorderDirty,
        )
        if (finishingChangedSession) enqueueNotice("즐겨찾기 순서를 저장했습니다")
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
        _uiState.value = _uiState.value.copy(reorderDirty = true)
        viewModelScope.launch(dispatcher) {
            ordered.forEachIndexed { index, favorite -> favorites.save(favorite.copy(sortOrder = index)) }
        }
    }

    private fun enqueueNotice(message: String): Long {
        val eventId = nextFeedbackId()
        _uiState.value = _uiState.value.copy(
            feedbackEvents = _uiState.value.feedbackEvents + UiFeedbackEvent.Notice(eventId, message),
        )
        return eventId
    }

    private fun nextFeedbackId(): Long = ++feedbackSequence

    private fun directionLabel(code: String): String = when (code) {
        "0" -> "정방향"
        "1" -> "역방향"
        else -> "$code 방향"
    }

    private companion object {
        const val MAXIMUM_FAVORITES = 20
    }
}

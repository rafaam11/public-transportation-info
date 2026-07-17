package com.rafaam11.businfo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafaam11.businfo.data.CredentialGateway
import com.rafaam11.businfo.data.DashboardDataSource
import com.rafaam11.businfo.data.DashboardRepository
import com.rafaam11.businfo.data.DirectionOption
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteDashboardSnapshot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BusAppViewModel(
    private val credentials: CredentialGateway,
    private val dashboard: DashboardDataSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Starting)
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val _setupState = MutableStateFlow(SetupUiState())
    val setupState: StateFlow<SetupUiState> = _setupState.asStateFlow()

    private var snapshots: List<FavoriteDashboardSnapshot> = emptyList()
    private var errors = emptyMap<CommuteSlot, String>()
    private var refreshing = emptySet<CommuteSlot>()
    private var dashboardJob: Job? = null

    init {
        if (credentials.savedKeyExists()) enterDashboard() else _uiState.value = AppUiState.NeedsKey()
    }

    fun submitKey(key: String) {
        val state = _uiState.value as? AppUiState.NeedsKey ?: return
        if (state.submitting || key.isBlank()) return
        _uiState.value = state.copy(submitting = true, error = null)
        viewModelScope.launch(dispatcher) {
            val error = credentials.validateAndSave(key)
            if (error == null) enterDashboard() else {
                _uiState.value = state.copy(error = error)
            }
        }
    }

    fun beginKeyChange() {
        if (!credentials.savedKeyExists()) return
        dashboardJob?.cancel()
        _uiState.value = AppUiState.NeedsKey(changeMode = true)
    }

    fun clearKey() {
        dashboardJob?.cancel()
        credentials.clearKey()
        _uiState.value = AppUiState.NeedsKey()
    }

    private fun enterDashboard() {
        _uiState.value = AppUiState.Ready(dashboardCards(emptyList(), emptyMap(), emptySet()), catalogPreparing = true)
        dashboardJob?.cancel()
        dashboardJob = viewModelScope.launch(dispatcher) {
            launch {
                dashboard.observeDashboard().collect {
                    snapshots = it
                    publishCards()
                }
            }
            launch {
                val catalogError = dashboard.ensureRouteCatalog()
                val current = _uiState.value as? AppUiState.Ready ?: return@launch
                _uiState.value = current.copy(catalogPreparing = false, catalogError = catalogError)
            }
            launch { refreshAllInternal() }
        }
    }

    fun refreshAll() = viewModelScope.launch(dispatcher) { refreshAllInternal() }

    fun retryCatalog() {
        val current = _uiState.value as? AppUiState.Ready ?: return
        _uiState.value = current.copy(catalogPreparing = true, catalogError = null)
        viewModelScope.launch(dispatcher) {
            val error = dashboard.ensureRouteCatalog(force = true)
            val ready = _uiState.value as? AppUiState.Ready ?: return@launch
            _uiState.value = ready.copy(catalogPreparing = false, catalogError = error)
        }
    }

    private suspend fun refreshAllInternal() {
        refreshing = snapshots.map { it.selection.slot }.toSet()
        publishCards()
        val result = dashboard.refreshAll()
        errors = result.mapNotNull { (slot, error) -> error?.let { slot to it.userMessage() } }.toMap()
        refreshing = emptySet()
        publishCards()
    }

    fun openSetup(slot: CommuteSlot) {
        viewModelScope.launch(dispatcher) {
            _setupState.value = SetupUiState(slot = slot, existing = dashboard.favorite(slot))
        }
    }

    fun searchRoutes(query: String) {
        _setupState.value = _setupState.value.copy(query = query, loading = true, error = null)
        viewModelScope.launch(dispatcher) {
            _setupState.value = _setupState.value.copy(routes = dashboard.searchRoutes(query), loading = false)
        }
    }

    fun selectRoute(route: RouteSummary) {
        _setupState.value = _setupState.value.copy(selectedRoute = route, selectedDirection = null, loading = true, error = null)
        viewModelScope.launch(dispatcher) {
            dashboard.directions(route).fold(
                onSuccess = { _setupState.value = _setupState.value.copy(directions = it, loading = false) },
                onFailure = {
                    val error = (it as? DashboardRepository.RepositoryException)?.error ?: BusDataError.ServiceUnavailable
                    _setupState.value = _setupState.value.copy(loading = false, error = error)
                },
            )
        }
    }

    fun selectDirection(direction: DirectionOption) {
        _setupState.value = _setupState.value.copy(selectedDirection = direction)
    }

    fun saveStop(stop: RouteStop) {
        val state = _setupState.value
        val route = state.selectedRoute ?: return
        val direction = state.selectedDirection ?: return
        viewModelScope.launch(dispatcher) {
            dashboard.saveFavorite(
                FavoriteSelection(
                    slot = state.slot,
                    routeId = route.routeId,
                    routeNo = route.routeNo,
                    directionCode = direction.code,
                    directionLabel = direction.label,
                    stopId = stop.stopId,
                    stopName = stop.stopName,
                    routeTypeCode = route.routeTypeCode,
                ),
            )
            val error = dashboard.refreshFavorite(state.slot)
            errors = if (error == null) errors - state.slot else errors + (state.slot to error.userMessage())
            publishCards()
        }
    }

    fun deleteFavorite(slot: CommuteSlot) {
        viewModelScope.launch(dispatcher) { dashboard.deleteFavorite(slot) }
    }

    private fun publishCards() {
        val current = _uiState.value as? AppUiState.Ready ?: return
        _uiState.value = current.copy(cards = dashboardCards(snapshots, errors, refreshing))
    }
}

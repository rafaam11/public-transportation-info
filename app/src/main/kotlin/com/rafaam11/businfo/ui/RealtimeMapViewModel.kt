package com.rafaam11.businfo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafaam11.businfo.data.DashboardDataSource
import com.rafaam11.businfo.data.RouteGeometryDataSource
import com.rafaam11.businfo.data.RouteMapLoadResult
import com.rafaam11.businfo.data.VehiclePositionDataSource
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.DataFreshness
import com.rafaam11.businfo.domain.FreshnessPolicy
import com.rafaam11.businfo.domain.PollDecision
import com.rafaam11.businfo.domain.PollResult
import com.rafaam11.businfo.domain.PollingPolicy
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.VehicleLoadResult
import com.rafaam11.businfo.ui.map.MapAuthMonitor
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RealtimeMapViewModel(
    private val dashboard: DashboardDataSource,
    private val geometry: RouteGeometryDataSource,
    private val vehicles: VehiclePositionDataSource,
    private val mapAuthMonitor: MapAuthMonitor,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RealtimeMapUiState())
    val uiState: StateFlow<RealtimeMapUiState> = _uiState.asStateFlow()

    private var visible = false
    private var openedSlot: CommuteSlot? = null
    private var bootstrapJob: Job? = null
    private var pollingJob: Job? = null
    private var freshnessJob: Job? = null
    private val mapAuthJob = viewModelScope.launch(dispatcher) {
        mapAuthMonitor.errorCode.filterNotNull().collect { code ->
            pollingJob?.cancel()
            freshnessJob?.cancel()
            pollingJob = null
            freshnessJob = null
            _uiState.value = _uiState.value.copy(
                mapErrorCode = code,
                visibleVehicles = emptyList(),
            )
        }
    }

    fun open(slot: CommuteSlot) {
        if (openedSlot == slot && _uiState.value.selection != null) {
            startJobsIfReady()
            return
        }
        bootstrapJob?.cancel()
        pollingJob?.cancel()
        freshnessJob?.cancel()
        openedSlot = slot
        _uiState.value = RealtimeMapUiState(
            loadingGeometry = true,
            mapErrorCode = mapAuthMonitor.errorCode.value,
        )
        bootstrapJob = viewModelScope.launch(dispatcher) {
            val selection = dashboard.favorite(slot)
            if (selection == null) {
                _uiState.value = RealtimeMapUiState(geometryError = BusDataError.MalformedResponse)
                return@launch
            }
            _uiState.value = _uiState.value.copy(selection = selection)
            when (val result = geometry.load(selection)) {
                is RouteMapLoadResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        loadingGeometry = false,
                        geometryError = result.error,
                    )
                }
                is RouteMapLoadResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        geometry = result.geometry,
                        stops = result.stops,
                        loadingGeometry = false,
                        geometryError = result.warning,
                    )
                    startJobsIfReady()
                }
            }
        }
    }

    fun setVisible(value: Boolean) {
        visible = value
        if (value) {
            startJobsIfReady()
        } else {
            pollingJob?.cancel()
            freshnessJob?.cancel()
            pollingJob = null
            freshnessJob = null
        }
    }

    fun retry() {
        if (_uiState.value.mapErrorCode != null) {
            mapAuthMonitor.clear()
            _uiState.value = _uiState.value.copy(mapErrorCode = null)
        }
        val selection = _uiState.value.selection ?: return
        if (_uiState.value.geometry == null) {
            bootstrapJob?.cancel()
            bootstrapJob = viewModelScope.launch(dispatcher) {
                _uiState.value = _uiState.value.copy(loadingGeometry = true, geometryError = null)
                when (val result = geometry.load(selection, force = true)) {
                    is RouteMapLoadResult.Failure -> _uiState.value = _uiState.value.copy(
                        loadingGeometry = false, geometryError = result.error,
                    )
                    is RouteMapLoadResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            geometry = result.geometry,
                            stops = result.stops,
                            loadingGeometry = false,
                            geometryError = result.warning,
                        )
                        startJobsIfReady()
                    }
                }
            }
        } else {
            pollingJob?.cancel()
            pollingJob = null
            startJobsIfReady()
        }
    }

    fun selectVehicle(key: String) {
        if (_uiState.value.visibleVehicles.any { it.key == key }) {
            _uiState.value = _uiState.value.copy(selectedVehicleKey = key)
        }
    }

    fun close() {
        visible = false
        openedSlot = null
        bootstrapJob?.cancel()
        pollingJob?.cancel()
        freshnessJob?.cancel()
        bootstrapJob = null
        pollingJob = null
        freshnessJob = null
        _uiState.value = RealtimeMapUiState()
    }

    private fun startJobsIfReady() {
        val selection = _uiState.value.selection ?: return
        if (
            _uiState.value.geometry == null ||
            _uiState.value.mapErrorCode != null ||
            !visible ||
            pollingJob?.isActive == true
        ) return
        pollingJob = viewModelScope.launch(dispatcher) {
            var consecutiveFailures = 0
            while (isActive && visible) {
                when (val result = vehicles.refresh(selection)) {
                    is VehicleLoadResult.Success -> {
                        consecutiveFailures = 0
                        publishBatch(result.batch, null)
                        delay(POLL_INTERVAL_MILLIS)
                    }
                    is VehicleLoadResult.Failure -> {
                        publishBatch(result.retained, result.error)
                        val pollResult = when (result.error) {
                            BusDataError.InvalidCredential -> PollResult.AuthenticationFailure
                            BusDataError.RateLimited -> PollResult.QuotaExceeded
                            else -> PollResult.TransientFailure(++consecutiveFailures)
                        }
                        when (val decision = PollingPolicy.after(pollResult)) {
                            PollDecision.Stop -> return@launch
                            is PollDecision.Wait -> delay(decision.seconds * 1_000)
                        }
                    }
                }
            }
        }
        if (freshnessJob?.isActive != true) {
            freshnessJob = viewModelScope.launch(dispatcher) {
                while (isActive && visible) {
                    publishFreshness()
                    delay(FRESHNESS_TICK_MILLIS)
                }
            }
        }
    }

    private fun publishBatch(batch: VehicleBatch?, error: BusDataError?) {
        _uiState.value = _uiState.value.copy(
            vehicleBatch = batch,
            vehicleError = error,
            selectedVehicleKey = null,
        )
        publishFreshness()
    }

    private fun publishFreshness() {
        val current = _uiState.value
        val freshness = FreshnessPolicy.classify(current.vehicleBatch?.fetchedAt, clock.instant())
        val visibleVehicles = if (freshness == DataFreshness.STALE) {
            emptyList()
        } else {
            current.vehicleBatch?.let { batch ->
                current.selection?.let { selection -> mapVehicles(selection, current.stops, batch) }
            }.orEmpty()
        }
        _uiState.value = current.copy(freshness = freshness, visibleVehicles = visibleVehicles)
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 8_000L
        const val FRESHNESS_TICK_MILLIS = 1_000L
    }
}

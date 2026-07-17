package com.rafaam11.businfo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafaam11.businfo.data.DashboardDataSource
import com.rafaam11.businfo.data.PreciseDataResult
import com.rafaam11.businfo.data.PreciseVehiclePositionDataSource
import com.rafaam11.businfo.data.RouteGeometryDataSource
import com.rafaam11.businfo.data.RouteMapLoadResult
import com.rafaam11.businfo.data.VehiclePositionDataSource
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.DataFreshness
import com.rafaam11.businfo.domain.PreciseSourceHealth
import com.rafaam11.businfo.domain.PreciseVehicleBatch
import com.rafaam11.businfo.domain.PreciseVehiclePosition
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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class RealtimeMapViewModel(
    private val dashboard: DashboardDataSource,
    private val geometry: RouteGeometryDataSource,
    private val summaryVehicles: VehiclePositionDataSource,
    private val preciseVehicles: PreciseVehiclePositionDataSource,
    private val mapAuthMonitor: MapAuthMonitor,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RealtimeMapUiState())
    val uiState: StateFlow<RealtimeMapUiState> = _uiState.asStateFlow()

    private var visible = false
    private var openedSlot: CommuteSlot? = null
    private var bootstrapJob: Job? = null
    private var summaryJob: Job? = null
    private var preciseJob: Job? = null
    private var freshnessJob: Job? = null
    private val mapAuthJob = viewModelScope.launch(dispatcher) {
        mapAuthMonitor.errorCode.filterNotNull().collect { code ->
            stopPollingJobs()
            _uiState.value = _uiState.value.copy(mapErrorCode = code, visibleVehicles = emptyList())
        }
    }

    fun open(slot: CommuteSlot) {
        if (openedSlot == slot && _uiState.value.selection != null) {
            startJobsIfReady()
            return
        }
        cancelAllJobs()
        if (openedSlot != null) preciseVehicles.closeSession()
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
                is RouteMapLoadResult.Failure -> _uiState.value = _uiState.value.copy(
                    loadingGeometry = false,
                    geometryError = result.error,
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
    }

    fun setVisible(value: Boolean) {
        visible = value
        if (value) startJobsIfReady() else stopPollingJobs()
    }

    fun retry() {
        if (_uiState.value.mapErrorCode != null) {
            mapAuthMonitor.clear()
        }
        _uiState.value = _uiState.value.copy(
            mapErrorCode = null,
            vehicleError = null,
            preciseError = null,
        )
        val selection = _uiState.value.selection ?: return
        if (_uiState.value.geometry == null) {
            bootstrapJob?.cancel()
            bootstrapJob = viewModelScope.launch(dispatcher) {
                _uiState.value = _uiState.value.copy(loadingGeometry = true, geometryError = null)
                when (val result = geometry.load(selection, force = true)) {
                    is RouteMapLoadResult.Failure -> _uiState.value = _uiState.value.copy(
                        loadingGeometry = false,
                        geometryError = result.error,
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
            stopPollingJobs()
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
        cancelAllJobs()
        preciseVehicles.closeSession()
        _uiState.value = RealtimeMapUiState()
    }

    private fun startJobsIfReady() {
        val selection = _uiState.value.selection ?: return
        if (_uiState.value.geometry == null || _uiState.value.mapErrorCode != null || !visible) return

        if (summaryJob?.isActive != true) {
            summaryJob = viewModelScope.launch(dispatcher) {
                while (isActive && visible) {
                    when (val result = summaryVehicles.refresh(selection)) {
                        is VehicleLoadResult.Success -> _uiState.value = _uiState.value.copy(
                            vehicleBatch = result.batch,
                            totalOperatingCount = result.batch.vehicles.size,
                            vehicleError = null,
                        )
                        is VehicleLoadResult.Failure -> {
                            val retained = result.retained
                            _uiState.value = _uiState.value.copy(
                                vehicleBatch = retained ?: _uiState.value.vehicleBatch,
                                totalOperatingCount = retained?.vehicles?.size ?: _uiState.value.totalOperatingCount,
                                vehicleError = result.error,
                            )
                            if (result.error == BusDataError.InvalidCredential || result.error == BusDataError.RateLimited) {
                                return@launch
                            }
                        }
                    }
                    delay(SUMMARY_INTERVAL_MILLIS)
                }
            }
        }

        if (preciseJob?.isActive != true) {
            preciseJob = viewModelScope.launch(dispatcher) {
                val initialRosterSucceeded = refreshRoster(selection)
                val rosterLoop = launch {
                    var failures = if (initialRosterSucceeded) 0 else 1
                    delay(rosterDelay(failures))
                    while (isActive && visible) {
                        val succeeded = refreshRoster(selection)
                        failures = if (succeeded) 0 else failures + 1
                        delay(rosterDelay(failures))
                    }
                }
                val detailLoop = launch {
                    var failures = 0
                    while (isActive && visible) {
                        when (val result = preciseVehicles.refreshPositions(selection)) {
                            is PreciseDataResult.Failure -> {
                                failures++
                                _uiState.value = _uiState.value.copy(preciseError = result.error)
                                publishPreciseVisibility()
                                delay(detailFailureDelay(failures))
                            }
                            is PreciseDataResult.Success -> {
                                val batch = result.value
                                publishPreciseBatch(batch)
                                failures = if (batch.positions.isNotEmpty() || batch.rosterCount == 0) 0 else failures + 1
                                delay(if (failures == 0) DETAIL_INTERVAL_MILLIS else detailFailureDelay(failures))
                            }
                        }
                    }
                }
                joinAll(rosterLoop, detailLoop)
            }
        }

        if (freshnessJob?.isActive != true) {
            freshnessJob = viewModelScope.launch(dispatcher) {
                while (isActive && visible) {
                    publishPreciseVisibility()
                    delay(FRESHNESS_TICK_MILLIS)
                }
            }
        }
    }

    private suspend fun refreshRoster(selection: com.rafaam11.businfo.domain.FavoriteSelection): Boolean =
        when (val result = preciseVehicles.refreshRoster(selection)) {
            is PreciseDataResult.Failure -> {
                _uiState.value = _uiState.value.copy(preciseError = result.error)
                false
            }
            is PreciseDataResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    totalOperatingCount = _uiState.value.totalOperatingCount ?: result.value.vehicleCount,
                    preciseError = null,
                )
                true
            }
        }

    private fun publishPreciseBatch(incoming: PreciseVehicleBatch) {
        val previous = _uiState.value.preciseBatch
        val mergedPositions = if (incoming.rosterCount == 0) {
            emptyList()
        } else {
            (previous?.positions.orEmpty().filter { it.sessionKey in incoming.rosterSessionKeys } + incoming.positions)
                .associateBy(PreciseVehiclePosition::sessionKey)
                .values
                .toList()
        }
        _uiState.value = _uiState.value.copy(
            preciseBatch = incoming.copy(positions = mergedPositions),
            totalOperatingCount = _uiState.value.totalOperatingCount ?: incoming.rosterCount,
            preciseError = if (incoming.failureCount == 0) null else _uiState.value.preciseError,
        )
        publishPreciseVisibility()
    }

    private fun publishPreciseVisibility() {
        val current = _uiState.value
        val batch = current.preciseBatch
        val now = clock.instant()
        val visibleVehicles = if (batch != null && current.selection != null) {
            mapVehicles(current.selection, current.stops, batch, now)
        } else {
            emptyList()
        }
        val delayedCount = visibleVehicles.count(MapVehicleUi::delayed)
        val hiddenCount = (batch?.rosterCount?.minus(visibleVehicles.size) ?: 0).coerceAtLeast(0)
        val health = when {
            batch == null -> PreciseSourceHealth.UNAVAILABLE
            batch.rosterCount == 0 && current.preciseError == null -> PreciseSourceHealth.HEALTHY
            visibleVehicles.isEmpty() && batch.positions.isNotEmpty() -> PreciseSourceHealth.DELAYED
            visibleVehicles.isEmpty() -> PreciseSourceHealth.UNAVAILABLE
            batch.failureCount > 0 || current.preciseError != null -> PreciseSourceHealth.PARTIAL
            else -> PreciseSourceHealth.HEALTHY
        }
        val freshness = when {
            visibleVehicles.any { !it.delayed } -> DataFreshness.FRESH
            visibleVehicles.isNotEmpty() -> DataFreshness.DELAYED
            batch != null -> DataFreshness.STALE
            else -> DataFreshness.UNAVAILABLE
        }
        _uiState.value = current.copy(
            visibleVehicles = visibleVehicles,
            delayedVehicleCount = delayedCount,
            hiddenVehicleCount = hiddenCount,
            preciseSourceHealth = health,
            freshness = freshness,
            dataAgeSeconds = visibleVehicles.minOfOrNull(MapVehicleUi::ageSeconds),
            selectedVehicleKey = current.selectedVehicleKey?.takeIf { selected ->
                visibleVehicles.any { it.key == selected }
            },
        )
    }

    private fun stopPollingJobs() {
        summaryJob?.cancel()
        preciseJob?.cancel()
        freshnessJob?.cancel()
        summaryJob = null
        preciseJob = null
        freshnessJob = null
    }

    private fun cancelAllJobs() {
        bootstrapJob?.cancel()
        bootstrapJob = null
        stopPollingJobs()
    }

    private fun rosterDelay(failures: Int): Long = when {
        failures <= 0 -> ROSTER_INTERVAL_MILLIS
        failures == 1 -> 15_000L
        failures == 2 -> 30_000L
        else -> 60_000L
    }

    private fun detailFailureDelay(failures: Int): Long = when (failures) {
        1 -> 6_000L
        2 -> 15_000L
        else -> 30_000L
    }

    private companion object {
        const val DETAIL_INTERVAL_MILLIS = 3_000L
        const val SUMMARY_INTERVAL_MILLIS = 15_000L
        const val ROSTER_INTERVAL_MILLIS = 15_000L
        const val FRESHNESS_TICK_MILLIS = 1_000L
    }
}

package com.rafaam11.businfo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafaam11.businfo.data.PreciseDataResult
import com.rafaam11.businfo.data.PreciseVehiclePositionDataSource
import com.rafaam11.businfo.data.PreciseVehicleSessionFactory
import com.rafaam11.businfo.data.StopSearchGateway
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.PrecisePositionFreshness
import com.rafaam11.businfo.domain.RouteDirectionKey
import com.rafaam11.businfo.domain.StopArrivalGroup
import com.rafaam11.businfo.domain.StopCatalogItem
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.freshnessAt
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class StopMapVehicleUi(
    val key: String,
    val routeKey: RouteDirectionKey,
    val routeNo: String,
    val point: GeoPoint,
    val heading: Float?,
    val remainingStops: Int?,
    val delayed: Boolean,
    val observedAt: Instant,
)

data class StopRealtimeMapUiState(
    val stop: StopCatalogItem? = null,
    val groups: List<StopArrivalGroup> = emptyList(),
    val highlightedRoute: RouteDirectionKey? = null,
    val vehicles: List<StopMapVehicleUi> = emptyList(),
    val routeErrors: Map<RouteDirectionKey, BusDataError> = emptyMap(),
    val routeStops: Map<RouteDirectionKey, List<RouteStop>> = emptyMap(),
)

class StopRealtimeMapViewModel(
    private val search: StopSearchGateway,
    private val sessionFactory: PreciseVehicleSessionFactory,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StopRealtimeMapUiState())
    val uiState: StateFlow<StopRealtimeMapUiState> = _uiState.asStateFlow()
    private val sessions = mutableMapOf<RouteDirectionKey, PreciseVehiclePositionDataSource>()
    private val jobs = mutableListOf<Job>()
    private val vehiclesByRoute = mutableMapOf<RouteDirectionKey, List<StopMapVehicleUi>>()
    private var signature: Pair<String, Set<RouteDirectionKey>>? = null
    private var generation = 0L

    fun open(stop: StopCatalogItem, groups: List<StopArrivalGroup>) {
        val nextSignature = stop.stopId to groups.map(StopArrivalGroup::key).toSet()
        if (signature == nextSignature) return
        close()
        val openGeneration = generation
        signature = nextSignature
        _uiState.value = StopRealtimeMapUiState(stop, groups, groups.firstOrNull()?.key)
        groups.forEach { group ->
            jobs += viewModelScope.launch(dispatcher) { prepareSession(stop, group, openGeneration) }
        }
        jobs += viewModelScope.launch(dispatcher) {
            while (isActive) {
                publishFreshVehicles()
                delay(FRESHNESS_TICK_MILLIS)
            }
        }
    }

    fun highlight(key: RouteDirectionKey) {
        if (_uiState.value.groups.any { it.key == key }) {
            _uiState.value = _uiState.value.copy(highlightedRoute = key)
        }
    }

    fun close() {
        generation++
        jobs.forEach(Job::cancel)
        jobs.clear()
        sessions.values.forEach(PreciseVehiclePositionDataSource::closeSession)
        sessions.clear()
        vehiclesByRoute.clear()
        signature = null
        _uiState.value = StopRealtimeMapUiState()
    }

    private suspend fun prepareSession(stop: StopCatalogItem, group: StopArrivalGroup, openGeneration: Long) {
        val stops = search.routeStops(group.key.routeId).getOrElse {
            if (openGeneration == generation) publishError(group.key, BusDataError.ServiceUnavailable)
            return
        }.filter { it.moveDirection == group.key.moveDirection }
        if (openGeneration != generation) return
        val targetSequence = stops.firstOrNull { it.stopId == stop.stopId }?.sequence
        if (targetSequence == null) {
            publishError(group.key, BusDataError.MalformedResponse)
            return
        }
        _uiState.value = _uiState.value.copy(routeStops = _uiState.value.routeStops + (group.key to stops))
        val selection = FavoriteSelection(
            CommuteSlot.MORNING,
            group.key.routeId,
            group.routeNo,
            group.key.moveDirection,
            stops.maxByOrNull { it.sequence }?.let { "${it.stopName} 방면" }.orEmpty(),
            stop.stopId,
            stop.stopName,
        )
        val session = sessionFactory.create().also { it.configureTargetStopSequence(targetSequence) }
        if (openGeneration != generation) {
            session.closeSession()
            return
        }
        sessions[group.key] = session
        val initialRoster = session.refreshRoster(selection)
        if (openGeneration != generation) {
            session.closeSession()
            sessions.remove(group.key, session)
            return
        }
        when (val roster = initialRoster) {
            is PreciseDataResult.Failure -> publishError(group.key, roster.error)
            is PreciseDataResult.Success -> publishError(group.key, null)
        }
        jobs += viewModelScope.launch(dispatcher) {
            while (isActive) {
                delay(ROSTER_INTERVAL_MILLIS)
                val roster = session.refreshRoster(selection)
                if (openGeneration != generation) return@launch
                when (val result = roster) {
                    is PreciseDataResult.Failure -> publishError(group.key, result.error)
                    is PreciseDataResult.Success -> publishError(group.key, null)
                }
            }
        }
        jobs += viewModelScope.launch(dispatcher) {
            while (isActive) {
                val positions = session.refreshPositions(selection)
                if (openGeneration != generation) return@launch
                when (val result = positions) {
                    is PreciseDataResult.Failure -> publishError(group.key, result.error)
                    is PreciseDataResult.Success -> {
                        val now = clock.instant()
                        vehiclesByRoute[group.key] = result.value.positions.mapNotNull { position ->
                            val freshness = position.freshnessAt(now)
                            if (freshness == PrecisePositionFreshness.HIDDEN) null else StopMapVehicleUi(
                                key = "${group.key.routeId}:${group.key.moveDirection}:${position.sessionKey}",
                                routeKey = group.key,
                                routeNo = group.routeNo,
                                point = position.point,
                                heading = position.heading,
                                remainingStops = position.stopSequence?.let(targetSequence::minus),
                                delayed = freshness == PrecisePositionFreshness.DELAYED,
                                observedAt = position.observedAt,
                            )
                        }
                        publishError(group.key, if (result.value.failureCount > 0) BusDataError.ServiceUnavailable else null)
                        publishVehicles()
                    }
                }
                val highlighted = _uiState.value.highlightedRoute == group.key
                delay(if (highlighted) HIGHLIGHTED_DETAIL_INTERVAL_MILLIS else DETAIL_INTERVAL_MILLIS)
            }
        }
    }

    private fun publishFreshVehicles() {
        val now = clock.instant()
        vehiclesByRoute.replaceAll { _, vehicles ->
            vehicles.mapNotNull { vehicle ->
                val age = Duration.between(vehicle.observedAt, now).seconds.coerceAtLeast(0)
                if (age > 30) null else vehicle.copy(delayed = age > 15)
            }
        }
        publishVehicles()
    }

    private fun publishVehicles() {
        _uiState.value = _uiState.value.copy(vehicles = vehiclesByRoute.values.flatten())
    }

    private fun publishError(key: RouteDirectionKey, error: BusDataError?) {
        val errors = if (error == null) _uiState.value.routeErrors - key else _uiState.value.routeErrors + (key to error)
        _uiState.value = _uiState.value.copy(routeErrors = errors)
    }

    override fun onCleared() {
        close()
    }

    private companion object {
        const val HIGHLIGHTED_DETAIL_INTERVAL_MILLIS = 3_000L
        const val DETAIL_INTERVAL_MILLIS = 8_000L
        const val ROSTER_INTERVAL_MILLIS = 30_000L
        const val FRESHNESS_TICK_MILLIS = 1_000L
    }
}

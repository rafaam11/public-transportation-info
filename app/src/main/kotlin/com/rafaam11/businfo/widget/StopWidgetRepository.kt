package com.rafaam11.businfo.widget

import com.rafaam11.businfo.data.local.StopCenteredLocalDataSource
import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import com.rafaam11.businfo.domain.StopArrival
import com.rafaam11.businfo.domain.StopArrivalSnapshot
import com.rafaam11.businfo.domain.WidgetBinding
import com.rafaam11.businfo.domain.routesForHome
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface StopWidgetStore {
    suspend fun binding(appWidgetId: Int): WidgetBinding?
    suspend fun favorite(id: FavoriteStopId): FavoriteStop?
    suspend fun snapshot(stopId: String): StopArrivalSnapshot?
    suspend fun saveBinding(value: WidgetBinding)
    suspend fun saveSnapshot(value: StopArrivalSnapshot)
    suspend fun deleteBinding(appWidgetId: Int)
}

class RoomStopWidgetStore(private val local: StopCenteredLocalDataSource) : StopWidgetStore {
    override suspend fun binding(appWidgetId: Int) = local.widgetBinding(appWidgetId)
    override suspend fun favorite(id: FavoriteStopId) = local.favoriteStop(id)
    override suspend fun snapshot(stopId: String) = local.stopArrival(stopId)
    override suspend fun saveBinding(value: WidgetBinding) = local.saveWidgetBinding(value)
    override suspend fun saveSnapshot(value: StopArrivalSnapshot) = local.saveStopArrival(value)
    override suspend fun deleteBinding(appWidgetId: Int) = local.deleteWidgetBinding(appWidgetId)
}

data class StopWidgetRouteUi(
    val routeNo: String,
    val direction: String,
    val arrivalText: String,
)

data class StopWidgetUiState(
    val appWidgetId: Int,
    val favoriteStopId: FavoriteStopId?,
    val stopName: String?,
    val routes: List<StopWidgetRouteUi>,
    val fetchedAt: Instant?,
    val isRefreshing: Boolean,
    val refreshFailed: Boolean,
    val requiresConfiguration: Boolean,
)

enum class StopWidgetRefreshResult { Success, AlreadyRunning, RequiresConfiguration, Failed }

class StopWidgetRepository(
    private val store: StopWidgetStore,
    private val refresher: suspend (String) -> Result<StopArrivalSnapshot>,
    private val clock: Clock,
) {
    private val locks = ConcurrentHashMap<Int, Mutex>()
    private val refreshing = ConcurrentHashMap.newKeySet<Int>()
    private val failed = ConcurrentHashMap.newKeySet<Int>()

    suspend fun state(appWidgetId: Int): StopWidgetUiState {
        val binding = store.binding(appWidgetId)
        val favorite = binding?.let { store.favorite(it.favoriteStopId) }
        val snapshot = favorite?.let { store.snapshot(it.stopId) }
        val routes = favorite?.routesForHome(snapshot?.groups.orEmpty(), 4).orEmpty().map { group ->
            StopWidgetRouteUi(
                routeNo = group.routeNo,
                direction = when (group.key.moveDirection) { "0" -> "정방향"; "1" -> "역방향"; else -> group.key.moveDirection },
                arrivalText = group.arrivals.firstOrNull()?.widgetArrivalText().orEmpty(),
            )
        }
        return StopWidgetUiState(
            appWidgetId = appWidgetId,
            favoriteStopId = favorite?.id,
            stopName = favorite?.stopName,
            routes = routes,
            fetchedAt = snapshot?.fetchedAt,
            isRefreshing = appWidgetId in refreshing,
            refreshFailed = appWidgetId in failed,
            requiresConfiguration = binding == null || favorite == null,
        )
    }

    suspend fun bind(appWidgetId: Int, favoriteStopId: FavoriteStopId) {
        store.saveBinding(WidgetBinding(appWidgetId, favoriteStopId, clock.instant()))
        failed -= appWidgetId
    }

    suspend fun refresh(appWidgetId: Int): StopWidgetRefreshResult {
        val lock = locks.computeIfAbsent(appWidgetId) { Mutex() }
        if (!lock.tryLock()) return StopWidgetRefreshResult.AlreadyRunning
        return try {
            val binding = store.binding(appWidgetId) ?: return StopWidgetRefreshResult.RequiresConfiguration
            val favorite = store.favorite(binding.favoriteStopId) ?: return StopWidgetRefreshResult.RequiresConfiguration
            refreshing += appWidgetId
            val result = refresher(favorite.stopId)
            result.onSuccess { store.saveSnapshot(it); failed -= appWidgetId }
                .onFailure { failed += appWidgetId }
            if (result.isSuccess) StopWidgetRefreshResult.Success else StopWidgetRefreshResult.Failed
        } finally {
            refreshing -= appWidgetId
            lock.unlock()
        }
    }

    suspend fun clear(appWidgetId: Int) {
        store.deleteBinding(appWidgetId)
        refreshing -= appWidgetId
        failed -= appWidgetId
        locks.remove(appWidgetId)
    }

    private fun StopArrival.widgetArrivalText(): String = when {
        stopGap == 0 -> "도착 임박"
        arrivalSeconds < 60 -> "곧 도착"
        else -> "${ceil(arrivalSeconds / 60.0).toInt()}분"
    }
}

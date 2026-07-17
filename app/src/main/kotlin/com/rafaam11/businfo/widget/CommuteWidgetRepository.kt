package com.rafaam11.businfo.widget

import com.rafaam11.businfo.data.DashboardDataSource
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

data class CommuteWidgetUiState(
    val appWidgetId: Int,
    val slot: CommuteSlot?,
    val routeNo: String?,
    val routeTypeCode: String?,
    val stopName: String?,
    val directionLabel: String?,
    val primaryText: String,
    val secondaryText: String?,
    val fetchedAt: Instant?,
    val refreshError: BusDataError?,
    val refreshErrorAt: Long?,
    val isRefreshing: Boolean,
    val requiresConfiguration: Boolean,
)

sealed interface WidgetRefreshResult {
    data object Success : WidgetRefreshResult
    data object AlreadyRunning : WidgetRefreshResult
    data class Failed(val error: BusDataError) : WidgetRefreshResult
    data object RequiresConfiguration : WidgetRefreshResult
}

class CommuteWidgetRepository(
    private val dashboard: DashboardDataSource,
    private val preferences: WidgetPreferenceGateway,
    private val clock: Clock,
) {
    private class RefreshToken(@Volatile var valid: Boolean = true)

    private val refreshMutexes = ConcurrentHashMap<Int, Mutex>()
    private val refreshingIds = ConcurrentHashMap.newKeySet<Int>()
    private val activeRefreshes = ConcurrentHashMap<Int, RefreshToken>()

    suspend fun state(appWidgetId: Int, now: Instant): CommuteWidgetUiState {
        val slot = preferences.slot(appWidgetId)
        val snapshot = slot?.let { configured ->
            dashboard.observeDashboard().first().firstOrNull { it.selection.slot == configured }
        }
        val error = preferences.errorState(appWidgetId)
        val arrival = snapshot?.arrivals?.firstOrNull()
        return CommuteWidgetUiState(
            appWidgetId = appWidgetId,
            slot = slot,
            routeNo = snapshot?.selection?.routeNo,
            routeTypeCode = snapshot?.selection?.routeTypeCode,
            stopName = snapshot?.selection?.stopName,
            directionLabel = snapshot?.selection?.directionLabel,
            primaryText = arrival?.primaryText() ?: if (snapshot != null) "아직 받은 정보 없음" else "",
            secondaryText = arrival?.secondaryText(),
            fetchedAt = snapshot?.fetchedAt,
            refreshError = error?.error,
            refreshErrorAt = error?.atEpochMillis,
            isRefreshing = appWidgetId in refreshingIds,
            requiresConfiguration = slot == null || snapshot == null,
        )
    }

    suspend fun refresh(
        appWidgetId: Int,
        onStarted: suspend () -> Unit = {},
    ): WidgetRefreshResult {
        val mutex = refreshMutexes.computeIfAbsent(appWidgetId) { Mutex() }
        if (!mutex.tryLock()) return WidgetRefreshResult.AlreadyRunning
        val token = RefreshToken()
        activeRefreshes[appWidgetId] = token
        refreshingIds += appWidgetId
        return try {
            onStarted()
            val slot = preferences.slot(appWidgetId) ?: return WidgetRefreshResult.RequiresConfiguration
            val error = dashboard.refreshFavorite(slot)
            synchronized(token) {
                if (token.valid) {
                    preferences.saveError(appWidgetId, error, error?.let { clock.instant().toEpochMilli() })
                }
            }
            if (error == null) WidgetRefreshResult.Success else WidgetRefreshResult.Failed(error)
        } finally {
            if (activeRefreshes.remove(appWidgetId, token)) refreshingIds -= appWidgetId
            mutex.unlock()
            refreshMutexes.remove(appWidgetId, mutex)
        }
    }

    fun clear(appWidgetId: Int) {
        val token = activeRefreshes.remove(appWidgetId)
        refreshingIds -= appWidgetId
        refreshMutexes.remove(appWidgetId)
        if (token == null) {
            preferences.clear(appWidgetId)
        } else {
            synchronized(token) {
                token.valid = false
                preferences.clear(appWidgetId)
            }
        }
    }
}

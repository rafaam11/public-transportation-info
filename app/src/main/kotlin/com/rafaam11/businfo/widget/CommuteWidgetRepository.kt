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
    private val beforeRefreshStartup: suspend (Int) -> Unit = {},
) {
    private class RefreshToken(@Volatile var valid: Boolean = true)
    private class WidgetLifecycle {
        val refreshMutex = Mutex()
        var generation: Long = 0
        var startupOwners: Int = 0
        var activeToken: RefreshToken? = null
        var removeWhenIdle: Boolean = false
    }
    private data class StartupLease(val lifecycle: WidgetLifecycle, val generation: Long)
    private sealed interface RefreshStart {
        data class Started(val slot: CommuteSlot, val token: RefreshToken) : RefreshStart
        data object AlreadyRunning : RefreshStart
        data object RequiresConfiguration : RefreshStart
    }

    private val lifecycles = ConcurrentHashMap<Int, WidgetLifecycle>()
    private val refreshingIds = ConcurrentHashMap.newKeySet<Int>()

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
        val lease = acquireStartupLease(appWidgetId)
        var startupReleased = false
        var started: RefreshStart.Started? = null
        return try {
            beforeRefreshStartup(appWidgetId)
            val start = synchronized(lease.lifecycle) {
                val result = when {
                    lease.generation != lease.lifecycle.generation -> RefreshStart.RequiresConfiguration
                    else -> {
                        val slot = preferences.slot(appWidgetId)
                        when {
                            slot == null -> RefreshStart.RequiresConfiguration
                            !lease.lifecycle.refreshMutex.tryLock() -> RefreshStart.AlreadyRunning
                            else -> {
                                val token = RefreshToken()
                                lease.lifecycle.activeToken = token
                                lease.lifecycle.removeWhenIdle = false
                                refreshingIds += appWidgetId
                                RefreshStart.Started(slot, token)
                            }
                        }
                    }
                }
                lease.lifecycle.startupOwners--
                startupReleased = true
                removeLifecycleIfIdle(appWidgetId, lease.lifecycle)
                result
            }
            when (start) {
                RefreshStart.AlreadyRunning -> return WidgetRefreshResult.AlreadyRunning
                RefreshStart.RequiresConfiguration -> return WidgetRefreshResult.RequiresConfiguration
                is RefreshStart.Started -> started = start
            }
            onStarted()
            val active = requireNotNull(started)
            val error = dashboard.refreshFavorite(active.slot)
            synchronized(lease.lifecycle) {
                if (lease.lifecycle.activeToken === active.token && active.token.valid) {
                    preferences.saveError(appWidgetId, error, error?.let { clock.instant().toEpochMilli() })
                }
            }
            if (error == null) WidgetRefreshResult.Success else WidgetRefreshResult.Failed(error)
        } finally {
            synchronized(lease.lifecycle) {
                if (!startupReleased) {
                    lease.lifecycle.startupOwners--
                    startupReleased = true
                }
                started?.let { active ->
                    if (lease.lifecycle.activeToken === active.token) {
                        lease.lifecycle.activeToken = null
                        refreshingIds -= appWidgetId
                        lease.lifecycle.refreshMutex.unlock()
                    }
                }
                removeLifecycleIfIdle(appWidgetId, lease.lifecycle)
            }
        }
    }

    fun clear(appWidgetId: Int) {
        val lifecycle = lifecycles.computeIfAbsent(appWidgetId) { WidgetLifecycle() }
        synchronized(lifecycle) {
            lifecycle.generation++
            lifecycle.removeWhenIdle = true
            lifecycle.activeToken?.valid = false
            refreshingIds -= appWidgetId
            preferences.clear(appWidgetId)
            removeLifecycleIfIdle(appWidgetId, lifecycle)
        }
    }

    private fun acquireStartupLease(appWidgetId: Int): StartupLease {
        lateinit var lease: StartupLease
        lifecycles.compute(appWidgetId) { _, existing ->
            val lifecycle = existing ?: WidgetLifecycle()
            synchronized(lifecycle) {
                lifecycle.startupOwners++
                lease = StartupLease(lifecycle, lifecycle.generation)
            }
            lifecycle
        }
        return lease
    }

    private fun removeLifecycleIfIdle(appWidgetId: Int, lifecycle: WidgetLifecycle) {
        if (lifecycle.removeWhenIdle && lifecycle.startupOwners == 0 && lifecycle.activeToken == null) {
            lifecycles.remove(appWidgetId, lifecycle)
        }
    }
}

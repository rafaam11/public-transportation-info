package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.credential.CredentialStore
import com.rafaam11.businfo.data.local.BusLocalDataSource
import com.rafaam11.businfo.data.local.StopCenteredLocalDataSource
import com.rafaam11.businfo.data.remote.DaeguBusRemoteDataSource
import com.rafaam11.businfo.data.remote.RemoteResult
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.PlaceResult
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.StopArrivalSnapshot
import com.rafaam11.businfo.domain.StopCatalogItem
import com.rafaam11.businfo.domain.groupByRouteDirection
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class GroupedSearchResult(
    val routes: List<RouteSummary>,
    val stops: List<StopCatalogItem>,
    val places: List<PlaceResult>,
)

interface PlaceSearchDataSource {
    suspend fun search(query: String): Result<List<PlaceResult>>
}

class StopDataException(val error: BusDataError) : Exception(error.toString())

class StopSearchRepository(
    private val credentials: CredentialStore,
    private val remote: DaeguBusRemoteDataSource,
    private val stopLocal: StopCenteredLocalDataSource,
    private val routeLocal: BusLocalDataSource,
    private val placeSearch: PlaceSearchDataSource,
    private val callCounter: ApiCallCounter,
    private val clock: Clock,
) {
    private val catalogMutex = Mutex()
    private val arrivalMutex = Mutex()

    suspend fun ensureStopCatalog(force: Boolean = false): Result<Unit> {
        if (!force && stopLocal.stops().isNotEmpty()) return Result.success(Unit)
        return catalogMutex.withLock {
            if (!force && stopLocal.stops().isNotEmpty()) return@withLock Result.success(Unit)
            val key = credentials.read() ?: return@withLock Result.failure(StopDataException(BusDataError.InvalidCredential))
            callCounter.increment(clock.instant())
            when (val result = remote.stopCatalog(key)) {
                is RemoteResult.Success -> {
                    if (result.value.isNotEmpty()) stopLocal.replaceStopCatalog(result.value)
                    Result.success(Unit)
                }
                is RemoteResult.Failure -> Result.failure(StopDataException(result.error))
            }
        }
    }

    suspend fun search(rawQuery: String): GroupedSearchResult {
        val query = rawQuery.trim().replace(Regex("\\s+"), " ")
        if (query.isBlank()) return GroupedSearchResult(emptyList(), emptyList(), emptyList())
        val routes = routeLocal.routes().filter { route ->
            route.routeNo.contains(query, ignoreCase = true) ||
                route.startName.contains(query, ignoreCase = true) ||
                route.endName.contains(query, ignoreCase = true)
        }.take(10)
        val stops = stopLocal.searchStops(query, 10)
        val places = if (query.length >= 2) placeSearch.search(query).getOrDefault(emptyList()) else emptyList()
        return GroupedSearchResult(routes, stops, places)
    }

    suspend fun refreshArrivals(stopId: String, force: Boolean = false): Result<StopArrivalSnapshot> {
        cachedFresh(stopId, force)?.let { return Result.success(it) }
        return arrivalMutex.withLock {
            cachedFresh(stopId, force)?.let { return@withLock Result.success(it) }
            val key = credentials.read() ?: return@withLock Result.failure(StopDataException(BusDataError.InvalidCredential))
            callCounter.increment(clock.instant())
            when (val result = remote.stopArrivals(key, stopId)) {
                is RemoteResult.Success -> {
                    val snapshot = StopArrivalSnapshot(stopId, result.value.groupByRouteDirection(), clock.instant())
                    stopLocal.saveStopArrival(snapshot)
                    Result.success(snapshot)
                }
                is RemoteResult.Failure -> Result.failure(StopDataException(result.error))
            }
        }
    }

    suspend fun todayApiCallCount(): Int = callCounter.count(clock.instant())

    private suspend fun cachedFresh(stopId: String, force: Boolean): StopArrivalSnapshot? {
        if (force) return null
        return stopLocal.stopArrival(stopId)?.takeIf {
            Duration.between(it.fetchedAt, clock.instant()) < ARRIVAL_CACHE_WINDOW
        }
    }

    private companion object {
        val ARRIVAL_CACHE_WINDOW: Duration = Duration.ofSeconds(8)
    }
}

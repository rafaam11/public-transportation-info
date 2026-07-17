package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.credential.CredentialStore
import com.rafaam11.businfo.data.local.BusLocalDataSource
import com.rafaam11.businfo.data.remote.DaeguBusRemoteDataSource
import com.rafaam11.businfo.data.remote.RemoteResult
import com.rafaam11.businfo.domain.ArrivalSnapshot
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteDashboardSnapshot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.VehicleLoadResult
import com.rafaam11.businfo.domain.directionLabel
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

data class DirectionOption(
    val code: String,
    val label: String,
    val stops: List<RouteStop>,
)

interface DashboardDataSource {
    fun observeDashboard(): Flow<List<FavoriteDashboardSnapshot>>
    suspend fun ensureRouteCatalog(force: Boolean = false): BusDataError?
    suspend fun searchRoutes(query: String): List<RouteSummary>
    suspend fun directions(route: RouteSummary, force: Boolean = false): Result<List<DirectionOption>>
    suspend fun saveFavorite(selection: FavoriteSelection)
    suspend fun deleteFavorite(slot: CommuteSlot)
    suspend fun favorite(slot: CommuteSlot): FavoriteSelection?
    suspend fun refreshFavorite(slot: CommuteSlot): BusDataError?
    suspend fun refreshAll(): Map<CommuteSlot, BusDataError?>
    suspend fun refreshRouteVehicles(slot: CommuteSlot): VehicleLoadResult
    suspend fun routeSummary(routeId: String): RouteSummary?
    suspend fun cachedDirections(route: RouteSummary): List<DirectionOption>
}

fun interface DashboardUpdateNotifier {
    suspend fun notifyChanged()

    companion object {
        val NONE = DashboardUpdateNotifier {}
    }
}

class DashboardRepository(
    private val credentials: CredentialStore,
    private val remote: DaeguBusRemoteDataSource,
    private val local: BusLocalDataSource,
    private val clock: Clock,
    private val updateNotifier: DashboardUpdateNotifier = DashboardUpdateNotifier.NONE,
) : DashboardDataSource {
    override fun observeDashboard(): Flow<List<FavoriteDashboardSnapshot>> = local.observeDashboard()

    override suspend fun ensureRouteCatalog(force: Boolean): BusDataError? {
        val cached = local.routes()
        val fresh = local.syncTime(ROUTE_CATALOG_SYNC_KEY)?.let {
            Duration.between(it, clock.instant()) < CACHE_TTL
        } == true
        if (!force && cached.isNotEmpty() && fresh) return null
        val key = credentials.read() ?: return BusDataError.InvalidCredential
        return when (val result = remote.routes(key)) {
            is RemoteResult.Success -> {
                if (result.value.isNotEmpty()) local.replaceRoutes(result.value)
                local.saveSyncTime(ROUTE_CATALOG_SYNC_KEY, clock.instant())
                null
            }
            is RemoteResult.Failure -> result.error
        }
    }

    override suspend fun searchRoutes(query: String): List<RouteSummary> {
        val needle = query.filterNot(Char::isWhitespace)
        if (needle.isEmpty()) return emptyList()
        return local.routes().filter { route ->
            listOf(route.routeNo, route.startName, route.endName)
                .any { it.filterNot(Char::isWhitespace).contains(needle, ignoreCase = true) }
        }.sortedWith(compareBy<RouteSummary>({ it.routeNo != needle }, { !it.routeNo.startsWith(needle) }, RouteSummary::routeNo))
    }

    override suspend fun directions(route: RouteSummary, force: Boolean): Result<List<DirectionOption>> {
        val syncKey = routeStopsSyncKey(route.routeId)
        var stops = local.routeStops(route.routeId)
        val fresh = local.syncTime(syncKey)?.let { Duration.between(it, clock.instant()) < CACHE_TTL } == true
        if (force || stops.isEmpty() || !fresh) {
            val key = credentials.read() ?: return Result.failure(RepositoryException(BusDataError.InvalidCredential))
            when (val result = remote.routeStops(key, route.routeId)) {
                is RemoteResult.Success -> {
                    if (result.value.isNotEmpty()) {
                        local.replaceRouteStops(route.routeId, result.value)
                        stops = result.value
                    }
                    local.saveSyncTime(syncKey, clock.instant())
                }
                is RemoteResult.Failure -> if (stops.isEmpty()) return Result.failure(RepositoryException(result.error))
            }
        }
        return Result.success(stops.groupBy(RouteStop::moveDirection).map { (code, directionStops) ->
            val note = if (code == "0") route.directionNote else route.reverseDirectionNote
            DirectionOption(code, directionLabel(code, note, directionStops), directionStops.sortedBy(RouteStop::sequence))
        }.sortedBy(DirectionOption::code))
    }

    override suspend fun saveFavorite(selection: FavoriteSelection) = local.saveFavorite(selection)
    override suspend fun deleteFavorite(slot: CommuteSlot) = local.deleteFavorite(slot)
    override suspend fun favorite(slot: CommuteSlot): FavoriteSelection? = local.favorite(slot)

    override suspend fun refreshFavorite(slot: CommuteSlot): BusDataError? {
        val selection = local.favorite(slot) ?: return null
        val key = credentials.read() ?: return BusDataError.InvalidCredential
        return when (val result = remote.arrivals(key, selection.stopId, selection.routeNo)) {
            is RemoteResult.Success -> {
                val arrivals = result.value.filter { it.moveDirection == selection.directionCode }
                    .sortedBy { it.arrivalSeconds }
                    .take(2)
                local.saveArrival(slot, ArrivalSnapshot(arrivals, clock.instant()))
                updateNotifier.notifyChanged()
                null
            }
            is RemoteResult.Failure -> result.error
        }
    }

    override suspend fun refreshAll(): Map<CommuteSlot, BusDataError?> = coroutineScope {
        CommuteSlot.entries.map { slot -> async { slot to refreshFavorite(slot) } }.awaitAll().toMap()
    }

    override suspend fun refreshRouteVehicles(slot: CommuteSlot): VehicleLoadResult {
        val selection = local.favorite(slot)
            ?: return VehicleLoadResult.Failure(BusDataError.MalformedResponse, null)
        val key = credentials.read()
            ?: return VehicleLoadResult.Failure(BusDataError.InvalidCredential, local.vehicleBatch(selection.routeId))
        return when (val result = remote.vehicles(key, selection.routeId)) {
            is RemoteResult.Success -> {
                if (result.value.isEmpty()) {
                    local.vehicleBatch(selection.routeId)?.let(VehicleLoadResult::Success)
                        ?: VehicleLoadResult.Success(VehicleBatch.from(emptyList(), clock.instant()))
                } else {
                    val batch = VehicleBatch.from(result.value, clock.instant())
                    local.saveVehicleBatch(selection.routeId, batch)
                    VehicleLoadResult.Success(batch)
                }
            }
            is RemoteResult.Failure -> VehicleLoadResult.Failure(result.error, local.vehicleBatch(selection.routeId))
        }
    }

    override suspend fun routeSummary(routeId: String): RouteSummary? = local.routes().firstOrNull { it.routeId == routeId }

    override suspend fun cachedDirections(route: RouteSummary): List<DirectionOption> =
        local.routeStops(route.routeId).groupBy(RouteStop::moveDirection).map { (code, stops) ->
            val note = if (code == "0") route.directionNote else route.reverseDirectionNote
            DirectionOption(code, directionLabel(code, note, stops), stops.sortedBy(RouteStop::sequence))
        }.sortedBy(DirectionOption::code)

    class RepositoryException(val error: BusDataError) : Exception(error.toString())

    companion object {
        const val ROUTE_CATALOG_SYNC_KEY = "route_catalog"
        private val CACHE_TTL: Duration = Duration.ofHours(24)
        fun routeStopsSyncKey(routeId: String) = "route_stops:$routeId"
    }
}

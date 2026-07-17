package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.credential.CredentialStore
import com.rafaam11.businfo.data.local.BusLocalDataSource
import com.rafaam11.businfo.data.remote.DaeguBusRemoteDataSource
import com.rafaam11.businfo.data.remote.RemoteResult
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.RouteGeometry
import com.rafaam11.businfo.domain.RouteGeometryAssembler
import com.rafaam11.businfo.domain.RouteStop
import java.time.Clock
import java.time.Duration

sealed interface RouteMapLoadResult {
    data class Success(
        val geometry: RouteGeometry,
        val stops: List<RouteStop>,
        val warning: BusDataError?,
    ) : RouteMapLoadResult

    data class Failure(val error: BusDataError) : RouteMapLoadResult
}

interface RouteGeometryDataSource {
    suspend fun load(selection: FavoriteSelection, force: Boolean = false): RouteMapLoadResult
}

class RouteGeometryRepository(
    private val credentials: CredentialStore,
    private val remote: DaeguBusRemoteDataSource,
    private val local: BusLocalDataSource,
    private val clock: Clock,
) : RouteGeometryDataSource {
    override suspend fun load(selection: FavoriteSelection, force: Boolean): RouteMapLoadResult {
        val cached = local.routeGeometry(selection.routeId, selection.directionCode)
        var stops = local.routeStops(selection.routeId).filter { it.moveDirection == selection.directionCode }
        val fresh = cached != null && Duration.between(cached.fetchedAt, clock.instant()) < CACHE_TTL
        if (!force && fresh && stops.isNotEmpty()) {
            return RouteMapLoadResult.Success(requireNotNull(cached), stops, null)
        }
        val key = credentials.read() ?: return cached.asFallback(stops, BusDataError.InvalidCredential)

        if (stops.isEmpty()) {
            when (val stopResult = remote.routeStops(key, selection.routeId)) {
                is RemoteResult.Failure -> return cached.asFallback(stops, stopResult.error)
                is RemoteResult.Success -> {
                    if (stopResult.value.isNotEmpty()) local.replaceRouteStops(selection.routeId, stopResult.value)
                    stops = stopResult.value.filter { it.moveDirection == selection.directionCode }
                }
            }
        }
        if (!force && fresh) return RouteMapLoadResult.Success(requireNotNull(cached), stops, null)

        val nodes = remote.basicNodes(key)
        if (nodes is RemoteResult.Failure) return cached.asFallback(stops, nodes.error)
        val links = remote.routeLinks(key, selection.routeId)
        if (links is RemoteResult.Failure) return cached.asFallback(stops, links.error)
        val geometry = RouteGeometryAssembler.assemble(
            selection.routeId,
            selection.directionCode,
            (nodes as RemoteResult.Success).value,
            (links as RemoteResult.Success).value,
            clock.instant(),
        )
        if (geometry.segments.isEmpty()) return cached.asFallback(stops, BusDataError.MalformedResponse)
        local.saveRouteGeometry(geometry)
        return RouteMapLoadResult.Success(geometry, stops, null)
    }

    private fun RouteGeometry?.asFallback(stops: List<RouteStop>, error: BusDataError): RouteMapLoadResult =
        this?.let { RouteMapLoadResult.Success(it, stops, error) } ?: RouteMapLoadResult.Failure(error)

    private companion object {
        val CACHE_TTL: Duration = Duration.ofHours(24)
    }
}

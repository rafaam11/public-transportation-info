package com.rafaam11.businfo.data.remote

import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.ArrivalEstimate
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.RouteLink
import com.rafaam11.businfo.domain.RouteNode
import com.rafaam11.businfo.domain.VehicleSnapshot

sealed interface RemoteResult<out T> {
    data class Success<T>(val value: T) : RemoteResult<T>
    data class Failure(val error: BusDataError) : RemoteResult<Nothing>
}

interface DaeguBusRemoteDataSource {
    suspend fun validateKey(serviceKey: String): RemoteResult<Unit>
    suspend fun routes(serviceKey: String): RemoteResult<List<RouteSummary>> = RemoteResult.Success(emptyList())
    suspend fun routeStops(serviceKey: String, routeId: String): RemoteResult<List<RouteStop>> = RemoteResult.Success(emptyList())
    suspend fun basicNodes(serviceKey: String): RemoteResult<List<RouteNode>> = RemoteResult.Success(emptyList())
    suspend fun routeLinks(serviceKey: String, routeId: String): RemoteResult<List<RouteLink>> = RemoteResult.Success(emptyList())
    suspend fun arrivals(serviceKey: String, stopId: String, routeNo: String): RemoteResult<List<ArrivalEstimate>> = RemoteResult.Success(emptyList())
    suspend fun vehicles(serviceKey: String, routeId: String): RemoteResult<List<VehicleSnapshot>>
}

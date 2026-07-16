package com.rafaam11.businfo.data.remote

import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.VehicleSnapshot

sealed interface RemoteResult<out T> {
    data class Success<T>(val value: T) : RemoteResult<T>
    data class Failure(val error: BusDataError) : RemoteResult<Nothing>
}

interface DaeguBusRemoteDataSource {
    suspend fun validateKey(serviceKey: String): RemoteResult<Unit>
    suspend fun vehicles(serviceKey: String, routeId: String): RemoteResult<List<VehicleSnapshot>>
}

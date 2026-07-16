package com.rafaam11.businfo.domain

sealed interface BusDataError {
    data object InvalidCredential : BusDataError
    data object RateLimited : BusDataError
    data object NetworkUnavailable : BusDataError
    data object ServiceUnavailable : BusDataError
    data object MalformedResponse : BusDataError
}

sealed interface VehicleLoadResult {
    data class Success(val batch: VehicleBatch) : VehicleLoadResult
    data class Failure(val error: BusDataError, val retained: VehicleBatch?) : VehicleLoadResult
}

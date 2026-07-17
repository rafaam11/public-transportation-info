package com.rafaam11.businfo.data

import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.PreciseVehicleBatch
import java.time.Instant

data class PreciseRosterSnapshot(
    val vehicleCount: Int,
    val receivedAt: Instant,
)

sealed interface PreciseDataResult<out T> {
    data class Success<T>(val value: T) : PreciseDataResult<T>
    data class Failure(val error: BusDataError) : PreciseDataResult<Nothing>
}

interface PreciseVehiclePositionDataSource {
    suspend fun refreshRoster(selection: FavoriteSelection): PreciseDataResult<PreciseRosterSnapshot>
    suspend fun refreshPositions(selection: FavoriteSelection): PreciseDataResult<PreciseVehicleBatch>
    fun closeSession()
}

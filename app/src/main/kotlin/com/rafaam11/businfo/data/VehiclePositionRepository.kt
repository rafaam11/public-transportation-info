package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.credential.CredentialStore
import com.rafaam11.businfo.data.local.BusLocalDataSource
import com.rafaam11.businfo.data.remote.DaeguBusRemoteDataSource
import com.rafaam11.businfo.data.remote.RemoteResult
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.VehicleLoadResult
import java.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface VehiclePositionDataSource {
    suspend fun refresh(selection: FavoriteSelection): VehicleLoadResult
}

class VehiclePositionRepository(
    private val credentials: CredentialStore,
    private val remote: DaeguBusRemoteDataSource,
    private val local: BusLocalDataSource,
    private val clock: Clock,
) : VehiclePositionDataSource {
    private val requestMutex = Mutex()

    override suspend fun refresh(selection: FavoriteSelection): VehicleLoadResult = requestMutex.withLock {
        val retained = local.vehicleBatch(selection.routeId)?.forDirection(selection.directionCode)
        val key = credentials.read()
            ?: return@withLock VehicleLoadResult.Failure(BusDataError.InvalidCredential, retained)
        when (val result = remote.vehicles(key, selection.routeId)) {
            is RemoteResult.Failure -> VehicleLoadResult.Failure(result.error, retained)
            is RemoteResult.Success -> {
                val complete = VehicleBatch.from(result.value, clock.instant())
                local.saveVehicleBatch(selection.routeId, complete)
                VehicleLoadResult.Success(complete.forDirection(selection.directionCode))
            }
        }
    }

    private fun VehicleBatch.forDirection(direction: String) =
        VehicleBatch.from(vehicles.filter { it.moveDirection == direction }, fetchedAt)
}

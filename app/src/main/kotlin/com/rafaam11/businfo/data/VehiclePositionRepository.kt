package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.credential.CredentialStore
import com.rafaam11.businfo.data.local.BusLocalDataSource
import com.rafaam11.businfo.data.remote.DaeguBusRemoteDataSource
import com.rafaam11.businfo.data.remote.RemoteResult
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.VehicleLoadResult
import com.rafaam11.businfo.domain.hasPlausibleDaeguPosition
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
        val retained = local.vehicleBatch(selection.routeId)?.forSelection(selection)
        val key = credentials.read()
            ?: return@withLock VehicleLoadResult.Failure(BusDataError.InvalidCredential, retained)
        when (val result = remote.vehicles(key, selection.routeId)) {
            is RemoteResult.Failure -> VehicleLoadResult.Failure(result.error, retained)
            is RemoteResult.Success -> {
                val verified = result.value.filter { vehicle ->
                    vehicle.routeId == selection.routeId && vehicle.hasPlausibleDaeguPosition()
                }
                if (result.value.isNotEmpty() && verified.isEmpty()) {
                    return@withLock VehicleLoadResult.Failure(BusDataError.MalformedResponse, retained)
                }
                val complete = VehicleBatch.from(verified, clock.instant())
                local.saveVehicleBatch(selection.routeId, complete)
                VehicleLoadResult.Success(complete.forSelection(selection))
            }
        }
    }

    private fun VehicleBatch.forSelection(selection: FavoriteSelection) = VehicleBatch.from(
        vehicles.filter { vehicle ->
            vehicle.routeId == selection.routeId &&
                vehicle.moveDirection == selection.directionCode &&
                vehicle.hasPlausibleDaeguPosition()
        },
        fetchedAt,
    )
}

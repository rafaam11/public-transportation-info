package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.credential.CredentialStore
import com.rafaam11.businfo.data.remote.DaeguBusRemoteDataSource
import com.rafaam11.businfo.data.remote.RemoteResult
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.VehicleLoadResult
import java.time.Clock

class BusRepository(
    private val credentials: CredentialStore,
    private val remote: DaeguBusRemoteDataSource,
    private val clock: Clock,
) {
    private var lastSuccessful: VehicleBatch? = null

    fun savedKeyExists(): Boolean = credentials.read() != null

    fun clearKey() {
        credentials.clear()
        lastSuccessful = null
    }

    suspend fun validateAndSave(key: String): BusDataError? {
        val candidate = key.trim()
        if (candidate.isBlank()) return BusDataError.InvalidCredential

        return when (val result = remote.validateKey(candidate)) {
            is RemoteResult.Success -> {
                credentials.write(candidate)
                null
            }
            is RemoteResult.Failure -> result.error
        }
    }

    suspend fun refreshVehicles(): VehicleLoadResult {
        val serviceKey = credentials.read()
            ?: return VehicleLoadResult.Failure(BusDataError.InvalidCredential, lastSuccessful)

        return when (val result = remote.vehicles(serviceKey, ROUTE_ID)) {
            is RemoteResult.Success -> {
                val batch = VehicleBatch.from(result.value, clock.instant())
                lastSuccessful = batch
                VehicleLoadResult.Success(batch)
            }
            is RemoteResult.Failure -> VehicleLoadResult.Failure(result.error, lastSuccessful)
        }
    }

    private companion object {
        const val ROUTE_ID = "3000814001"
    }
}

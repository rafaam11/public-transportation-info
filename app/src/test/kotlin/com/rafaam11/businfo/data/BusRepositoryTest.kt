package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.credential.CredentialStore
import com.rafaam11.businfo.data.remote.DaeguBusRemoteDataSource
import com.rafaam11.businfo.data.remote.RemoteResult
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.VehicleLoadResult
import com.rafaam11.businfo.domain.VehicleSnapshot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusRepositoryTest {
    private val now = Instant.parse("2026-07-16T12:00:00Z")
    private val credentials = FakeCredentialStore()
    private val remote = FakeRemoteDataSource()
    private val repository = BusRepository(
        credentials = credentials,
        remote = remote,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )
    private val vehicle = VehicleSnapshot(
        routeId = "3000814001",
        routeNo = "814",
        moveDirection = "0",
        stopId = "stop-a",
        stopSequence = 12,
        latitude = 35.8,
        longitude = 128.6,
        arrivalState = "soon",
        busTypeCode2 = "1",
        busTypeCode3 = "2",
    )

    @Test fun blankKeyIsRejectedWithoutNetworkCall() = runTest {
        assertEquals(BusDataError.InvalidCredential, repository.validateAndSave("   "))

        assertTrue(remote.validatedKeys.isEmpty())
        assertNull(credentials.read())
    }

    @Test fun credentialIsSavedOnlyAfterSuccessfulValidation() = runTest {
        remote.validationResults.add(RemoteResult.Failure(BusDataError.ServiceUnavailable))
        remote.validationResults.add(RemoteResult.Success(Unit))

        assertEquals(BusDataError.ServiceUnavailable, repository.validateAndSave(" candidate "))
        assertNull(credentials.read())
        assertNull(repository.validateAndSave(" candidate "))

        assertEquals("candidate", credentials.read())
        assertEquals(listOf("candidate", "candidate"), remote.validatedKeys)
        assertTrue(repository.savedKeyExists())
    }

    @Test fun successfulVehicleFetchUsesSavedKeyRouteAndInjectedClock() = runTest {
        credentials.write("saved-key")
        remote.vehicleResults.add(RemoteResult.Success(listOf(vehicle)))

        val result = repository.refreshVehicles() as VehicleLoadResult.Success

        assertEquals(listOf(vehicle), result.batch.vehicles)
        assertEquals(now, result.batch.fetchedAt)
        assertEquals(listOf("saved-key" to "3000814001"), remote.vehicleRequests)
    }

    @Test fun missingCredentialFailsWithoutVehicleRequest() = runTest {
        val result = repository.refreshVehicles()

        assertEquals(VehicleLoadResult.Failure(BusDataError.InvalidCredential, null), result)
        assertTrue(remote.vehicleRequests.isEmpty())
    }

    @Test fun failedRefreshRetainsLastSuccess() = runTest {
        credentials.write("saved-key")
        remote.vehicleResults.add(RemoteResult.Success(listOf(vehicle)))
        remote.vehicleResults.add(RemoteResult.Failure(BusDataError.NetworkUnavailable))
        repository.refreshVehicles()

        val second = repository.refreshVehicles() as VehicleLoadResult.Failure

        assertEquals(BusDataError.NetworkUnavailable, second.error)
        assertEquals(listOf(vehicle), second.retained?.vehicles)
        assertEquals(now, second.retained?.fetchedAt)
    }

    @Test fun successfulEmptyResponseReplacesPreviousVehicles() = runTest {
        credentials.write("saved-key")
        remote.vehicleResults.add(RemoteResult.Success(listOf(vehicle)))
        remote.vehicleResults.add(RemoteResult.Success(emptyList()))
        remote.vehicleResults.add(RemoteResult.Failure(BusDataError.NetworkUnavailable))
        repository.refreshVehicles()

        val empty = repository.refreshVehicles() as VehicleLoadResult.Success
        val subsequentFailure = repository.refreshVehicles() as VehicleLoadResult.Failure

        assertTrue(empty.batch.vehicles.isEmpty())
        assertEquals(now, empty.batch.fetchedAt)
        assertTrue(subsequentFailure.retained?.vehicles?.isEmpty() == true)
    }

    @Test fun clearingCredentialAlsoClearsRetainedSnapshot() = runTest {
        credentials.write("saved-key")
        remote.vehicleResults.add(RemoteResult.Success(listOf(vehicle)))
        repository.refreshVehicles()

        repository.clearKey()

        assertFalse(repository.savedKeyExists())
        assertEquals(VehicleLoadResult.Failure(BusDataError.InvalidCredential, null), repository.refreshVehicles())
    }

    private class FakeCredentialStore : CredentialStore {
        private var value: String? = null

        override fun read(): String? = value
        override fun write(serviceKey: String) {
            value = serviceKey
        }
        override fun clear() {
            value = null
        }
    }

    private class FakeRemoteDataSource : DaeguBusRemoteDataSource {
        val validationResults = ArrayDeque<RemoteResult<Unit>>()
        val vehicleResults = ArrayDeque<RemoteResult<List<VehicleSnapshot>>>()
        val validatedKeys = mutableListOf<String>()
        val vehicleRequests = mutableListOf<Pair<String, String>>()

        override suspend fun validateKey(serviceKey: String): RemoteResult<Unit> {
            validatedKeys += serviceKey
            return validationResults.removeFirst()
        }

        override suspend fun vehicles(serviceKey: String, routeId: String): RemoteResult<List<VehicleSnapshot>> {
            vehicleRequests += serviceKey to routeId
            return vehicleResults.removeFirst()
        }
    }
}

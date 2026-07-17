package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.credential.CredentialStore
import com.rafaam11.businfo.data.remote.DaeguBusRemoteDataSource
import com.rafaam11.businfo.data.remote.RemoteResult
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.VehicleSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusRepositoryTest {
    private val credentials = FakeCredentialStore()
    private val remote = FakeRemoteDataSource()
    private val repository = BusRepository(credentials, remote)

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

    @Test fun clearingCredentialDoesNotDependOnDashboardDatabase() {
        credentials.write("saved-key")
        repository.clearKey()
        assertFalse(repository.savedKeyExists())
    }

    private class FakeCredentialStore : CredentialStore {
        private var value: String? = null
        override fun read(): String? = value
        override fun write(serviceKey: String) { value = serviceKey }
        override fun clear() { value = null }
    }

    private class FakeRemoteDataSource : DaeguBusRemoteDataSource {
        val validationResults = ArrayDeque<RemoteResult<Unit>>()
        val validatedKeys = mutableListOf<String>()
        override suspend fun validateKey(serviceKey: String): RemoteResult<Unit> {
            validatedKeys += serviceKey
            return validationResults.removeFirst()
        }
        override suspend fun vehicles(serviceKey: String, routeId: String) = RemoteResult.Success(emptyList<VehicleSnapshot>())
    }
}

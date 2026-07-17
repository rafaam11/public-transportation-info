package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.credential.CredentialStore
import com.rafaam11.businfo.data.remote.DaeguBusRemoteDataSource
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.data.remote.RemoteResult

interface CredentialGateway {
    fun savedKeyExists(): Boolean
    fun clearKey()
    suspend fun validateAndSave(key: String): BusDataError?
}

class BusRepository(
    private val credentials: CredentialStore,
    private val remote: DaeguBusRemoteDataSource,
) : CredentialGateway {
    override fun savedKeyExists(): Boolean = credentials.read() != null

    override fun clearKey() {
        credentials.clear()
    }

    override suspend fun validateAndSave(key: String): BusDataError? {
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

}

package com.rafaam11.businfo.ui

import com.rafaam11.businfo.data.BusRepository
import com.rafaam11.businfo.data.credential.CredentialStore
import com.rafaam11.businfo.data.remote.DaeguBusRemoteDataSource
import com.rafaam11.businfo.data.remote.RemoteResult
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.VehicleSnapshot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleListViewModelTest {
    private val now = Instant.parse("2026-07-16T12:00:00Z")
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

    @Test fun missingSavedKeyStartsInNeedsKey() = runTest {
        val fixture = fixture()

        val viewModel = VehicleListViewModel(fixture.repository, StandardTestDispatcher(testScheduler))

        assertEquals(VehicleListUiState.NeedsKey(), viewModel.uiState.value)
        assertTrue(fixture.remote.vehicleRequests.isEmpty())
    }

    @Test fun savedKeyStartsLoadingAndLoadsContent() = runTest {
        val fixture = fixture(savedKey = "saved-key")
        fixture.remote.vehicleResults.add(CompletableDeferred(RemoteResult.Success(listOf(vehicle))))

        val viewModel = VehicleListViewModel(fixture.repository, StandardTestDispatcher(testScheduler))

        assertEquals(VehicleListUiState.Content(batch = null, refreshing = true), viewModel.uiState.value)
        runCurrent()
        val content = viewModel.uiState.value as VehicleListUiState.Content
        assertEquals(listOf(vehicle), content.batch?.vehicles)
        assertEquals(now, content.batch?.fetchedAt)
        assertFalse(content.refreshing)
        assertNull(content.error)
        assertEquals(1, fixture.remote.vehicleRequests.size)
    }

    @Test fun validKeyIsSavedThenLoadsContentOnce() = runTest {
        val fixture = fixture()
        fixture.remote.validationResults.add(CompletableDeferred(RemoteResult.Success(Unit)))
        fixture.remote.vehicleResults.add(CompletableDeferred(RemoteResult.Success(listOf(vehicle))))
        val viewModel = VehicleListViewModel(fixture.repository, StandardTestDispatcher(testScheduler))

        viewModel.submitKey(" candidate ")

        assertEquals(VehicleListUiState.NeedsKey(submitting = true), viewModel.uiState.value)
        runCurrent()
        assertEquals("candidate", fixture.credentials.read())
        assertEquals(1, fixture.remote.validatedKeys.size)
        assertEquals(1, fixture.remote.vehicleRequests.size)
        assertEquals(listOf(vehicle), (viewModel.uiState.value as VehicleListUiState.Content).batch?.vehicles)
    }

    @Test fun invalidKeyReturnsToNeedsKeyWithError() = runTest {
        val fixture = fixture()
        fixture.remote.validationResults.add(CompletableDeferred(RemoteResult.Failure(BusDataError.InvalidCredential)))
        val viewModel = VehicleListViewModel(fixture.repository, StandardTestDispatcher(testScheduler))

        viewModel.submitKey("wrong-key")
        runCurrent()

        assertEquals(
            VehicleListUiState.NeedsKey(error = BusDataError.InvalidCredential),
            viewModel.uiState.value,
        )
        assertNull(fixture.credentials.read())
        assertTrue(fixture.remote.vehicleRequests.isEmpty())
    }

    @Test fun failedRefreshRetainsContentAndExposesNonblockingError() = runTest {
        val fixture = fixture(savedKey = "saved-key")
        fixture.remote.vehicleResults.add(CompletableDeferred(RemoteResult.Success(listOf(vehicle))))
        fixture.remote.vehicleResults.add(CompletableDeferred(RemoteResult.Failure(BusDataError.NetworkUnavailable)))
        val viewModel = VehicleListViewModel(fixture.repository, StandardTestDispatcher(testScheduler))
        runCurrent()

        viewModel.refresh()
        runCurrent()

        val content = viewModel.uiState.value as VehicleListUiState.Content
        assertEquals(listOf(vehicle), content.batch?.vehicles)
        assertFalse(content.refreshing)
        assertEquals(BusDataError.NetworkUnavailable, content.error)
    }

    @Test fun failedInitialRefreshExposesContentWithoutBatch() = runTest {
        val fixture = fixture(savedKey = "saved-key")
        fixture.remote.vehicleResults.add(CompletableDeferred(RemoteResult.Failure(BusDataError.ServiceUnavailable)))
        val viewModel = VehicleListViewModel(fixture.repository, StandardTestDispatcher(testScheduler))

        runCurrent()

        assertEquals(
            VehicleListUiState.Content(
                batch = null,
                refreshing = false,
                error = BusDataError.ServiceUnavailable,
            ),
            viewModel.uiState.value,
        )
    }

    @Test fun successfulEmptyResponseIsContentWithEmptyBatch() = runTest {
        val fixture = fixture(savedKey = "saved-key")
        fixture.remote.vehicleResults.add(CompletableDeferred(RemoteResult.Success(emptyList())))
        val viewModel = VehicleListViewModel(fixture.repository, StandardTestDispatcher(testScheduler))

        runCurrent()

        val content = viewModel.uiState.value as VehicleListUiState.Content
        assertTrue(content.batch?.vehicles?.isEmpty() == true)
        assertFalse(content.refreshing)
        assertNull(content.error)
    }

    @Test fun duplicateRefreshWhileLoadingIsIgnored() = runTest {
        val fixture = fixture(savedKey = "saved-key")
        fixture.remote.vehicleResults.add(CompletableDeferred(RemoteResult.Success(listOf(vehicle))))
        val pending = CompletableDeferred<RemoteResult<List<VehicleSnapshot>>>()
        fixture.remote.vehicleResults.add(pending)
        val viewModel = VehicleListViewModel(fixture.repository, StandardTestDispatcher(testScheduler))
        runCurrent()

        viewModel.refresh()
        viewModel.refresh()
        runCurrent()

        assertEquals(2, fixture.remote.vehicleRequests.size)
        pending.complete(RemoteResult.Success(listOf(vehicle)))
        runCurrent()
    }

    @Test fun duplicateKeySubmissionWhileValidatingIsIgnored() = runTest {
        val fixture = fixture()
        val pending = CompletableDeferred<RemoteResult<Unit>>()
        fixture.remote.validationResults.add(pending)
        fixture.remote.vehicleResults.add(CompletableDeferred(RemoteResult.Success(emptyList())))
        val viewModel = VehicleListViewModel(fixture.repository, StandardTestDispatcher(testScheduler))

        viewModel.submitKey("first")
        viewModel.submitKey("second")
        runCurrent()

        assertEquals(listOf("first"), fixture.remote.validatedKeys)
        pending.complete(RemoteResult.Success(Unit))
        runCurrent()
    }

    @Test fun clearKeyCancelsInFlightEventAndReturnsToNeedsKey() = runTest {
        val fixture = fixture(savedKey = "saved-key")
        val pending = CompletableDeferred<RemoteResult<List<VehicleSnapshot>>>()
        fixture.remote.vehicleResults.add(pending)
        val viewModel = VehicleListViewModel(fixture.repository, StandardTestDispatcher(testScheduler))
        runCurrent()

        viewModel.clearKey()
        pending.complete(RemoteResult.Success(listOf(vehicle)))
        runCurrent()

        assertEquals(VehicleListUiState.NeedsKey(), viewModel.uiState.value)
        assertFalse(fixture.repository.savedKeyExists())
    }

    @Test fun clearKeyCancelsValidationBeforeCredentialCanBeSaved() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)
        try {
            val fixture = fixture()
            val pending = CompletableDeferred<RemoteResult<Unit>>()
            fixture.remote.validationResults.add(pending)
            val viewModel = VehicleListViewModel(fixture.repository)

            viewModel.submitKey("candidate")
            runCurrent()
            viewModel.clearKey()
            pending.complete(RemoteResult.Success(Unit))
            runCurrent()

            assertTrue(fixture.remote.validationStarted.isCompleted)
            assertEquals(Dispatchers.Main, fixture.remote.validationDispatcher)
            assertNull(fixture.credentials.read())
            assertEquals(VehicleListUiState.NeedsKey(), viewModel.uiState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun fixture(savedKey: String? = null): Fixture {
        val credentials = FakeCredentialStore(savedKey)
        val remote = FakeRemoteDataSource()
        return Fixture(
            credentials = credentials,
            remote = remote,
            repository = BusRepository(
                credentials = credentials,
                remote = remote,
                clock = Clock.fixed(now, ZoneOffset.UTC),
            ),
        )
    }

    private data class Fixture(
        val credentials: FakeCredentialStore,
        val remote: FakeRemoteDataSource,
        val repository: BusRepository,
    )

    private class FakeCredentialStore(initialValue: String?) : CredentialStore {
        private var value = initialValue

        override fun read(): String? = value
        override fun write(serviceKey: String) {
            value = serviceKey
        }
        override fun clear() {
            value = null
        }
    }

    private class FakeRemoteDataSource : DaeguBusRemoteDataSource {
        val validationResults = ArrayDeque<CompletableDeferred<RemoteResult<Unit>>>()
        val vehicleResults = ArrayDeque<CompletableDeferred<RemoteResult<List<VehicleSnapshot>>>>()
        val validatedKeys = mutableListOf<String>()
        val vehicleRequests = mutableListOf<Pair<String, String>>()
        val validationStarted = CompletableDeferred<Unit>()
        var validationDispatcher: Any? = null

        override suspend fun validateKey(serviceKey: String): RemoteResult<Unit> {
            validatedKeys += serviceKey
            validationDispatcher = currentCoroutineContext()[kotlin.coroutines.ContinuationInterceptor]
            validationStarted.complete(Unit)
            return validationResults.removeFirst().await()
        }

        override suspend fun vehicles(serviceKey: String, routeId: String): RemoteResult<List<VehicleSnapshot>> {
            vehicleRequests += serviceKey to routeId
            return vehicleResults.removeFirst().await()
        }
    }
}

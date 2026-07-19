package com.rafaam11.businfo.ui

import com.rafaam11.businfo.data.FavoriteStopRepository
import com.rafaam11.businfo.data.GroupedSearchResult
import com.rafaam11.businfo.data.SaveFavoriteResult
import com.rafaam11.businfo.data.StopSearchGateway
import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.NearbyStopResult
import com.rafaam11.businfo.domain.RouteDirectionKey
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.StopArrival
import com.rafaam11.businfo.domain.StopArrivalGroup
import com.rafaam11.businfo.domain.StopArrivalSnapshot
import com.rafaam11.businfo.domain.StopCatalogItem
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StopHomeViewModelTest {
    private val stop = StopCatalogItem("s1", "동대구역건너", 128.628, 35.880)

    @Test fun `search publishes grouped results without changing favorites`() = runTest {
        val favorites = FakeFavorites(listOf(favorite(stop)))
        val search = FakeSearch().apply { result = GroupedSearchResult(emptyList(), listOf(stop), emptyList()) }
        val viewModel = StopHomeViewModel(favorites, search, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.search("동대구")
        advanceUntilIdle()

        assertEquals(listOf(stop), viewModel.uiState.value.searchResult.stops)
        assertEquals(1, viewModel.uiState.value.favorites.size)
    }

    @Test fun `location denial keeps current search and favorite content`() = runTest {
        val favorites = FakeFavorites(listOf(favorite(stop)))
        val search = FakeSearch().apply { result = GroupedSearchResult(emptyList(), listOf(stop), emptyList()) }
        val viewModel = StopHomeViewModel(favorites, search, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        viewModel.search("동대구")
        advanceUntilIdle()

        val requestId = viewModel.beginNearby()
        viewModel.locationPermissionDenied(requestId)

        assertEquals("동대구", viewModel.uiState.value.query)
        assertEquals(listOf(stop), viewModel.uiState.value.searchResult.stops)
        assertFalse(viewModel.uiState.value.nearbyLoading)
        assertTrue(viewModel.uiState.value.message!!.contains("권한"))
    }

    @Test fun `nearby lookup exposes progress and honest unavailable message`() = runTest {
        val viewModel = StopHomeViewModel(FakeFavorites(emptyList()), FakeSearch(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        val requestId = viewModel.beginNearby()

        assertTrue(viewModel.uiState.value.nearbyLoading)
        assertEquals("현재 위치를 확인하는 중", viewModel.uiState.value.nearbyLoadingMessage)
        assertEquals(null, viewModel.uiState.value.nearby)

        viewModel.locationUnavailable(requestId)

        assertFalse(viewModel.uiState.value.nearbyLoading)
        assertTrue(viewModel.uiState.value.message!!.contains("현재 위치"))
        assertFalse(viewModel.uiState.value.message!!.contains("권한 없이도"))
    }

    @Test fun `adding searched stop creates a stable favorite`() = runTest {
        val favorites = FakeFavorites(emptyList())
        val viewModel = StopHomeViewModel(favorites, FakeSearch(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.addFavorite(stop)
        advanceUntilIdle()

        assertEquals("s1", favorites.values.single().stopId)
        assertFalse(favorites.values.single().id.value.isBlank())
    }

    @Test fun `cached arrival is visible immediately after process restart`() = runTest {
        val cached = StopArrivalSnapshot(
            stop.stopId,
            listOf(StopArrivalGroup(RouteDirectionKey("r1", "0"), "814", emptyList())),
            Instant.parse("2026-07-18T00:00:00Z"),
        )
        val search = FakeSearch().apply { cachedArrival = cached }

        val viewModel = StopHomeViewModel(
            FakeFavorites(listOf(favorite(stop))),
            search,
            StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()

        assertEquals(cached, viewModel.uiState.value.arrivals[stop.stopId])
    }

    @Test fun `catalog preparation can retry after api key onboarding`() = runTest {
        val search = FakeSearch().apply {
            ensureResult = Result.failure(IllegalStateException("missing key"))
        }
        val viewModel = StopHomeViewModel(FakeFavorites(emptyList()), search, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        assertEquals(1, search.ensureCalls)

        search.ensureResult = Result.success(Unit)
        viewModel.prepareCatalog()
        advanceUntilIdle()

        assertEquals(2, search.ensureCalls)
        assertEquals(null, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.catalogPreparing)
    }

    @Test fun `route can be pinned and unpinned for a favorite stop`() = runTest {
        val favorites = FakeFavorites(listOf(favorite(stop)))
        val viewModel = StopHomeViewModel(favorites, FakeSearch(), StandardTestDispatcher(testScheduler))
        val group = StopArrivalGroup(
            key = RouteDirectionKey("r1", "0"),
            routeNo = "814",
            arrivals = listOf(StopArrival("r1", "814", "0", 2, 180, null)),
        )
        advanceUntilIdle()

        viewModel.togglePinnedRoute(stop.stopId, group)
        advanceUntilIdle()

        assertEquals(listOf(group.key), favorites.values.single().pinnedRoutes.map { it.key })

        viewModel.togglePinnedRoute(stop.stopId, group)
        advanceUntilIdle()

        assertTrue(favorites.values.single().pinnedRoutes.isEmpty())
    }

    @Test fun `starting a search leaves nearby results`() = runTest {
        val viewModel = StopHomeViewModel(FakeFavorites(emptyList()), FakeSearch(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        viewModel.showNearby(GeoPoint(128.6, 35.8))
        advanceUntilIdle()

        viewModel.search("동대구")

        assertEquals(null, viewModel.uiState.value.nearby)
    }

    @Test fun `nearby result clears lookup progress`() = runTest {
        val viewModel = StopHomeViewModel(FakeFavorites(emptyList()), FakeSearch(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        viewModel.beginNearby()

        viewModel.showNearby(GeoPoint(128.6, 35.8))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.nearbyLoading)
        assertTrue(viewModel.uiState.value.nearby != null)
    }

    @Test fun `cancelled current location request cannot leave loading behind`() = runTest {
        val viewModel = StopHomeViewModel(FakeFavorites(emptyList()), FakeSearch(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        val requestId = viewModel.beginNearby()

        viewModel.cancelCurrentLocationRequest(requestId)

        assertFalse(viewModel.uiState.value.nearbyLoading)
        assertEquals(null, viewModel.uiState.value.nearbyLoadingMessage)
    }

    @Test fun `late current location cannot replace a selected place`() = runTest {
        val viewModel = StopHomeViewModel(FakeFavorites(emptyList()), FakeSearch(), StandardTestDispatcher(testScheduler))
        val place = GeoPoint(128.628, 35.880)
        advanceUntilIdle()
        val requestId = viewModel.beginNearby()

        viewModel.showNearby(place, "동대구역 주변 정류장")
        assertEquals("주변 정류장을 찾는 중", viewModel.uiState.value.nearbyLoadingMessage)
        viewModel.showNearbyFromCurrentLocation(requestId, GeoPoint(128.7, 35.9))
        advanceUntilIdle()

        assertEquals(place, viewModel.uiState.value.nearbyOrigin)
        assertEquals("동대구역 주변 정류장", viewModel.uiState.value.nearbyTitle)
    }

    @Test fun `late current location cannot replace a search started while locating`() = runTest {
        val viewModel = StopHomeViewModel(FakeFavorites(emptyList()), FakeSearch(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        val requestId = viewModel.beginNearby()

        viewModel.search("동대구")
        viewModel.showNearbyFromCurrentLocation(requestId, GeoPoint(128.6, 35.8))
        advanceUntilIdle()

        assertEquals("동대구", viewModel.uiState.value.query)
        assertEquals(null, viewModel.uiState.value.nearby)
        assertFalse(viewModel.uiState.value.nearbyLoading)
    }

    @Test fun `late route stop response cannot replace the currently selected route`() = runTest {
        val first = CompletableDeferred<Result<List<RouteStop>>>()
        val second = CompletableDeferred<Result<List<RouteStop>>>()
        var calls = 0
        val search = object : FakeSearch() {
            override suspend fun routeStops(routeId: String) = withContext(NonCancellable) {
                if (calls++ == 0) first.await() else second.await()
            }
        }
        val viewModel = StopHomeViewModel(FakeFavorites(emptyList()), search, StandardTestDispatcher(testScheduler))
        val routeOne = RouteSummary("r1", "814", "A", "B", null, null)
        val routeTwo = RouteSummary("r2", "급행1", "C", "D", null, null)
        val firstStops = listOf(RouteStop("r1", "s1", "첫 정류장", "0", 1, 128.6, 35.8))
        val secondStops = listOf(RouteStop("r2", "s2", "둘째 정류장", "0", 1, 128.7, 35.9))
        advanceUntilIdle()

        viewModel.selectRoute(routeOne)
        runCurrent()
        viewModel.selectRoute(routeTwo)
        runCurrent()
        second.complete(Result.success(secondStops))
        runCurrent()
        first.complete(Result.success(firstStops))
        runCurrent()

        assertEquals(routeTwo, viewModel.uiState.value.selectedRoute)
        assertEquals(secondStops, viewModel.uiState.value.routeStops)
    }

    private fun favorite(item: StopCatalogItem) = FavoriteStop(
        FavoriteStopId("favorite-${item.stopId}"), item.stopId, item.stopName,
        GeoPoint(item.longitude, item.latitude), 0,
    )

    private class FakeFavorites(initial: List<FavoriteStop>) : FavoriteStopRepository {
        val values = initial.toMutableList()
        private val flow = MutableStateFlow(values.toList())
        override fun observeFavorites(): Flow<List<FavoriteStop>> = flow
        override suspend fun favorite(id: FavoriteStopId) = values.firstOrNull { it.id == id }
        override suspend fun save(stop: FavoriteStop): SaveFavoriteResult {
            values.removeAll { it.id == stop.id }; values += stop; flow.value = values.toList()
            return SaveFavoriteResult.Saved
        }
        override suspend fun delete(id: FavoriteStopId) { values.removeAll { it.id == id }; flow.value = values.toList() }
    }

    private open class FakeSearch : StopSearchGateway {
        var result = GroupedSearchResult(emptyList(), emptyList(), emptyList())
        var cachedArrival: StopArrivalSnapshot? = null
        var ensureResult: Result<Unit> = Result.success(Unit)
        var ensureCalls = 0
        override suspend fun ensureStopCatalog(force: Boolean): Result<Unit> {
            ensureCalls++
            return ensureResult
        }
        override suspend fun search(rawQuery: String) = result
        override suspend fun refreshArrivals(stopId: String, force: Boolean) = Result.success(
            StopArrivalSnapshot(stopId, emptyList(), Instant.EPOCH),
        )
        override suspend fun cachedArrivals(stopId: String) = cachedArrival
        override suspend fun nearby(origin: GeoPoint): NearbyStopResult = NearbyStopResult(emptyList(), 500)
        override suspend fun routeStops(routeId: String): Result<List<RouteStop>> = Result.success(emptyList())
        override suspend fun todayApiCallCount() = 0
    }
}

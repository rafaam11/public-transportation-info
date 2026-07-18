package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.local.StopCenteredLocalDataSource
import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.StopArrivalSnapshot
import com.rafaam11.businfo.domain.StopCatalogItem
import com.rafaam11.businfo.domain.WidgetBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteStopRepositoryTest {
    @Test fun `twentieth favorite is accepted and twenty first is rejected`() = runTest {
        val local = FakeStopCenteredLocalDataSource((0 until 19).map(::favorite).toMutableList())
        val repository = DefaultFavoriteStopRepository(local)

        assertEquals(SaveFavoriteResult.Saved, repository.save(favorite(19)))
        assertEquals(SaveFavoriteResult.LimitReached, repository.save(favorite(20)))
        assertEquals(20, local.values.size)
    }

    @Test fun `same stop cannot be registered twice`() = runTest {
        val local = FakeStopCenteredLocalDataSource(mutableListOf(favorite(1)))
        val repository = DefaultFavoriteStopRepository(local)

        assertEquals(SaveFavoriteResult.AlreadyExists, repository.save(favorite(1).copy(id = FavoriteStopId("new-id"))))
        assertEquals(1, local.values.size)
    }

    private fun favorite(index: Int) = FavoriteStop(
        id = FavoriteStopId("favorite-$index"),
        stopId = "stop-$index",
        stopName = "정류장 $index",
        point = GeoPoint(128.6, 35.8),
        sortOrder = index,
    )

    private class FakeStopCenteredLocalDataSource(
        val values: MutableList<FavoriteStop>,
    ) : StopCenteredLocalDataSource {
        private val flow = MutableStateFlow(values.toList())
        override fun observeFavoriteStops(): Flow<List<FavoriteStop>> = flow
        override suspend fun favoriteStop(id: FavoriteStopId) = values.firstOrNull { it.id == id }
        override suspend fun favoriteStopByStopId(stopId: String) = values.firstOrNull { it.stopId == stopId }
        override suspend fun favoriteStopCount() = values.size
        override suspend fun saveFavoriteStop(stop: FavoriteStop) { values.removeAll { it.id == stop.id }; values += stop; flow.value = values.toList() }
        override suspend fun deleteFavoriteStop(id: FavoriteStopId) { values.removeAll { it.id == id }; flow.value = values.toList() }
        override suspend fun replaceStopCatalog(stops: List<StopCatalogItem>) = Unit
        override suspend fun stops() = emptyList<StopCatalogItem>()
        override suspend fun searchStops(query: String, limit: Int) = emptyList<StopCatalogItem>()
        override fun observeStopArrival(stopId: String): Flow<StopArrivalSnapshot?> = MutableStateFlow(null)
        override suspend fun stopArrival(stopId: String): StopArrivalSnapshot? = null
        override suspend fun saveStopArrival(snapshot: StopArrivalSnapshot) = Unit
        override fun observeWidgetBinding(appWidgetId: Int): Flow<WidgetBinding?> = MutableStateFlow(null)
        override suspend fun widgetBinding(appWidgetId: Int): WidgetBinding? = null
        override suspend fun saveWidgetBinding(binding: WidgetBinding) = Unit
        override suspend fun deleteWidgetBinding(appWidgetId: Int) = Unit
    }
}

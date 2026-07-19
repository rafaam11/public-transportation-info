package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.local.StopCenteredLocalDataSource
import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.PinnedRoute
import com.rafaam11.businfo.domain.RouteDirectionKey
import com.rafaam11.businfo.domain.StopArrivalSnapshot
import com.rafaam11.businfo.domain.StopCatalogItem
import com.rafaam11.businfo.domain.WidgetBinding
import java.time.Instant
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

    @Test fun `favorite mutations notify bound widgets`() = runTest {
        val local = FakeStopCenteredLocalDataSource(mutableListOf())
        var notifications = 0
        val repository = DefaultFavoriteStopRepository(local, onChanged = { notifications++ })

        repository.save(favorite(1))
        repository.remove(FavoriteStopId("favorite-1"))

        assertEquals(2, notifications)
    }

    @Test fun `removed favorite restores pinned routes and widget bindings`() = runTest {
        val favorite = favorite(1).copy(
            pinnedRoutes = listOf(
                PinnedRoute(
                    FavoriteStopId("favorite-1"),
                    RouteDirectionKey("route-814", "0"),
                    "814",
                    "정방향",
                    0,
                ),
            ),
        )
        val binding = WidgetBinding(41, favorite.id, Instant.parse("2026-07-19T00:00:00Z"))
        val local = FakeStopCenteredLocalDataSource(mutableListOf(favorite), mutableListOf(binding))
        val repository = DefaultFavoriteStopRepository(local)

        val removal = requireNotNull(repository.remove(favorite.id))

        assertEquals(emptyList<FavoriteStop>(), local.values)
        assertEquals(emptyList<WidgetBinding>(), local.bindings)

        repository.restore(removal)

        assertEquals(favorite, local.values.single())
        assertEquals(binding, local.bindings.single())
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
        val bindings: MutableList<WidgetBinding> = mutableListOf(),
    ) : StopCenteredLocalDataSource {
        private val flow = MutableStateFlow(values.toList())
        override fun observeFavoriteStops(): Flow<List<FavoriteStop>> = flow
        override suspend fun favoriteStop(id: FavoriteStopId) = values.firstOrNull { it.id == id }
        override suspend fun favoriteStopByStopId(stopId: String) = values.firstOrNull { it.stopId == stopId }
        override suspend fun favoriteStopCount() = values.size
        override suspend fun saveFavoriteStop(stop: FavoriteStop) { values.removeAll { it.id == stop.id }; values += stop; flow.value = values.toList() }
        override suspend fun removeFavoriteStop(id: FavoriteStopId): com.rafaam11.businfo.domain.FavoriteRemovalSnapshot? {
            val favorite = values.firstOrNull { it.id == id } ?: return null
            val removedBindings = bindings.filter { it.favoriteStopId == id }
            values.removeAll { it.id == id }
            bindings.removeAll { it.favoriteStopId == id }
            flow.value = values.toList()
            return com.rafaam11.businfo.domain.FavoriteRemovalSnapshot(favorite, removedBindings)
        }
        override suspend fun restoreFavoriteStop(snapshot: com.rafaam11.businfo.domain.FavoriteRemovalSnapshot) {
            values.removeAll { it.id == snapshot.favorite.id }
            values += snapshot.favorite
            snapshot.widgetBindings.forEach { binding ->
                bindings.removeAll { it.appWidgetId == binding.appWidgetId }
                bindings += binding
            }
            flow.value = values.toList()
        }
        override suspend fun replaceStopCatalog(stops: List<StopCatalogItem>) = Unit
        override suspend fun stops() = emptyList<StopCatalogItem>()
        override suspend fun searchStops(query: String, limit: Int) = emptyList<StopCatalogItem>()
        override fun observeStopArrival(stopId: String): Flow<StopArrivalSnapshot?> = MutableStateFlow(null)
        override suspend fun stopArrival(stopId: String): StopArrivalSnapshot? = null
        override suspend fun saveStopArrival(snapshot: StopArrivalSnapshot) = Unit
        override fun observeWidgetBinding(appWidgetId: Int): Flow<WidgetBinding?> = MutableStateFlow(null)
        override suspend fun widgetBinding(appWidgetId: Int): WidgetBinding? = bindings.firstOrNull { it.appWidgetId == appWidgetId }
        override suspend fun saveWidgetBinding(binding: WidgetBinding) {
            bindings.removeAll { it.appWidgetId == binding.appWidgetId }
            bindings += binding
        }
        override suspend fun deleteWidgetBinding(appWidgetId: Int) { bindings.removeAll { it.appWidgetId == appWidgetId } }
    }
}

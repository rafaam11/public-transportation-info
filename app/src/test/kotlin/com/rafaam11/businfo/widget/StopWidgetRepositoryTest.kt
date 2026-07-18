package com.rafaam11.businfo.widget

import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.RouteDirectionKey
import com.rafaam11.businfo.domain.StopArrival
import com.rafaam11.businfo.domain.StopArrivalGroup
import com.rafaam11.businfo.domain.StopArrivalSnapshot
import com.rafaam11.businfo.domain.WidgetBinding
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopWidgetRepositoryTest {
    private val now = Instant.parse("2026-07-18T10:00:00Z")
    private val favoriteId = FavoriteStopId("favorite-1")
    private val favorite = FavoriteStop(favoriteId, "stop-1", "동대구역건너", GeoPoint(128.62, 35.87), 0)
    private val snapshot = StopArrivalSnapshot("stop-1", listOf(
        group("r1", "814", 120), group("r2", "급행1", 60), group("r3", "401", 300),
    ), now)

    @Test fun `an early OEM render can be configured afterward without remaining stuck`() = runTest {
        val store = FakeStopWidgetStore(favorite, snapshot)
        val repository = StopWidgetRepository(store, { Result.success(snapshot) }, Clock.fixed(now, ZoneOffset.UTC))

        assertTrue(repository.state(42).requiresConfiguration)
        repository.bind(42, favoriteId)
        val configured = repository.state(42)

        assertFalse(configured.requiresConfiguration)
        assertEquals("동대구역건너", configured.stopName)
        assertEquals(listOf("급행1", "814", "401"), configured.routes.map { it.routeNo })
    }

    @Test fun `manual refresh uses bound stop and keeps its new snapshot`() = runTest {
        val store = FakeStopWidgetStore(favorite, snapshot).apply { binding = WidgetBinding(7, favoriteId, now) }
        var refreshedStop: String? = null
        val repository = StopWidgetRepository(
            store,
            { stopId -> refreshedStop = stopId; Result.success(snapshot.copy(fetchedAt = now.plusSeconds(5))) },
            Clock.fixed(now, ZoneOffset.UTC),
        )

        assertEquals(StopWidgetRefreshResult.Success, repository.refresh(7))
        assertEquals("stop-1", refreshedStop)
        assertEquals(now.plusSeconds(5), store.snapshot?.fetchedAt)
    }

    private fun group(routeId: String, routeNo: String, seconds: Int) = StopArrivalGroup(
        RouteDirectionKey(routeId, "0"), routeNo,
        listOf(StopArrival(routeId, routeNo, "0", 2, seconds, null)),
    )

    private class FakeStopWidgetStore(
        private val favorite: FavoriteStop,
        var snapshot: StopArrivalSnapshot?,
    ) : StopWidgetStore {
        var binding: WidgetBinding? = null
        override suspend fun binding(appWidgetId: Int) = binding
        override suspend fun favorite(id: FavoriteStopId) = favorite.takeIf { it.id == id }
        override suspend fun snapshot(stopId: String) = snapshot.takeIf { it?.stopId == stopId }
        override suspend fun saveBinding(value: WidgetBinding) { binding = value }
        override suspend fun saveSnapshot(value: StopArrivalSnapshot) { snapshot = value }
        override suspend fun deleteBinding(appWidgetId: Int) { if (binding?.appWidgetId == appWidgetId) binding = null }
    }
}

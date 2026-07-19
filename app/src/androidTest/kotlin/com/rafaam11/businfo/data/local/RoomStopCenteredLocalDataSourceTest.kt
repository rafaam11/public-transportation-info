package com.rafaam11.businfo.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.rafaam11.businfo.domain.FavoriteStop
import com.rafaam11.businfo.domain.FavoriteStopId
import com.rafaam11.businfo.domain.GeoPoint
import com.rafaam11.businfo.domain.PinnedRoute
import com.rafaam11.businfo.domain.RouteDirectionKey
import com.rafaam11.businfo.domain.WidgetBinding
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RoomStopCenteredLocalDataSourceTest {
    private lateinit var database: BusDatabase
    private lateinit var local: RoomStopCenteredLocalDataSource

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            BusDatabase::class.java,
        ).allowMainThreadQueries().build()
        local = RoomStopCenteredLocalDataSource(database.stopCenteredDao())
    }

    @After fun tearDown() = database.close()

    @Test fun removeAndRestorePreservesPinnedRoutesAndAllWidgetBindings() = runTest {
        val id = FavoriteStopId("favorite-1")
        val favorite = FavoriteStop(
            id,
            "stop-1",
            "동대구역건너",
            GeoPoint(128.62, 35.87),
            2,
            listOf(PinnedRoute(id, RouteDirectionKey("route-814", "0"), "814", "정방향", 0)),
        )
        val bindings = listOf(
            WidgetBinding(41, id, Instant.parse("2026-07-19T00:00:00Z")),
            WidgetBinding(42, id, Instant.parse("2026-07-19T00:01:00Z")),
        )
        local.saveFavoriteStop(favorite)
        bindings.forEach { local.saveWidgetBinding(it) }

        val removal = requireNotNull(local.removeFavoriteStop(id))

        assertNull(local.favoriteStop(id))
        assertNull(local.widgetBinding(41))
        assertEquals(bindings, removal.widgetBindings)

        local.restoreFavoriteStop(removal)

        assertEquals(favorite, local.favoriteStop(id))
        assertEquals(bindings[0], local.widgetBinding(41))
        assertEquals(bindings[1], local.widgetBinding(42))
    }
}

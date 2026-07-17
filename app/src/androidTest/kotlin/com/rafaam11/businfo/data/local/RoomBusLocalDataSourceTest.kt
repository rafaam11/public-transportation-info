package com.rafaam11.businfo.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.rafaam11.businfo.domain.ArrivalEstimate
import com.rafaam11.businfo.domain.ArrivalSnapshot
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteSelection
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomBusLocalDataSourceTest {
    private lateinit var database: BusDatabase
    private lateinit var local: RoomBusLocalDataSource

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            BusDatabase::class.java,
        ).allowMainThreadQueries().build()
        local = RoomBusLocalDataSource(database.dao())
    }

    @After fun tearDown() = database.close()

    @Test fun favoriteAndArrivalRoundTripThroughDashboardFlow() = runTest {
        val favorite = FavoriteSelection(
            CommuteSlot.MORNING, "route", "814", "0", "범물동 방면", "stop", "효동초등학교건너",
        )
        local.saveFavorite(favorite)
        local.saveArrival(
            CommuteSlot.MORNING,
            ArrivalSnapshot(listOf(ArrivalEstimate(2, 120, "2분", "0")), Instant.parse("2026-07-17T12:00:00Z")),
        )

        val dashboard = local.observeDashboard().first()

        assertEquals(favorite, dashboard.single().selection)
        assertEquals(120, dashboard.single().arrivals.single().arrivalSeconds)
    }

    @Test fun successfulEmptySnapshotReplacesPriorArrivals() = runTest {
        local.saveFavorite(FavoriteSelection(
            CommuteSlot.MORNING, "route", "814", "0", "범물동 방면", "stop", "정류장",
        ))
        local.saveArrival(CommuteSlot.MORNING, ArrivalSnapshot(listOf(ArrivalEstimate(2, 120, "2분")), Instant.EPOCH))
        local.saveArrival(CommuteSlot.MORNING, ArrivalSnapshot(emptyList(), Instant.EPOCH.plusSeconds(60)))

        val card = local.observeDashboard().first().single()
        assertTrue(card.arrivals.isEmpty())
        assertEquals(Instant.EPOCH.plusSeconds(60), card.fetchedAt)
    }
}

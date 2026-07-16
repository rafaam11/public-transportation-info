package com.rafaam11.businfo.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VehicleSnapshotTest {
    @Test fun snapshotContainsOnlyDisplaySafeFields() {
        val fields = VehicleSnapshot::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it.contains("vhc", ignoreCase = true) || it.contains("vehicleNo", ignoreCase = true) })
    }

    @Test fun batchSortsByDirectionThenSequence() {
        val fetchedAt = Instant.parse("2026-07-16T12:00:00Z")
        val items = listOf(
            VehicleSnapshot("3000814001", "814", "1", "B", 8, 35.8, 128.6, "soon", null, null),
            VehicleSnapshot("3000814001", "814", "0", "A", 4, 35.7, 128.5, "soon", null, null),
        )
        assertEquals(listOf("A", "B"), VehicleBatch.from(items, fetchedAt).vehicles.map { it.stopId })
    }
}

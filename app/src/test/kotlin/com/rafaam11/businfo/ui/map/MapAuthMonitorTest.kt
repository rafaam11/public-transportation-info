package com.rafaam11.businfo.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapAuthMonitorTest {
    @Test
    fun `map auth monitor exposes and clears the SDK code`() {
        val monitor = MapAuthMonitor()

        monitor.report("401")
        assertEquals("401", monitor.errorCode.value)

        monitor.clear()
        assertEquals(null, monitor.errorCode.value)
    }
}

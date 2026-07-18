package com.rafaam11.businfo.ui.map

import com.rafaam11.businfo.domain.StopCatalogItem
import org.junit.Assert.assertEquals
import org.junit.Test

class StopMapCameraTest {
    @Test fun `first camera is created from selected stop before map data arrives`() {
        val stop = StopCatalogItem("s1", "동대구역건너", 128.62792, 35.879612)

        val camera = initialStopCamera(stop)

        assertEquals(35.879612, camera.latitude, 0.0)
        assertEquals(128.62792, camera.longitude, 0.0)
        assertEquals(16.0, camera.zoom, 0.0)
    }
}

package com.rafaam11.businfo.ui.map

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaverMapOverlayContractTest {
    private val sourceRoot = File(System.getProperty("user.dir").orEmpty()).let { cwd ->
        if (File(cwd, "src/main").isDirectory) cwd else File(cwd, "app")
    }
    private val controller = File(
        sourceRoot,
        "src/main/kotlin/com/rafaam11/businfo/ui/map/NaverMapOverlayController.kt",
    ).readText()
    private val renderer = File(
        sourceRoot,
        "src/main/kotlin/com/rafaam11/businfo/ui/map/BusMarkerRenderer.kt",
    )

    @Test
    fun vehicleMarkersUseRenderedFlatHeadingAwareIconsWithoutSnappingCoordinates() {
        assertTrue(renderer.isFile)
        assertTrue(renderer.readText().contains("OverlayImage.fromBitmap"))
        assertTrue(controller.contains("marker.angle ="))
        assertTrue(controller.contains("marker.isFlat = true"))
        assertTrue(controller.contains("marker.anchor = PointF(0.5f, 0.5f)"))
        assertTrue(controller.contains("marker.position = vehicle.point.toLatLng()"))
        assertFalse(controller.contains("projectVehicle"))
        assertFalse(controller.contains("snapVehicle"))
    }

    @Test
    fun vehiclePoolKeepsSelectedLayeringAndHorizontalArrivalCaptions() {
        assertTrue(controller.contains("ensureVehicleMarkerCount"))
        assertTrue(controller.contains("selectedVehicleKey) 20 else 10"))
        assertTrue(controller.contains("setCaptionAligns(Align.Bottom)"))
        assertTrue(controller.contains("arrivalCaption(vehicle.remainingStops"))
        assertTrue(controller.contains("state.visibleVehicles.sortedBy"))
    }

    @Test
    fun stalePolicyStillRemovesEveryVehicleBeforeOverlayRendering() {
        val viewModel = File(
            sourceRoot,
            "src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapViewModel.kt",
        ).readText()
        assertTrue(Regex("if \\(freshness == DataFreshness\\.STALE\\) \\{\\s+emptyList\\(\\)").containsMatchIn(viewModel))
        assertTrue(controller.contains("ensureVehicleMarkerCount(state.visibleVehicles.size)"))
    }
}

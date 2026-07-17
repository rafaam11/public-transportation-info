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
    fun vehicleMarkersUseOnlySourceHeadingAndConfirmedCoordinates() {
        assertTrue(renderer.isFile)
        assertTrue(renderer.readText().contains("OverlayImage.fromBitmap"))
        assertTrue(controller.contains("vehicle.headingDegrees"))
        assertTrue(controller.contains("marker.alpha = if (vehicle.delayed)"))
        assertTrue(controller.contains("marker.isFlat = true"))
        assertTrue(controller.contains("marker.anchor = PointF(0.5f, 0.5f)"))
        assertTrue(controller.contains("marker.position = vehicle.point.toLatLng()"))
        assertFalse(controller.contains("projectVehicle"))
        assertFalse(controller.contains("snapVehicle"))
        assertFalse(controller.contains("VehicleHeadingResolver"))
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
    fun perVehicleStalePolicyRemovesPositionsBeforeOverlayRendering() {
        val viewModel = File(
            sourceRoot,
            "src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapViewModel.kt",
        ).readText()
        val uiState = File(
            sourceRoot,
            "src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapUiState.kt",
        ).readText()
        assertTrue(uiState.contains("PrecisePositionFreshness.HIDDEN"))
        assertTrue(viewModel.contains("publishPreciseVisibility"))
        assertTrue(controller.contains("ensureVehicleMarkerCount(state.visibleVehicles.size)"))
    }

    @Test
    fun sheetSeparatesOperatingAndPreciseCountsWithoutSignalClaims() {
        val screen = File(
            sourceRoot,
            "src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapScreen.kt",
        ).readText()
        assertTrue(screen.contains("전체 운행"))
        assertTrue(screen.contains("초정밀 위치"))
        assertFalse(screen.contains("신호등"))
        assertFalse(screen.contains("신호 정보"))
    }
}

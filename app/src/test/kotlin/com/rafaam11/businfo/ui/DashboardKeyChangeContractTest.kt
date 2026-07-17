package com.rafaam11.businfo.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardKeyChangeContractTest {
    private val sourceRoot = File(System.getProperty("user.dir").orEmpty()).let { cwd ->
        if (File(cwd, "src/main").isDirectory) cwd else File(cwd, "app")
    }

    @Test fun `dashboard key change action uses non destructive replacement mode`() {
        val app = File(sourceRoot, "src/main/kotlin/com/rafaam11/businfo/BusInfoApp.kt").readText()
        val screens = File(sourceRoot, "src/main/kotlin/com/rafaam11/businfo/ui/DashboardScreens.kt").readText()

        assertTrue(app.contains("onChangeKey = viewModel::beginKeyChange"))
        assertFalse(app.contains("onClearKey = viewModel::clearKey"))
        assertTrue(screens.contains("onChangeKey: () -> Unit"))
        assertTrue(screens.contains("TextButton(onClick = onChangeKey)"))
        assertFalse(screens.contains("onClearKey"))
    }
}

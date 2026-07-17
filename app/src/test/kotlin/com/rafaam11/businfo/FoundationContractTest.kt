package com.rafaam11.businfo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoundationContractTest {
    @Test
    fun releaseVersionIs040() {
        val repoRoot = File(requireNotNull(System.getProperty("user.dir"))).let { cwd ->
            if (File(cwd, "gradle/libs.versions.toml").isFile) cwd else requireNotNull(cwd.parentFile)
        }
        val appBuild = File(repoRoot, "app/build.gradle.kts").readText()

        assertTrue(appBuild.contains("versionCode = 4"))
        assertTrue(appBuild.contains("versionName = \"0.4.0\""))
    }

    @Test
    fun visualIdentityAndWidgetPackagingIsWired() {
        val repoRoot = File(requireNotNull(System.getProperty("user.dir"))).let { cwd ->
            if (File(cwd, "gradle/libs.versions.toml").isFile) cwd else requireNotNull(cwd.parentFile)
        }
        val palette = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/ui/map/RoutePalette.kt").readText()
        val renderer = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/ui/map/BusMarkerRenderer.kt").readText()
        val adaptiveIcon = File(repoRoot, "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        val manifest = File(repoRoot, "app/src/main/AndroidManifest.xml").readText()
        val provider = File(repoRoot, "app/src/main/res/xml/commute_widget_info.xml").readText()
        val receiver = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidgetReceiver.kt").readText()
        val widget = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidget.kt").readText()

        assertTrue(palette.contains("object RoutePaletteResolver"))
        assertTrue(palette.contains("0xFFFF4917"))
        assertTrue(renderer.contains("Canvas(bitmap)"))
        assertTrue(adaptiveIcon.contains("<adaptive-icon"))
        assertTrue(adaptiveIcon.contains("@drawable/ic_launcher_foreground"))
        assertTrue(manifest.contains("android:name=\".widget.CommuteWidgetReceiver\""))
        assertTrue(receiver.contains("GlanceAppWidgetReceiver"))
        assertTrue(receiver.contains("GlanceAppWidget = CommuteWidget()"))
        assertTrue(provider.contains("android:updatePeriodMillis=\"0\""))
        assertTrue(widget.contains("class CommuteWidget : GlanceAppWidget()"))
        assertTrue(widget.contains("MainActivity.EXTRA_OPEN_MAP_SLOT"))
    }

    @Test
    fun productionPackageIsStable() {
        assertEquals("com.rafaam11.businfo", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun credentialDraftAndBackupsStayInsideApprovedBoundary() {
        val sourceRoot = File(requireNotNull(System.getProperty("user.dir"))).let { cwd ->
            if (File(cwd, "src/main").isDirectory) cwd else File(cwd, "app")
        }
        val screen = File(sourceRoot, "src/main/kotlin/com/rafaam11/businfo/ui/DashboardScreens.kt").readText()
        val manifest = File(sourceRoot, "src/main/AndroidManifest.xml").readText()
        val legacyRules = File(sourceRoot, "src/main/res/xml/backup_rules.xml").takeIf(File::isFile)?.readText().orEmpty()
        val extractionRules = File(sourceRoot, "src/main/res/xml/data_extraction_rules.xml").takeIf(File::isFile)?.readText().orEmpty()

        assertFalse(screen.contains("rememberSaveable"))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(legacyRules.contains("domain=\"sharedpref\" path=\"credentials.xml\""))
        val credentialExclusion = Regex.escape("domain=\"sharedpref\" path=\"credentials.xml\"").toRegex()
        assertEquals(2, credentialExclusion.findAll(extractionRules).count())
    }

    @Test
    fun composeInstrumentationHasBomManagedTestActivityHost() {
        val repoRoot = File(requireNotNull(System.getProperty("user.dir"))).let { cwd ->
            if (File(cwd, "gradle/libs.versions.toml").isFile) cwd else requireNotNull(cwd.parentFile)
        }
        val catalog = File(repoRoot, "gradle/libs.versions.toml").readText()
        val appBuild = File(repoRoot, "app/build.gradle.kts").readText()

        assertTrue(catalog.contains("compose-ui-test-manifest = { module = \"androidx.compose.ui:ui-test-manifest\" }"))
        assertTrue(appBuild.contains("debugImplementation(libs.compose.ui.test.manifest)"))
    }

    @Test
    fun naverMapKeyIsInjectedWithoutACommittedLiteral() {
        val repoRoot = File(requireNotNull(System.getProperty("user.dir"))).let { cwd ->
            if (File(cwd, "gradle/libs.versions.toml").isFile) cwd else requireNotNull(cwd.parentFile)
        }
        val catalog = File(repoRoot, "gradle/libs.versions.toml").readText()
        val appBuild = File(repoRoot, "app/build.gradle.kts").readText()
        val manifest = File(repoRoot, "app/src/main/AndroidManifest.xml").readText()

        assertTrue(catalog.contains("naverMap = \"3.23.3\""))
        assertTrue(catalog.contains("naver-map = { module = \"com.naver.maps:map-sdk\""))
        assertTrue(appBuild.contains("NAVER_MAP_NCP_KEY_ID"))
        assertTrue(appBuild.contains("implementation(libs.naver.map)"))
        assertTrue(manifest.contains("android:name=\"com.naver.maps.map.NCP_KEY_ID\""))
        assertTrue(manifest.contains("android:value=\"\${naverMapNcpKeyId}\""))
        assertFalse(manifest.contains("YOUR_NCP_KEY"))
        assertFalse(manifest.contains("android.permission.ACCESS_FINE_LOCATION"))
        assertFalse(manifest.contains("android.permission.ACCESS_COARSE_LOCATION"))
    }

    @Test
    fun dashboardNavigationTargetsRealtimeMap() {
        val repoRoot = File(requireNotNull(System.getProperty("user.dir"))).let { cwd ->
            if (File(cwd, "gradle/libs.versions.toml").isFile) cwd else requireNotNull(cwd.parentFile)
        }
        val app = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/BusInfoApp.kt").readText()
        val dashboardViewModel = File(
            repoRoot,
            "app/src/main/kotlin/com/rafaam11/businfo/ui/BusAppViewModel.kt",
        ).readText()

        assertTrue(app.contains("map/{slot}"))
        assertFalse(app.contains("detail/{slot}"))
        assertTrue(app.contains("realtimeMapViewModel.setVisible"))
        assertFalse(dashboardViewModel.contains("detailState"))
        assertFalse(dashboardViewModel.contains("loadDetail"))
    }

    @Test
    fun appGraphSurvivesActivityRecreation() {
        val repoRoot = File(requireNotNull(System.getProperty("user.dir"))).let { cwd ->
            if (File(cwd, "gradle/libs.versions.toml").isFile) cwd else requireNotNull(cwd.parentFile)
        }
        val graph = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/AppGraph.kt").readText()
        val activity = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/MainActivity.kt").readText()

        assertTrue(graph.contains("@Volatile"))
        assertTrue(graph.contains("fun get(context: Context): AppGraph"))
        assertTrue(activity.contains("AppGraph.get(applicationContext)"))
        assertFalse(activity.contains("AppGraph(applicationContext)"))
    }
}

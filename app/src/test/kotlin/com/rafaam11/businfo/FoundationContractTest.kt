package com.rafaam11.businfo

import com.rafaam11.businfo.ui.map.RoutePalette
import com.rafaam11.businfo.ui.map.RoutePaletteResolver
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class FoundationContractTest {
    @Test
    fun releaseVersionIs040() {
        val repoRoot = File(requireNotNull(System.getProperty("user.dir"))).let { cwd ->
            if (File(cwd, "gradle/libs.versions.toml").isFile) cwd else requireNotNull(cwd.parentFile)
        }
        val appBuild = File(repoRoot, "app/build.gradle.kts").readText()

        assertTrue(Regex("(?m)^\\s*versionCode\\s*=\\s*4\\s*$").containsMatchIn(appBuild))
        assertTrue(Regex("(?m)^\\s*versionName\\s*=\\s*\"0\\.4\\.0\"\\s*$").containsMatchIn(appBuild))
    }

    @Test
    fun visualIdentityAndWidgetPackagingIsWired() {
        val repoRoot = File(requireNotNull(System.getProperty("user.dir"))).let { cwd ->
            if (File(cwd, "gradle/libs.versions.toml").isFile) cwd else requireNotNull(cwd.parentFile)
        }
        val renderer = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/ui/map/BusMarkerRenderer.kt").readText()
        val adaptiveIcon = File(repoRoot, "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(repoRoot, "app/src/main/AndroidManifest.xml"))
        val provider = File(repoRoot, "app/src/main/res/xml/commute_widget_info.xml").readText()
        val receiver = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidgetReceiver.kt").readText()
        val widget = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidget.kt").readText()

        assertEquals(RoutePalette(0xFFFF4917.toInt(), 0xFFFFFFFF.toInt()), RoutePaletteResolver.resolve("1"))
        assertEquals(RoutePalette(0xFF5BD338.toInt(), 0xFF131313.toInt()), RoutePaletteResolver.resolve("2"))
        assertEquals(RoutePalette(0xFF2C78CF.toInt(), 0xFFFFFFFF.toInt()), RoutePaletteResolver.resolve("3"))
        assertEquals(RoutePalette(0xFFFFC000.toInt(), 0xFF131313.toInt()), RoutePaletteResolver.resolve("4"))
        assertEquals(RoutePalette(0xFF4330D6.toInt(), 0xFFFFFFFF.toInt()), RoutePaletteResolver.resolve("5"))
        assertEquals(RoutePalette(0xFF4330D6.toInt(), 0xFFFFFFFF.toInt()), RoutePaletteResolver.resolve("6"))
        assertEquals(RoutePalette(0xFF4330D6.toInt(), 0xFFFFFFFF.toInt()), RoutePaletteResolver.resolve("7"))
        assertEquals(RoutePalette(0xFF306FD9.toInt(), 0xFFFFFFFF.toInt()), RoutePaletteResolver.resolve(null))
        assertTrue(Regex("val\\s+canvas\\s*=\\s*Canvas\\(bitmap\\)").containsMatchIn(renderer))
        assertTrue(adaptiveIcon.contains("<adaptive-icon"))
        assertTrue(adaptiveIcon.contains("@drawable/ic_launcher_foreground"))

        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val receivers = manifest.getElementsByTagName("receiver")
        val commuteReceiver = (0 until receivers.length)
            .map { receivers.item(it) as Element }
            .single { it.getAttributeNS(androidNamespace, "name") == ".widget.CommuteWidgetReceiver" }
        val actions = commuteReceiver.getElementsByTagName("action")
        assertTrue((0 until actions.length).any {
            (actions.item(it) as Element).getAttributeNS(androidNamespace, "name") ==
                "android.appwidget.action.APPWIDGET_UPDATE"
        })
        val metadata = commuteReceiver.getElementsByTagName("meta-data")
        assertTrue((0 until metadata.length).any {
            val element = metadata.item(it) as Element
            element.getAttributeNS(androidNamespace, "name") == "android.appwidget.provider" &&
                element.getAttributeNS(androidNamespace, "resource") == "@xml/commute_widget_info"
        })
        assertTrue(receiver.contains("GlanceAppWidgetReceiver"))
        assertTrue(receiver.contains("GlanceAppWidget = CommuteWidget()"))
        assertTrue(provider.contains("android:updatePeriodMillis=\"0\""))

        val compactContent = functionBody(widget, "private fun CompactCommuteWidgetContent")
        val expandedContent = functionBody(widget, "private fun ExpandedCommuteWidgetContent")
        val cardModifier = functionBody(widget, "private fun widgetCardModifier")
        val openActionSource = widget.substringAfter("class OpenCommuteMapAction")
        val openAction = functionBody(openActionSource, "override suspend fun onAction")
        assertTrue(compactContent.contains("Column(widgetCardModifier(state"))
        assertTrue(expandedContent.contains("Column(widgetCardModifier(state"))
        assertTrue(cardModifier.contains("actionRunCallback<OpenCommuteMapAction>"))
        assertTrue(cardModifier.contains("OpenCommuteMapAction.slotKey to slot.name"))
        assertTrue(cardModifier.contains("modifier::clickable"))
        assertTrue(openAction.contains("val slot = parameters[slotKey] ?: return"))
        assertTrue(openAction.contains("Intent(context, MainActivity::class.java)"))
        assertTrue(openAction.contains(".putExtra(MainActivity.EXTRA_OPEN_MAP_SLOT, slot)"))
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

    private fun functionBody(source: String, signature: String): String {
        val signatureStart = source.indexOf(signature)
        require(signatureStart >= 0) { "Missing function signature: $signature" }
        val bodyStart = source.indexOf('{', signatureStart)
        require(bodyStart >= 0) { "Missing function body: $signature" }
        var depth = 0
        for (index in bodyStart until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(bodyStart + 1, index)
                }
            }
        }
        error("Unclosed function body: $signature")
    }
}

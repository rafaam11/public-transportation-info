package com.rafaam11.businfo.widget

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommuteWidgetContractTest {
    private val repoRoot = File(requireNotNull(System.getProperty("user.dir"))).let { cwd ->
        if (File(cwd, "gradle/libs.versions.toml").isFile) cwd else requireNotNull(cwd.parentFile)
    }

    @Test
    fun manifestDeclaresLauncherConfigurationAndPrivateReceiver() {
        val manifest = File(repoRoot, "app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:name=\".widget.CommuteWidgetConfigurationActivity\""))
        assertTrue(manifest.contains("android:name=\".widget.CommuteWidgetReceiver\""))
        assertTrue(manifest.contains("android.appwidget.action.APPWIDGET_UPDATE"))
        assertTrue(Regex("CommuteWidgetConfigurationActivity[\\s\\S]*?android:exported=\"true\"").containsMatchIn(manifest))
        assertTrue(Regex("CommuteWidgetReceiver[\\s\\S]*?android:exported=\"false\"").containsMatchIn(manifest))
        assertFalse(manifest.contains("android.permission.BIND_APPWIDGET"))
    }

    @Test
    fun providerDisablesPeriodicWorkAndSupportsConfigurationAndResize() {
        val provider = File(repoRoot, "app/src/main/res/xml/commute_widget_info.xml").readText()

        assertTrue(provider.contains("android:updatePeriodMillis=\"0\""))
        assertTrue(provider.contains("android:configure=\"com.rafaam11.businfo.widget.CommuteWidgetConfigurationActivity\""))
        assertTrue(provider.contains("android:resizeMode=\"horizontal|vertical\""))
        assertTrue(provider.contains("android:widgetCategory=\"home_screen\""))
        assertTrue(provider.contains("android:targetCellWidth=\"3\""))
        assertTrue(provider.contains("android:targetCellHeight=\"2\""))
    }

    @Test
    fun activityDeclaresAndConsumesWidgetNavigationExtras() {
        val activity = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/MainActivity.kt").readText()
        val app = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/BusInfoApp.kt").readText()

        assertTrue(activity.contains("EXTRA_OPEN_MAP_SLOT"))
        assertTrue(activity.contains("EXTRA_OPEN_KEY_SETTINGS"))
        assertTrue(activity.contains("onNewIntent"))
        assertTrue(activity.contains("intent.removeExtra(EXTRA_OPEN_MAP_SLOT)"))
        assertTrue(activity.contains("intent.removeExtra(EXTRA_OPEN_KEY_SETTINGS)"))
        assertTrue(app.contains("nav.navigate(\"map/${'$'}{slot.name}\")"))
        assertTrue(app.contains("onOpenMapSlotConsumed"))
    }

    @Test
    fun widgetHasDistinctCompactAndExpandedResponsiveLayouts() {
        val widget = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidget.kt").readText()

        assertTrue(widget.contains("DpSize(180.dp, 110.dp)"))
        assertTrue(widget.contains("DpSize(300.dp, 180.dp)"))
        assertTrue(widget.contains("LocalSize.current"))
        assertTrue(widget.contains("CompactCommuteWidgetContent"))
        assertTrue(widget.contains("ExpandedCommuteWidgetContent"))
    }

    @Test
    fun widgetUsesGlanceIdsAndExactRepositoryBoundaries() {
        val widget = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidget.kt").readText()
        val notifier = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidgetUpdateNotifier.kt").readText()
        val receiver = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidgetReceiver.kt").readText()

        assertTrue(widget.contains("GlanceAppWidgetManager(context).getAppWidgetId(glanceId)"))
        assertFalse(widget.contains("glanceId as"))
        assertTrue(widget.contains("commuteWidgetRepository.refresh(appWidgetId)"))
        assertTrue(widget.contains("API 키 변경"))
        assertTrue(widget.contains("카드를 다시 설정해 주세요"))
        assertTrue(notifier.contains("CommuteWidget().updateAll(applicationContext)"))
        assertTrue(receiver.contains("appWidgetIds.forEach(repository::clear)"))
    }
}

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
    fun configurationRejectsIdsNotOwnedByThisWidgetProvider() {
        val configuration = File(
            repoRoot,
            "app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidgetConfigurationActivity.kt",
        ).readText()

        assertTrue(configuration.contains("getAppWidgetInfo(id)?.provider"))
        assertTrue(configuration.contains("ComponentName(this, CommuteWidgetReceiver::class.java)"))
        assertTrue(configuration.contains("setResult(Activity.RESULT_CANCELED)"))
        assertTrue(configuration.contains("if (!ownership.isOwned())"))
        assertTrue(configuration.contains("if (!ownership.runIfOwned { configurationViewModel.choose(favorite) })"))
        assertTrue(configuration.contains("private fun completeConfiguration()"))
        assertTrue(configuration.contains("if (!ownership.isOwned())"))
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
    fun widgetDeepLinkUsesFavoriteStopIdAndIsConsumedOnce() {
        val activity = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/MainActivity.kt").readText()
        val app = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/BusInfoApp.kt").readText()
        val trampoline = File(
            repoRoot,
            "app/src/main/kotlin/com/rafaam11/businfo/widget/WidgetKeySettingsActivity.kt",
        )

        assertTrue(activity.contains("EXTRA_OPEN_FAVORITE_STOP_ID"))
        assertFalse(activity.contains("EXTRA_OPEN_KEY_SETTINGS"))
        assertTrue(activity.contains("keySettingsRequests.consume()"))
        assertTrue(activity.contains("onNewIntent"))
        assertTrue(activity.contains("intent.removeExtra(EXTRA_OPEN_FAVORITE_STOP_ID)"))
        assertTrue(trampoline.isFile)
        assertTrue(trampoline.readText().contains("keySettingsRequests.request()"))
        assertTrue(app.contains("nav.navigate(\"stop\")"))
        assertTrue(app.contains("onOpenFavoriteStopConsumed"))
    }

    @Test
    fun keySettingsTrampolineIsNotExported() {
        val manifest = File(repoRoot, "app/src/main/AndroidManifest.xml").readText()

        assertTrue(Regex("WidgetKeySettingsActivity[\\s\\S]*?android:exported=\"false\"").containsMatchIn(manifest))
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

        assertTrue(widget.contains("GlanceAppWidgetManager(context).getAppWidgetId(id)"))
        assertFalse(widget.contains("glanceId as"))
        assertTrue(widget.contains("stopWidgetRepository.refresh(appWidgetId)"))
        assertFalse(widget.contains("EXTRA_OPEN_KEY_SETTINGS"))
        assertTrue(widget.contains("정류장을 설정해 주세요"))
        assertTrue(notifier.contains("CommuteWidget().updateAll(applicationContext)"))
        assertTrue(receiver.contains("appWidgetIds.forEach { repository.clear(it) }"))
        assertTrue(receiver.contains("val pendingResult = goAsync()"))
        assertTrue(receiver.contains("pendingResult.finish()"))
    }

    @Test
    fun configurationPersistsBindingBeforeImmediateUpdateAndUniqueBootstrap() {
        val configuration = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidgetConfigurationActivity.kt").readText()
        val worker = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/widget/StopWidgetBootstrapWorker.kt").readText()

        assertTrue(configuration.indexOf("stopWidgetRepository.bind") < configuration.indexOf("CommuteWidget().update"))
        assertTrue(configuration.contains("EXTRA_APPWIDGET_ID"))
        assertTrue(configuration.contains("StopWidgetBootstrapWorker.enqueue"))
        assertTrue(worker.contains("enqueueUniqueWork"))
        assertTrue(worker.contains("stop-widget-bootstrap-${'$'}appWidgetId"))
        assertTrue(worker.contains("ExistingWorkPolicy.REPLACE"))
    }
}

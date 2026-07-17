package com.rafaam11.businfo.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.rafaam11.businfo.data.DashboardUpdateNotifier

/** Notifies the Glance receiver without taking a compile-time dependency on Task 6's widget UI. */
class CommuteWidgetUpdateNotifier(context: Context) : DashboardUpdateNotifier {
    private val applicationContext = context.applicationContext

    override suspend fun notifyChanged() {
        val receiver = ComponentName(
            applicationContext.packageName,
            "${applicationContext.packageName}.widget.CommuteWidgetReceiver",
        )
        val ids = AppWidgetManager.getInstance(applicationContext).getAppWidgetIds(receiver)
        if (ids.isEmpty()) return
        applicationContext.sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(receiver)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
        )
    }
}

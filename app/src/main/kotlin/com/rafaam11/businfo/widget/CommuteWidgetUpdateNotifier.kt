package com.rafaam11.businfo.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.rafaam11.businfo.data.DashboardUpdateNotifier
import com.rafaam11.businfo.data.StopArrivalUpdateNotifier

class CommuteWidgetUpdateNotifier(context: Context) : DashboardUpdateNotifier, StopArrivalUpdateNotifier {
    private val applicationContext = context.applicationContext

    override suspend fun notifyChanged() {
        CommuteWidget().updateAll(applicationContext)
    }
}

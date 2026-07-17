package com.rafaam11.businfo.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.rafaam11.businfo.AppGraph

class CommuteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CommuteWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val repository = AppGraph.get(context).commuteWidgetRepository
        appWidgetIds.forEach(repository::clear)
        super.onDeleted(context, appWidgetIds)
    }
}

package com.rafaam11.businfo.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rafaam11.businfo.AppGraph

class StopWidgetBootstrapWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val appWidgetId = inputData.getInt(APP_WIDGET_ID, -1)
        if (appWidgetId < 0) return Result.failure()
        AppGraph.get(applicationContext).stopWidgetRepository.refresh(appWidgetId)
        runCatching {
            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
            CommuteWidget().update(applicationContext, glanceId)
        }
        return Result.success()
    }

    companion object {
        private const val APP_WIDGET_ID = "app-widget-id"

        fun enqueue(context: Context, appWidgetId: Int) {
            val request = OneTimeWorkRequestBuilder<StopWidgetBootstrapWorker>()
                .setInputData(workDataOf(APP_WIDGET_ID to appWidgetId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "stop-widget-bootstrap-$appWidgetId",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

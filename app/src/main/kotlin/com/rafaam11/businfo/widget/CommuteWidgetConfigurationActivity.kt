package com.rafaam11.businfo.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.rafaam11.businfo.AppGraph
import com.rafaam11.businfo.R
import com.rafaam11.businfo.domain.CommuteSlot
import kotlinx.coroutines.launch

class CommuteWidgetConfigurationActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        val owner = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        if (
            appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            owner != ComponentName(this, CommuteWidgetReceiver::class.java)
        ) {
            finish()
            return
        }
        setContent { MaterialTheme { ConfigurationContent(::choose) } }
    }

    private fun choose(slot: CommuteSlot) {
        AppGraph.get(applicationContext).widgetPreferences.saveSlot(appWidgetId, slot)
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
            CommuteWidget().update(applicationContext, glanceId)
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}

@Composable
private fun ConfigurationContent(onChoose: (CommuteSlot) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = androidx.compose.ui.res.stringResource(R.string.commute_widget_configure_title))
        Button(onClick = { onChoose(CommuteSlot.MORNING) }) { Text(CommuteSlot.MORNING.label) }
        Button(onClick = { onChoose(CommuteSlot.EVENING) }) { Text(CommuteSlot.EVENING.label) }
    }
}

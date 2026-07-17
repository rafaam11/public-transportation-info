package com.rafaam11.businfo.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
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
    private lateinit var ownership: WidgetOwnershipGuard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        ownership = WidgetOwnershipGuard(appWidgetId, ::isOwnedWidgetId)
        if (!ownership.isOwned()) {
            finish()
            return
        }
        setContent { MaterialTheme { ConfigurationContent(::choose) } }
    }

    private fun choose(slot: CommuteSlot) {
        if (!ownership.runIfOwned { persistChoice(slot) }) finish()
    }

    private fun persistChoice(slot: CommuteSlot) {
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

    private fun isOwnedWidgetId(id: Int): Boolean =
        id != AppWidgetManager.INVALID_APPWIDGET_ID &&
            AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider ==
            ComponentName(this, CommuteWidgetReceiver::class.java)
}

internal class WidgetOwnershipGuard(
    private val appWidgetId: Int,
    private val isOwnedWidgetId: (Int) -> Boolean,
) {
    fun isOwned(): Boolean = isOwnedWidgetId(appWidgetId)

    fun runIfOwned(action: () -> Unit): Boolean {
        if (!isOwned()) return false
        action()
        return true
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

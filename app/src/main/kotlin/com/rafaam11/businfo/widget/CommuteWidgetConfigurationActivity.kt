package com.rafaam11.businfo.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.rafaam11.businfo.AppGraph
import com.rafaam11.businfo.domain.FavoriteStop
import kotlinx.coroutines.launch

class CommuteWidgetConfigurationActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var ownership: WidgetOwnershipGuard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        ownership = WidgetOwnershipGuard(appWidgetId, ::isOwnedWidgetId)
        if (!ownership.isOwned()) {
            finish()
            return
        }
        val favorites = AppGraph.get(applicationContext).favoriteStopRepository.observeFavorites()
        setContent {
            MaterialTheme {
                val values by favorites.collectAsState(initial = emptyList())
                ConfigurationContent(values, ::choose)
            }
        }
    }

    private fun choose(favorite: FavoriteStop) {
        if (!ownership.runIfOwned { persistChoice(favorite) }) finish()
    }

    private fun persistChoice(favorite: FavoriteStop) {
        lifecycleScope.launch {
            val graph = AppGraph.get(applicationContext)
            graph.stopWidgetRepository.bind(appWidgetId, favorite.id)
            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, result)
            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
            CommuteWidget().update(applicationContext, glanceId)
            StopWidgetBootstrapWorker.enqueue(applicationContext, appWidgetId)
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
private fun ConfigurationContent(favorites: List<FavoriteStop>, onChoose: (FavoriteStop) -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("위젯 정류장 선택", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("즐겨찾는 정류장 하나를 연결합니다. 작은 위젯은 2개, 넓은 위젯은 4개 노선을 표시합니다.")
        }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (favorites.isEmpty()) item {
                Text("앱에서 정류장을 먼저 즐겨찾기에 저장해 주세요", color = MaterialTheme.colorScheme.error)
            }
            items(favorites, key = { it.id.value }) { favorite ->
                Card(
                    Modifier.fillMaxWidth().clickable { onChoose(favorite) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F5F7)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(favorite.stopName, fontWeight = FontWeight.Bold)
                        Text(favorite.stopId, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

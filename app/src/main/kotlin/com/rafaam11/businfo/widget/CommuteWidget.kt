package com.rafaam11.businfo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.rafaam11.businfo.AppGraph
import com.rafaam11.businfo.MainActivity
import java.time.Duration
import java.time.Instant

class CommuteWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(180.dp, 110.dp), DpSize(300.dp, 180.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val state = AppGraph.get(context).stopWidgetRepository.state(appWidgetId)
        provideContent { CommuteWidgetContent(context, state) }
    }
}

@Composable
private fun CommuteWidgetContent(context: Context, state: StopWidgetUiState) {
    if (LocalSize.current.height < 150.dp) CompactCommuteWidgetContent(context, state)
    else ExpandedCommuteWidgetContent(context, state)
}

@Composable
private fun CompactCommuteWidgetContent(context: Context, state: StopWidgetUiState) {
    Column(widgetCardModifier(context, state, 8.dp, 14.dp)) {
        WidgetHeader(state, 11)
        Spacer(GlanceModifier.height(5.dp))
        if (state.requiresConfiguration) {
            ConfigurationPrompt(context, state)
            return@Column
        }
        state.routes.take(2).forEach { WidgetRouteRow(it, compact = true) }
        if (state.routes.isEmpty()) Text("현재 도착 예정 버스 없음", style = mutedStyle(10))
        Spacer(GlanceModifier.defaultWeight())
        WidgetFooter(state)
    }
}

@Composable
private fun ExpandedCommuteWidgetContent(context: Context, state: StopWidgetUiState) {
    Column(widgetCardModifier(context, state, 14.dp, 18.dp)) {
        WidgetHeader(state, 14)
        Spacer(GlanceModifier.height(8.dp))
        if (state.requiresConfiguration) {
            ConfigurationPrompt(context, state)
            return@Column
        }
        state.routes.take(4).forEach { WidgetRouteRow(it, compact = false) }
        if (state.routes.isEmpty()) Text("현재 도착 예정 버스가 없습니다", style = mutedStyle(12))
        Spacer(GlanceModifier.defaultWeight())
        WidgetFooter(state)
    }
}

@Composable
private fun WidgetHeader(state: StopWidgetUiState, fontSize: Int) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            state.stopName ?: "대구 버스 정류장",
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = ink(), fontSize = fontSize.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(stopWidgetStatus(state, Instant.now()), style = mutedStyle((fontSize - 2).coerceAtLeast(8)))
    }
}

@Composable
private fun WidgetRouteRow(route: StopWidgetRouteUi, compact: Boolean) {
    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            route.routeNo,
            modifier = GlanceModifier.background(day = Color(0xFF1557C0), night = Color(0xFF1557C0)).cornerRadius(8.dp)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            style = TextStyle(color = ColorProvider(Color.White), fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            "  ${route.direction}",
            modifier = GlanceModifier.defaultWeight(),
            style = mutedStyle(if (compact) 9 else 11),
            maxLines = 1,
        )
        Text(
            route.arrivalText,
            style = TextStyle(color = ink(), fontSize = if (compact) 12.sp else 15.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun WidgetFooter(state: StopWidgetUiState) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            state.fetchedAt?.let { elapsedLabel(it.toEpochMilli(), Instant.now()) } ?: "조회 전",
            modifier = GlanceModifier.defaultWeight(),
            style = mutedStyle(9),
        )
        Text(
            if (state.isRefreshing) "새로고침 중…" else "새로고침",
            modifier = GlanceModifier.clickable(actionRunCallback<RefreshStopWidgetAction>()),
            style = TextStyle(color = ColorProvider(Color(0xFF1557C0)), fontSize = 10.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun ConfigurationPrompt(context: Context, state: StopWidgetUiState) {
    Text("정류장을 설정해 주세요", style = TextStyle(color = ink(), fontSize = 13.sp, fontWeight = FontWeight.Bold))
    Spacer(GlanceModifier.height(8.dp))
    Text(
        "설정",
        modifier = GlanceModifier.clickable(configurationAction(context, state.appWidgetId)),
        style = TextStyle(color = ColorProvider(Color(0xFF1557C0)), fontSize = 11.sp, fontWeight = FontWeight.Bold),
    )
}

private fun widgetCardModifier(
    context: Context,
    state: StopWidgetUiState,
    padding: Dp,
    cornerRadius: Dp,
): GlanceModifier {
    val open = state.favoriteStopId?.let { favoriteId ->
        actionStartActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_FAVORITE_STOP_ID, favoriteId.value),
        )
    }
    return GlanceModifier.fillMaxSize()
        .background(day = Color(0xFFFFFCF7), night = Color(0xFFFFFCF7))
        .cornerRadius(cornerRadius)
        .padding(padding)
        .let { modifier -> open?.let(modifier::clickable) ?: modifier }
}

private fun configurationAction(context: Context, appWidgetId: Int): Action = actionStartActivity(
    Intent(context, CommuteWidgetConfigurationActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
)

class RefreshStopWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        try {
            AppGraph.get(context).stopWidgetRepository.refresh(appWidgetId)
        } finally {
            CommuteWidget().update(context, glanceId)
        }
    }
}

private fun stopWidgetStatus(state: StopWidgetUiState, now: Instant): String = when {
    state.refreshFailed -> "갱신 실패"
    state.isRefreshing -> "업데이트 중"
    state.fetchedAt == null -> "조회 전"
    else -> elapsedLabel(state.fetchedAt.toEpochMilli(), now)
}

internal fun widgetStatusLabel(state: CommuteWidgetUiState, now: Instant): String = when {
    state.refreshError != null -> "갱신 실패 · ${elapsedLabel(state.refreshErrorAt, now)}"
    state.isRefreshing -> "업데이트 중"
    state.fetchedAt == null -> "조회 전"
    else -> elapsedLabel(state.fetchedAt.toEpochMilli(), now)
}

private fun elapsedLabel(epochMillis: Long?, now: Instant): String {
    val seconds = epochMillis?.let { Duration.between(Instant.ofEpochMilli(it), now).seconds.coerceAtLeast(0) } ?: 0
    return if (seconds < 60) "${seconds}초 전" else "${seconds / 60}분 전"
}

private fun ink() = ColorProvider(Color(0xFF17212B))
private fun mutedStyle(fontSize: Int) = TextStyle(color = ColorProvider(Color(0xFF52606D)), fontSize = fontSize.sp)

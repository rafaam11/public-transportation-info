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
import androidx.glance.action.actionParametersOf
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
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.ui.map.RoutePaletteResolver
import java.time.Duration
import java.time.Instant

class CommuteWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(180.dp, 110.dp), DpSize(300.dp, 180.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val state = AppGraph.get(context).commuteWidgetRepository.state(appWidgetId, Instant.now())
        provideContent { CommuteWidgetContent(context, state) }
    }
}

@Composable
private fun CommuteWidgetContent(context: Context, state: CommuteWidgetUiState) {
    if (LocalSize.current.height < 150.dp) {
        CompactCommuteWidgetContent(context, state)
    } else {
        ExpandedCommuteWidgetContent(context, state)
    }
}

@Composable
private fun CompactCommuteWidgetContent(context: Context, state: CommuteWidgetUiState) {
    val textColor = ColorProvider(Color(0xFF17212B))
    val mutedColor = ColorProvider(Color(0xFF52606D))
    Column(widgetCardModifier(state, padding = 8.dp, cornerRadius = 14.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.slot?.label ?: "버스 카드",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
            Text(statusLabel(state), style = TextStyle(color = mutedColor, fontSize = 9.sp))
        }
        Spacer(GlanceModifier.height(3.dp))
        if (state.requiresConfiguration) {
            Text("카드를 다시 설정해 주세요", style = TextStyle(color = textColor, fontSize = 11.sp))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "설정",
                modifier = GlanceModifier.clickable(configurationAction(context, state.appWidgetId)),
                style = TextStyle(color = ColorProvider(Color(0xFF005BAC)), fontSize = 10.sp, fontWeight = FontWeight.Bold),
            )
            return@Column
        }

        val palette = RoutePaletteResolver.resolve(state.routeTypeCode)
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.routeNo.orEmpty(),
                modifier = GlanceModifier
                    .background(day = Color(palette.bodyColor), night = Color(palette.bodyColor))
                    .cornerRadius(9.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = TextStyle(
                    color = ColorProvider(Color(palette.textColor)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                "  ${if (state.isRefreshing && state.primaryText.isBlank()) "불러오는 중…" else state.primaryText}",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = textColor, fontSize = 17.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                "${state.stopName.orEmpty()} · ${state.directionLabel.orEmpty()}",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = mutedColor, fontSize = 9.sp),
            )
            Text(
                widgetActionLabel(state),
                modifier = GlanceModifier.clickable(widgetAction(context, state)),
                style = TextStyle(color = ColorProvider(Color(0xFF005BAC)), fontSize = 9.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun ExpandedCommuteWidgetContent(context: Context, state: CommuteWidgetUiState) {
    val textColor = ColorProvider(Color(0xFF17212B))
    val mutedColor = ColorProvider(Color(0xFF52606D))
    Column(widgetCardModifier(state, padding = 16.dp, cornerRadius = 18.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.slot?.label ?: "버스 카드",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                statusLabel(state),
                style = TextStyle(color = mutedColor, fontSize = 11.sp),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        if (state.requiresConfiguration) {
            Text("카드를 다시 설정해 주세요", style = TextStyle(color = textColor, fontSize = 16.sp))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "설정",
                modifier = GlanceModifier.clickable(configurationAction(context, state.appWidgetId)),
                style = TextStyle(color = ColorProvider(Color(0xFF005BAC)), fontWeight = FontWeight.Bold),
            )
            return@Column
        }

        val palette = RoutePaletteResolver.resolve(state.routeTypeCode)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.routeNo.orEmpty(),
                modifier = GlanceModifier
                    .background(day = Color(palette.bodyColor), night = Color(palette.bodyColor))
                    .cornerRadius(12.dp)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                style = TextStyle(
                    color = ColorProvider(Color(palette.textColor)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                "  ${state.directionLabel.orEmpty()}",
                style = TextStyle(color = mutedColor, fontSize = 12.sp),
            )
        }
        Spacer(GlanceModifier.height(7.dp))
        Text(
            if (state.isRefreshing && state.primaryText.isBlank()) "불러오는 중…" else state.primaryText,
            style = TextStyle(color = textColor, fontSize = 23.sp, fontWeight = FontWeight.Bold),
        )
        state.secondaryText?.let {
            Text(it, style = TextStyle(color = mutedColor, fontSize = 12.sp))
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                "${state.stopName.orEmpty()} · ${state.directionLabel.orEmpty()}",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = mutedColor, fontSize = 11.sp),
            )
            Text(
                widgetActionLabel(state),
                modifier = GlanceModifier.clickable(widgetAction(context, state)),
                style = TextStyle(color = ColorProvider(Color(0xFF005BAC)), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

private fun widgetCardModifier(state: CommuteWidgetUiState, padding: Dp, cornerRadius: Dp): GlanceModifier {
    val openAction = state.slot?.let { slot ->
        actionRunCallback<OpenCommuteMapAction>(
            actionParametersOf(OpenCommuteMapAction.slotKey to slot.name),
        )
    }
    return GlanceModifier
        .fillMaxSize()
        .background(day = Color(0xFFFFFCF5), night = Color(0xFFFFFCF5))
        .cornerRadius(cornerRadius)
        .padding(padding)
        .let { modifier -> openAction?.let(modifier::clickable) ?: modifier }
}

private fun configurationAction(context: Context, appWidgetId: Int): Action = actionStartActivity(
    Intent(context, CommuteWidgetConfigurationActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
)

private fun widgetAction(context: Context, state: CommuteWidgetUiState): Action =
    if (state.refreshError == BusDataError.InvalidCredential) {
        actionStartActivity(
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_KEY_SETTINGS, true),
        )
    } else {
        actionRunCallback<RefreshCommuteWidgetAction>()
    }

private fun widgetActionLabel(state: CommuteWidgetUiState): String = when {
    state.refreshError == BusDataError.InvalidCredential -> "API 키 변경"
    state.isRefreshing -> "새로고침 중…"
    else -> "새로고침"
}

private fun statusLabel(state: CommuteWidgetUiState): String = when {
    state.refreshError != null -> when (state.refreshError) {
        BusDataError.InvalidCredential -> "API 키 확인 필요"
        BusDataError.RateLimited -> "요청 한도 초과"
        BusDataError.NetworkUnavailable -> "네트워크 오류"
        BusDataError.ServiceUnavailable -> "서비스 오류"
        BusDataError.MalformedResponse -> "응답 오류"
    }
    state.isRefreshing -> "업데이트 중"
    state.fetchedAt == null -> "조회 전"
    else -> "${Duration.between(state.fetchedAt, Instant.now()).seconds.coerceAtLeast(0)}초 전"
}

class RefreshCommuteWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        AppGraph.get(context).commuteWidgetRepository.refresh(appWidgetId) {
            CommuteWidget().update(context, glanceId)
        }
        CommuteWidget().update(context, glanceId)
    }
}

class OpenCommuteMapAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val slot = parameters[slotKey] ?: return
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_MAP_SLOT, slot),
        )
    }

    companion object {
        val slotKey = ActionParameters.Key<String>("commute-slot")
    }
}

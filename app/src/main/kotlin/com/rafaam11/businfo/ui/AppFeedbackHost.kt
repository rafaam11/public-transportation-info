package com.rafaam11.businfo.ui

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun AppFeedbackHost(
    events: List<UiFeedbackEvent>,
    modifier: Modifier = Modifier,
    onResolved: (eventId: Long, actionPerformed: Boolean) -> Unit,
) {
    val hostState = remember { SnackbarHostState() }
    val event = events.firstOrNull()
    LaunchedEffect(event?.id) {
        event ?: return@LaunchedEffect
        val result = hostState.showSnackbar(
            message = event.message,
            actionLabel = if (event is UiFeedbackEvent.FavoriteRemoved) "실행 취소" else null,
            withDismissAction = event is UiFeedbackEvent.FavoriteRemoved,
            duration = if (event is UiFeedbackEvent.FavoriteRemoved) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        onResolved(event.id, result == SnackbarResult.ActionPerformed)
    }
    SnackbarHost(hostState, modifier.navigationBarsPadding())
}

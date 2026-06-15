package com.raptorx.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.raptorx.wear.data.RunStatus
import com.raptorx.wear.data.TimelineEvent
import com.raptorx.wear.presentation.RpxViewModel
import com.raptorx.wear.presentation.RunDetailState

@Composable
fun RunDetailScreen(viewModel: RpxViewModel) {
    val state by viewModel.state.collectAsState()
    val detail = state.detail

    // Release the per-run room subscription when the user swipes back off this
    // screen, so we stop tracking a run we're no longer looking at.
    DisposableEffect(Unit) { onDispose { viewModel.closeRun() } }

    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (detail == null) {
                item { Text("No run selected", style = MaterialTheme.typography.bodyMedium) }
                return@ScalingLazyColumn
            }

            item { StatusHeader(detail) }
            item { MetaCard(detail) }

            if (detail.run?.status == RunStatus.FAILED && !detail.run.errorMessage.isNullOrBlank()) {
                item { ErrorCard(detail.run.errorMessage) }
            }

            item { Spacer(Modifier.size(2.dp)) }
            item { ListHeader { Text("Activity") } }

            if (detail.recentEvents.isEmpty()) {
                item {
                    Text(
                        text = if (detail.loading) "Loading…" else "Waiting for events…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(detail.recentEvents, key = { it.eventId ?: it.hashCode().toString() }) { ev ->
                    EventRow(ev)
                }
            }
        }
    }
}

@Composable
private fun StatusHeader(detail: RunDetailState) {
    val status = detail.run?.status ?: RunStatus.QUEUED
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(RunStatusVisuals.color(status)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = RunStatusVisuals.label(status),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RunStatusVisuals.color(status),
            )
        }
        Text(
            text = detail.run?.gameLabel ?: "…",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetaCard(detail: RunDetailState) {
    val run = detail.run
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MetaRow("SUT", run?.sutLabel ?: "—")
        val iter = run?.currentIteration
        val total = run?.iterations
        MetaRow(
            "Iteration",
            when {
                iter == null -> "—"
                total != null -> "$iter / $total"
                else -> "$iter"
            },
        )
        if (!run?.currentPhase.isNullOrBlank()) {
            MetaRow("Phase", run!!.currentPhase!!)
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(110.dp),
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
    ) {
        Text(
            "Error",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EventRow(ev: TimelineEvent) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(RunStatusVisuals.color(ev.status ?: "")),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = ev.message ?: ev.eventType ?: "event",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

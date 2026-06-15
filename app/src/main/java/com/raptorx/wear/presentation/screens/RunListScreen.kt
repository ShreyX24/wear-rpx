package com.raptorx.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.raptorx.wear.data.RpxRun
import com.raptorx.wear.presentation.RpxViewModel

@Composable
fun RunListScreen(
    viewModel: RpxViewModel,
    onOpenRun: (String) -> Unit,
    onConnect: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    // Refresh whenever we land here and a master is configured.
    LaunchedEffect(state.masterUrl) {
        if (state.masterUrl != null) viewModel.refreshRuns()
    }

    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                ListHeader {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConnectionDot(connected = state.socketConnected)
                        Spacer(Modifier.width(6.dp))
                        Text("Runs")
                    }
                }
            }

            when {
                state.masterUrl == null -> item { NoMasterCard(onConnect = onConnect) }

                state.activeRuns.isEmpty() && !state.runsLoading -> item {
                    EmptyOrErrorCard(
                        message = state.runsError ?: "No active runs",
                        isError = state.runsError != null,
                        onRetry = { viewModel.refreshRuns() },
                    )
                }

                else -> items(state.activeRuns, key = { it.runId }) { run ->
                    RunRow(run = run, onClick = { onOpenRun(run.runId) })
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.refreshRuns() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh", style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(onClick = onConnect, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.SettingsEthernet, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Master", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionDot(connected: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (connected) RunStatusVisuals.color("running") else MaterialTheme.colorScheme.error),
    )
}

@Composable
private fun RunRow(run: RpxRun, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(RunStatusVisuals.color(run.status)),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = run.gameLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = run.sutLabel + iterationSuffix(run),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun iterationSuffix(run: RpxRun): String {
    val cur = run.currentIteration ?: return ""
    val total = run.iterations
    return if (total != null) " · ${cur}/${total}" else " · iter $cur"
}

@Composable
private fun NoMasterCard(onConnect: () -> Unit) {
    InfoCard(
        title = "No master",
        body = "Find your RPX master on the network to start watching runs.",
        actionLabel = "Connect",
        onAction = onConnect,
    )
}

@Composable
private fun EmptyOrErrorCard(message: String, isError: Boolean, onRetry: () -> Unit) {
    InfoCard(
        title = if (isError) "Couldn't load" else "All quiet",
        body = message,
        actionLabel = "Retry",
        onAction = onRetry,
    )
}

@Composable
private fun InfoCard(title: String, body: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onAction) {
            Text(actionLabel, style = MaterialTheme.typography.labelMedium)
        }
    }
}

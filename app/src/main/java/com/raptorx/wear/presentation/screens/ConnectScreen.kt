package com.raptorx.wear.presentation.screens

import android.app.Activity
import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.outlined.Dns
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
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.input.RemoteInputIntentHelper
import com.raptorx.wear.data.DiscoveredNode
import com.raptorx.wear.presentation.RpxViewModel

private const val MANUAL_IP_KEY = "rpx_manual_master"

@Composable
fun ConnectScreen(
    viewModel: RpxViewModel,
    onConnected: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        if (!state.isScanning && state.discoveredNodes.isEmpty()) viewModel.scanForMasters()
    }

    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { ListHeader { Text("RPX Master") } }

            item {
                CurrentMasterCard(selectedUrl = state.masterUrl)
            }

            item {
                ScanControls(
                    isScanning = state.isScanning,
                    discoveredCount = state.discoveredNodes.size,
                    onScan = { viewModel.scanForMasters() },
                    onStop = { viewModel.stopScanning() },
                )
            }

            if (state.discoveredNodes.isEmpty() && !state.isScanning && state.masterUrl == null) {
                item { CouldntFindCard(onRetry = { viewModel.scanForMasters() }) }
            } else {
                items(state.discoveredNodes, key = { it.serviceName }) { node ->
                    MasterRow(
                        node = node,
                        isSelected = node.baseUrl == state.masterUrl,
                        onClick = {
                            viewModel.selectDiscovered(node)
                            onConnected()
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(6.dp)) }
            item { ListHeader { Text("Manual") } }
            item {
                ManualEntry(onSubmit = {
                    viewModel.selectManual(it)
                    onConnected()
                })
            }
        }
    }
}

@Composable
private fun CurrentMasterCard(selectedUrl: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Dns,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Current",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = selectedUrl?.removePrefix("http://") ?: "none",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ScanControls(
    isScanning: Boolean,
    discoveredCount: Int,
    onScan: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledIconButton(
            onClick = if (isScanning) onStop else onScan,
            shapes = IconButtonDefaults.animatedShapes(),
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (isScanning) Icons.Filled.WifiFind else Icons.Filled.Refresh,
                contentDescription = if (isScanning) "Stop scanning" else "Scan",
                modifier = Modifier.size(18.dp),
            )
        }
        Column {
            Text(
                text = when {
                    isScanning && discoveredCount > 0 -> "Scanning · $discoveredCount found"
                    isScanning -> "Scanning…"
                    discoveredCount == 0 -> "Tap to scan"
                    discoveredCount == 1 -> "1 master found"
                    else -> "$discoveredCount masters found"
                },
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "mDNS · _rpx-master._tcp",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MasterRow(node: DiscoveredNode, isSelected: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = if (isSelected) Icons.Filled.Dns else Icons.Outlined.Dns,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.prettyName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${node.host}:${node.port}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (isSelected) {
            Icon(Icons.Filled.Check, "Selected", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CouldntFindCard(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Couldn't find a master", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Text(
            "Check Wi-Fi and that the master is running, then retry — or enter its IP below.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledIconButton(
            onClick = onRetry,
            shapes = IconButtonDefaults.animatedShapes(),
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Filled.Refresh, "Retry", Modifier.size(18.dp))
        }
    }
}

/**
 * Wear OS replaces soft keyboards with a full-screen input surface; a plain
 * Compose TextField never gets the text back. Use the RemoteInput pattern.
 */
@Composable
private fun ManualEntry(onSubmit: (String) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val typed = RemoteInput.getResultsFromIntent(data)
            ?.getCharSequence(MANUAL_IP_KEY)?.toString()?.trim()
        if (!typed.isNullOrBlank()) onSubmit(typed)
    }

    FilledTonalButton(
        onClick = {
            val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            val remoteInput = RemoteInput.Builder(MANUAL_IP_KEY)
                .setLabel("Master IP (e.g. 192.168.0.100:5000)")
                .setAllowFreeFormInput(true)
                .build()
            RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
            launcher.launch(intent)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Keyboard, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Enter IP manually", style = MaterialTheme.typography.bodyMedium)
    }
}

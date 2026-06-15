package com.raptorx.wear.presentation.screens

import androidx.compose.ui.graphics.Color
import com.raptorx.wear.data.RunStatus

/**
 * Status → wrist visuals. Colors are deliberately fixed (not theme-derived) so
 * a glance reads the same in any lighting: green=good/live, amber=attention,
 * red=failure, grey=inert. This is the one place the run-state palette lives.
 */
object RunStatusVisuals {

    fun color(status: String): Color = when (status) {
        RunStatus.RUNNING -> Color(0xFF4CAF50)            // green — alive
        RunStatus.PAUSED -> Color(0xFFFFB300)             // amber — held
        RunStatus.COMPLETED -> Color(0xFF66BB6A)          // green — done well
        RunStatus.PARTIALLY_COMPLETED -> Color(0xFFFFB300) // amber — partial
        RunStatus.FAILED -> Color(0xFFEF5350)             // red — failed
        RunStatus.STOPPED -> Color(0xFF9E9E9E)            // grey — stopped
        RunStatus.SKIPPED -> Color(0xFF9E9E9E)            // grey — skipped
        RunStatus.QUEUED -> Color(0xFF78909C)             // slate — waiting
        else -> Color(0xFF9E9E9E)
    }

    fun label(status: String): String = when (status) {
        RunStatus.RUNNING -> "Running"
        RunStatus.PAUSED -> "Paused"
        RunStatus.COMPLETED -> "Completed"
        RunStatus.PARTIALLY_COMPLETED -> "Partial"
        RunStatus.FAILED -> "Failed"
        RunStatus.STOPPED -> "Stopped"
        RunStatus.SKIPPED -> "Skipped"
        RunStatus.QUEUED -> "Queued"
        else -> status.replaceFirstChar { it.uppercase() }
    }
}

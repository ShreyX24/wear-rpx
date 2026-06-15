package com.raptorx.wear.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal data models for the read-only watch view of RPX runs.
 *
 * These are intentionally a SUBSET of the master's full `AutomationRun` /
 * timeline schema — we only deserialize the fields the watch renders. The
 * shared [json] instance uses `ignoreUnknownKeys = true`, so the master can
 * keep adding fields without breaking the watch.
 *
 * snake_case JSON keys are mapped explicitly with [SerialName] (rather than a
 * global naming strategy) to avoid the experimental JsonNamingStrategy API.
 */

/** Run lifecycle status as reported by the master. */
object RunStatus {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val PAUSED = "paused"
    const val COMPLETED = "completed"
    const val PARTIALLY_COMPLETED = "partially_completed"
    const val FAILED = "failed"
    const val STOPPED = "stopped"
    const val SKIPPED = "skipped"

    /** Terminal states — the run is finished and will not change further. */
    val TERMINAL = setOf(COMPLETED, PARTIALLY_COMPLETED, FAILED, STOPPED, SKIPPED)
}

@Serializable
data class RpxRun(
    @SerialName("run_id") val runId: String,
    @SerialName("game_name") val gameName: String? = null,
    @SerialName("campaign_name") val campaignName: String? = null,
    @SerialName("sut_display_name") val sutDisplayName: String? = null,
    @SerialName("sut_ip") val sutIp: String? = null,
    val status: String = RunStatus.QUEUED,
    @SerialName("current_iteration") val currentIteration: Int? = null,
    val iterations: Int? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("current_phase") val currentPhase: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
) {
    val isLive: Boolean get() = status == RunStatus.RUNNING || status == RunStatus.PAUSED
    val isTerminal: Boolean get() = status in RunStatus.TERMINAL
    val sutLabel: String get() = sutDisplayName ?: sutIp ?: "unknown SUT"
    val gameLabel: String get() = gameName ?: "Unknown game"
}

/**
 * `GET /api/runs` response: active is a map keyed by run_id, history is a list.
 * We expose the active runs (the watch's primary concern) as a sorted list.
 */
@Serializable
data class RunsResponse(
    val active: Map<String, RpxRun> = emptyMap(),
    val history: List<RpxRun> = emptyList(),
) {
    val activeRuns: List<RpxRun> get() = active.values.toList()
}

/**
 * A live `timeline_event` pushed over Socket.IO. Emitted both globally and to
 * the `run_{run_id}` room. `replaces_event_id` means this event supersedes an
 * earlier one in place (e.g. a pending step transitioning to completed).
 */
@Serializable
data class TimelineEvent(
    @SerialName("run_id") val runId: String? = null,
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("event_type") val eventType: String? = null,
    val message: String? = null,
    val status: String? = null,
    // iteration is a LABEL string on the wire (e.g. "perf-run-1"), NOT an int —
    // typing it Int? threw and dropped every event. (Distinct from RpxRun's
    // numeric current_iteration.)
    val iteration: String? = null,
    // Server sends an ISO-8601 string (event.timestamp.isoformat()), NOT an
    // epoch number — typing this as Double here made kotlinx throw and silently
    // drop EVERY timeline event. Keep it String?; we don't render it anyway.
    val timestamp: String? = null,
    @SerialName("replaces_event_id") val replacesEventId: String? = null,
)

/**
 * A lifecycle `automation_event` (room `general_updates`). `event` is one of
 * automation_started / automation_completed / automation_failed /
 * run_paused / run_resumed / iteration_skipped.
 */
@Serializable
data class AutomationEvent(
    val event: String,
    val data: AutomationEventData? = null,
)

@Serializable
data class AutomationEventData(
    @SerialName("run_id") val runId: String? = null,
    val status: String? = null,
    @SerialName("game_name") val gameName: String? = null,
    @SerialName("sut_display_name") val sutDisplayName: String? = null,
    // Label string on the wire (e.g. "perf-run-1"), same as TimelineEvent.iteration.
    val iteration: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
)

/** Subset of `GET /api/runs/<id>/logbook` — only the historical timeline we render. */
@Serializable
data class RunLogbook(
    @SerialName("timeline_events") val timelineEvents: List<TimelineEvent> = emptyList(),
)

object AutomationEvents {
    const val STARTED = "automation_started"
    const val COMPLETED = "automation_completed"
    const val FAILED = "automation_failed"
    const val PAUSED = "run_paused"
    const val RESUMED = "run_resumed"
    const val ITERATION_SKIPPED = "iteration_skipped"
}

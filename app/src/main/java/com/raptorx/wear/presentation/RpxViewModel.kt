package com.raptorx.wear.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raptorx.wear.data.AutomationEvent
import com.raptorx.wear.data.DiscoveredNode
import com.raptorx.wear.data.RpxRepository
import com.raptorx.wear.data.RpxRun
import com.raptorx.wear.data.TimelineEvent
import com.raptorx.wear.network.RpxDiscovery
import com.raptorx.wear.notification.RunMonitorService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Detail state for the currently-open run. */
data class RunDetailState(
    val runId: String,
    val run: RpxRun? = null,
    val recentEvents: List<TimelineEvent> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class RpxUiState(
    val masterUrl: String? = null,
    val socketConnected: Boolean = false,
    val isScanning: Boolean = false,
    val discoveredNodes: List<DiscoveredNode> = emptyList(),
    val runsLoading: Boolean = false,
    val runsError: String? = null,
    val activeRuns: List<RpxRun> = emptyList(),
    val detail: RunDetailState? = null,
)

/**
 * Single source of UI state for the watch. Owns nothing network-y itself —
 * it observes the process-wide [RpxRepository] (so the notification service
 * shares the same socket) and folds live Socket.IO events into [state].
 */
class RpxViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RpxRepository.get(app)

    private val _state = MutableStateFlow(RpxUiState())
    val state: StateFlow<RpxUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        // Mirror repository connection state into the UI.
        viewModelScope.launch {
            repo.masterUrl.collect { url -> _state.update { it.copy(masterUrl = url) } }
        }
        viewModelScope.launch {
            repo.socket.connected.collect { connected ->
                _state.update { it.copy(socketConnected = connected) }
                if (connected) refreshRuns() // catch up after (re)connect
            }
        }
        // Live event folding.
        viewModelScope.launch {
            repo.socket.automationEvents.collect { onAutomationEvent(it) }
        }
        viewModelScope.launch {
            repo.socket.timelineEvents.collect { onTimelineEvent(it) }
        }

        if (repo.hasMaster) refreshRuns()
    }

    // ---- Discovery / connection ---------------------------------------------

    fun scanForMasters() {
        if (_state.value.isScanning) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update { it.copy(isScanning = true, discoveredNodes = emptyList()) }
            try {
                // discover() is an infinite callbackFlow; bound the scan window.
                withTimeoutOrNull(SCAN_WINDOW_MS) {
                    RpxDiscovery(getApplication()).discover().collect { nodes ->
                        _state.update { it.copy(discoveredNodes = nodes) }
                    }
                }
            } finally {
                _state.update { it.copy(isScanning = false) }
            }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = false) }
    }

    fun selectDiscovered(node: DiscoveredNode) = selectMaster(node.baseUrl)

    /** Accepts "host", "host:port", or a full URL; normalizes to http://host:port. */
    fun selectManual(input: String) {
        var s = input.trim()
        if (s.isBlank()) return
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        val afterScheme = s.substringAfter("://")
        if (!afterScheme.contains(":")) s = "$s:5000"
        selectMaster(s)
    }

    private fun selectMaster(url: String) {
        repo.selectMaster(url)
        // Begin background monitoring immediately so alerts work even after the
        // user closes the app on this very first connect.
        RunMonitorService.start(getApplication())
        // socket reconnect → connected collector will refreshRuns(); also kick now.
        refreshRuns()
    }

    // ---- Runs ----------------------------------------------------------------

    fun refreshRuns() {
        if (!repo.hasMaster) return
        viewModelScope.launch {
            _state.update { it.copy(runsLoading = true, runsError = null) }
            try {
                val resp = repo.api.getRuns()
                val runs = resp.activeRuns.sortedBy { it.gameLabel.lowercase() }
                _state.update { it.copy(activeRuns = runs, runsLoading = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(runsLoading = false, runsError = e.message ?: "Failed to load runs")
                }
            }
        }
    }

    fun openRun(runId: String) {
        repo.socket.subscribeToRun(runId)
        _state.update { it.copy(detail = RunDetailState(runId = runId, loading = true)) }
        // Run snapshot (status, iteration, game).
        viewModelScope.launch {
            try {
                val run = repo.api.getRun(runId)
                updateDetailIfCurrent(runId) { it.copy(run = run, loading = false) }
            } catch (e: Exception) {
                updateDetailIfCurrent(runId) { it.copy(loading = false, error = e.message) }
            }
        }
        // Historical timeline: timeline_event is a LIVE broadcast that the server
        // never replays, so a mid-run connect would otherwise show nothing until
        // the next event. Seed from the logbook (most-recent-first). Live events
        // then merge on top via onTimelineEvent.
        viewModelScope.launch {
            try {
                val logbook = repo.api.getRunLogbook(runId)
                val seed = logbook.timelineEvents.takeLast(MAX_RECENT_EVENTS).reversed()
                updateDetailIfCurrent(runId) { d ->
                    if (d.recentEvents.isEmpty()) d.copy(recentEvents = seed) else d
                }
            } catch (_: Exception) {
                // Best-effort; live events still populate going forward.
            }
        }
    }

    fun closeRun() {
        repo.socket.clearRunSubscription()
        _state.update { it.copy(detail = null) }
    }

    // ---- Live event folding --------------------------------------------------

    private fun onAutomationEvent(e: AutomationEvent) {
        // Lifecycle change for some run — cheapest correct move is a refetch.
        refreshRuns()
        val runId = e.data?.runId ?: return
        val newStatus = e.data.status
        if (newStatus != null) {
            updateDetailIfCurrent(runId) { d ->
                d.copy(run = d.run?.copy(status = newStatus))
            }
        }
    }

    private fun onTimelineEvent(e: TimelineEvent) {
        val runId = e.runId ?: return
        updateDetailIfCurrent(runId) { d -> d.copy(recentEvents = mergeEvent(d.recentEvents, e)) }
    }

    /**
     * Merge a timeline event into the recent-events list (most-recent-first).
     * Honors `replaces_event_id`: an event that supersedes an earlier one
     * (e.g. pending → completed) replaces it in place rather than appending.
     */
    private fun mergeEvent(list: List<TimelineEvent>, e: TimelineEvent): List<TimelineEvent> {
        val targetId = e.replacesEventId ?: e.eventId
        val idx = list.indexOfFirst { existing ->
            existing.eventId != null &&
                (existing.eventId == e.replacesEventId || existing.eventId == e.eventId)
        }
        val merged = if (idx >= 0) {
            list.toMutableList().also { it[idx] = e }
        } else {
            listOf(e) + list
        }
        return merged.take(MAX_RECENT_EVENTS)
    }

    private inline fun updateDetailIfCurrent(runId: String, transform: (RunDetailState) -> RunDetailState) {
        _state.update { st ->
            val d = st.detail
            if (d != null && d.runId == runId) st.copy(detail = transform(d)) else st
        }
    }

    companion object {
        private const val SCAN_WINDOW_MS = 12_000L
        private const val MAX_RECENT_EVENTS = 15
    }
}

package com.raptorx.wear.network

import android.util.Log
import com.raptorx.wear.data.AutomationEvent
import com.raptorx.wear.data.TimelineEvent
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.json.JSONObject

/**
 * Socket.IO connection to the RPX master, exposed as Kotlin Flows.
 *
 * The master (python-socketio 5.x, Socket.IO protocol v5) auto-joins every
 * client to the `general_updates` room on connect, and broadcasts
 * `timeline_event` globally. So this client needs NO explicit room join to
 * receive lifecycle events — connecting is enough. [subscribeToRun] is
 * belt-and-suspenders for the per-run room and is re-emitted on reconnect.
 *
 * Socket.IO callbacks fire on the library's own thread, so we publish into
 * SharedFlows via [MutableSharedFlow.tryEmit] (non-suspending) and parse
 * defensively — a malformed payload must never crash the socket thread.
 */
class RpxSocket {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private var socket: Socket? = null
    private var subscribedRunId: String? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _timeline = MutableSharedFlow<TimelineEvent>(extraBufferCapacity = 128)
    val timelineEvents: SharedFlow<TimelineEvent> = _timeline.asSharedFlow()

    private val _automation = MutableSharedFlow<AutomationEvent>(extraBufferCapacity = 64)
    val automationEvents: SharedFlow<AutomationEvent> = _automation.asSharedFlow()

    /** Connect to a master base URL (e.g. http://192.168.0.100:5000). Idempotent. */
    fun connect(baseUrl: String) {
        if (socket != null) return
        val opts = IO.Options().apply {
            path = "/socket.io/"
            reconnection = true
            reconnectionDelay = 1_000
            // transports default to polling→websocket upgrade; master's threading
            // async_mode may not upgrade, in which case polling carries the stream.
        }
        val s = IO.socket(baseUrl.trimEnd('/'), opts)

        s.on(Socket.EVENT_CONNECT) {
            Log.i(TAG, "socket connected to $baseUrl")
            _connected.tryEmit(true)
            // Re-assert the per-run subscription: python-socketio drops room
            // membership on a new sid, so we must re-emit after every reconnect.
            subscribedRunId?.let { emitSubscribe(it) }
        }
        s.on(Socket.EVENT_DISCONNECT) { _connected.tryEmit(false) }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.w(TAG, "connect_error: ${args.firstOrNull()}")
        }

        s.on("timeline_event") { args -> parse(args)?.let(::emitTimeline) }
        s.on("automation_event") { args -> parse(args)?.let(::emitAutomation) }

        socket = s
        s.connect()
    }

    /** Track this run and join its room (and re-join on reconnect). */
    fun subscribeToRun(runId: String) {
        subscribedRunId = runId
        if (socket?.connected() == true) emitSubscribe(runId)
    }

    fun clearRunSubscription() {
        subscribedRunId = null
    }

    fun disconnect() {
        socket?.let {
            it.off()
            it.disconnect()
            it.close()
        }
        socket = null
        subscribedRunId = null
        _connected.tryEmit(false)
    }

    private fun emitSubscribe(runId: String) {
        runCatching { socket?.emit("subscribe_to_run", JSONObject().put("run_id", runId)) }
    }

    private fun parse(args: Array<out Any?>): JSONObject? =
        args.firstOrNull() as? JSONObject

    private fun emitTimeline(obj: JSONObject) {
        runCatching { json.decodeFromString(TimelineEvent.serializer(), obj.toString()) }
            .onSuccess { Log.d(TAG, "timeline_event ${it.eventType} iter=${it.iteration}"); _timeline.tryEmit(it) }
            .onFailure { Log.w(TAG, "bad timeline_event: ${it.message}") }
    }

    private fun emitAutomation(obj: JSONObject) {
        runCatching { json.decodeFromString(AutomationEvent.serializer(), obj.toString()) }
            .onSuccess { _automation.tryEmit(it) }
            .onFailure { Log.w(TAG, "bad automation_event: ${it.message}") }
    }

    companion object {
        private const val TAG = "RpxSocket"
    }
}

package com.raptorx.wear.notification

import com.raptorx.wear.data.AutomationEvent
import com.raptorx.wear.data.AutomationEvents

/**
 * Decides which run lifecycle events are worth buzzing the wrist.
 *
 * This is the product heart of the app — pure Kotlin (no Android types) so it
 * stays testable and is the single place to tune alerting. The locked policy:
 *   • Run FAILED            → HIGH  (the core use case: cater to the error now)
 *   • Run COMPLETED         → NORMAL
 *   • ITERATION skipped     → HIGH  (mid-run trouble)
 *   • Every iteration result→ silent (too noisy — deliberately not alerted)
 *
 * De-duplication: a `(run, kind)` is alerted at most once. The master pushes
 * each lifecycle event a single time, but a flaky socket can redeliver — and we
 * never want the same failure to buzz twice — so we remember recent keys.
 *
 * To change what alerts, edit [decide]. It's intentionally the only logic here.
 */
object NotificationPolicy {

    enum class Priority { HIGH, NORMAL }

    data class Decision(
        val notificationId: Int,
        val priority: Priority,
        val title: String,
        val text: String,
    )

    // Bounded LRU of keys we've already alerted on (access-ordered, capped).
    private const val MAX_SEEN = 200
    private val seen = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>): Boolean =
            size > MAX_SEEN
    }

    @Synchronized
    private fun firstTime(key: String): Boolean {
        if (seen.containsKey(key)) return false
        seen[key] = true
        return true
    }

    /** Returns a [Decision] to notify, or null to stay silent. */
    fun decide(event: AutomationEvent): Decision? {
        val data = event.data ?: return null
        val runId = data.runId ?: return null
        val game = data.gameName ?: "Run"

        return when (event.event) {
            AutomationEvents.FAILED -> {
                if (!firstTime("$runId:failed")) return null
                Decision(
                    notificationId = "$runId:failed".hashCode(),
                    priority = Priority.HIGH,
                    title = "Run failed",
                    text = data.errorMessage?.let { "$game — $it" } ?: game,
                )
            }

            AutomationEvents.COMPLETED -> {
                if (!firstTime("$runId:completed")) return null
                Decision(
                    notificationId = "$runId:completed".hashCode(),
                    priority = Priority.NORMAL,
                    title = "Run completed",
                    text = game,
                )
            }

            AutomationEvents.ITERATION_SKIPPED -> {
                val iter = data.iteration
                if (!firstTime("$runId:skip:$iter")) return null
                Decision(
                    notificationId = "$runId:skip:$iter".hashCode(),
                    priority = Priority.HIGH,
                    title = "Iteration skipped",
                    text = if (iter != null) "$game · iteration $iter" else game,
                )
            }

            // Everything else (started, paused, resumed, per-iteration results)
            // is deliberately silent.
            else -> null
        }
    }
}

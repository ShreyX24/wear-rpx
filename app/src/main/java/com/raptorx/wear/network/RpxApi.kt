package com.raptorx.wear.network

import com.raptorx.wear.data.RpxRun
import com.raptorx.wear.data.RunLogbook
import com.raptorx.wear.data.RunsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Read-only REST client for the RPX master `/api` surface.
 *
 * The base URL is resolved lazily via [baseUrlProvider] so the same client
 * survives the user switching masters (the repository just updates the
 * provider). Timeouts are short — a watch on Wi-Fi should fail fast and let
 * the UI show a retry rather than hang.
 *
 * NOTE: only GET endpoints are exposed. Control endpoints (stop/pause/skip)
 * are deliberately omitted — this app is strictly read-only.
 */
class RpxApi(private val baseUrlProvider: () -> String?) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true // tolerate nulls where we declared non-null defaults
    }

    private fun base(): String =
        baseUrlProvider()?.trimEnd('/') ?: error("No RPX master selected")

    /** `GET /api/runs` — active map + history list. */
    suspend fun getRuns(): RunsResponse = withContext(Dispatchers.IO) {
        val body = get("/api/runs")
        json.decodeFromString(RunsResponse.serializer(), body)
    }

    /** `GET /api/runs/<id>` — single run snapshot. */
    suspend fun getRun(runId: String): RpxRun = withContext(Dispatchers.IO) {
        val body = get("/api/runs/$runId")
        json.decodeFromString(RpxRun.serializer(), body)
    }

    /** `GET /api/runs/<id>/logbook` — historical snapshot (we use timeline_events). */
    suspend fun getRunLogbook(runId: String): RunLogbook = withContext(Dispatchers.IO) {
        val body = get("/api/runs/$runId/logbook")
        json.decodeFromString(RunLogbook.serializer(), body)
    }

    private fun get(path: String): String {
        val request = Request.Builder()
            .url(base() + path)
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RpxApiException(resp.code, "GET $path failed: ${resp.code}")
            }
            return resp.body?.string() ?: throw RpxApiException(resp.code, "Empty body")
        }
    }
}

class RpxApiException(val code: Int, message: String) : Exception(message)

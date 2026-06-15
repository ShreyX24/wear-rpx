package com.raptorx.wear.data

import android.content.Context
import android.content.SharedPreferences

/**
 * App-wide SharedPreferences wrapper. Holds the base URL of the currently
 * selected RPX master so we reconnect to it on launch without re-discovering.
 */
class RpxPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Base URL of the currently-selected RPX master (e.g.
     * `"http://192.168.0.100:5000"`). Null when the user has never picked one —
     * caller then triggers discovery or manual entry.
     */
    var selectedMasterUrl: String?
        get() = prefs.getString(KEY_SELECTED_MASTER_URL, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_SELECTED_MASTER_URL)
                else putString(KEY_SELECTED_MASTER_URL, value)
            }.apply()
        }

    companion object {
        /** Sensible fallback for manual entry — RPX master listens on :5000. */
        const val DEFAULT_MASTER_URL = "http://192.168.0.100:5000"

        private const val PREFS_NAME = "rpx_watch_prefs"
        private const val KEY_SELECTED_MASTER_URL = "selected_master_url"
    }
}

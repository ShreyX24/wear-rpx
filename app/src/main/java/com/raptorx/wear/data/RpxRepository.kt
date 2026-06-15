package com.raptorx.wear.data

import android.content.Context
import com.raptorx.wear.network.LanNetwork
import com.raptorx.wear.network.RpxApi
import com.raptorx.wear.network.RpxSocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide singleton holding the shared connection to the selected RPX
 * master: the REST client, the Socket.IO stream, the LAN/Wi-Fi binding, and
 * the persisted master URL.
 *
 * Why a singleton (not a ViewModel-owned object): background surfaces — the
 * notification foreground service in particular — need the same socket/URL as
 * the UI but can't hold a ViewModel. This mirrors WLED's `WLEDRepository`.
 */
class RpxRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = RpxPrefs(appContext)
    val lanNetwork = LanNetwork(appContext)

    private val _masterUrl = MutableStateFlow(prefs.selectedMasterUrl)
    val masterUrl: StateFlow<String?> = _masterUrl.asStateFlow()

    /** REST client resolves the base URL lazily, so switching masters is free. */
    val api = RpxApi(baseUrlProvider = { _masterUrl.value })
    val socket = RpxSocket()

    init {
        // Force LAN traffic onto the Wi-Fi radio (otherwise a watch egresses via
        // the Bluetooth-to-phone proxy and can't reach the master).
        lanNetwork.bindToWifi()
        _masterUrl.value?.let { socket.connect(it) }
    }

    /** Select (and persist) a master, reconnecting the socket to it. */
    fun selectMaster(baseUrl: String) {
        val normalized = baseUrl.trimEnd('/')
        if (normalized == _masterUrl.value) return
        prefs.selectedMasterUrl = normalized
        _masterUrl.value = normalized
        socket.disconnect()
        socket.connect(normalized)
    }

    val hasMaster: Boolean get() = _masterUrl.value != null

    companion object {
        @Volatile
        private var instance: RpxRepository? = null

        fun get(context: Context): RpxRepository =
            instance ?: synchronized(this) {
                instance ?: RpxRepository(context).also { instance = it }
            }
    }
}

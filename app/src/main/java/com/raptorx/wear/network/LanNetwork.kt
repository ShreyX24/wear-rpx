package com.raptorx.wear.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

class LanNetwork(context: Context) {
    private val connectivity =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun bindToWifi() {
        if (callback != null) return
        // On an emulator the default network already reaches the host (10.0.2.2);
        // pinning the process to a requested Wi-Fi network can blackhole that
        // route. Real watches need the bind (LAN egresses the BT proxy otherwise).
        if (isEmulator()) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectivity.bindProcessToNetwork(network)
            }

            override fun onLost(network: Network) {
                connectivity.bindProcessToNetwork(null)
            }
        }
        callback = cb
        connectivity.requestNetwork(request, cb)
    }

    fun release() {
        callback?.let {
            runCatching { connectivity.unregisterNetworkCallback(it) }
        }
        callback = null
        connectivity.bindProcessToNetwork(null)
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.BRAND.startsWith("generic") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("emulator") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")
}

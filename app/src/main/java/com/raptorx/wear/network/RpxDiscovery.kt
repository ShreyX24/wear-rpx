package com.raptorx.wear.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.raptorx.wear.data.DiscoveredNode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Discovers RPX master instances on the local network via mDNS (`_rpx-master._tcp.`).
 *
 * The RPX master advertises itself through Bonjour/Zeroconf. We use Android's
 * [NsdManager] to scan and resolve each service to its IP:port.
 *
 * Usage:
 * ```
 * RpxDiscovery(context).discover().collect { nodes ->
 *     // Render nodes list. List grows as discovery finds + resolves more devices.
 * }
 * ```
 * Cancel the collector to stop scanning (discovery is stopped in `awaitClose`).
 *
 * Gotchas handled:
 * - `resolveService` on API 30–33 can only have ONE call in flight. We queue
 *   resolves and run them strictly serially.
 * - `NsdServiceInfo.host` is deprecated API 34+ → use `hostAddresses` when present.
 * - `NsdManager` callbacks dispatch on a library-chosen thread — NOT the main
 *   thread — so we route all shared-state mutations through a main-thread Handler
 *   to confine the `found` / `resolveQueue` / `resolving` state to one thread.
 * - Wi-Fi drivers filter multicast by default; we acquire a [WifiManager.MulticastLock]
 *   for the duration of the scan.
 * - If `discoverServices` throws synchronously before the flow sets up `awaitClose`,
 *   we release the multicast lock in a try/catch to avoid leaking it.
 */
class RpxDiscovery(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun discover(): Flow<List<DiscoveredNode>> = callbackFlow {
        val mainHandler = Handler(Looper.getMainLooper())

        // Wi-Fi drivers filter multicast by default to save battery. mDNS lives
        // on 224.0.0.251/5353 which gets dropped without this lock.
        val multicastLock = wifiManager.createMulticastLock("wled-mdns").apply {
            setReferenceCounted(true)
            acquire()
        }

        // All of the following are accessed ONLY on the main thread (via mainHandler).
        val found = linkedMapOf<String, DiscoveredNode>()
        val resolveQueue: ArrayDeque<NsdServiceInfo> = ArrayDeque()
        var resolving = false

        // Guards against a race where `trySend` fires on a now-closed channel.
        fun emitCurrent() {
            trySend(found.values.toList().sortedBy { it.prettyName.lowercase() })
        }

        fun resolveNext() {
            if (resolving || resolveQueue.isEmpty()) return
            val next = resolveQueue.removeFirst()
            resolving = true
            nsdManager.resolveService(next, object : NsdManager.ResolveListener {
                override fun onServiceResolved(info: NsdServiceInfo) {
                    mainHandler.post {
                        val host = extractHost(info)
                        if (host.isNotBlank()) {
                            found[info.serviceName] = DiscoveredNode(
                                serviceName = info.serviceName,
                                host = host,
                                port = info.port,
                            )
                            emitCurrent()
                        }
                        resolving = false
                        resolveNext()
                    }
                }

                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    mainHandler.post {
                        resolving = false
                        resolveNext()
                    }
                }
            })
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                mainHandler.post { close() }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                mainHandler.post {
                    resolveQueue.addLast(serviceInfo)
                    resolveNext()
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                mainHandler.post {
                    found.remove(serviceInfo.serviceName)
                    emitCurrent()
                }
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            runCatching { multicastLock.release() }
            throw e
        }
        trySend(emptyList())

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
            runCatching { multicastLock.release() }
        }
    }

    private fun extractHost(info: NsdServiceInfo): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses.firstOrNull()?.hostAddress.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.host?.hostAddress.orEmpty()
        }

    companion object {
        // Trailing dot is required by NsdManager — it's treated as FQDN.
        private const val SERVICE_TYPE = "_rpx-master._tcp."
    }
}

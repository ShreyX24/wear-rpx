package com.raptorx.wear.data

/**
 * A WLED instance located on the LAN via mDNS (`_wled._tcp.`).
 *
 * [serviceName] is the raw mDNS service name (e.g. `"wled-Kosen"`). [prettyName]
 * is the human-friendly form — we strip the `wled-` prefix at construction and,
 * if we've already HTTP-fetched `/json/si`, replace with the user's configured
 * name from the node.
 */
data class DiscoveredNode(
    val serviceName: String,
    val host: String,
    val port: Int,
    val prettyName: String = defaultPretty(serviceName),
    val ledCount: Int? = null,
) {
    val baseUrl: String get() = "http://$host:$port"

    companion object {
        fun defaultPretty(serviceName: String): String =
            serviceName.removePrefix("wled-").ifBlank { serviceName }
    }
}

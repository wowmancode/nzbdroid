package com.owan.nzbdroid.data.settings

/**
 * Connection details for a single NZBGet instance.
 * NZBGet's JSON-RPC endpoint lives at http(s)://host:port/jsonrpc and uses HTTP basic auth
 * (the same username/password configured in nzbget.conf as ControlUsername/ControlPassword).
 */
data class NzbGetConfig(
    val host: String = "",
    val port: Int = 6789,
    val username: String = "nzbget",
    val password: String = "",
    val useSsl: Boolean = false
) {
    val isConfigured: Boolean get() = host.isNotBlank()

    fun baseUrl(): String {
        val scheme = if (useSsl) "https" else "http"
        return "$scheme://$host:$port"
    }
}

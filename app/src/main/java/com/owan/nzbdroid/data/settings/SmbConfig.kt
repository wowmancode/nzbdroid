package com.owan.nzbdroid.data.settings

/**
 * A single saved SMB/Samba share (e.g. your NZBGet completed-downloads folder,
 * or a media share you want to push/pull files from on your phone).
 */
data class SmbConfig(
    val host: String = "",
    val share: String = "",
    val domain: String = "",       // leave blank unless your server is on an AD domain
    val username: String = "",
    val password: String = "",
    // Path inside the share to start browsing at, e.g. "Downloads/complete". Blank = share root.
    val startPath: String = ""
) {
    val isConfigured: Boolean get() = host.isNotBlank() && share.isNotBlank()

    /** smb://host/share/ base URL that jcifs-ng's SmbFile expects. */
    fun smbUrl(): String {
        val cleanShare = share.trim('/')
        return "smb://$host/$cleanShare/"
    }
}

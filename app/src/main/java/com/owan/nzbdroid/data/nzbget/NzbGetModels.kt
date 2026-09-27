package com.owan.nzbdroid.data.nzbget

/** A minimal, immutable snapshot of NZBGet's global state (from the `status` RPC method). */
data class NzbGetStatus(
    val downloadPaused: Boolean,
    val serverStandBy: Boolean,
    val downloadRateBytesPerSec: Long,
    val remainingSizeMB: Double,
    val freeDiskSpaceMB: Double
) {
    val downloadRateKBps: Double get() = downloadRateBytesPerSec / 1024.0
}

/** One item in the active download queue (from `listgroups`). */
data class QueueItem(
    val nzbId: Int,
    val name: String,
    val category: String,
    val status: String,        // e.g. QUEUED, DOWNLOADING, PAUSED, FETCHING
    val fileSizeMB: Double,
    val remainingSizeMB: Double,
    val pausedSizeMB: Double
) {
    val isPaused: Boolean get() = status.equals("PAUSED", ignoreCase = true) || pausedSizeMB > 0.0
    val progressPercent: Int
        get() = if (fileSizeMB <= 0.0) 0
        else (((fileSizeMB - remainingSizeMB) / fileSizeMB) * 100).toInt().coerceIn(0, 100)
}

/** One completed/failed item in download history (from `history`). */
data class HistoryItem(
    val nzbId: Int,
    val name: String,
    val category: String,
    val status: String,        // e.g. SUCCESS/ALL, FAILURE/PAR, DELETED/MANUAL
    val kind: String,           // NZB, URL, DUP
    val fileSizeMB: Double
) {
    val isSuccess: Boolean get() = status.startsWith("SUCCESS", ignoreCase = true)
}

class NzbGetException(message: String, val rpcCode: Int? = null) : Exception(message)

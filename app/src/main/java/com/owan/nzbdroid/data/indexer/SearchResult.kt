package com.owan.nzbdroid.data.indexer

/** One search hit from a Newznab-compatible indexer, ready to hand to NZBGet. */
data class SearchResult(
    val title: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val pubDateMillis: Long,   // 0 if unknown/unparseable
    val category: String,
    val indexerName: String
) {
    val sizeDisplay: String get() = formatBytes(sizeBytes)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "? size"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}

class IndexerException(val indexerName: String, message: String, cause: Throwable? = null) :
    Exception("$indexerName: $message", cause)

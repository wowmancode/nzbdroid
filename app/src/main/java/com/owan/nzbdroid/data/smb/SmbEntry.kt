package com.owan.nzbdroid.data.smb

data class SmbEntry(
    val name: String,
    /** Path relative to the share root, e.g. "Downloads/complete/Some.Show". Never starts with '/'. */
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long
)

class SmbException(message: String, cause: Throwable? = null) : Exception(message, cause)

package com.owan.nzbdroid.data.settings

import java.util.UUID

/**
 * A single Newznab-compatible indexer (NZBHydra2, NZBFinder, NZBgeek, DrunkenSlug,
 * a private tracker's Newznab-API front end, etc). Any indexer speaking the standard
 * Newznab `api?t=search` protocol works here.
 */
data class IndexerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",       // e.g. https://api.nzbgeek.info  (no trailing slash, no /api)
    val apiKey: String = "",
    val enabled: Boolean = true
) {
    val isConfigured: Boolean get() = url.isNotBlank() && apiKey.isNotBlank()
}

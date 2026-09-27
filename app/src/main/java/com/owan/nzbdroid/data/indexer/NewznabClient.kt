package com.owan.nzbdroid.data.indexer

import android.util.Xml
import com.owan.nzbdroid.data.settings.IndexerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Queries a single Newznab-compatible indexer's search endpoint:
 *   GET {baseUrl}/api?t=search&q=...&apikey=...&limit=...
 * and parses the RSS/XML response. Plain RSS (not the optional o=json mode) is used
 * because JSON output support is inconsistent across different Newznab implementations,
 * while every one of them supports the original RSS format.
 */
class NewznabClient(private val config: IndexerConfig) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 100): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val base = config.url.trimEnd('/')
            val httpUrl = "$base/api".toHttpUrlOrNull()
                ?: throw IndexerException(config.name, "Invalid indexer URL: ${config.url}")

            val url = httpUrl.newBuilder()
                .addQueryParameter("t", "search")
                .addQueryParameter("q", query)
                .addQueryParameter("apikey", config.apiKey)
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("extended", "1")
                .build()

            val request = Request.Builder().url(url).build()

            try {
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IndexerException(config.name, "HTTP ${response.code}")
                    }
                    parseRss(body)
                }
            } catch (e: IndexerException) {
                throw e
            } catch (e: Exception) {
                throw IndexerException(config.name, e.message ?: "search failed", e)
            }
        }

    private fun parseRss(xml: String): List<SearchResult> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }

        val results = mutableListOf<SearchResult>()

        var title = ""
        var link = ""
        var pubDate = ""
        var category = ""
        var enclosureUrl = ""
        var enclosureLength = 0L
        var sizeAttr = 0L
        var inItem = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    // Namespace processing is off, so a prefixed tag like <newznab:attr> keeps
                    // its "newznab:" prefix glued onto parser.name. Strip any prefix before
                    // matching so this works regardless of which prefix a feed declares.
                    val localName = parser.name.substringAfter(':')
                    when (localName) {
                        "item" -> {
                            inItem = true
                            title = ""; link = ""; pubDate = ""; category = ""
                            enclosureUrl = ""; enclosureLength = 0L; sizeAttr = 0L
                        }
                        "title" -> if (inItem) title = parser.nextTextSafe()
                        "link" -> if (inItem) link = parser.nextTextSafe()
                        "pubDate" -> if (inItem) pubDate = parser.nextTextSafe()
                        "category" -> if (inItem) category = parser.nextTextSafe()
                        "enclosure" -> if (inItem) {
                            enclosureUrl = parser.getAttributeValue(null, "url").orEmpty()
                            enclosureLength =
                                parser.getAttributeValue(null, "length")?.toLongOrNull() ?: 0L
                        }
                        "attr" -> if (inItem) { // newznab:attr name="size" value="..."
                            val name = parser.getAttributeValue(null, "name")
                            val value = parser.getAttributeValue(null, "value")
                            if (name.equals("size", ignoreCase = true)) {
                                sizeAttr = value?.toLongOrNull() ?: 0L
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && inItem) {
                        val downloadUrl = enclosureUrl.ifBlank { link }
                        if (downloadUrl.isNotBlank()) {
                            results += SearchResult(
                                title = title.ifBlank { "Untitled" },
                                downloadUrl = downloadUrl,
                                sizeBytes = if (sizeAttr > 0) sizeAttr else enclosureLength,
                                pubDateMillis = parsePubDate(pubDate),
                                category = category,
                                indexerName = config.name
                            )
                        }
                        inItem = false
                    }
                }
            }
            eventType = parser.next()
        }

        return results
    }

    private fun parsePubDate(text: String): Long {
        if (text.isBlank()) return 0L
        return runCatching {
            java.time.ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }

    /** Reads the text content of the current tag and leaves the parser positioned at its END_TAG. */
    private fun XmlPullParser.nextTextSafe(): String =
        runCatching { this.nextText() }.getOrDefault("")
}

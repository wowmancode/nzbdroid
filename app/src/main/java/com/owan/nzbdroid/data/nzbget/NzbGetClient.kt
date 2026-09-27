package com.owan.nzbdroid.data.nzbget

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.owan.nzbdroid.data.settings.NzbGetConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Talks to a single NZBGet instance's JSON-RPC endpoint (http(s)://host:port/jsonrpc).
 * NZBGet's JSON-RPC is plain JSON-RPC 1.0 style: POST {"method": "...", "params": [...]}
 * to one endpoint, with HTTP Basic auth using the ControlUsername/ControlPassword from
 * nzbget.conf.
 *
 * Reference: https://nzbget.com/documentation/api/
 */
class NzbGetClient(private val config: NzbGetConfig) {

    private val gson = Gson()
    private val jsonMedia = "application/json".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Raw JSON-RPC call. Runs on Dispatchers.IO. Returns the "result" element, throws on RPC error. */
    private suspend fun call(method: String, params: List<Any?> = emptyList()): JsonElement =
        withContext(Dispatchers.IO) {
            val paramsArray = JsonArray()
            params.forEach { paramsArray.add(gson.toJsonTree(it)) }

            val body = JsonObject().apply {
                addProperty("method", method)
                add("params", paramsArray)
            }

            val request = Request.Builder()
                .url("${config.baseUrl()}/jsonrpc")
                .header("Authorization", Credentials.basic(config.username, config.password))
                .post(gson.toJson(body).toRequestBody(jsonMedia))
                .build()

            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw NzbGetException("HTTP ${response.code} from NZBGet: ${raw.take(200)}")
                }
                val parsed = runCatching { gson.fromJson(raw, JsonObject::class.java) }.getOrNull()
                    ?: throw NzbGetException("Malformed response from NZBGet")

                parsed.get("error")?.takeIf { !it.isJsonNull }?.let { err ->
                    val errObj = err.asJsonObject
                    val msg = errObj.get("message")?.asString ?: "Unknown NZBGet RPC error"
                    val code = errObj.get("code")?.asInt
                    throw NzbGetException(msg, code)
                }
                parsed.get("result") ?: throw NzbGetException("No result in NZBGet response")
            }
        }

    suspend fun testConnection(): Boolean = runCatching { status() }.isSuccess

    suspend fun status(): NzbGetStatus {
        val r = call("status").asJsonObject
        fun lo(name: String) = r.get(name)?.asLong ?: 0L
        val rateLo = lo("DownloadRateLo")
        val rateHi = lo("DownloadRateHi")
        val rate = (rateHi shl 32) or (rateLo and 0xFFFFFFFFL)
        return NzbGetStatus(
            downloadPaused = r.get("DownloadPaused")?.asBoolean ?: false,
            serverStandBy = r.get("ServerStandBy")?.asBoolean ?: true,
            downloadRateBytesPerSec = rate,
            remainingSizeMB = r.get("RemainingSizeMB")?.asDouble ?: 0.0,
            freeDiskSpaceMB = r.get("FreeDiskSpaceMB")?.asDouble ?: 0.0
        )
    }

    suspend fun listQueue(): List<QueueItem> {
        val arr = call("listgroups", listOf(0)).asJsonArray
        return arr.map { el ->
            val o = el.asJsonObject
            QueueItem(
                nzbId = o.get("NZBID")?.asInt ?: 0,
                name = o.get("NZBName")?.asString ?: o.get("NZBFilename")?.asString ?: "Unknown",
                category = o.get("Category")?.asString.orEmpty(),
                status = o.get("Status")?.asString.orEmpty(),
                fileSizeMB = o.get("FileSizeMB")?.asDouble ?: 0.0,
                remainingSizeMB = o.get("RemainingSizeMB")?.asDouble ?: 0.0,
                pausedSizeMB = o.get("PausedSizeMB")?.asDouble ?: 0.0
            )
        }
    }

    suspend fun listHistory(includeHidden: Boolean = false): List<HistoryItem> {
        val arr = call("history", listOf(includeHidden)).asJsonArray
        return arr.map { el ->
            val o = el.asJsonObject
            HistoryItem(
                nzbId = o.get("NZBID")?.asInt ?: 0,
                name = o.get("Name")?.asString ?: "Unknown",
                category = o.get("Category")?.asString.orEmpty(),
                status = o.get("Status")?.asString.orEmpty(),
                kind = o.get("Kind")?.asString.orEmpty(),
                fileSizeMB = o.get("FileSizeMB")?.asDouble ?: 0.0
            )
        }
    }

    /**
     * Add an NZB by direct download URL (e.g. a Newznab indexer's getnzb link).
     * Returns the new NZBID, or throws if NZBGet rejected it (returns <= 0).
     */
    suspend fun addNzbByUrl(
        url: String,
        category: String = "",
        priority: Int = 0,
        addPaused: Boolean = false,
        addToTop: Boolean = false
    ): Int {
        val result = call(
            "append",
            listOf(
                "",              // Filename empty => filename derived from URL/headers
                url,              // Content = the URL itself
                category,
                priority,
                addToTop,
                addPaused,
                "",               // DupeKey
                0,                // DupeScore
                "SCORE",          // DupeMode
                true,             // AutoCategory
                JsonArray()       // PPParameters
            )
        )
        val nzbId = result.asInt
        if (nzbId <= 0) throw NzbGetException("NZBGet rejected the URL (returned $nzbId)")
        return nzbId
    }

    suspend fun pauseQueueItem(nzbId: Int) = editQueue("GroupPause", listOf(nzbId))
    suspend fun resumeQueueItem(nzbId: Int) = editQueue("GroupResume", listOf(nzbId))
    suspend fun deleteQueueItem(nzbId: Int) = editQueue("GroupDelete", listOf(nzbId))
    suspend fun deleteHistoryItem(nzbId: Int, permanent: Boolean = false) =
        editQueue(if (permanent) "HistoryFinalDelete" else "HistoryDelete", listOf(nzbId))

    private suspend fun editQueue(command: String, ids: List<Int>) {
        call("editqueue", listOf(command, "", ids))
    }

    suspend fun pauseAll() { call("pausedownload") }
    suspend fun resumeAll() { call("resumedownload") }
}

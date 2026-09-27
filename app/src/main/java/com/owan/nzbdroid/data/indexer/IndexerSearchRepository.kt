package com.owan.nzbdroid.data.indexer

import com.owan.nzbdroid.data.settings.IndexerConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** One indexer's search either succeeded with results, or failed — surfaced to the UI either way. */
sealed class IndexerSearchOutcome {
    data class Success(val indexerName: String, val results: List<SearchResult>) : IndexerSearchOutcome()
    data class Failure(val indexerName: String, val error: String) : IndexerSearchOutcome()
}

data class MultiIndexerSearchResult(
    val merged: List<SearchResult>,
    val outcomes: List<IndexerSearchOutcome>
)

/**
 * Searches every enabled indexer in parallel and merges the results. A slow or broken
 * indexer never blocks the others — each one's failure is reported individually so the
 * UI can show "3 of 4 indexers responded" instead of failing the whole search.
 */
class IndexerSearchRepository {

    suspend fun searchAll(indexers: List<IndexerConfig>, query: String): MultiIndexerSearchResult =
        coroutineScope {
            val enabled = indexers.filter { it.enabled && it.isConfigured }

            val outcomes = enabled.map { indexer ->
                async {
                    try {
                        val results = NewznabClient(indexer).search(query)
                        IndexerSearchOutcome.Success(indexer.name, results)
                    } catch (e: IndexerException) {
                        IndexerSearchOutcome.Failure(indexer.name, e.message ?: "search failed")
                    } catch (e: Exception) {
                        IndexerSearchOutcome.Failure(indexer.name, e.message ?: "search failed")
                    }
                }
            }.map { it.await() }

            val merged = outcomes
                .filterIsInstance<IndexerSearchOutcome.Success>()
                .flatMap { it.results }
                .sortedByDescending { it.pubDateMillis }

            MultiIndexerSearchResult(merged, outcomes)
        }
}

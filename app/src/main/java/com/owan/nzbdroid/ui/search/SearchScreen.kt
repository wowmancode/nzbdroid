package com.owan.nzbdroid.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.owan.nzbdroid.AppContainer
import com.owan.nzbdroid.data.indexer.IndexerSearchOutcome
import com.owan.nzbdroid.data.indexer.SearchResult
import com.owan.nzbdroid.data.nzbget.NzbGetClient
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(container: AppContainer) {
    val indexers by container.settingsRepository.indexers.collectAsState()
    val nzbGetConfig by container.settingsRepository.nzbGet.collectAsState()
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var outcomes by remember { mutableStateOf<List<IndexerSearchOutcome>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SearchResult?>(null) }
    var sendMessage by remember { mutableStateOf<String?>(null) }

    fun runSearch() {
        if (query.isBlank()) return
        searching = true
        scope.launch {
            val outcome = container.indexerSearchRepository.searchAll(indexers, query.trim())
            results = outcome.merged
            outcomes = outcome.outcomes
            searching = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Search") }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search all indexers") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { runSearch() }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }

            when {
                indexers.none { it.enabled && it.isConfigured } ->
                    EmptyState("Add at least one indexer in Settings to search.")
                searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                results.isEmpty() && outcomes.isNotEmpty() ->
                    EmptyState("No results. ${failureSummary(outcomes)}")
                else -> Column(Modifier.fillMaxSize()) {
                    if (outcomes.any { it is IndexerSearchOutcome.Failure }) {
                        Text(
                            failureSummary(outcomes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results) { r ->
                            ResultRow(r, onClick = { selected = r })
                        }
                    }
                }
            }
        }
    }

    selected?.let { result ->
        ResultDetailDialog(
            result = result,
            sending = false,
            onDismiss = { selected = null },
            onDownload = { category ->
                scope.launch {
                    try {
                        NzbGetClient(nzbGetConfig).addNzbByUrl(result.downloadUrl, category = category)
                        sendMessage = "Sent \"${result.title}\" to NZBGet"
                    } catch (e: Exception) {
                        sendMessage = "Failed to send: ${e.message}"
                    }
                    selected = null
                }
            }
        )
    }

    sendMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            sendMessage = null
        }
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            Card {
                Text(msg, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

private fun failureSummary(outcomes: List<IndexerSearchOutcome>): String {
    val failures = outcomes.filterIsInstance<IndexerSearchOutcome.Failure>()
    if (failures.isEmpty()) return ""
    val ok = outcomes.size - failures.size
    return "$ok of ${outcomes.size} indexers responded (${failures.joinToString { it.indexerName }} failed)."
}

@Composable
private fun ResultRow(result: SearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(result.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                "${result.sizeDisplay} · ${result.indexerName}" +
                    if (result.pubDateMillis > 0) " · ${DateFormat.getDateInstance().format(Date(result.pubDateMillis))}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultDetailDialog(
    result: SearchResult,
    sending: Boolean,
    onDismiss: () -> Unit,
    onDownload: (category: String) -> Unit
) {
    var category by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(result.title) },
        text = {
            Column {
                Text("Size: ${result.sizeDisplay}")
                Text("Indexer: ${result.indexerName}")
                if (result.category.isNotBlank()) Text("Category: ${result.category}")
                if (result.pubDateMillis > 0) {
                    Text("Posted: ${DateFormat.getDateTimeInstance().format(Date(result.pubDateMillis))}")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("NZBGet category (optional)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDownload(category) }) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

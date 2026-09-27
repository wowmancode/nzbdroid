package com.owan.nzbdroid.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owan.nzbdroid.AppContainer
import com.owan.nzbdroid.data.nzbget.HistoryItem
import com.owan.nzbdroid.data.nzbget.NzbGetClient
import com.owan.nzbdroid.data.nzbget.NzbGetStatus
import com.owan.nzbdroid.data.nzbget.QueueItem
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(container: AppContainer) {
    val config by container.settingsRepository.nzbGet.collectAsState()
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<NzbGetStatus?>(null) }
    var queue by remember { mutableStateOf<List<QueueItem>>(emptyList()) }
    var history by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    // Polls NZBGet every 4s while this screen is on screen. Restarts whenever the
    // server config changes (e.g. the user just filled in Settings for the first time).
    LaunchedEffect(config) {
        if (!config.isConfigured) {
            loading = false
            return@LaunchedEffect
        }
        val client = NzbGetClient(config)
        while (isActive) {
            try {
                status = client.status()
                queue = client.listQueue()
                history = client.listHistory().take(20)
                error = null
            } catch (e: Exception) {
                error = e.message ?: "Couldn't reach NZBGet"
            }
            loading = false
            kotlinx.coroutines.delay(4.seconds)
        }
    }

    fun client() = NzbGetClient(config)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Queue") })
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !config.isConfigured -> EmptyState("Set up your NZBGet server in Settings to see your queue.")
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> Column(Modifier.fillMaxSize()) {
                    error?.let {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }

                    status?.let { s ->
                        StatusBar(
                            status = s,
                            onTogglePause = {
                                scope.launch {
                                    runCatching {
                                        if (s.downloadPaused) client().resumeAll() else client().pauseAll()
                                    }
                                }
                            }
                        )
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (queue.isEmpty() && history.isEmpty()) {
                            item { EmptyState("Nothing in the queue right now.") }
                        }

                        if (queue.isNotEmpty()) {
                            item { SectionHeader("Queue (${queue.size})") }
                            items(queue, key = { it.nzbId }) { qi ->
                                QueueRow(
                                    item = qi,
                                    onPauseResume = {
                                        scope.launch {
                                            runCatching {
                                                if (qi.isPaused) client().resumeQueueItem(qi.nzbId)
                                                else client().pauseQueueItem(qi.nzbId)
                                            }
                                        }
                                    },
                                    onDelete = {
                                        scope.launch { runCatching { client().deleteQueueItem(qi.nzbId) } }
                                    }
                                )
                            }
                        }

                        if (history.isNotEmpty()) {
                            item { SectionHeader("Recent history") }
                            items(history, key = { it.nzbId }) { hi ->
                                HistoryRow(
                                    item = hi,
                                    onDelete = {
                                        scope.launch { runCatching { client().deleteHistoryItem(hi.nzbId) } }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBar(status: NzbGetStatus, onTogglePause: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val speedText = if (status.downloadPaused) "Paused" else
                    "%.1f MB/s".format(status.downloadRateKBps / 1024.0)
                Text(speedText, style = MaterialTheme.typography.titleMedium)
                Text(
                    "%.0f MB remaining · %.0f GB free".format(
                        status.remainingSizeMB, status.freeDiskSpaceMB / 1024.0
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onTogglePause) {
                Text(if (status.downloadPaused) "Resume all" else "Pause all")
            }
        }
    }
}

@Composable
private fun QueueRow(item: QueueItem, onPauseResume: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    Text(
                        "${item.category.ifBlank { "no category" }} · ${item.progressPercent}% · ${item.status}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onPauseResume) {
                    Icon(
                        if (item.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (item.isPaused) "Resume" else "Pause"
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { item.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Text(
                    "${item.category.ifBlank { "no category" }} · ${item.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove from history")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

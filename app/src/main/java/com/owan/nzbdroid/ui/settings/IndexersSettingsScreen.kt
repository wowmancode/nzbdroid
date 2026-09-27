package com.owan.nzbdroid.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.owan.nzbdroid.AppContainer
import com.owan.nzbdroid.data.settings.IndexerConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexersSettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val indexers by container.settingsRepository.indexers.collectAsState()
    var editing by remember { mutableStateOf<IndexerConfig?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Indexers") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add indexer")
            }
        }
    ) { padding ->
        if (indexers.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(padding).padding(32.dp)
            ) {
                Text(
                    "No indexers yet. Add a Newznab-compatible indexer (NZBGeek, NZBHydra2, DrunkenSlug, a private tracker's Newznab API, etc.) with its URL and API key.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().padding(padding)) {
                items(indexers, key = { it.id }) { indexer ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        onClick = { editing = indexer }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(indexer.name.ifBlank { "Unnamed indexer" }, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    indexer.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = indexer.enabled,
                                onCheckedChange = {
                                    container.settingsRepository.upsertIndexer(indexer.copy(enabled = it))
                                }
                            )
                            IconButton(onClick = { container.settingsRepository.removeIndexer(indexer.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        IndexerEditDialog(
            initial = IndexerConfig(),
            onDismiss = { showAddDialog = false },
            onSave = {
                container.settingsRepository.upsertIndexer(it)
                showAddDialog = false
            }
        )
    }

    editing?.let { config ->
        IndexerEditDialog(
            initial = config,
            onDismiss = { editing = null },
            onSave = {
                container.settingsRepository.upsertIndexer(it)
                editing = null
            }
        )
    }
}

@Composable
private fun IndexerEditDialog(
    initial: IndexerConfig,
    onDismiss: () -> Unit,
    onSave: (IndexerConfig) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank() && initial.url.isBlank()) "Add indexer" else "Edit indexer") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.nzbgeek.info") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim().ifBlank { url.substringAfter("://").substringBefore("/") },
                            url = url.trim().trimEnd('/'),
                            apiKey = apiKey.trim()
                        )
                    )
                },
                enabled = url.isNotBlank() && apiKey.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

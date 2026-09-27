package com.owan.nzbdroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onOpenNzbGet: () -> Unit,
    onOpenIndexers: () -> Unit,
    onOpenSmb: () -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(Modifier.fillMaxWidth().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("NZBGet server") },
                    supportingContent = { Text("Host, port, and login") },
                    leadingContent = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenNzbGet)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Indexers") },
                    supportingContent = { Text("Newznab-compatible search sources") },
                    leadingContent = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenIndexers)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("SMB share") },
                    supportingContent = { Text("Network folder for upload/download") },
                    leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenSmb)
                )
            }
        }
    }
}

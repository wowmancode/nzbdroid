package com.owan.nzbdroid.ui.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.owan.nzbdroid.AppContainer
import com.owan.nzbdroid.data.smb.SmbClient
import com.owan.nzbdroid.data.smb.SmbEntry
import kotlinx.coroutines.launch
import java.io.File

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) { value /= 1024; i++ }
    return "%.1f %s".format(value, units[i])
}

private sealed class Transfer {
    data class Downloading(val name: String, val progress: Float) : Transfer()
    data class Uploading(val name: String, val progress: Float) : Transfer()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(container: AppContainer) {
    val smbConfig by container.settingsRepository.smb.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var currentPath by remember { mutableStateOf(smbConfig.startPath.trim('/')) }
    var entries by remember { mutableStateOf<List<SmbEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var transfer by remember { mutableStateOf<Transfer?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    fun client() = SmbClient(smbConfig)

    LaunchedEffect(smbConfig, currentPath, refreshTick) {
        if (!smbConfig.isConfigured) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            entries = client().listDirectory(currentPath)
        } catch (e: Exception) {
            error = e.message ?: "Couldn't reach the share"
        }
        loading = false
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = queryDisplayName(context, uri) ?: "upload_${System.currentTimeMillis()}"
            transfer = Transfer.Uploading(name, 0f)
            try {
                val local = FileTransferUtils.copyUriToCacheFile(context, uri, name)
                val destPath = (currentPath.trim('/') + "/" + name).trim('/')
                client().uploadFile(local, destPath) { done, total ->
                    if (total > 0) transfer = Transfer.Uploading(name, done.toFloat() / total)
                }
                local.delete()
                refreshTick++
            } catch (e: Exception) {
                error = "Upload failed: ${e.message}"
            }
            transfer = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (currentPath.isBlank()) smbConfig.share.ifBlank { "Files" } else currentPath.substringAfterLast('/')) },
                navigationIcon = {
                    if (currentPath.isNotBlank()) {
                        IconButton(onClick = {
                            currentPath = currentPath.substringBeforeLast('/', "").let {
                                if (it == currentPath) "" else it
                            }
                        }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Up a folder")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (smbConfig.isConfigured) {
                FloatingActionButton(onClick = { pickFileLauncher.launch("*/*") }) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = "Upload a file here")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            transfer?.let { t ->
                val (label, progress) = when (t) {
                    is Transfer.Downloading -> "Downloading ${t.name}" to t.progress
                    is Transfer.Uploading -> "Uploading ${t.name}" to t.progress
                }
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(label, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }

            when {
                !smbConfig.isConfigured -> EmptyState("Set up your SMB share in Settings to browse files.")
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null -> EmptyState(error!!)
                entries.isEmpty() -> EmptyState("This folder is empty.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.path }) { entry ->
                        EntryRow(
                            entry = entry,
                            onClick = {
                                if (entry.isDirectory) {
                                    currentPath = entry.path
                                }
                            },
                            onDownload = {
                                scope.launch {
                                    transfer = Transfer.Downloading(entry.name, 0f)
                                    try {
                                        val tmp = File(context.cacheDir, "dl_${System.currentTimeMillis()}_${entry.name}")
                                        client().downloadFile(entry.path, tmp) { done, total ->
                                            if (total > 0) transfer = Transfer.Downloading(entry.name, done.toFloat() / total)
                                        }
                                        FileTransferUtils.publishToDownloads(context, tmp, entry.name)
                                    } catch (e: Exception) {
                                        error = "Download failed: ${e.message}"
                                    }
                                    transfer = null
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    runCatching { client().deleteFile(entry.path) }
                                    refreshTick++
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: SmbEntry,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                contentDescription = null,
                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, maxLines = 1)
                if (!entry.isDirectory) {
                    Text(
                        formatBytes(entry.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!entry.isDirectory) {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Filled.Download, contentDescription = "Download")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && nameIndex >= 0) return it.getString(nameIndex)
    }
    return null
}

package com.owan.nzbdroid.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.owan.nzbdroid.AppContainer
import com.owan.nzbdroid.data.settings.SmbConfig
import com.owan.nzbdroid.data.smb.SmbClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmbSettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val saved by container.settingsRepository.smb.collectAsState()
    val scope = rememberCoroutineScope()

    var host by remember(saved) { mutableStateOf(saved.host) }
    var share by remember(saved) { mutableStateOf(saved.share) }
    var domain by remember(saved) { mutableStateOf(saved.domain) }
    var username by remember(saved) { mutableStateOf(saved.username) }
    var password by remember(saved) { mutableStateOf(saved.password) }
    var startPath by remember(saved) { mutableStateOf(saved.startPath) }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    fun currentConfig() = SmbConfig(
        host = host.trim(),
        share = share.trim(),
        domain = domain.trim(),
        username = username.trim(),
        password = password,
        startPath = startPath.trim()
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMB share") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host or IP") },
                placeholder = { Text("192.168.1.10") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = share,
                onValueChange = { share = it },
                label = { Text("Share name") },
                placeholder = { Text("media") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = startPath,
                onValueChange = { startPath = it },
                label = { Text("Starting folder (optional)") },
                placeholder = { Text("Downloads/complete") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Domain (leave blank unless on AD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            testResult?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            }

            Row {
                Button(
                    onClick = {
                        testing = true
                        testResult = null
                        val config = currentConfig()
                        scope.launch {
                            val ok = runCatching { SmbClient(config).testConnection() }.getOrDefault(false)
                            testResult = if (ok) "Connected successfully" else "Couldn't connect — check host/share/credentials"
                            testing = false
                        }
                    },
                    enabled = !testing && host.isNotBlank() && share.isNotBlank()
                ) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Test connection")
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        container.settingsRepository.saveSmb(currentConfig())
                        onBack()
                    },
                    enabled = host.isNotBlank() && share.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        }
    }
}

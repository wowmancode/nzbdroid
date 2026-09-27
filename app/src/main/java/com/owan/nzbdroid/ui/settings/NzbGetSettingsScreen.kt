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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.owan.nzbdroid.AppContainer
import com.owan.nzbdroid.data.nzbget.NzbGetClient
import com.owan.nzbdroid.data.settings.NzbGetConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NzbGetSettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val saved by container.settingsRepository.nzbGet.collectAsState()
    val scope = rememberCoroutineScope()

    var host by remember(saved) { mutableStateOf(saved.host) }
    var port by remember(saved) { mutableStateOf(saved.port.toString()) }
    var username by remember(saved) { mutableStateOf(saved.username) }
    var password by remember(saved) { mutableStateOf(saved.password) }
    var useSsl by remember(saved) { mutableStateOf(saved.useSsl) }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NZBGet server") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host or IP") },
                placeholder = { Text("192.168.1.50") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username (ControlUsername)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (ControlPassword)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use HTTPS", modifier = Modifier.weight(1f))
                Switch(checked = useSsl, onCheckedChange = { useSsl = it })
            }

            Spacer(Modifier.height(16.dp))

            testResult?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            }

            Row {
                Button(
                    onClick = {
                        val config = NzbGetConfig(
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 6789,
                            username = username.trim(),
                            password = password,
                            useSsl = useSsl
                        )
                        testing = true
                        testResult = null
                        scope.launch {
                            val ok = NzbGetClient(config).testConnection()
                            testResult = if (ok) "Connected successfully" else "Couldn't connect — check host/port/credentials"
                            testing = false
                        }
                    },
                    enabled = !testing && host.isNotBlank()
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
                        container.settingsRepository.saveNzbGet(
                            NzbGetConfig(
                                host = host.trim(),
                                port = port.toIntOrNull() ?: 6789,
                                username = username.trim(),
                                password = password,
                                useSsl = useSsl
                            )
                        )
                        onBack()
                    },
                    enabled = host.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        }
    }
}

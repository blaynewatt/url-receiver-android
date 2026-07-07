package com.example.urlreceiver.ui.main

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.urlreceiver.HistoryItem
import com.example.urlreceiver.theme.UrlReceiverTheme

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: MainScreenViewModel = viewModel {
        MainScreenViewModel(context.applicationContext as Application)
    }

    val port by viewModel.portState.collectAsStateWithLifecycle()
    val origins by viewModel.originsState.collectAsStateWithLifecycle()
    val autoOpen by viewModel.autoOpenState.collectAsStateWithLifecycle()
    val autoCopy by viewModel.autoCopyState.collectAsStateWithLifecycle()
    val isClientMode by viewModel.isClientModeState.collectAsStateWithLifecycle()
    val relayUrl by viewModel.relayUrlState.collectAsStateWithLifecycle()
    val secretToken by viewModel.secretTokenState.collectAsStateWithLifecycle()
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val urlHistory by viewModel.urlHistory.collectAsStateWithLifecycle()

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
            if (isGranted) {
                viewModel.startService()
            } else {
                Toast.makeText(context, "Notification permission is required to run the background service.", Toast.LENGTH_LONG).show()
            }
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "URL Receiver",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        StatusCard(
            isRunning = serverState.isRunning,
            isClientMode = serverState.isClientMode,
            port = serverState.port,
            relayUrl = serverState.relayUrl,
            onToggleServer = {
                if (serverState.isRunning) {
                    viewModel.stopService()
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.startService()
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        ConfigurationCard(
            port = port,
            origins = origins,
            isClientMode = isClientMode,
            relayUrl = relayUrl,
            secretToken = secretToken,
            autoOpen = autoOpen,
            autoCopy = autoCopy,
            isRunning = serverState.isRunning,
            onPortChange = { viewModel.setPort(it) },
            onOriginsChange = { viewModel.setOrigins(it) },
            onIsClientModeChange = { viewModel.setIsClientMode(it) },
            onRelayUrlChange = { viewModel.setRelayUrl(it) },
            onSecretTokenChange = { viewModel.setSecretToken(it) },
            onAutoOpenChange = { viewModel.setAutoOpen(it) },
            onAutoCopyChange = { viewModel.setAutoCopy(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        LogsCard(
            urlHistory = urlHistory,
            onClear = { viewModel.clearHistory() },
            onCopy = { url ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Received URL", url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            onOpen = { url ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open URL", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun StatusCard(
    isRunning: Boolean,
    isClientMode: Boolean,
    port: Int,
    relayUrl: String,
    onToggleServer: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )

                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRunning) Color.Green.copy(alpha = alpha) else Color.Gray.copy(
                                    alpha = alpha
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) {
                            if (isClientMode) "Client: ACTIVE" else "Server: ACTIVE"
                        } else {
                            if (isClientMode) "Client: INACTIVE" else "Server: INACTIVE"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = if (isRunning) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
                    )
                }
                if (isRunning) {
                    Text(
                        text = if (isClientMode) "Connected to $relayUrl" else "Listening on port $port",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (isClientMode && relayUrl.isNotEmpty()) {
                    Text(
                        text = "Disconnected from $relayUrl",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onToggleServer,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = if (isRunning) "Stop" else "Start")
            }
        }
    }
}

@Composable
fun ConfigurationCard(
    port: Int,
    origins: String,
    isClientMode: Boolean,
    relayUrl: String,
    secretToken: String,
    autoOpen: Boolean,
    autoCopy: Boolean,
    isRunning: Boolean,
    onPortChange: (Int) -> Unit,
    onOriginsChange: (String) -> Unit,
    onIsClientModeChange: (Boolean) -> Unit,
    onRelayUrlChange: (String) -> Unit,
    onSecretTokenChange: (String) -> Unit,
    onAutoOpenChange: (Boolean) -> Unit,
    onAutoCopyChange: (Boolean) -> Unit
) {
    var portText by remember(port) { mutableStateOf(port.toString()) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Configuration",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Segmented Mode Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(
                    onClick = { onIsClientModeChange(false) },
                    enabled = !isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (!isClientMode) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                ) {
                    Text(
                        text = "Local Server",
                        color = if (!isClientMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                TextButton(
                    onClick = { onIsClientModeChange(true) },
                    enabled = !isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isClientMode) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                ) {
                    Text(
                        text = "Cloud Relay",
                        color = if (isClientMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            if (!isClientMode) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = {
                        portText = it
                        it.toIntOrNull()?.let { p -> onPortChange(p) }
                    },
                    label = { Text("Server Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunning,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = origins,
                    onValueChange = onOriginsChange,
                    label = { Text("Whitelisted Origins (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunning,
                    singleLine = true
                )
            } else {
                OutlinedTextField(
                    value = relayUrl,
                    onValueChange = onRelayUrlChange,
                    label = { Text("Relay Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunning,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = secretToken,
                    onValueChange = onSecretTokenChange,
                    label = { Text("Secret Session Token") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunning,
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Auto-open URLs in browser",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = autoOpen,
                    onCheckedChange = onAutoOpenChange
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Auto-copy URLs to clipboard",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = autoCopy,
                    onCheckedChange = onAutoCopyChange
                )
            }
        }
    }
}

@Composable
fun LogsCard(
    urlHistory: List<HistoryItem>,
    onClear: () -> Unit,
    onCopy: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Received URLs Log",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (urlHistory.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (urlHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No URLs received yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(urlHistory) { item ->
                        LogItem(
                            item = item,
                            onCopy = { onCopy(item.url) },
                            onOpen = { onOpen(item.url) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogItem(
    item: HistoryItem,
    onCopy: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.timestamp,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.url,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row {
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Link",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onOpen) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Open in Browser",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    UrlReceiverTheme {
        MainScreen(onItemClick = {})
    }
}

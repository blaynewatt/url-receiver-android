package com.example.urlreceiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WebSocketService : Service() {

    private val binder = LocalBinder()
    private var server: UrlWebSocketServer? = null
    private var client: UrlWebSocketClient? = null

    // For auto-reconnection
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private var isServiceRunning = false

    private var currentPort = 8080
    private var currentOrigins = emptySet<String>()
    private var isClientMode = false
    private var currentRelayUrl = ""
    private var currentSecretToken = ""
    private var autoOpenUrls = true
    private var autoCopyUrls = false

    inner class LocalBinder : Binder() {
        fun getService(): WebSocketService = this@WebSocketService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            stopAllWithFinalShutdown()
            stopSelf()
            return START_NOT_STICKY
        }

        isServiceRunning = true

        currentPort = intent?.getIntExtra("PORT", 8080) ?: 8080
        val originsString = intent?.getStringExtra("WHITELISTED_ORIGINS") ?: "http://localhost:3000"
        currentOrigins = originsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        autoOpenUrls = intent?.getBooleanExtra("AUTO_OPEN", true) ?: true
        autoCopyUrls = intent?.getBooleanExtra("AUTO_COPY", false) ?: false

        isClientMode = intent?.getBooleanExtra("IS_CLIENT_MODE", false) ?: false
        currentRelayUrl = intent?.getStringExtra("RELAY_URL") ?: "ws://localhost:8082"
        currentSecretToken = intent?.getStringExtra("SECRET_TOKEN") ?: "default-secret-token"

        if (isClientMode) {
            startCloudClient()
        } else {
            startLocalServer()
        }

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun startLocalServer() {
        stopAll()
        try {
            server = UrlWebSocketServer(
                port = currentPort,
                whitelistedOrigins = currentOrigins,
                onServerStarted = {
                    Companion.setRunningState(
                        isRunning = true,
                        port = currentPort,
                        origins = currentOrigins,
                        isClientMode = false,
                        relayUrl = "",
                        secretToken = ""
                    )
                },
                onServerError = { ex ->
                    Log.e("WebSocketService", "Server error callback", ex)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(this, "Server failed: ${ex?.message}", Toast.LENGTH_LONG).show()
                    }
                    Companion.setRunningState(false, 0, emptySet(), false, "", "")
                    stopSelf()
                },
                onUrlReceived = { url ->
                    handleUrlReceived(url)
                }
            )
            server?.isReuseAddr = true
            server?.start()
        } catch (e: Exception) {
            Log.e("WebSocketService", "Failed to start local server", e)
            Toast.makeText(this, "Failed to start server: ${e.message}", Toast.LENGTH_LONG).show()
            Companion.setRunningState(false, 0, emptySet(), false, "", "")
        }
    }

    private fun startCloudClient() {
        stopAll()
        val cleanUrl = currentRelayUrl.trim().replace(Regex("/$"), "")
        val formattedUrl = if (cleanUrl.startsWith("http://")) {
            cleanUrl.replace("http://", "ws://")
        } else if (cleanUrl.startsWith("https://")) {
            cleanUrl.replace("https://", "wss://")
        } else if (!cleanUrl.startsWith("ws://") && !cleanUrl.startsWith("wss://")) {
            "ws://$cleanUrl"
        } else {
            cleanUrl
        }

        val fullUriString = "$formattedUrl/phone?token=$currentSecretToken"
        try {
            val uri = URI(fullUriString)
            Log.i("WebSocketService", "Connecting client to relay server: $uri")
            
            client = UrlWebSocketClient(
                serverUri = uri,
                onConnectionOpened = {
                    Companion.setRunningState(
                        isRunning = true,
                        port = 0,
                        origins = emptySet(),
                        isClientMode = true,
                        relayUrl = currentRelayUrl,
                        secretToken = currentSecretToken
                    )
                    // Cancel any pending reconnection attempts
                    reconnectRunnable?.let {
                        handler.removeCallbacks(it)
                        reconnectRunnable = null
                    }
                },
                onConnectionClosed = { code, reason, remote ->
                    Log.w("WebSocketService", "Client connection closed. Code: $code, Reason: $reason")
                    Companion.setRunningState(
                        isRunning = false,
                        port = 0,
                        origins = emptySet(),
                        isClientMode = true,
                        relayUrl = currentRelayUrl,
                        secretToken = currentSecretToken
                    )
                    // If service is supposed to be running, trigger reconnection
                    if (isServiceRunning) {
                        scheduleReconnection()
                    }
                },
                onMessageReceived = { url ->
                    if (url == "Connected to relay server") {
                        Log.i("WebSocketService", "Relay server handshake greeting received")
                    } else {
                        handleUrlReceived(url)
                    }
                },
                onErrorOccurred = { ex ->
                    Log.e("WebSocketService", "Client error occurred: ", ex)
                    Companion.setRunningState(
                        isRunning = false,
                        port = 0,
                        origins = emptySet(),
                        isClientMode = true,
                        relayUrl = currentRelayUrl,
                        secretToken = currentSecretToken
                    )
                    if (isServiceRunning) {
                        scheduleReconnection()
                    }
                }
            )
            client?.connect()
        } catch (e: Exception) {
            Log.e("WebSocketService", "Invalid URI or client start failed", e)
            Toast.makeText(this, "Relay URL error: ${e.message}", Toast.LENGTH_SHORT).show()
            Companion.setRunningState(false, 0, emptySet(), true, currentRelayUrl, currentSecretToken)
        }
    }

    private fun scheduleReconnection() {
        if (reconnectRunnable != null) return // Already scheduled

        reconnectRunnable = Runnable {
            reconnectRunnable = null // Reset reference when it runs
            if (isServiceRunning && isClientMode) {
                Log.i("WebSocketService", "Attempting auto-reconnection to relay...")
                startCloudClient()
            }
        }
        handler.postDelayed(reconnectRunnable!!, 4000)
    }

    private fun handleUrlReceived(url: String) {
        Log.d("WebSocketService", "Received URL to process: $url")
        Companion.emitUrl(url)
        if (autoCopyUrls) {
            copyToClipboard(url)
        }
        if (autoOpenUrls) {
            openUrlInBrowser(url)
        }
    }

    private fun stopAll() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = null

        server?.let {
            try {
                it.stop(1000)
            } catch (e: Exception) {
                Log.e("WebSocketService", "Error stopping server", e)
            }
        }
        server = null

        client?.let {
            try {
                it.close()
            } catch (e: Exception) {
                Log.e("WebSocketService", "Error closing client", e)
            }
        }
        client = null

        Companion.setRunningState(false, 0, emptySet(), isClientMode, currentRelayUrl, currentSecretToken)
    }

    private fun stopAllWithFinalShutdown() {
        isServiceRunning = false
        stopAll()
    }

    private fun openUrlInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("WebSocketService", "Failed to open URL in browser", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Invalid URL received: $url", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        try {
            Handler(Looper.getMainLooper()).post {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Received URL", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("WebSocketService", "Failed to copy to clipboard", e)
        }
    }

    override fun onDestroy() {
        stopAllWithFinalShutdown()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "URL Receiver Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of the URL Receiver service"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, WebSocketService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val textContent = if (isClientMode) "Connecting/Connected to Cloud Relay..." else "Listening for local connections..."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("URL Receiver Active")
            .setContentText(textContent)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "UrlReceiverChannel"
        private const val NOTIFICATION_ID = 1

        private val _urlFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val urlFlow: SharedFlow<String> = _urlFlow

        private val _serverStateFlow = MutableSharedFlow<ServerState>(extraBufferCapacity = 1)
        val serverStateFlow: SharedFlow<ServerState> = _serverStateFlow

        private val _urlHistory = MutableStateFlow<List<HistoryItem>>(emptyList())
        val urlHistoryFlow: StateFlow<List<HistoryItem>> = _urlHistory.asStateFlow()

        // Initial state
        var lastState = ServerState(false, 0, emptySet(), false, "", "")
            private set

        fun emitUrl(url: String) {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val newItem = HistoryItem(url, timestamp)
            _urlHistory.update { listOf(newItem) + it }
            _urlFlow.tryEmit(url)
        }

        fun clearHistory() {
            _urlHistory.value = emptyList()
        }

        fun setRunningState(
            isRunning: Boolean,
            port: Int,
            origins: Set<String>,
            isClientMode: Boolean,
            relayUrl: String,
            secretToken: String
        ) {
            val state = ServerState(isRunning, port, origins, isClientMode, relayUrl, secretToken)
            lastState = state
            _serverStateFlow.tryEmit(state)
        }
    }
}

data class ServerState(
    val isRunning: Boolean,
    val port: Int,
    val whitelistedOrigins: Set<String>,
    val isClientMode: Boolean = false,
    val relayUrl: String = "",
    val secretToken: String = ""
)

data class HistoryItem(
    val url: String,
    val timestamp: String
)

package com.example.urlreceiver

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class UrlWebSocketClient(
    serverUri: URI,
    private val onConnectionOpened: () -> Unit,
    private val onConnectionClosed: (code: Int, reason: String?, remote: Boolean) -> Unit,
    private val onMessageReceived: (String) -> Unit,
    private val onErrorOccurred: (Exception?) -> Unit
) : WebSocketClient(serverUri) {

    override fun onOpen(handshakedata: ServerHandshake?) {
        Log.i("UrlWebSocketClient", "Connection opened successfully to relay server")
        onConnectionOpened()
    }

    override fun onMessage(message: String?) {
        Log.d("UrlWebSocketClient", "Message received from relay: $message")
        if (!message.isNullOrBlank()) {
            onMessageReceived(message.trim())
        }
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        Log.i("UrlWebSocketClient", "Connection closed: $reason ($code)")
        onConnectionClosed(code, reason, remote)
    }

    override fun onError(ex: Exception?) {
        Log.e("UrlWebSocketClient", "Connection error: ", ex)
        onErrorOccurred(ex)
    }
}

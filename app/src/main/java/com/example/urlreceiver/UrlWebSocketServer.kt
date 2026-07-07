package com.example.urlreceiver

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.drafts.Draft
import org.java_websocket.exceptions.InvalidDataException
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.ServerHandshakeBuilder
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class UrlWebSocketServer(
    port: Int,
    private val whitelistedOrigins: Set<String>,
    private val onServerStarted: () -> Unit,
    private val onServerError: (Exception?) -> Unit,
    private val onUrlReceived: (String) -> Unit
) : WebSocketServer(InetSocketAddress(port)) {

    override fun onWebsocketHandshakeReceivedAsServer(
        conn: WebSocket?,
        draft: Draft?,
        request: ClientHandshake?
    ): ServerHandshakeBuilder {
        val origin = request?.getFieldValue("Origin")?.trim() ?: ""
        Log.d("UrlWebSocketServer", "Handshake upgrade request from origin: '$origin'")
        
        val isAllowed = whitelistedOrigins.contains(origin)
        
        if (!isAllowed) {
            Log.w("UrlWebSocketServer", "Origin '$origin' is not in whitelist $whitelistedOrigins. Rejecting handshake.")
            // Throwing InvalidDataException here responds with HTTP 403 Forbidden
            throw InvalidDataException(403, "Forbidden: Origin not whitelisted")
        }
        
        Log.i("UrlWebSocketServer", "Handshake accepted from origin: '$origin'")
        return super.onWebsocketHandshakeReceivedAsServer(conn, draft, request)
    }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        Log.d("UrlWebSocketServer", "Connection opened successfully.")
        conn?.send("Connection accepted")
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        Log.d("UrlWebSocketServer", "Connection closed: $reason ($code)")
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        Log.d("UrlWebSocketServer", "Message received: $message")
        if (!message.isNullOrBlank()) {
            onUrlReceived(message.trim())
            conn?.send("URL Received: $message")
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        Log.e("UrlWebSocketServer", "WebSocket Server Error: ", ex)
        if (conn == null) {
            onServerError(ex)
        }
    }

    override fun onStart() {
        Log.i("UrlWebSocketServer", "WebSocket Server started successfully on port: $port")
        onServerStarted()
    }
}

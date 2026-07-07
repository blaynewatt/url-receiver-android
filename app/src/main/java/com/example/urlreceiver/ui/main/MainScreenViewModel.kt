package com.example.urlreceiver.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.urlreceiver.ServerState
import com.example.urlreceiver.WebSocketService
import com.example.urlreceiver.HistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("UrlReceiverPrefs", Context.MODE_PRIVATE)

    private val _portState = MutableStateFlow(sharedPrefs.getInt("PORT", 8080))
    val portState = _portState.asStateFlow()

    private val _originsState = MutableStateFlow(sharedPrefs.getString("WHITELISTED_ORIGINS", "http://localhost:3000") ?: "http://localhost:3000")
    val originsState = _originsState.asStateFlow()

    private val _autoOpenState = MutableStateFlow(sharedPrefs.getBoolean("AUTO_OPEN", true))
    val autoOpenState = _autoOpenState.asStateFlow()

    private val _autoCopyState = MutableStateFlow(sharedPrefs.getBoolean("AUTO_COPY", false))
    val autoCopyState = _autoCopyState.asStateFlow()

    private val _isClientModeState = MutableStateFlow(sharedPrefs.getBoolean("IS_CLIENT_MODE", false))
    val isClientModeState = _isClientModeState.asStateFlow()

    private val _relayUrlState = MutableStateFlow(sharedPrefs.getString("RELAY_URL", "ws://localhost:8082") ?: "ws://localhost:8082")
    val relayUrlState = _relayUrlState.asStateFlow()

    private val _secretTokenState = MutableStateFlow(sharedPrefs.getString("SECRET_TOKEN", "default-secret-token") ?: "default-secret-token")
    val secretTokenState = _secretTokenState.asStateFlow()

    private val _serverState = MutableStateFlow(WebSocketService.lastState)
    val serverState = _serverState.asStateFlow()

    private val _urlHistory = MutableStateFlow<List<HistoryItem>>(emptyList())
    val urlHistory = _urlHistory.asStateFlow()

    init {
        viewModelScope.launch {
            WebSocketService.serverStateFlow.collect { state ->
                _serverState.value = state
            }
        }

        viewModelScope.launch {
            WebSocketService.urlHistoryFlow.collect { history ->
                _urlHistory.value = history
            }
        }
    }

    fun setPort(port: Int) {
        _portState.value = port
        sharedPrefs.edit().putInt("PORT", port).apply()
    }

    fun setOrigins(origins: String) {
        _originsState.value = origins
        sharedPrefs.edit().putString("WHITELISTED_ORIGINS", origins).apply()
    }

    fun setAutoOpen(enabled: Boolean) {
        _autoOpenState.value = enabled
        sharedPrefs.edit().putBoolean("AUTO_OPEN", enabled).apply()
        if (serverState.value.isRunning) {
            startService()
        }
    }

    fun setAutoCopy(enabled: Boolean) {
        _autoCopyState.value = enabled
        sharedPrefs.edit().putBoolean("AUTO_COPY", enabled).apply()
        if (serverState.value.isRunning) {
            startService()
        }
    }

    fun setIsClientMode(enabled: Boolean) {
        _isClientModeState.value = enabled
        sharedPrefs.edit().putBoolean("IS_CLIENT_MODE", enabled).apply()
        if (serverState.value.isRunning) {
            startService()
        }
    }

    fun setRelayUrl(url: String) {
        _relayUrlState.value = url
        sharedPrefs.edit().putString("RELAY_URL", url).apply()
    }

    fun setSecretToken(token: String) {
        _secretTokenState.value = token
        sharedPrefs.edit().putString("SECRET_TOKEN", token).apply()
    }

    fun startService() {
        val intent = Intent(getApplication(), WebSocketService::class.java).apply {
            putExtra("PORT", portState.value)
            putExtra("WHITELISTED_ORIGINS", originsState.value)
            putExtra("AUTO_OPEN", autoOpenState.value)
            putExtra("AUTO_COPY", autoCopyState.value)
            putExtra("IS_CLIENT_MODE", isClientModeState.value)
            putExtra("RELAY_URL", relayUrlState.value)
            putExtra("SECRET_TOKEN", secretTokenState.value)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    fun stopService() {
        val intent = Intent(getApplication(), WebSocketService::class.java).apply {
            action = "STOP"
        }
        getApplication<Application>().startService(intent)
    }

    fun clearHistory() {
        WebSocketService.clearHistory()
    }
}

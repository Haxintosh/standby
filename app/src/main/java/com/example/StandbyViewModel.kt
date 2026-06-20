package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StandbyViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _plugins = MutableStateFlow<List<String>>(listOf(DefaultPlugins.BUILT_IN_CLOCK_PLUGIN))
    val plugins: StateFlow<List<String>> = _plugins.asStateFlow()

    private val sharedPreferences = application.getSharedPreferences("standby_settings", Context.MODE_PRIVATE)

    private val _burnInProtectionEnabled = MutableStateFlow(sharedPreferences.getBoolean("burn_in_protection", true))
    val burnInProtectionEnabled: StateFlow<Boolean> = _burnInProtectionEnabled.asStateFlow()

    private val _delayAfterInteraction = MutableStateFlow(sharedPreferences.getBoolean("delay_after_interaction", false))
    val delayAfterInteraction: StateFlow<Boolean> = _delayAfterInteraction.asStateFlow()

    private val _protectionRatio = MutableStateFlow(sharedPreferences.getInt("protection_ratio", 1))
    val protectionRatio: StateFlow<Int> = _protectionRatio.asStateFlow()

    private val _hideControlsOnIdle = MutableStateFlow(sharedPreferences.getBoolean("hide_controls_on_idle", true))
    val hideControlsOnIdle: StateFlow<Boolean> = _hideControlsOnIdle.asStateFlow()

    private val _lowRefreshRateEnabled = MutableStateFlow(sharedPreferences.getBoolean("low_refresh_rate_enabled", false))
    val lowRefreshRateEnabled: StateFlow<Boolean> = _lowRefreshRateEnabled.asStateFlow()

    private val _lowRefreshRateValue = MutableStateFlow(sharedPreferences.getInt("low_refresh_rate_value", 60))
    val lowRefreshRateValue: StateFlow<Int> = _lowRefreshRateValue.asStateFlow()

    private var pluginServer: PluginServer? = null

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverPort = MutableStateFlow(0)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _serverPin = MutableStateFlow("")
    val serverPin: StateFlow<String> = _serverPin.asStateFlow()

    private val _serverIp = MutableStateFlow("")
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    init {
        if (sharedPreferences.getBoolean("server_enabled", true)) {
            startServer()
        }
    }

    fun setBurnInProtectionEnabled(enabled: Boolean) {
        _burnInProtectionEnabled.value = enabled
        sharedPreferences.edit().putBoolean("burn_in_protection", enabled).apply()
    }

    fun setDelayAfterInteraction(enabled: Boolean) {
        _delayAfterInteraction.value = enabled
        sharedPreferences.edit().putBoolean("delay_after_interaction", enabled).apply()
    }

    fun setProtectionRatio(ratio: Int) {
        _protectionRatio.value = ratio
        sharedPreferences.edit().putInt("protection_ratio", ratio).apply()
    }

    fun setHideControlsOnIdle(enabled: Boolean) {
        _hideControlsOnIdle.value = enabled
        sharedPreferences.edit().putBoolean("hide_controls_on_idle", enabled).apply()
    }

    fun setLowRefreshRateEnabled(enabled: Boolean) {
        _lowRefreshRateEnabled.value = enabled
        sharedPreferences.edit().putBoolean("low_refresh_rate_enabled", enabled).apply()
    }

    fun setLowRefreshRateValue(value: Int) {
        _lowRefreshRateValue.value = value
        sharedPreferences.edit().putInt("low_refresh_rate_value", value).apply()
    }

    fun setServerEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("server_enabled", enabled).apply()
        if (enabled) {
            startServer()
        } else {
            stopServer()
        }
    }

    private fun startServer() {
        if (pluginServer != null) return
        val server = PluginServer(
            context = getApplication(),
            onPluginReceived = { newHtml ->
                viewModelScope.launch {
                    // Clear the webview and replace with the newly uploaded plugin HTML file
                    _plugins.value = listOf(newHtml)
                }
            }
        )
        if (server.start()) {
            pluginServer = server
            _serverPort.value = server.port
            _serverPin.value = server.pin
            _serverIp.value = server.ipAddress
            _isServerRunning.value = true
        } else {
            _isServerRunning.value = false
        }
    }

    private fun stopServer() {
        pluginServer?.stop()
        pluginServer = null
        _serverPort.value = 0
        _serverPin.value = ""
        _serverIp.value = ""
        _isServerRunning.value = false
    }

    fun loadPluginFromFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader().readText()
                    // When loading from file picker, also clear and show only the loaded plugin
                    _plugins.value = listOf(content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pluginServer?.stop()
    }
}

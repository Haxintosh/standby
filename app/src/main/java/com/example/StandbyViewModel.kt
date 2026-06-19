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

    private var pluginServer: PluginServer? = null

    private val _serverPort = MutableStateFlow(0)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _serverPin = MutableStateFlow("")
    val serverPin: StateFlow<String> = _serverPin.asStateFlow()

    private val _serverIp = MutableStateFlow("")
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    init {
        startServer()
    }

    private fun startServer() {
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
        }
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

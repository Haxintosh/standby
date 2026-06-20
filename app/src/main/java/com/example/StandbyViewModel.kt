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
import java.io.File

class StandbyViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _plugins = MutableStateFlow<List<PluginModel>>(emptyList())
    val plugins: StateFlow<List<PluginModel>> = _plugins.asStateFlow()

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
        loadPlugins()
        if (sharedPreferences.getBoolean("server_enabled", true)) {
            startServer()
        }
    }

    fun loadPlugins() {
        viewModelScope.launch {
            val list = mutableListOf<PluginModel>()
            
            // Add built-in clock plugin first
            list.add(DefaultPlugins.getBuiltInClockPlugin(sharedPreferences))

            // Load registered plugins from disk
            val context = getApplication<Application>()
            val registry = PluginManager.loadRegistry(context)
            for (entry in registry) {
                val plugin = PluginManager.loadPluginDirectory(context, entry.folderName, entry.localId)
                if (plugin != null) {
                    list.add(plugin)
                }
            }
            _plugins.value = list
        }
    }

    fun updateCustomizationValue(pluginLocalId: String, varName: String, varValue: String) {
        val context = getApplication<Application>()
        val currentList = _plugins.value
        val updatedList = currentList.map { plugin ->
            if (plugin.localId == pluginLocalId) {
                if (plugin.isBuiltIn) {
                    sharedPreferences.edit().putString("builtin_customization_${pluginLocalId}_${varName}", varValue).apply()
                    val updatedCustomizations = plugin.customizations.mapValues { (key, option) ->
                        if (key == varName) option.copy(value = varValue) else option
                    }
                    plugin.copy(customizations = updatedCustomizations)
                } else {
                    plugin.directoryPath?.let { dirPath ->
                        val folderName = File(dirPath).name
                        PluginManager.saveCustomizationValue(context, pluginLocalId, folderName, varName, varValue)
                    }
                    val updatedCustomizations = plugin.customizations.mapValues { (key, option) ->
                        if (key == varName) option.copy(value = varValue) else option
                    }
                    plugin.copy(customizations = updatedCustomizations)
                }
            } else {
                plugin
            }
        }
        _plugins.value = updatedList
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
            onPluginReceived = { file, contentType ->
                viewModelScope.launch {
                    try {
                        val context = getApplication<Application>()
                        if (contentType.contains("application/zip") || file.name.endsWith(".zip")) {
                            file.inputStream().use { input ->
                                PluginManager.importZipPlugin(context, input)
                            }
                        } else {
                            val htmlContent = file.readText()
                            PluginManager.importHtmlPlugin(context, htmlContent, "Uploaded Plugin")
                        }
                        loadPlugins()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        file.delete()
                    }
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
                val fileName = PluginManager.getFileName(context, uri) ?: "imported_plugin.zip"
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    if (fileName.endsWith(".zip")) {
                        PluginManager.importZipPlugin(context, inputStream)
                    } else {
                        val htmlContent = inputStream.bufferedReader().readText()
                        PluginManager.importHtmlPlugin(context, htmlContent, fileName)
                    }
                    loadPlugins()
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

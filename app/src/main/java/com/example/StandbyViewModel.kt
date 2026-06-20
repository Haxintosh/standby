package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
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

    private val _standbyPages = MutableStateFlow<List<StandbyPage>>(emptyList())
    val standbyPages: StateFlow<List<StandbyPage>> = _standbyPages.asStateFlow()

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
            
            rebuildStandbyPages()
        }
    }

    private fun rebuildStandbyPages() {
        val context = getApplication<Application>()
        val installed = _plugins.value
        val layout = PluginManager.loadLayoutConfig(context)
        
        val pagesList = mutableListOf<StandbyPage>()
        
        if (layout.isEmpty()) {
            val fullPlugins = installed.filter { it.isBuiltIn || it.size == "full" }
            val halfPlugins = installed.filter { !it.isBuiltIn && it.size == "half" }
            
            for (p in fullPlugins) {
                pagesList.add(StandbyPage.FullWidth(p))
            }
            
            for (i in halfPlugins.indices step 2) {
                val left = halfPlugins[i]
                val right = if (i + 1 < halfPlugins.size) halfPlugins[i + 1] else halfPlugins[i]
                pagesList.add(StandbyPage.HalfWidth(left, right, "default_half_$i"))
            }
            
            if (pagesList.isEmpty()) {
                val defaultClock = installed.firstOrNull { it.localId == "com.example.builtin.clock" }
                if (defaultClock != null) {
                    pagesList.add(StandbyPage.FullWidth(defaultClock))
                }
            }
        } else {
            for (entry in layout) {
                if (entry.type == "full") {
                    val plugin = installed.firstOrNull { it.localId == entry.pluginLocalId }
                        ?: installed.firstOrNull { it.localId == "com.example.builtin.clock" }
                    if (plugin != null) {
                        pagesList.add(StandbyPage.FullWidth(plugin, entry.pageId))
                    }
                } else {
                    val left = installed.firstOrNull { it.localId == entry.leftLocalId }
                        ?: installed.firstOrNull { it.size == "half" }
                        ?: installed.firstOrNull { it.localId == "com.example.builtin.clock" }
                    
                    val right = installed.firstOrNull { it.localId == entry.rightLocalId }
                        ?: installed.firstOrNull { it.size == "half" }
                        ?: installed.firstOrNull { it.localId == "com.example.builtin.clock" }
                    
                    if (left != null && right != null) {
                        pagesList.add(StandbyPage.HalfWidth(left, right, entry.pageId))
                    }
                }
            }
        }
        _standbyPages.value = pagesList
    }

    private fun ensureLayoutConfigExists(context: Context) {
        val file = File(PluginManager.getPluginsDir(context), "pages_layout.json")
        if (!file.exists()) {
            val installed = _plugins.value
            val pagesList = mutableListOf<PluginManager.LayoutEntry>()
            
            val fullPlugins = installed.filter { it.isBuiltIn || it.size == "full" }
            val halfPlugins = installed.filter { !it.isBuiltIn && it.size == "half" }
            
            for (p in fullPlugins) {
                pagesList.add(
                    PluginManager.LayoutEntry(
                        type = "full",
                        pluginLocalId = p.localId,
                        leftLocalId = null,
                        rightLocalId = null,
                        pageId = java.util.UUID.randomUUID().toString()
                    )
                )
            }
            
            for (i in halfPlugins.indices step 2) {
                val left = halfPlugins[i]
                val right = if (i + 1 < halfPlugins.size) halfPlugins[i + 1] else halfPlugins[i]
                pagesList.add(
                    PluginManager.LayoutEntry(
                        type = "half",
                        pluginLocalId = null,
                        leftLocalId = left.localId,
                        rightLocalId = right.localId,
                        pageId = "half_page_$i"
                    )
                )
            }
            
            if (pagesList.isEmpty()) {
                pagesList.add(
                    PluginManager.LayoutEntry(
                        type = "full",
                        pluginLocalId = "com.example.builtin.clock",
                        leftLocalId = null,
                        rightLocalId = null,
                        pageId = "default_clock_page"
                    )
                )
            }
            
            PluginManager.saveLayoutConfig(context, pagesList)
        }
    }

    fun addPageSlot(type: String) {
        val context = getApplication<Application>()
        ensureLayoutConfigExists(context)
        val installed = _plugins.value
        val layout = PluginManager.loadLayoutConfig(context).toMutableList()
        
        val defaultFull = installed.firstOrNull { it.localId == "com.example.builtin.clock" }?.localId ?: ""
        val defaultHalf = installed.firstOrNull { it.size == "half" }?.localId ?: defaultFull

        if (type == "full") {
            layout.add(
                PluginManager.LayoutEntry(
                    type = "full",
                    pluginLocalId = defaultFull,
                    leftLocalId = null,
                    rightLocalId = null,
                    pageId = java.util.UUID.randomUUID().toString()
                )
            )
        } else {
            layout.add(
                PluginManager.LayoutEntry(
                    type = "half",
                    pluginLocalId = null,
                    leftLocalId = defaultHalf,
                    rightLocalId = defaultHalf,
                    pageId = java.util.UUID.randomUUID().toString()
                )
            )
        }
        PluginManager.saveLayoutConfig(context, layout)
        rebuildStandbyPages()
    }

    fun removePageSlot(pageId: String) {
        val context = getApplication<Application>()
        ensureLayoutConfigExists(context)
        val layout = PluginManager.loadLayoutConfig(context).toMutableList()
        layout.removeAll { it.pageId == pageId }
        PluginManager.saveLayoutConfig(context, layout)
        rebuildStandbyPages()
    }

    fun movePageSlot(fromIndex: Int, toIndex: Int) {
        val context = getApplication<Application>()
        ensureLayoutConfigExists(context)
        val layout = PluginManager.loadLayoutConfig(context).toMutableList()
        if (fromIndex in layout.indices && toIndex in layout.indices) {
            val item = layout.removeAt(fromIndex)
            layout.add(toIndex, item)
            PluginManager.saveLayoutConfig(context, layout)
            rebuildStandbyPages()
        }
    }

    fun updatePageSlotPlugin(pageId: String, isLeft: Boolean, newPluginLocalId: String) {
        val context = getApplication<Application>()
        ensureLayoutConfigExists(context)
        val layout = PluginManager.loadLayoutConfig(context).map { entry ->
            if (entry.pageId == pageId) {
                if (isLeft) {
                    entry.copy(leftLocalId = newPluginLocalId)
                } else {
                    entry.copy(rightLocalId = newPluginLocalId)
                }
            } else entry
        }
        PluginManager.saveLayoutConfig(context, layout)
        rebuildStandbyPages()
    }

    fun updatePageSlotFull(pageId: String, newPluginLocalId: String) {
        val context = getApplication<Application>()
        ensureLayoutConfigExists(context)
        val layout = PluginManager.loadLayoutConfig(context).map { entry ->
            if (entry.pageId == pageId) {
                entry.copy(pluginLocalId = newPluginLocalId)
            } else entry
        }
        PluginManager.saveLayoutConfig(context, layout)
        rebuildStandbyPages()
    }

    fun updatePageSlotType(pageId: String, newType: String) {
        val context = getApplication<Application>()
        ensureLayoutConfigExists(context)
        val installed = _plugins.value
        val defaultFull = installed.firstOrNull { it.localId == "com.example.builtin.clock" }?.localId ?: ""
        val defaultHalf = installed.firstOrNull { it.size == "half" }?.localId ?: defaultFull

        val layout = PluginManager.loadLayoutConfig(context).map { entry ->
            if (entry.pageId == pageId) {
                if (newType == "full") {
                    entry.copy(type = "full", pluginLocalId = defaultFull, leftLocalId = null, rightLocalId = null)
                } else {
                    entry.copy(type = "half", pluginLocalId = null, leftLocalId = defaultHalf, rightLocalId = defaultHalf)
                }
            } else entry
        }
        PluginManager.saveLayoutConfig(context, layout)
        rebuildStandbyPages()
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

        val updatedPages = _standbyPages.value.map { page ->
            when (page) {
                is StandbyPage.FullWidth -> {
                    if (page.plugin.localId == pluginLocalId) {
                        val updatedPlugin = updatedList.first { it.localId == pluginLocalId }
                        page.copy(plugin = updatedPlugin)
                    } else page
                }
                is StandbyPage.HalfWidth -> {
                    val newLeft = if (page.leftPlugin.localId == pluginLocalId) {
                        updatedList.first { it.localId == pluginLocalId }
                    } else page.leftPlugin
                    
                    val newRight = if (page.rightPlugin.localId == pluginLocalId) {
                        updatedList.first { it.localId == pluginLocalId }
                    } else page.rightPlugin
                    
                    page.copy(leftPlugin = newLeft, rightPlugin = newRight)
                }
            }
        }
        _standbyPages.value = updatedPages
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
                        val cachedFile = File(context.cacheDir, "uploaded_plugin_temp_" + System.currentTimeMillis())
                        file.copyTo(cachedFile, overwrite = true)
                        
                        try {
                            if (contentType.contains("application/zip") || file.name.endsWith(".zip")) {
                                cachedFile.inputStream().use { input ->
                                    PluginManager.importZipPlugin(context, input)
                                }
                            } else {
                                val htmlContent = cachedFile.readText()
                                PluginManager.importHtmlPlugin(context, htmlContent, "Uploaded Plugin")
                            }
                            loadPlugins()
                        } finally {
                            cachedFile.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
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

    fun deletePlugin(localId: String) {
        val context = getApplication<Application>()
        if (PluginManager.deletePlugin(context, localId)) {
            loadPlugins()
        }
    }

    override fun onCleared() {
        super.onCleared()
        pluginServer?.stop()
    }
}

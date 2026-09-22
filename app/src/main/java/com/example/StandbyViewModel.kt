package com.example

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class StandbyViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _plugins = MutableStateFlow<List<PluginModel>>(emptyList())
    val plugins: StateFlow<List<PluginModel>> = _plugins.asStateFlow()

    private val _standbyPages = MutableStateFlow<List<StandbyPage>>(emptyList())
    val standbyPages: StateFlow<List<StandbyPage>> = _standbyPages.asStateFlow()

    private val container = (application as StandbyApplication).container
    private val settings = container.settings
    private val services = container.services

    /**
     * Stored reference so onCleared can check identity before clearing it. Declared here
     * rather than next to the function because init assigns it, and Kotlin initialises
     * properties in declaration order.
     */
    private val uploadHandler: (java.io.File, String) -> Unit = ::handlePluginUpload

    // Settings are re-exposed straight from the repository rather than mirrored here, so
    // there is one copy of each value and one place it can be changed.
    val burnInProtectionEnabled: StateFlow<Boolean> = settings.burnInProtectionEnabled
    val delayAfterInteraction: StateFlow<Boolean> = settings.delayAfterInteraction
    val protectionRatio: StateFlow<Int> = settings.protectionRatio
    val hideControlsOnIdle: StateFlow<Boolean> = settings.hideControlsOnIdle
    val lowRefreshRateEnabled: StateFlow<Boolean> = settings.lowRefreshRateEnabled
    val lowRefreshRateValue: StateFlow<Int> = settings.lowRefreshRateValue

    val isServerRunning: StateFlow<Boolean> = services.isServerRunning
    val serverPort: StateFlow<Int> = services.serverPort
    val serverPin: StateFlow<String> = services.serverPin
    val serverIp: StateFlow<String> = services.serverIp

    private val _pendingImport = MutableStateFlow<PendingPluginImport?>(null)
    val pendingImport: StateFlow<PendingPluginImport?> = _pendingImport.asStateFlow()

    val confirmImportEnabled: StateFlow<Boolean> = settings.confirmImportEnabled

    fun setConfirmImportEnabled(enabled: Boolean) = settings.setConfirmImportEnabled(enabled)

    val appWidgetsEnabled: StateFlow<Boolean> = settings.appWidgetsEnabled

    fun setAppWidgetsEnabled(enabled: Boolean) {
        settings.setAppWidgetsEnabled(enabled)
        rebuildStandbyPages()
    }

    val nightModeEnabled: StateFlow<Boolean> = settings.nightModeEnabled
    val nightStartHour: StateFlow<Int> = settings.nightStartHour
    val nightStartMinute: StateFlow<Int> = settings.nightStartMinute
    val nightEndHour: StateFlow<Int> = settings.nightEndHour
    val nightEndMinute: StateFlow<Int> = settings.nightEndMinute
    val nightProtectionRatio: StateFlow<Int> = settings.nightProtectionRatio
    val nightBrightnessEnabled: StateFlow<Boolean> = settings.nightBrightnessEnabled
    val nightBrightnessValue: StateFlow<Float> = settings.nightBrightnessValue

    private val _isNightModeActive = MutableStateFlow(false)
    val isNightModeActive: StateFlow<Boolean> = _isNightModeActive.asStateFlow()

    fun setNightModeEnabled(enabled: Boolean) {
        settings.setNightModeEnabled(enabled)
        updateNightModeActiveState()
    }

    fun setNightStartTime(hour: Int, minute: Int) {
        settings.setNightStartTime(hour, minute)
        updateNightModeActiveState()
    }

    fun setNightEndTime(hour: Int, minute: Int) {
        settings.setNightEndTime(hour, minute)
        updateNightModeActiveState()
    }

    fun setNightProtectionRatio(ratio: Int) = settings.setNightProtectionRatio(ratio)

    fun setNightBrightnessEnabled(enabled: Boolean) = settings.setNightBrightnessEnabled(enabled)

    fun setNightBrightnessValue(value: Float) = settings.setNightBrightnessValue(value)

    fun updateNightModeActiveState(cal: Calendar = Calendar.getInstance()) {
        if (!settings.nightModeEnabled.value) {
            _isNightModeActive.value = false
            return
        }
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(Calendar.MINUTE)
        _isNightModeActive.value = isNightTime(
            currentHour = currentHour,
            currentMinute = currentMinute,
            startHour = settings.nightStartHour.value,
            startMinute = settings.nightStartMinute.value,
            endHour = settings.nightEndHour.value,
            endMinute = settings.nightEndMinute.value
        )
    }



    // ProviderManager writes weather_lat / weather_lon / weather_city / weather_last_update
    // to the same prefs file, so the repository's change listener picks those up on its own.
    // The old code re-read them by hand after every fetch.
    val weatherLat: StateFlow<String> = settings.weatherLat
    val weatherLon: StateFlow<String> = settings.weatherLon
    val weatherCity: StateFlow<String> = settings.weatherCity
    val weatherUseGps: StateFlow<Boolean> = settings.weatherUseGps
    val weatherLastUpdate: StateFlow<Long> = settings.weatherLastUpdate

    private val _pluginRefreshTriggers = MutableStateFlow<Map<String, Long>>(emptyMap())
    val pluginRefreshTriggers: StateFlow<Map<String, Long>> = _pluginRefreshTriggers.asStateFlow()

    fun refreshPlugin(localId: String) {
        val current = _pluginRefreshTriggers.value.toMutableMap()
        current[localId] = System.currentTimeMillis()
        _pluginRefreshTriggers.value = current
        triggerWeatherRefresh()
    }

    fun refreshAllWidgets(context: Context) {
        val current = _pluginRefreshTriggers.value.toMutableMap()
        _plugins.value.forEach { plugin ->
            current[plugin.localId] = System.currentTimeMillis()
        }
        _pluginRefreshTriggers.value = current
        triggerWeatherRefresh()

        _standbyPages.value.forEach { page ->
            when (page) {
                is StandbyPage.FullWidth -> {
                    (page.item as? StandbyItem.NativeAppWidget)?.let { widgetItem ->
                        refreshNativeAppWidget(context, widgetItem.appWidgetId, widgetItem.providerInfo.provider)
                    }
                }
                is StandbyPage.HalfWidth -> {
                    (page.leftItem as? StandbyItem.NativeAppWidget)?.let { widgetItem ->
                        refreshNativeAppWidget(context, widgetItem.appWidgetId, widgetItem.providerInfo.provider)
                    }
                    (page.rightItem as? StandbyItem.NativeAppWidget)?.let { widgetItem ->
                        refreshNativeAppWidget(context, widgetItem.appWidgetId, widgetItem.providerInfo.provider)
                    }
                }
            }
        }
    }

    fun refreshNativeAppWidget(context: Context, appWidgetId: Int, provider: ComponentName) {
        try {
            val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = provider
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            context.sendBroadcast(updateIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setWeatherLocation(lat: String, lon: String, city: String) {
        settings.setWeatherLocation(lat, lon, city)
        triggerWeatherRefresh()
    }

    fun setWeatherUseGps(enabled: Boolean) {
        settings.setWeatherUseGps(enabled)
        triggerWeatherRefresh()
    }

    fun triggerWeatherRefresh() {
        viewModelScope.launch { services.providerManager.fetchWeather() }
    }

    suspend fun searchLocations(query: String): List<ProviderManager.GeocodingResult> {
        return services.providerManager.searchLocations(query)
    }

    init {
        updateNightModeActiveState()
        viewModelScope.launch {
            while (true) {
                val cal = Calendar.getInstance()
                updateNightModeActiveState(cal)
                delay(10_000L)
            }
        }
        loadPlugins()
        // The server and the weather poller belong to StandbyServices now. All this has
        // to do is say where an upload should land.
        services.onPluginUploaded = uploadHandler
    }

    fun loadPlugins() {
        viewModelScope.launch {
            val list = mutableListOf<PluginModel>()
            
            // add built-in clock
            list.add(DefaultPlugins.getBuiltInClockPlugin(settings.preferences))

            // load registered plugins
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

    private fun resolveStandbyItem(context: Context, localId: String?, installed: List<PluginModel>): StandbyItem? {
        if (localId.isNullOrBlank()) return null
        if (localId.startsWith("appwidget:")) {
            if (!settings.appWidgetsEnabled.value) return null
            val appWidgetId = localId.removePrefix("appwidget:").toIntOrNull()
            if (appWidgetId != null) {
                val providerInfo = AppWidgetHostHelper.getAppWidgetInfo(context, appWidgetId)
                if (providerInfo != null) {
                    val pm = context.packageManager
                    val label = try { providerInfo.loadLabel(pm)?.toString() } catch (e: Exception) { null } ?: "Android Widget"
                    val pkgName = providerInfo.provider.packageName
                    return StandbyItem.NativeAppWidget(appWidgetId, providerInfo, label, pkgName)
                }
            }
            return null
        }
        val plugin = installed.firstOrNull { it.localId == localId }
        return if (plugin != null) StandbyItem.Plugin(plugin) else null
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
                pagesList.add(StandbyPage.FullWidth(StandbyItem.Plugin(p)))
            }
            
            for (i in halfPlugins.indices step 2) {
                val left = halfPlugins[i]
                val right = if (i + 1 < halfPlugins.size) halfPlugins[i + 1] else halfPlugins[i]
                pagesList.add(StandbyPage.HalfWidth(StandbyItem.Plugin(left), StandbyItem.Plugin(right), "default_half_$i"))
            }
            
            if (pagesList.isEmpty()) {
                val defaultClock = installed.firstOrNull { it.localId == "com.example.builtin.clock" }
                if (defaultClock != null) {
                    pagesList.add(StandbyPage.FullWidth(StandbyItem.Plugin(defaultClock)))
                }
            }
        } else {
            val defaultFull = installed.firstOrNull { it.localId == "com.example.builtin.clock" }?.localId ?: ""
            val defaultHalf = installed.firstOrNull { it.size == "half" }?.localId ?: defaultFull

            for (entry in layout) {
                if (entry.type == "full") {
                    val item = resolveStandbyItem(context, entry.pluginLocalId, installed)
                        ?: resolveStandbyItem(context, defaultFull, installed)
                    if (item != null) {
                        pagesList.add(StandbyPage.FullWidth(item, entry.pageId))
                    }
                } else {
                    val leftItem = resolveStandbyItem(context, entry.leftLocalId, installed)
                        ?: resolveStandbyItem(context, defaultHalf, installed)
                        ?: resolveStandbyItem(context, defaultFull, installed)
                    
                    val rightItem = resolveStandbyItem(context, entry.rightLocalId, installed)
                        ?: resolveStandbyItem(context, defaultHalf, installed)
                        ?: resolveStandbyItem(context, defaultFull, installed)
                    
                    if (leftItem != null && rightItem != null) {
                        pagesList.add(StandbyPage.HalfWidth(leftItem, rightItem, entry.pageId))
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
        val removed = layout.filter { it.pageId == pageId }
        for (entry in removed) {
            cleanupAppWidgetId(context, entry.pluginLocalId)
            cleanupAppWidgetId(context, entry.leftLocalId)
            cleanupAppWidgetId(context, entry.rightLocalId)
        }
        layout.removeAll { it.pageId == pageId }
        PluginManager.saveLayoutConfig(context, layout)
        rebuildStandbyPages()
    }

    private fun cleanupAppWidgetId(context: Context, localId: String?) {
        if (localId != null && localId.startsWith("appwidget:")) {
            val appWidgetId = localId.removePrefix("appwidget:").toIntOrNull()
            if (appWidgetId != null) {
                AppWidgetHostHelper.deleteAppWidgetId(context, appWidgetId)
            }
        }
    }

    fun updatePageSlotWithAppWidget(pageId: String, isLeft: Boolean?, appWidgetId: Int) {
        val context = getApplication<Application>()
        ensureLayoutConfigExists(context)
        val widgetLocalId = "appwidget:$appWidgetId"
        val layout = PluginManager.loadLayoutConfig(context).map { entry ->
            if (entry.pageId == pageId) {
                if (isLeft == null || entry.type == "full") {
                    cleanupAppWidgetId(context, entry.pluginLocalId)
                    entry.copy(pluginLocalId = widgetLocalId)
                } else if (isLeft) {
                    cleanupAppWidgetId(context, entry.leftLocalId)
                    entry.copy(leftLocalId = widgetLocalId)
                } else {
                    cleanupAppWidgetId(context, entry.rightLocalId)
                    entry.copy(rightLocalId = widgetLocalId)
                }
            } else entry
        }
        PluginManager.saveLayoutConfig(context, layout)
        rebuildStandbyPages()
    }

    fun addPageSlotWithAppWidget(appWidgetId: Int, type: String = "full") {
        val context = getApplication<Application>()
        ensureLayoutConfigExists(context)
        val layout = PluginManager.loadLayoutConfig(context).toMutableList()
        val widgetLocalId = "appwidget:$appWidgetId"

        if (type == "full") {
            layout.add(
                PluginManager.LayoutEntry(
                    type = "full",
                    pluginLocalId = widgetLocalId,
                    leftLocalId = null,
                    rightLocalId = null,
                    pageId = java.util.UUID.randomUUID().toString()
                )
            )
        } else {
            val installed = _plugins.value
            val defaultHalf = installed.firstOrNull { it.size == "half" }?.localId ?: "com.example.builtin.clock"
            layout.add(
                PluginManager.LayoutEntry(
                    type = "half",
                    pluginLocalId = null,
                    leftLocalId = widgetLocalId,
                    rightLocalId = defaultHalf,
                    pageId = java.util.UUID.randomUUID().toString()
                )
            )
        }
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
                    settings.preferences.edit()
                        .putString("builtin_customization_${pluginLocalId}_${varName}", varValue)
                        .apply()
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
                    if (page.item is StandbyItem.Plugin && page.item.plugin.localId == pluginLocalId) {
                        val updatedPlugin = updatedList.first { it.localId == pluginLocalId }
                        page.copy(item = StandbyItem.Plugin(updatedPlugin))
                    } else page
                }
                is StandbyPage.HalfWidth -> {
                    val newLeftItem = if (page.leftItem is StandbyItem.Plugin && page.leftItem.plugin.localId == pluginLocalId) {
                        val updatedPlugin = updatedList.first { it.localId == pluginLocalId }
                        StandbyItem.Plugin(updatedPlugin)
                    } else page.leftItem
                    
                    val newRightItem = if (page.rightItem is StandbyItem.Plugin && page.rightItem.plugin.localId == pluginLocalId) {
                        val updatedPlugin = updatedList.first { it.localId == pluginLocalId }
                        StandbyItem.Plugin(updatedPlugin)
                    } else page.rightItem
                    
                    page.copy(leftItem = newLeftItem, rightItem = newRightItem)
                }
            }
        }
        _standbyPages.value = updatedPages
    }

    fun setBurnInProtectionEnabled(enabled: Boolean) = settings.setBurnInProtectionEnabled(enabled)

    fun setDelayAfterInteraction(enabled: Boolean) = settings.setDelayAfterInteraction(enabled)

    fun setProtectionRatio(ratio: Int) = settings.setProtectionRatio(ratio)

    fun setHideControlsOnIdle(enabled: Boolean) = settings.setHideControlsOnIdle(enabled)

    fun setLowRefreshRateEnabled(enabled: Boolean) = settings.setLowRefreshRateEnabled(enabled)

    fun setLowRefreshRateValue(value: Int) = settings.setLowRefreshRateValue(value)

    fun setServerEnabled(enabled: Boolean) = settings.setServerEnabled(enabled)

    private fun handlePluginUpload(file: java.io.File, contentType: String) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val pending = if (contentType.contains("application/zip") || file.name.endsWith(".zip")) {
                    file.inputStream().use { input ->
                        PluginManager.prepareZipPluginImport(context, input, file.name)
                    }
                } else {
                    PluginManager.prepareHtmlPluginImport(context, file.readText(), "Uploaded Plugin")
                }
                if (settings.confirmImportEnabled.value) {
                    _pendingImport.value = pending
                } else {
                    PluginManager.completePendingImport(context, pending, pending.name)
                    loadPlugins()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // StandbyServices hands us ownership of this temp file.
                file.delete()
            }
        }
    }

    fun loadPluginFromFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = PluginManager.getFileName(context, uri) ?: "imported_plugin.zip"
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val pending = if (fileName.endsWith(".zip")) {
                        PluginManager.prepareZipPluginImport(context, inputStream, fileName)
                    } else {
                        val htmlContent = inputStream.bufferedReader().readText()
                        PluginManager.prepareHtmlPluginImport(context, htmlContent, fileName)
                    }
                    if (settings.confirmImportEnabled.value) {
                        _pendingImport.value = pending
                    } else {
                        PluginManager.completePendingImport(context, pending, pending.name)
                        loadPlugins()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun confirmImport(customName: String) {
        val pending = _pendingImport.value ?: return
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                PluginManager.completePendingImport(context, pending, customName)
                loadPlugins()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _pendingImport.value = null
            }
        }
    }

    fun cancelImport() {
        val pending = _pendingImport.value ?: return
        viewModelScope.launch {
            try {
                if (pending.tempDir.exists()) {
                    pending.tempDir.deleteRecursively()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _pendingImport.value = null
            }
        }
    }

    fun deletePlugin(localId: String) {
        val context = getApplication<Application>()
        if (PluginManager.deletePlugin(context, localId)) {
            loadPlugins()
        }
    }

    fun renamePlugin(localId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val context = getApplication<Application>()
        if (localId == "com.example.builtin.clock") {
            DefaultPlugins.renameBuiltInPlugin(settings.preferences, localId, trimmed)
            loadPlugins()
        } else {
            if (PluginManager.renamePlugin(context, localId, trimmed)) {
                loadPlugins()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // The server and weather poller outlive this ViewModel by design, so nothing to
        // tear down here. Drop the upload handler so a cleared ViewModel is not still
        // being handed files.
        if (services.onPluginUploaded === uploadHandler) {
            services.onPluginUploaded = null
        }
    }

    companion object {
        fun isNightTime(
            currentHour: Int,
            currentMinute: Int,
            startHour: Int,
            startMinute: Int,
            endHour: Int,
            endMinute: Int
        ): Boolean {
            val currentMin = currentHour * 60 + currentMinute
            val startMin = startHour * 60 + startMinute
            val endMin = endHour * 60 + endMinute

            return if (startMin == endMin) {
                false
            } else if (startMin < endMin) {
                currentMin in startMin until endMin
            } else {
                currentMin >= startMin || currentMin < endMin
            }
        }
    }
}

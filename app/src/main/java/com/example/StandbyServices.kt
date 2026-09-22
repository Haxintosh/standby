package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * The upload server and the weather poller, which both outlive any one screen.
 *
 * StandbyViewModel's constructor used to start both, so building a screen opened a
 * listening socket on the side. Add a DreamService and docking the phone would have done
 * it, on the lock screen, overnight. Now a server starts because [start] was called and
 * the user turned it on, and for no other reason.
 *
 * One per process, held by [AppContainer].
 */
class StandbyServices(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    /**
     * Not viewModelScope: this work is not owned by a screen. SupervisorJob so one failing
     * child does not take the others down with it.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val providerManager = ProviderManager(context)

    private var pluginServer: PluginServer? = null
    private var started = false

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverPort = MutableStateFlow(0)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _serverPin = MutableStateFlow("")
    val serverPin: StateFlow<String> = _serverPin.asStateFlow()

    private val _serverIp = MutableStateFlow("")
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    /**
     * Called when an upload lands. Whoever handles imports sets this and owns the file,
     * including deleting it.
     *
     * One settable callback rather than a flow, because two collectors would import the
     * same upload twice. Worth another look once the dream exists and there is genuinely
     * a second host that might want it.
     */
    var onPluginUploaded: ((File, String) -> Unit)? = null

    /**
     * Begins following the upload server setting. Call once, from Application.onCreate.
     * The server itself only starts if the user has turned it on.
     */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            settings.serverEnabled.collect { enabled ->
                if (enabled) startServer() else stopServer()
            }
        }
    }

    /**
     * Weather polling follows whether any UI is on screen, so a backgrounded app is not
     * hitting the network hourly for a widget nobody can see.
     *
     * The upload server deliberately does not follow app visibility. You enable it, read
     * the address, and walk to your laptop; the phone screen going off must not kill the
     * upload you are about to send.
     */
    fun onAppForegrounded() {
        providerManager.startHourlyWeatherUpdates(scope)
    }

    fun onAppBackgrounded() {
        providerManager.stopWeatherUpdates()
    }

    private fun startServer() {
        if (pluginServer != null) return
        val server = PluginServer(
            context = context,
            onPluginReceived = { file, contentType ->
                val handler = onPluginUploaded
                if (handler != null) {
                    handler(file, contentType)
                } else {
                    Log.w(TAG, "Upload received with no handler attached; discarding")
                    file.delete()
                }
            },
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

    companion object {
        private const val TAG = "StandbyServices"
    }
}

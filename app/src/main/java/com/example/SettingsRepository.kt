package com.example

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the settings file and is the one place settings are read from.
 *
 * Before this, each setting was a MutableStateFlow seeded from SharedPreferences in
 * StandbyViewModel's constructor, read once and never refreshed. That holds up while
 * there is one ViewModel. With two, which is what adding a DreamService gives you, a
 * write from one instance reaches disk and never reaches the other instance's flow. The
 * two drift apart until the process restarts, with nothing in logcat to say so.
 *
 * Writes here go to SharedPreferences, and the change listener puts the value back into
 * the flow. Readers agree because they all take the same route. One per process.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Per-plugin values keyed by plugin id, like `builtin_name_<id>`, have no fixed key
     * set and are not app settings, so [DefaultPlugins] still reads them straight from
     * prefs. Exposed `internal` rather than pretending this class owns every key in the
     * file.
     */
    internal val preferences: SharedPreferences get() = prefs

    /** How to re-read each key, populated as the settings below are declared. */
    private val reloaders = mutableMapOf<String, () -> Unit>()

    /**
     * Held in a field deliberately. SharedPreferences keeps listeners in a WeakHashMap, so
     * one that exists only as a local gets collected and stops firing. The symptom is
     * settings that update for a while and then quietly stop.
     */
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        key?.let { reloaders[it]?.invoke() }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    // --- flow builders -------------------------------------------------------------
    // Each seeds a flow from prefs and records how to re-read that key later.

    private fun boolFlow(key: String, default: Boolean): MutableStateFlow<Boolean> =
        MutableStateFlow(prefs.getBoolean(key, default)).also { flow ->
            reloaders[key] = { flow.value = prefs.getBoolean(key, default) }
        }

    private fun intFlow(key: String, default: Int): MutableStateFlow<Int> =
        MutableStateFlow(prefs.getInt(key, default)).also { flow ->
            reloaders[key] = { flow.value = prefs.getInt(key, default) }
        }

    private fun floatFlow(key: String, default: Float): MutableStateFlow<Float> =
        MutableStateFlow(prefs.getFloat(key, default)).also { flow ->
            reloaders[key] = { flow.value = prefs.getFloat(key, default) }
        }

    private fun longFlow(key: String, default: Long): MutableStateFlow<Long> =
        MutableStateFlow(prefs.getLong(key, default)).also { flow ->
            reloaders[key] = { flow.value = prefs.getLong(key, default) }
        }

    private fun stringFlow(key: String, default: String): MutableStateFlow<String> =
        MutableStateFlow(prefs.getString(key, default) ?: default).also { flow ->
            reloaders[key] = { flow.value = prefs.getString(key, default) ?: default }
        }

    // --- burn-in protection and display --------------------------------------------

    private val _burnInProtectionEnabled = boolFlow(KEY_BURN_IN_PROTECTION, true)
    val burnInProtectionEnabled: StateFlow<Boolean> = _burnInProtectionEnabled.asStateFlow()

    private val _delayAfterInteraction = boolFlow(KEY_DELAY_AFTER_INTERACTION, false)
    val delayAfterInteraction: StateFlow<Boolean> = _delayAfterInteraction.asStateFlow()

    private val _protectionRatio = intFlow(KEY_PROTECTION_RATIO, 1)
    val protectionRatio: StateFlow<Int> = _protectionRatio.asStateFlow()

    private val _hideControlsOnIdle = boolFlow(KEY_HIDE_CONTROLS_ON_IDLE, true)
    val hideControlsOnIdle: StateFlow<Boolean> = _hideControlsOnIdle.asStateFlow()

    private val _lowRefreshRateEnabled = boolFlow(KEY_LOW_REFRESH_RATE_ENABLED, false)
    val lowRefreshRateEnabled: StateFlow<Boolean> = _lowRefreshRateEnabled.asStateFlow()

    private val _lowRefreshRateValue = intFlow(KEY_LOW_REFRESH_RATE_VALUE, 60)
    val lowRefreshRateValue: StateFlow<Int> = _lowRefreshRateValue.asStateFlow()

    fun setBurnInProtectionEnabled(enabled: Boolean) = putBoolean(KEY_BURN_IN_PROTECTION, enabled)
    fun setDelayAfterInteraction(enabled: Boolean) = putBoolean(KEY_DELAY_AFTER_INTERACTION, enabled)
    fun setProtectionRatio(ratio: Int) = putInt(KEY_PROTECTION_RATIO, ratio)
    fun setHideControlsOnIdle(enabled: Boolean) = putBoolean(KEY_HIDE_CONTROLS_ON_IDLE, enabled)
    fun setLowRefreshRateEnabled(enabled: Boolean) = putBoolean(KEY_LOW_REFRESH_RATE_ENABLED, enabled)
    fun setLowRefreshRateValue(value: Int) = putInt(KEY_LOW_REFRESH_RATE_VALUE, value)

    // --- night mode ------------------------------------------------------------------

    private val _nightModeEnabled = boolFlow(KEY_NIGHT_MODE_ENABLED, false)
    val nightModeEnabled: StateFlow<Boolean> = _nightModeEnabled.asStateFlow()

    private val _nightStartHour = intFlow(KEY_NIGHT_START_HOUR, 22)
    val nightStartHour: StateFlow<Int> = _nightStartHour.asStateFlow()

    private val _nightStartMinute = intFlow(KEY_NIGHT_START_MINUTE, 0)
    val nightStartMinute: StateFlow<Int> = _nightStartMinute.asStateFlow()

    private val _nightEndHour = intFlow(KEY_NIGHT_END_HOUR, 7)
    val nightEndHour: StateFlow<Int> = _nightEndHour.asStateFlow()

    private val _nightEndMinute = intFlow(KEY_NIGHT_END_MINUTE, 0)
    val nightEndMinute: StateFlow<Int> = _nightEndMinute.asStateFlow()

    private val _nightProtectionRatio = intFlow(KEY_NIGHT_PROTECTION_RATIO, 4)
    val nightProtectionRatio: StateFlow<Int> = _nightProtectionRatio.asStateFlow()

    private val _nightBrightnessEnabled = boolFlow(KEY_NIGHT_BRIGHTNESS_ENABLED, true)
    val nightBrightnessEnabled: StateFlow<Boolean> = _nightBrightnessEnabled.asStateFlow()

    private val _nightBrightnessValue = floatFlow(KEY_NIGHT_BRIGHTNESS_VALUE, 0.05f)
    val nightBrightnessValue: StateFlow<Float> = _nightBrightnessValue.asStateFlow()

    fun setNightModeEnabled(enabled: Boolean) = putBoolean(KEY_NIGHT_MODE_ENABLED, enabled)
    fun setNightProtectionRatio(ratio: Int) = putInt(KEY_NIGHT_PROTECTION_RATIO, ratio)
    fun setNightBrightnessEnabled(enabled: Boolean) = putBoolean(KEY_NIGHT_BRIGHTNESS_ENABLED, enabled)
    fun setNightBrightnessValue(value: Float) = putFloat(KEY_NIGHT_BRIGHTNESS_VALUE, value)

    fun setNightStartTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_NIGHT_START_HOUR, hour)
            .putInt(KEY_NIGHT_START_MINUTE, minute)
            .apply()
    }

    fun setNightEndTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_NIGHT_END_HOUR, hour)
            .putInt(KEY_NIGHT_END_MINUTE, minute)
            .apply()
    }

    // --- weather -----------------------------------------------------------------------

    private val _weatherLat = stringFlow(KEY_WEATHER_LAT, DEFAULT_LAT)
    val weatherLat: StateFlow<String> = _weatherLat.asStateFlow()

    private val _weatherLon = stringFlow(KEY_WEATHER_LON, DEFAULT_LON)
    val weatherLon: StateFlow<String> = _weatherLon.asStateFlow()

    private val _weatherCity = stringFlow(KEY_WEATHER_CITY, DEFAULT_CITY)
    val weatherCity: StateFlow<String> = _weatherCity.asStateFlow()

    private val _weatherUseGps = boolFlow(KEY_WEATHER_USE_GPS, false)
    val weatherUseGps: StateFlow<Boolean> = _weatherUseGps.asStateFlow()

    private val _weatherLastUpdate = longFlow(KEY_WEATHER_LAST_UPDATE, 0L)
    val weatherLastUpdate: StateFlow<Long> = _weatherLastUpdate.asStateFlow()

    fun setWeatherUseGps(enabled: Boolean) = putBoolean(KEY_WEATHER_USE_GPS, enabled)

    fun setWeatherLocation(lat: String, lon: String, city: String) {
        prefs.edit()
            .putString(KEY_WEATHER_LAT, lat)
            .putString(KEY_WEATHER_LON, lon)
            .putString(KEY_WEATHER_CITY, city)
            .apply()
    }

    // --- plugins and the upload server --------------------------------------------------

    private val _confirmImportEnabled = boolFlow(KEY_CONFIRM_PLUGIN_IMPORT, true)
    val confirmImportEnabled: StateFlow<Boolean> = _confirmImportEnabled.asStateFlow()

    private val _appWidgetsEnabled = boolFlow(KEY_APP_WIDGETS_ENABLED, true)
    val appWidgetsEnabled: StateFlow<Boolean> = _appWidgetsEnabled.asStateFlow()

    /** Off by default. An upload server nobody is using is a listening socket nobody asked for. */
    private val _serverEnabled = boolFlow(KEY_SERVER_ENABLED, false)
    val serverEnabled: StateFlow<Boolean> = _serverEnabled.asStateFlow()

    fun setConfirmImportEnabled(enabled: Boolean) = putBoolean(KEY_CONFIRM_PLUGIN_IMPORT, enabled)
    fun setAppWidgetsEnabled(enabled: Boolean) = putBoolean(KEY_APP_WIDGETS_ENABLED, enabled)
    fun setServerEnabled(enabled: Boolean) = putBoolean(KEY_SERVER_ENABLED, enabled)

    // --- writes ---------------------------------------------------------------------------
    // Writes go to prefs and nowhere else. The change listener puts the value back into the
    // flow, so the writer arrives at the new value by the same route as everyone else.

    private fun putBoolean(key: String, value: Boolean) =
        prefs.edit().putBoolean(key, value).apply()

    private fun putInt(key: String, value: Int) =
        prefs.edit().putInt(key, value).apply()

    private fun putFloat(key: String, value: Float) =
        prefs.edit().putFloat(key, value).apply()

    companion object {
        const val PREFS_NAME = "standby_settings"

        const val DEFAULT_LAT = "52.52"
        const val DEFAULT_LON = "13.41"
        const val DEFAULT_CITY = "Berlin"

        private const val KEY_BURN_IN_PROTECTION = "burn_in_protection"
        private const val KEY_DELAY_AFTER_INTERACTION = "delay_after_interaction"
        private const val KEY_PROTECTION_RATIO = "protection_ratio"
        private const val KEY_HIDE_CONTROLS_ON_IDLE = "hide_controls_on_idle"
        private const val KEY_LOW_REFRESH_RATE_ENABLED = "low_refresh_rate_enabled"
        private const val KEY_LOW_REFRESH_RATE_VALUE = "low_refresh_rate_value"

        private const val KEY_NIGHT_MODE_ENABLED = "night_mode_enabled"
        private const val KEY_NIGHT_START_HOUR = "night_mode_start_hour"
        private const val KEY_NIGHT_START_MINUTE = "night_mode_start_minute"
        private const val KEY_NIGHT_END_HOUR = "night_mode_end_hour"
        private const val KEY_NIGHT_END_MINUTE = "night_mode_end_minute"
        private const val KEY_NIGHT_PROTECTION_RATIO = "night_protection_ratio"
        private const val KEY_NIGHT_BRIGHTNESS_ENABLED = "night_brightness_enabled"
        private const val KEY_NIGHT_BRIGHTNESS_VALUE = "night_brightness_value"

        private const val KEY_WEATHER_LAT = "weather_lat"
        private const val KEY_WEATHER_LON = "weather_lon"
        private const val KEY_WEATHER_CITY = "weather_city"
        private const val KEY_WEATHER_USE_GPS = "weather_use_gps"
        private const val KEY_WEATHER_LAST_UPDATE = "weather_last_update"

        private const val KEY_CONFIRM_PLUGIN_IMPORT = "confirm_plugin_import"
        private const val KEY_APP_WIDGETS_ENABLED = "app_widgets_enabled"
        private const val KEY_SERVER_ENABLED = "server_enabled"
    }
}

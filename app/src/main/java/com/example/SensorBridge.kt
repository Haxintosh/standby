package com.example

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.webkit.JavascriptInterface
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SensorBridge(
    private val context: Context,
    private val allowedPermissionsProvider: () -> List<String>,
    private val customizationsProvider: () -> String
) {
    constructor(
        context: Context,
        allowedPermissions: List<String>,
        customizationsProvider: () -> String
    ) : this(context, { allowedPermissions }, customizationsProvider)

    private val allowedPermissions: List<String>
        get() = allowedPermissionsProvider()
    companion object {
        @Volatile
        private var lastAmbientLight: Int = 10
        @Volatile
        private var lastProximity: Int = 5
    }

    private fun getSensorValue(sensorType: Int): Float? {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
        val sensor = sensorManager.getDefaultSensor(sensorType) ?: return null
        
        var result: Float? = null
        val latch = CountDownLatch(1)
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.sensor.type == sensorType) {
                    result = event.values.getOrNull(0)
                    latch.countDown()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        val registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        if (registered) {
            try {
                latch.await(200, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Log.e("SensorBridge", "Sensor reading interrupted", e)
            } finally {
                sensorManager.unregisterListener(listener)
            }
        }
        return result
    }

    // note that for battery sensor access, the permission "battery" grants access to all battery related data
    // however, prioritize smaller scope permissions over larger ones
    @JavascriptInterface
    fun getBatteryLevel(): String {
        val fallbackValue = "{\"level\":0}"
        if (!allowedPermissions.contains("battery") && !allowedPermissions.contains("battery_level")) {
            Log.w("SensorBridge", "Blocked access to battery level: Permission not declared in manifest")
            return fallbackValue
        }
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level == -1 || scale == -1) 0 else (level * 100 / scale.toFloat()).toInt()
        
        val jsonObj = org.json.JSONObject()
        jsonObj.put("level", pct)
        return jsonObj.toString()
    }

    @JavascriptInterface
    fun getChargingStatus(): String {
        if (!allowedPermissions.contains("battery") && !allowedPermissions.contains("battery_charging")) {
            Log.w("SensorBridge", "Blocked access to charging status: Permission not declared in manifest")
            return "{\"isCharging\":false,\"source\":\"Unknown\",\"temp\":0,\"timeRemaining\":-1}"
        }
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
//        Log.d("chrg status", "typ: $chargePlug")
        val source = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (isCharging) "Unknown" else "None"
        }

        val rawTemp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val temp = rawTemp / 10

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val timeRemaining = if (batteryManager != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            batteryManager.computeChargeTimeRemaining()
        } else {
            -1L
        }
        Log.d("ChargingStatus", "Charging: $isCharging, Source: $source, Temp: $temp, TimeRemaining: $timeRemaining")
        
        val jsonObj = org.json.JSONObject()
        jsonObj.put("isCharging", isCharging)
        jsonObj.put("source", source)
        jsonObj.put("temp", temp)
        jsonObj.put("timeRemaining", timeRemaining)
        return jsonObj.toString()
    }

    @JavascriptInterface
    fun getBatteryStats(): String {
        if (!allowedPermissions.contains("battery") && !allowedPermissions.contains("battery_stats")) {
            Log.w("SensorBridge", "Blocked access to battery stats: Permission not declared in manifest")
            return "{\"current\":0,\"voltage\":0,\"health\":1,\"chargeCount\":0}"
        }
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        
        val currentNow = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val current = if (currentNow != Int.MIN_VALUE && currentNow != Int.MAX_VALUE) currentNow / 1000 else 0

        val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN

        val chargeCounter = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0
        val chargeCount = if (chargeCounter != Int.MIN_VALUE && chargeCounter != Int.MAX_VALUE) chargeCounter / 1000 else 0
        
//        Log.d("BatteryStats", "Current: $current, Voltage: $voltage, Health: $health, ChargeCount: $chargeCount")
        
        val jsonObj = org.json.JSONObject()
        jsonObj.put("current", current)
        jsonObj.put("voltage", voltage)
        jsonObj.put("health", health)
        jsonObj.put("chargeCount", chargeCount)
        return jsonObj.toString()
    }

    @JavascriptInterface
    fun getAmbientLight(): String {
        val fallbackValue = lastAmbientLight
        if (!allowedPermissions.contains("sensors") && !allowedPermissions.contains("ambient_light")) {
            Log.w("SensorBridge", "Blocked access to ambient light: Permission not declared in manifest")
            return "{\"light\":$fallbackValue}"
        }
        val value = getSensorValue(Sensor.TYPE_LIGHT)
        val lightVal = if (value != null) {
            val intVal = value.toInt()
            lastAmbientLight = intVal
            intVal
        } else {
            fallbackValue
        }
        val jsonObj = org.json.JSONObject()
        jsonObj.put("light", lightVal)
        return jsonObj.toString()
    }

    @JavascriptInterface
    fun getProximity(): String {
        val fallbackValue = lastProximity
        if (!allowedPermissions.contains("sensors") && !allowedPermissions.contains("proximity")) {
            Log.w("SensorBridge", "Blocked access to proximity: Permission not declared in manifest")
            return "{\"proximity\":$fallbackValue}"
        }
        val value = getSensorValue(Sensor.TYPE_PROXIMITY)
//        Log.d("Proximity pre", "Value: $value")
        val proximityVal = if (value != null) {
            val intVal = value.toInt()
            lastProximity = intVal
            intVal
        } else {
            fallbackValue
        }
        val jsonObj = org.json.JSONObject()
        jsonObj.put("proximity", proximityVal)
//        Log.d("Proximity", jsonObj.toString())
        return jsonObj.toString()
    }


    @JavascriptInterface
    fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
    
    @JavascriptInterface
    fun getFormattedTime(format: String): String {
        return try {
            SimpleDateFormat(format, Locale.getDefault()).format(Date())
        } catch (e: Exception) {
            getCurrentTime()
        }
    }

    @JavascriptInterface
    fun getCustomizations(): String {
        return customizationsProvider()
    }

    @JavascriptInterface
    fun getNextAlarm(): String {
        val fallbackValue = "{\"hasAlarm\":false,\"triggerTime\":0,\"creatorPackage\":null,\"formattedTime\":\"\"}"
        if (!allowedPermissions.contains("alarms")) {
            Log.w("SensorBridge", "Blocked access to next alarm: Permission not declared in manifest")
            return fallbackValue
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val nextAlarm = alarmManager?.nextAlarmClock

        if (nextAlarm == null) {
            return fallbackValue
        }

        val triggerTime = nextAlarm.triggerTime
        val creatorPackage = nextAlarm.showIntent?.creatorPackage

        val formattedTime = try {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(triggerTime))
        } catch (e: Exception) {
            ""
        }

        val jsonObj = org.json.JSONObject()
        jsonObj.put("hasAlarm", true)
        jsonObj.put("triggerTime", triggerTime)
        jsonObj.put("creatorPackage", creatorPackage ?: org.json.JSONObject.NULL)
        jsonObj.put("formattedTime", formattedTime)
        return jsonObj.toString()
    }
    // TODO: (maybe) split this away from SensorBridge, maybe ProviderBridge instead?
    @JavascriptInterface
    fun getWeatherData(): String {
        val fallbackValue = "{}"
        if (!allowedPermissions.contains("weather")) {
            Log.w("SensorBridge", "Blocked access to weather: Permission not declared in manifest")
            return fallbackValue
        }
        val prefs = context.getSharedPreferences("standby_settings", Context.MODE_PRIVATE)
        return prefs.getString("weather_cache", fallbackValue) ?: fallbackValue
    }

    @JavascriptInterface
    fun getCurrentWeather(): String {
        val fallbackValue = "{}"
        if (!allowedPermissions.contains("weather")) {
            Log.w("SensorBridge", "Blocked access to weather: Permission not declared in manifest")
            return fallbackValue
        }
        val prefs = context.getSharedPreferences("standby_settings", Context.MODE_PRIVATE)
        val cache = prefs.getString("weather_cache", null) ?: return fallbackValue

        return try {
            val json = org.json.JSONObject(cache)
            val hourly = json.optJSONObject("hourly") ?: return fallbackValue
            val timeArray = hourly.optJSONArray("time") ?: return fallbackValue
            if (timeArray.length() == 0) return fallbackValue

            val nowMs = System.currentTimeMillis()
            var closestIndex = 0
            var minDiff = Long.MAX_VALUE

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            for (i in 0 until timeArray.length()) {
                val timeStr = timeArray.getString(i)
                val timeMs = try {
                    sdf.parse(timeStr)?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }
                val diff = Math.abs(nowMs - timeMs)
                if (diff < minDiff) {
                    minDiff = diff
                    closestIndex = i
                }
            }

            val tempArray = hourly.optJSONArray("temperature_2m")
            val codeArray = hourly.optJSONArray("weather_code")
            val precipArray = hourly.optJSONArray("precipitation")
            val probArray = hourly.optJSONArray("precipitation_probability")
            val isDayArray = hourly.optJSONArray("is_day")
            val rhArray = hourly.optJSONArray("relative_humidity_2m")
            val dewArray = hourly.optJSONArray("dew_point_2m")
            val appTempArray = hourly.optJSONArray("apparent_temperature")
            val visArray = hourly.optJSONArray("visibility")
            val uvArray = hourly.optJSONArray("uv_index")

            val result = org.json.JSONObject()
            val cachedCity = json.optString("city")
            val resolvedCity = if (!cachedCity.isNullOrBlank() && cachedCity != "Unknown") {
                cachedCity
            } else {
                prefs.getString("weather_city", "Berlin")?.takeIf { it.isNotBlank() && it != "Unknown" } ?: "Berlin"
            }
            result.put("city", resolvedCity)
            result.put("latitude", json.optDouble("latitude", 0.0))
            result.put("longitude", json.optDouble("longitude", 0.0))

            val temp = if (tempArray != null && !tempArray.isNull(closestIndex)) tempArray.optDouble(closestIndex) else Double.NaN
            val weatherCode = if (codeArray != null && !codeArray.isNull(closestIndex)) codeArray.optInt(closestIndex) else -1
            val precipitation = if (precipArray != null && !precipArray.isNull(closestIndex)) precipArray.optDouble(closestIndex) else 0.0
            val precipitationProbability = if (probArray != null && !probArray.isNull(closestIndex)) probArray.optInt(closestIndex) else 0
            val isDay = if (isDayArray != null && !isDayArray.isNull(closestIndex)) isDayArray.optInt(closestIndex) else 1
            val humidity = if (rhArray != null && !rhArray.isNull(closestIndex)) rhArray.optInt(closestIndex) else 0
            val dewPoint = if (dewArray != null && !dewArray.isNull(closestIndex)) dewArray.optDouble(closestIndex) else Double.NaN
            val apparentTemperature = if (appTempArray != null && !appTempArray.isNull(closestIndex)) appTempArray.optDouble(closestIndex) else Double.NaN
            val visibility = if (visArray != null && !visArray.isNull(closestIndex)) visArray.optDouble(closestIndex) else 0.0
            val uvIndex = if (uvArray != null && !uvArray.isNull(closestIndex)) uvArray.optDouble(closestIndex) else 0.0

            if (temp.isFinite()) result.put("temp", temp) else result.put("temp", org.json.JSONObject.NULL)
            result.put("weatherCode", weatherCode)
            if (precipitation.isFinite()) result.put("precipitation", precipitation) else result.put("precipitation", 0.0)
            result.put("precipitationProbability", precipitationProbability)
            result.put("isDay", isDay)
            result.put("humidity", humidity)
            if (dewPoint.isFinite()) result.put("dewPoint", dewPoint) else result.put("dewPoint", org.json.JSONObject.NULL)
            if (apparentTemperature.isFinite()) result.put("apparentTemperature", apparentTemperature) else result.put("apparentTemperature", org.json.JSONObject.NULL)
            if (visibility.isFinite()) result.put("visibility", visibility) else result.put("visibility", 0.0)
            if (uvIndex.isFinite()) result.put("uvIndex", uvIndex) else result.put("uvIndex", 0.0)

            // add helper fields
            result.put("rh", humidity)
            if (uvIndex.isFinite()) result.put("uv", uvIndex) else result.put("uv", 0.0)

            // calculate today's high and low temps
            var tempMin = Double.MAX_VALUE
            var tempMax = -Double.MAX_VALUE
            val currentDatePrefix = if (timeArray.length() > closestIndex) {
                timeArray.getString(closestIndex).split("T").getOrNull(0)
            } else null

            if (currentDatePrefix != null && tempArray != null) {
                for (i in 0 until timeArray.length()) {
                    val timeStr = timeArray.getString(i)
                    if (timeStr.startsWith(currentDatePrefix)) {
                        if (!tempArray.isNull(i)) {
                            val t = tempArray.optDouble(i)
                            if (t.isFinite()) {
                                if (t < tempMin) tempMin = t
                                if (t > tempMax) tempMax = t
                            }
                        }
                    }
                }
            }

            if (tempMin != Double.MAX_VALUE) result.put("tempMin", tempMin) else result.put("tempMin", org.json.JSONObject.NULL)
            if (tempMax != -Double.MAX_VALUE) result.put("tempMax", tempMax) else result.put("tempMax", org.json.JSONObject.NULL)

            val wmoIcon = getIconLinkFromWmo(weatherCode, isDay)
            var icon = ""
            if (wmoIcon.isNotEmpty()) {
                var url = wmoIcon
                if (url.contains("@2x.png")) {
                    url = url.replace("@2x.png", "@4x.png")
                }
                val fileName = url.substringAfterLast('/')
                val cacheDir = java.io.File(context.cacheDir, "weather_icon_cache")
                val cachedFile = java.io.File(cacheDir, fileName)
                if (cachedFile.exists()) {
                    try {
                        val bytes = cachedFile.readBytes()
                        val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        val mimeType = when {
                            url.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
                            url.endsWith(".png", ignoreCase = true) -> "image/png"
                            url.endsWith(".jpg", ignoreCase = true) || url.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                            else -> "image/png"
                        }
                        icon = "data:$mimeType;base64,$base64Str"
                    } catch (e: Exception) {
                        Log.e("SensorBridge", "Error encoding cached icon", e)
                    }
                }
            }

            if (icon.isEmpty()) {
                icon = if (wmoIcon.isNotEmpty()) {
                    wmoIcon
                } else {
                    when (weatherCode) {
                        0, 1 -> "sunny"
                        2 -> "partly-cloudy"
                        3, 45, 48 -> "cloudy"
                        in 51..67, in 80..82 -> "rainy"
                        in 71..77, in 85..86 -> "snowy"
                        in 95..99 -> "thunderstorm"
                        else -> "unknown"
                    }
                }
            }
            result.put("icon", icon)

            val description = when (weatherCode) {
                0 -> "Clear"
                1 -> "Mainly Clear"
                2 -> "Partly Cloudy"
                3 -> "Cloudy"
                45 -> "Foggy"
                48 -> "Rime Fog"
                51 -> "Light Drizzle"
                53 -> "Drizzle"
                55 -> "Heavy Drizzle"
                56 -> "Light Freezing Drizzle"
                57 -> "Freezing Drizzle"
                61 -> "Light Rain"
                63 -> "Rain"
                65 -> "Heavy Rain"
                66 -> "Light Freezing Rain"
                67 -> "Freezing Rain"
                71 -> "Light Snow"
                73 -> "Snow"
                75 -> "Heavy Snow"
                77 -> "Snow Grains"
                80 -> "Light Showers"
                81 -> "Showers"
                82 -> "Heavy Showers"
                85 -> "Light Snow Showers"
                86 -> "Snow Showers"
                95 -> "Thunderstorm"
                96 -> "Thunderstorms with Hail"
                99 -> "Heavy Thunderstorms"
                else -> "Unknown"
            }
            result.put("description", description)

            result.toString()
        } catch (e: Exception) {
            Log.e("SensorBridge", "Error parsing current weather payload", e)
            fallbackValue
        }
    }

    private fun getIconLinkFromWmo(weatherCode: Int, isDay: Int): String {
        return try {
            val jsonStr = context.assets.open("weather_tools/wmo_code_interpretation.json").bufferedReader().use { it.readText() }
            val mapObj = org.json.JSONObject(jsonStr)
            val codeKey = weatherCode.toString()
            if (mapObj.has(codeKey)) {
                val codeObj = mapObj.getJSONObject(codeKey)
                val timeKey = if (isDay == 1) "day" else "night"
                if (codeObj.has(timeKey)) {
                    val timeObj = codeObj.getJSONObject(timeKey)
                    val image = timeObj.optString("image", "")
                    if (image.startsWith("http://")) {
                        return image.replace("http://", "https://")
                    }
                    return image
                }
            }
            ""
        } catch (e: Exception) {
            Log.e("SensorBridge", "Error loading wmo_code_interpretation.json", e)
            ""
        }
    }
}

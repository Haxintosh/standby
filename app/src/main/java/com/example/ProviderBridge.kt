package com.example

import android.content.Context
import android.webkit.JavascriptInterface
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale

class ProviderBridge(
    private val context: Context,
    private val allowedProvidersProvider: () -> List<String>
) {
    constructor(
        context: Context,
        allowedProviders: List<String>
    ) : this(context, { allowedProviders })

    private val allowedProviders: List<String>
        get() = allowedProvidersProvider()

    @JavascriptInterface
    fun getWeatherData(): String {
        val fallbackValue = "{}"
        if (!allowedProviders.contains("weather")) {
            Log.w("ProviderBridge", "Blocked access to weather: Provider not declared in manifest")
            return fallbackValue
        }
        val prefs = context.getSharedPreferences("standby_settings", Context.MODE_PRIVATE)
        return prefs.getString("weather_cache", fallbackValue) ?: fallbackValue
    }

    @JavascriptInterface
    fun getCurrentWeather(): String {
        val fallbackValue = "{}"
        if (!allowedProviders.contains("weather")) {
            Log.w("ProviderBridge", "Blocked access to weather: Provider not declared in manifest")
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
                        Log.e("ProviderBridge", "Error encoding cached icon", e)
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
            Log.e("ProviderBridge", "Error parsing current weather payload", e)
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
            Log.e("ProviderBridge", "Error loading wmo_code_interpretation.json", e)
            ""
        }
    }
}

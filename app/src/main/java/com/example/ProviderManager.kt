package com.example

import android.content.Context
import android.net.Uri
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.*
import java.io.File

class ProviderManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {

    private val prefs = context.getSharedPreferences("standby_settings", Context.MODE_PRIVATE)
    private var weatherJob: Job? = null

    data class GeocodingResult(
        val name: String,
        val country: String?,
        val admin1: String?,
        val latitude: Double,
        val longitude: Double
    )

    fun startHourlyWeatherUpdates(scope: CoroutineScope) {
        weatherJob?.cancel()
        weatherJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val lastUpdate = prefs.getLong("weather_last_update", 0L)
                val now = System.currentTimeMillis()
                if (now - lastUpdate >= 3600000L || prefs.getString("weather_cache", null).isNullOrBlank()) {
                    fetchWeather()
                }
                delay(60000L) // check every minute
            }
        }
    }

    fun stopWeatherUpdates() {
        weatherJob?.cancel()
        weatherJob = null
    }

    fun triggerWeatherRefresh(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            fetchWeather()
        }
    }

    suspend fun searchLocations(query: String): List<GeocodingResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(query)}&count=10&language=en&format=json"
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val json = JSONObject(body)
                    val resultsArray = json.optJSONArray("results") ?: return@withContext emptyList()
                    val list = mutableListOf<GeocodingResult>()
                    for (i in 0 until resultsArray.length()) {
                        val item = resultsArray.getJSONObject(i)
                        list.add(
                            GeocodingResult(
                                name = item.getString("name"),
                                country = item.optString("country").takeIf { it.isNotEmpty() },
                                admin1 = item.optString("admin1").takeIf { it.isNotEmpty() },
                                latitude = item.getDouble("latitude"),
                                longitude = item.getDouble("longitude")
                            )
                        )
                    }
                    list
                } else emptyList()
            }
        } catch (e: Exception) {
            Log.e("ProviderManager", "Error searching locations", e)
            emptyList()
        }
    }

    private fun getCityFromCoordinates(latitude: Double, longitude: Double): String? {
        return try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                address.locality ?: address.subAdminArea ?: address.adminArea ?: address.countryName
            } else null
        } catch (e: Exception) {
            Log.e("ProviderManager", "Geocoder failed", e)
            null
        }
    }

    suspend fun fetchWeather() = withContext(Dispatchers.IO) {
        var lat: String? = null
        var lon: String? = null

        if (prefs.getBoolean("weather_use_gps", false)) {
            val gpsLocation = getLastKnownGpsLocation()
            if (gpsLocation != null) {
                lat = gpsLocation.first
                lon = gpsLocation.second
                val resolvedCity = getCityFromCoordinates(lat.toDouble(), lon.toDouble()) ?: "Current Location"
                prefs.edit().putString("weather_city", resolvedCity).apply()
            }
        }

        if (lat.isNullOrBlank() || lon.isNullOrBlank()) {
            lat = prefs.getString("weather_lat", null)
            lon = prefs.getString("weather_lon", null)
        }

        if (lat.isNullOrBlank() || lon.isNullOrBlank()) {
            val ipLocation = fetchIpLocation()
            if (ipLocation != null) {
                lat = ipLocation.first
                lon = ipLocation.second
                val city = ipLocation.third
                prefs.edit()
                    .putString("weather_lat", lat)
                    .putString("weather_lon", lon)
                    .putString("weather_city", city)
                    .apply()
            }
        }

        if (lat.isNullOrBlank() || lon.isNullOrBlank()) {
            // Default fallback: Berlin
            lat = "52.52"
            lon = "13.41"
            if (prefs.getString("weather_city", null).isNullOrBlank()) {
                prefs.edit().putString("weather_city", "Berlin").apply()
            }
        }

        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&hourly=temperature_2m,weather_code,precipitation,precipitation_probability,is_day,relative_humidity_2m,dew_point_2m,apparent_temperature,visibility,uv_index&timezone=auto"
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val cityName = prefs.getString("weather_city", "Berlin") ?: "Berlin"
                        json.put("city", cityName)

                        // cache forecast icons
                        cacheForecastIcons(json)

                        prefs.edit()
                            .putString("weather_cache", json.toString())
                            .putLong("weather_last_update", System.currentTimeMillis())
                            .apply()
                        Log.d("ProviderManager", "Successfully updated weather forecast for $lat, $lon")
                    }
                } else {
                    Log.e("ProviderManager", "Failed weather fetch: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("ProviderManager", "Error fetching weather", e)
        }
    }

    private fun fetchIpLocation(): Triple<String, String, String>? {
        val request = Request.Builder()
            .url("https://ipapi.co/json/")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    val lat = json.optDouble("latitude", Double.NaN)
                    val lon = json.optDouble("longitude", Double.NaN)
                    val city = json.optString("city", "Auto (IP)")
                    if (!lat.isNaN() && !lon.isNaN()) {
                        Triple(lat.toString(), lon.toString(), city)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.e("ProviderManager", "Error fetching IP location", e)
            null
        }
    }

    // TO BE DEPRECATED - We don't want to expose the app the users actual location/request locations perms
    // the default should be IP addr based location if possible instead of using more sensitive APIs
    private fun getLastKnownGpsLocation(): Pair<String, String>? {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return null

        val gpsLocation = try {
            if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            } else null
        } catch (e: Exception) { null }

        val networkLocation = try {
            if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            } else null
        } catch (e: Exception) { null }

        val bestLocation = when {
            gpsLocation != null && networkLocation != null -> {
                if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
            }
            gpsLocation != null -> gpsLocation
            else -> networkLocation
        }

        return bestLocation?.let {
            Pair(it.latitude.toString(), it.longitude.toString())
        }
    }

    private fun cacheForecastIcons(json: JSONObject) {
        val hourly = json.optJSONObject("hourly") ?: return
        val weatherCodeArray = hourly.optJSONArray("weather_code") ?: return
        val isDayArray = hourly.optJSONArray("is_day") ?: return
        
        val uniquePairs = mutableSetOf<Pair<Int, Int>>()
        val count = Math.min(weatherCodeArray.length(), isDayArray.length())
        for (i in 0 until count) {
            if (!weatherCodeArray.isNull(i) && !isDayArray.isNull(i)) {
                uniquePairs.add(Pair(weatherCodeArray.getInt(i), isDayArray.getInt(i)))
            }
        }
        
        val wmoJsonStr = try {
            context.assets.open("weather_tools/wmo_code_interpretation.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("ProviderManager", "Error reading wmo_code_interpretation.json", e)
            return
        }
        val wmoJson = JSONObject(wmoJsonStr)
        
        val cacheDir = File(context.cacheDir, "weather_icon_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        for (pair in uniquePairs) {
            val code = pair.first
            val isDay = pair.second
            var url = getIconUrlFromWmo(wmoJson, code, isDay)
            if (url.isNotEmpty()) {
                // use 4x icon resolution
                if (url.contains("@2x.png")) {
                    url = url.replace("@2x.png", "@4x.png")
                }
                val fileName = getFileNameFromUrl(url)
                val targetFile = File(cacheDir, fileName)
                if (!targetFile.exists()) {
                    downloadAndCacheIcon(url, targetFile)
                }
            }
        }
    }

    private fun getIconUrlFromWmo(wmoJson: JSONObject, weatherCode: Int, isDay: Int): String {
        val codeKey = weatherCode.toString()
        if (wmoJson.has(codeKey)) {
            val codeObj = wmoJson.getJSONObject(codeKey)
            val timeKey = if (isDay == 1) "day" else "night"
            if (codeObj.has(timeKey)) {
                val timeObj = codeObj.getJSONObject(timeKey)
                return timeObj.optString("image", "")
            }
        }
        return ""
    }

    private fun getFileNameFromUrl(url: String): String {
        return url.substringAfterLast('/', "icon_${System.currentTimeMillis()}.png")
    }

    private fun downloadAndCacheIcon(url: String, targetFile: File) {
        val secureUrl = if (url.startsWith("http://")) url.replace("http://", "https://") else url
        val request = Request.Builder().url(secureUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        targetFile.outputStream().use { output ->
                            body.byteStream().copyTo(output)
                        }
                        Log.d("ProviderManager", "Successfully cached icon from $secureUrl to ${targetFile.absolutePath}")
                    }
                } else {
                    Log.e("ProviderManager", "Failed to download icon from $secureUrl: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("ProviderManager", "Error downloading icon from $secureUrl", e)
        }
    }
}

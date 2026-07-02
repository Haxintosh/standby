package com.example

import android.content.Context
import android.hardware.SensorManager
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBatteryManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SensorBridgeTest {

    @Test
    fun testBatteryPermissionsBlock() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridgeNoPerms = SensorBridge(context, emptyList()) { "{}" }

        // When permissions are missing, fallback values should be returned
        val levelObj = JSONObject(bridgeNoPerms.getBatteryLevel())
        assertEquals(0, levelObj.getInt("level"))
        
        val chargingStatus = JSONObject(bridgeNoPerms.getChargingStatus())
        assertEquals(false, chargingStatus.getBoolean("isCharging"))
        assertEquals("Unknown", chargingStatus.getString("source"))
        assertEquals(0, chargingStatus.getInt("temp"))
        assertEquals(-1L, chargingStatus.getLong("timeRemaining"))

        val batteryStats = JSONObject(bridgeNoPerms.getBatteryStats())
        assertEquals(0, batteryStats.getInt("current"))
        assertEquals(0, batteryStats.getInt("voltage"))
        assertEquals(1, batteryStats.getInt("health"))
        assertEquals(0, batteryStats.getInt("chargeCount"))

        val lightObj = JSONObject(bridgeNoPerms.getAmbientLight())
        assertEquals(10, lightObj.getInt("light"))

        val proximityObj = JSONObject(bridgeNoPerms.getProximity())
        assertEquals(5, proximityObj.getInt("proximity"))

        val alarmObj = JSONObject(bridgeNoPerms.getNextAlarm())
        assertEquals(false, alarmObj.getBoolean("hasAlarm"))
        assertEquals(0L, alarmObj.getLong("triggerTime"))
        assertTrue(alarmObj.isNull("creatorPackage"))
        assertEquals("", alarmObj.getString("formattedTime"))

        val providerNoPerms = ProviderBridge(context, emptyList())
        val weatherObj = JSONObject(providerNoPerms.getWeatherData())
        assertEquals("{}", weatherObj.toString())

        val currentWeatherObj = JSONObject(providerNoPerms.getCurrentWeather())
        assertEquals("{}", currentWeatherObj.toString())
    }

    @Test
    fun testBatteryPermissionsAllowed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridgeWithPerms = SensorBridge(
            context,
            listOf("battery_level", "battery_charging", "battery_stats", "ambient_light", "proximity")
        ) { "{}" }

        // By default on Robolectric, some values should be resolved without crashing
        // The battery level is mockable
        val levelObj = JSONObject(bridgeWithPerms.getBatteryLevel())
        assertTrue(levelObj.getInt("level") >= 0)

        val chargingStatus = JSONObject(bridgeWithPerms.getChargingStatus())
        assertTrue(chargingStatus.get("isCharging") is Boolean)
        assertTrue(chargingStatus.get("source") is String)
        assertTrue(chargingStatus.get("temp") is Int)
        assertTrue(chargingStatus.get("timeRemaining") is Number)

        val batteryStats = JSONObject(bridgeWithPerms.getBatteryStats())
        assertTrue(batteryStats.get("current") is Int)
        assertTrue(batteryStats.get("voltage") is Int)
        assertTrue(batteryStats.get("health") is Int)
        assertTrue(batteryStats.get("chargeCount") is Int)

        // Sensors will likely time out and return fallback values under Robolectric, but should not crash
        val lightObj = JSONObject(bridgeWithPerms.getAmbientLight())
        assertEquals(10, lightObj.getInt("light"))

        val proximityObj = JSONObject(bridgeWithPerms.getProximity())
        assertEquals(5, proximityObj.getInt("proximity"))
    }

    @Test
    fun testAlarmPermissionAllowed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridgeWithPerms = SensorBridge(
            context,
            listOf("alarms")
        ) { "{}" }

        val alarmObj = JSONObject(bridgeWithPerms.getNextAlarm())
        // Should query AlarmManager and return next alarm (which is null in test env) without crashing
        assertFalse(alarmObj.getBoolean("hasAlarm"))
        assertEquals(0L, alarmObj.getLong("triggerTime"))
        assertTrue(alarmObj.isNull("creatorPackage"))
        assertEquals("", alarmObj.getString("formattedTime"))
    }

    @Test
    fun testGeneralPermissionsAllowed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridgeWithGeneralPerms = SensorBridge(
            context,
            listOf("battery", "sensors")
        ) { "{}" }

        val levelObj = JSONObject(bridgeWithGeneralPerms.getBatteryLevel())
        assertTrue(levelObj.getInt("level") >= 0)

        val chargingStatus = JSONObject(bridgeWithGeneralPerms.getChargingStatus())
        assertTrue(chargingStatus.get("isCharging") is Boolean)

        val batteryStats = JSONObject(bridgeWithGeneralPerms.getBatteryStats())
        assertTrue(batteryStats.get("chargeCount") is Int)

        val lightObj = JSONObject(bridgeWithGeneralPerms.getAmbientLight())
        assertEquals(10, lightObj.getInt("light"))

        val proximityObj = JSONObject(bridgeWithGeneralPerms.getProximity())
        assertEquals(5, proximityObj.getInt("proximity"))
    }

    @Test
    fun testWeatherPermissionAllowed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val providerWithPerms = ProviderBridge(
            context,
            listOf("weather")
        )

        val weatherObj = JSONObject(providerWithPerms.getWeatherData())
        assertEquals("{}", weatherObj.toString())

        val currentWeatherObj = JSONObject(providerWithPerms.getCurrentWeather())
        assertEquals("{}", currentWeatherObj.toString())
    }

    @Test
    fun testCurrentWeatherCacheParsing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("standby_settings", Context.MODE_PRIVATE)

        val mockWeatherJson = """
            {
              "latitude": 52.52,
              "longitude": 13.41,
              "city": "Berlin",
              "hourly": {
                "time": ["2026-06-25T12:00"],
                "temperature_2m": [22.5],
                "weather_code": [3],
                "relative_humidity_2m": [65]
              }
            }
        """.trimIndent()

        prefs.edit().putString("weather_cache", mockWeatherJson).commit()

        // write mock icon file to cache
        val cacheDir = java.io.File(context.cacheDir, "weather_icon_cache")
        cacheDir.mkdirs()
        val mockFile = java.io.File(cacheDir, "03d@4x.png")
        mockFile.writeBytes("mock_image_bytes".toByteArray())

        val providerWithPerms = ProviderBridge(
            context,
            listOf("weather")
        )

        val currentWeatherObj = JSONObject(providerWithPerms.getCurrentWeather())
        assertEquals("Berlin", currentWeatherObj.getString("city"))
        assertEquals(22.5, currentWeatherObj.getDouble("temp"), 0.01)
        assertEquals(3, currentWeatherObj.getInt("weatherCode"))
        assertEquals(65, currentWeatherObj.getInt("humidity"))
        assertEquals(65, currentWeatherObj.getInt("rh"))
        assertEquals("data:image/png;base64,bW9ja19pbWFnZV9ieXRlcw==", currentWeatherObj.getString("icon"))
        assertEquals("Cloudy", currentWeatherObj.getString("description"))
        assertEquals(22.5, currentWeatherObj.getDouble("tempMin"), 0.01)
        assertEquals(22.5, currentWeatherObj.getDouble("tempMax"), 0.01)
        assertEquals(0.0, currentWeatherObj.getDouble("uv"), 0.01)
        assertEquals(0.0, currentWeatherObj.getDouble("uvIndex"), 0.01)
    }
}

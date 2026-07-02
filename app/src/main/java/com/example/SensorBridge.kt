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
}

package com.example

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.webkit.JavascriptInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SensorBridge(private val context: Context) {
    @JavascriptInterface
    fun getBatteryLevel(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level == -1 || scale == -1) {
            50 // Fallback
        } else {
            (level * 100 / scale.toFloat()).toInt()
        }
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
}

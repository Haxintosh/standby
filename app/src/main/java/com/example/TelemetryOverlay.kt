package com.example

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.*

@Composable
fun TelemetryOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var lightValue by remember { mutableStateOf(-1f) }
    var proximityValue by remember { mutableStateOf(-1f) }
    var proximityMaxRange by remember { mutableStateOf(-1f) }
    var vibrationStrength by remember { mutableStateOf(0f) }
    
    var lastVibrationTime by remember { mutableStateOf(0L) }
    var isVibratingActive by remember { mutableStateOf(false) }
    
    var lastLightTriggerTime by remember { mutableStateOf(0L) }
    var isLightSpikeActive by remember { mutableStateOf(false) }

    // reset vibration state after 5s
    LaunchedEffect(lastVibrationTime) {
        if (lastVibrationTime > 0) {
            isVibratingActive = true
            delay(5000L)
            isVibratingActive = false
        }
    }

    // reset light spike state after 5s
    LaunchedEffect(lastLightTriggerTime) {
        if (lastLightTriggerTime > 0) {
            isLightSpikeActive = true
            delay(5000L)
            isLightSpikeActive = false
        }
    }
    
    // sensor trigger states
    val isLightDetected = lightValue > 10f || isLightSpikeActive
    val isProximityDetected = proximityValue in 0f..<(if (proximityMaxRange > 0) proximityMaxRange else 5f)
    val isVibrationDetected = isVibratingActive

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        var lastLight = -1f

        // previous accelerometer values
        var prevX = 0f
        var prevY = 0f
        var prevZ = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                
                when (event.sensor.type) {
                    Sensor.TYPE_LIGHT -> {
                        val currentLight = event.values[0]
                        lightValue = currentLight
                        
                        // check for light spike
                        if (lastLight != -1f && (currentLight - lastLight) > 5f) {
                            lastLightTriggerTime = System.currentTimeMillis()
                        }
                        lastLight = currentLight
                    }
                    Sensor.TYPE_PROXIMITY -> {
                        proximityValue = event.values[0]
                        proximityMaxRange = event.sensor.maximumRange
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        
                        if (prevX != 0f || prevY != 0f || prevZ != 0f) {
                            val dx = x - prevX
                            val dy = y - prevY
                            val dz = z - prevZ
                            val diff = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
                            
                            vibrationStrength = diff
                            
                            // vibration sensitivity threshold
                            if (diff > 0.15f) {
                                lastVibrationTime = System.currentTimeMillis()
                            }
                        }
                        
                        prevX = x
                        prevY = y
                        prevZ = z
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                lightSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
                proximitySensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
                // sensor sample rate
                accelerometerSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                sensorManager.unregisterListener(listener)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(listener)
        }
    }

    // telemetry container
    Column(
        modifier = modifier
            .background(Color(0x99000000))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val activeColor = Color.Green
        val inactiveColor = Color.Red

        // sensor values
        Text(
            text = if (lightValue == -1f) "N/A" else String.format(Locale.US, "%.1f lx", lightValue),
            color = if (isLightDetected) activeColor else inactiveColor,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = if (proximityValue == -1f) "N/A" else String.format(Locale.US, "%.1f cm", proximityValue),
            color = if (isProximityDetected) activeColor else inactiveColor,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = String.format(Locale.US, "%.3f m/s²", vibrationStrength),
            color = if (isVibrationDetected) activeColor else inactiveColor,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

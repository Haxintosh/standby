package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    burnInProtectionEnabled: Boolean,
    onBurnInProtectionEnabledChange: (Boolean) -> Unit,
    delayAfterInteraction: Boolean,
    onDelayAfterInteractionChange: (Boolean) -> Unit,
    protectionRatio: Int,
    onProtectionRatioChange: (Int) -> Unit,
    serverEnabled: Boolean,
    onServerEnabledChange: (Boolean) -> Unit,
    serverIp: String,
    serverPort: Int,
    serverPin: String,
    hideControlsOnIdle: Boolean,
    onHideControlsOnIdleChange: (Boolean) -> Unit,
    lowRefreshRateEnabled: Boolean,
    onLowRefreshRateEnabledChange: (Boolean) -> Unit,
    lowRefreshRateValue: Int,
    onLowRefreshRateValueChange: (Int) -> Unit,
    supportedRefreshRates: List<Int>,
    confirmImportEnabled: Boolean,
    onConfirmImportEnabledChange: (Boolean) -> Unit,
    appWidgetsEnabled: Boolean,
    onAppWidgetsEnabledChange: (Boolean) -> Unit,
    nightModeEnabled: Boolean,
    onNightModeEnabledChange: (Boolean) -> Unit,
    nightStartHour: Int,
    nightStartMinute: Int,
    onNightStartTimeChange: (Int, Int) -> Unit,
    nightEndHour: Int,
    nightEndMinute: Int,
    onNightEndTimeChange: (Int, Int) -> Unit,
    nightProtectionRatio: Int,
    onNightProtectionRatioChange: (Int) -> Unit,
    nightBrightnessEnabled: Boolean,
    onNightBrightnessEnabledChange: (Boolean) -> Unit,
    nightBrightnessValue: Float,
    onNightBrightnessValueChange: (Float) -> Unit,
    isNightModeActive: Boolean,
    weatherLat: String,
    weatherLon: String,
    weatherCity: String,
    weatherUseGps: Boolean,
    weatherLastUpdate: Long,
    onWeatherLocationChange: (String, String, String) -> Unit,
    onWeatherUseGpsChange: (Boolean) -> Unit,
    onWeatherRefresh: () -> Unit,
    onSearchLocations: suspend (String) -> List<ProviderManager.GeocodingResult>,
    onDismissRequest: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp)
        ) {
            // header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(520.dp)
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("General", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Night Mode", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            text = { Text("Providers", fontWeight = FontWeight.Bold) }
                        )
                    }
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // tab content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTabIndex) {
                    0 -> GeneralSettingsTab(
                        burnInProtectionEnabled = burnInProtectionEnabled,
                        onBurnInProtectionEnabledChange = onBurnInProtectionEnabledChange,
                        delayAfterInteraction = delayAfterInteraction,
                        onDelayAfterInteractionChange = onDelayAfterInteractionChange,
                        protectionRatio = protectionRatio,
                        onProtectionRatioChange = onProtectionRatioChange,
                        serverEnabled = serverEnabled,
                        onServerEnabledChange = onServerEnabledChange,
                        serverIp = serverIp,
                        serverPort = serverPort,
                        serverPin = serverPin,
                        hideControlsOnIdle = hideControlsOnIdle,
                        onHideControlsOnIdleChange = onHideControlsOnIdleChange,
                        lowRefreshRateEnabled = lowRefreshRateEnabled,
                        onLowRefreshRateEnabledChange = onLowRefreshRateEnabledChange,
                        lowRefreshRateValue = lowRefreshRateValue,
                        onLowRefreshRateValueChange = onLowRefreshRateValueChange,
                        supportedRefreshRates = supportedRefreshRates,
                        confirmImportEnabled = confirmImportEnabled,
                        onConfirmImportEnabledChange = onConfirmImportEnabledChange,
                        appWidgetsEnabled = appWidgetsEnabled,
                        onAppWidgetsEnabledChange = onAppWidgetsEnabledChange
                    )
                    1 -> NightModeTab(
                        nightModeEnabled = nightModeEnabled,
                        onNightModeEnabledChange = onNightModeEnabledChange,
                        nightStartHour = nightStartHour,
                        nightStartMinute = nightStartMinute,
                        onNightStartTimeChange = onNightStartTimeChange,
                        nightEndHour = nightEndHour,
                        nightEndMinute = nightEndMinute,
                        onNightEndTimeChange = onNightEndTimeChange,
                        nightProtectionRatio = nightProtectionRatio,
                        onNightProtectionRatioChange = onNightProtectionRatioChange,
                        nightBrightnessEnabled = nightBrightnessEnabled,
                        onNightBrightnessEnabledChange = onNightBrightnessEnabledChange,
                        nightBrightnessValue = nightBrightnessValue,
                        onNightBrightnessValueChange = onNightBrightnessValueChange,
                        isNightModeActive = isNightModeActive
                    )
                    else -> ProviderSettingsTab(
                        weatherLat = weatherLat,
                        weatherLon = weatherLon,
                        weatherCity = weatherCity,
                        weatherUseGps = weatherUseGps,
                        weatherLastUpdate = weatherLastUpdate,
                        onWeatherLocationChange = onWeatherLocationChange,
                        onWeatherUseGpsChange = onWeatherUseGpsChange,
                        onWeatherRefresh = onWeatherRefresh,
                        onSearchLocations = onSearchLocations
                    )
                }
            }
        }
    }
}

@Composable
fun GeneralSettingsTab(
    burnInProtectionEnabled: Boolean,
    onBurnInProtectionEnabledChange: (Boolean) -> Unit,
    delayAfterInteraction: Boolean,
    onDelayAfterInteractionChange: (Boolean) -> Unit,
    protectionRatio: Int,
    onProtectionRatioChange: (Int) -> Unit,
    serverEnabled: Boolean,
    onServerEnabledChange: (Boolean) -> Unit,
    serverIp: String,
    serverPort: Int,
    serverPin: String,
    hideControlsOnIdle: Boolean,
    onHideControlsOnIdleChange: (Boolean) -> Unit,
    lowRefreshRateEnabled: Boolean,
    onLowRefreshRateEnabledChange: (Boolean) -> Unit,
    lowRefreshRateValue: Int,
    onLowRefreshRateValueChange: (Int) -> Unit,
    supportedRefreshRates: List<Int>,
    confirmImportEnabled: Boolean,
    onConfirmImportEnabledChange: (Boolean) -> Unit,
    appWidgetsEnabled: Boolean,
    onAppWidgetsEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // left column
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = borderStroke()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "OLED Protection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Protection",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Combats static screen burn-in risk",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = burnInProtectionEnabled,
                        onCheckedChange = onBurnInProtectionEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
                
                if (burnInProtectionEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hide on touch (5s delay)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Temporarily reveals widgets when interacted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = delayAfterInteraction,
                            onCheckedChange = onDelayAfterInteractionChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    
                    val percentage = (protectionRatio.toFloat() / (protectionRatio + 1) * 100).toInt()
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Protection Strength",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$percentage% Pixels Off",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val haptic = LocalHapticFeedback.current
                        Slider(
                            value = protectionRatio.toFloat(),
                            onValueChange = { newValue ->
                                val newInt = newValue.toInt()
                                if (newInt != protectionRatio) {
                                    onProtectionRatioChange(newInt)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            valueRange = 1f..5f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                thumbColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Minimal (50%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Maximum (83%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Warning: Disabling protection may lead to screen burn-in on OLED displays.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                // interface settings
                Text(
                    text = "Interface Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hide Controls on Idle",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Hides UI buttons after 5 seconds of inactivity",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hideControlsOnIdle,
                        onCheckedChange = onHideControlsOnIdleChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                // display refresh rate
                Text(
                    text = "Display Refresh Rate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Low Refresh Rate on Idle",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Forces display to lowest rate after 5s idle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lowRefreshRateEnabled,
                        onCheckedChange = onLowRefreshRateEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
                
                if (lowRefreshRateEnabled && supportedRefreshRates.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Idle Refresh Rate Target",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$lowRefreshRateValue Hz",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val haptic = LocalHapticFeedback.current
                        if (supportedRefreshRates.size > 1) {
                            val currentIndex = supportedRefreshRates.indexOf(lowRefreshRateValue).coerceAtLeast(0)
                            Slider(
                                value = currentIndex.toFloat(),
                                onValueChange = { newValue ->
                                    val newIndex = newValue.roundToInt().coerceIn(0, supportedRefreshRates.size - 1)
                                    if (supportedRefreshRates[newIndex] != lowRefreshRateValue) {
                                        onLowRefreshRateValueChange(supportedRefreshRates[newIndex])
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                valueRange = 0f..(supportedRefreshRates.size - 1).toFloat(),
                                steps = if (supportedRefreshRates.size > 2) supportedRefreshRates.size - 2 else 0,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    thumbColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${supportedRefreshRates.first()} Hz",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${supportedRefreshRates.last()} Hz",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = "Only ${supportedRefreshRates.firstOrNull() ?: 60} Hz is supported by this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // app widgets
                Text(
                    text = "App Widgets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable App Widgets",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Allows using native system and 3rd-party Android app widgets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appWidgetsEnabled,
                        onCheckedChange = onAppWidgetsEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
        
        // right column
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = borderStroke()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Remote Plugin Server",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable HTTP Server",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Allows remote widget installation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = serverEnabled,
                        onCheckedChange = onServerEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
                
                if (serverEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = Color(0xFF4CAF50),
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = "Server running on local network",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        
                        Column {
                            Text(
                                text = "HTTP SERVER ADDRESS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "http://$serverIp:$serverPort",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        
                        Column {
                            Text(
                                text = "SERVER SECURITY PIN",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = serverPin,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                    
                    Text(
                        text = "To upload plugins: Open the URL above on your computer/phone and enter the PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = "Server offline",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Remote widget uploader is disabled. Enable it to transfer HTML widget layouts wirelessly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                Text(
                    text = "Import Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Confirm Plugin Import",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Prompt details before adding new widgets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = confirmImportEnabled,
                        onCheckedChange = onConfirmImportEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun NightModeTab(
    nightModeEnabled: Boolean,
    onNightModeEnabledChange: (Boolean) -> Unit,
    nightStartHour: Int,
    nightStartMinute: Int,
    onNightStartTimeChange: (Int, Int) -> Unit,
    nightEndHour: Int,
    nightEndMinute: Int,
    onNightEndTimeChange: (Int, Int) -> Unit,
    nightProtectionRatio: Int,
    onNightProtectionRatioChange: (Int) -> Unit,
    nightBrightnessEnabled: Boolean,
    onNightBrightnessEnabledChange: (Boolean) -> Unit,
    nightBrightnessValue: Float,
    onNightBrightnessValueChange: (Float) -> Unit,
    isNightModeActive: Boolean
) {
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Left Column: Activation & Schedule
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = borderStroke()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Night Mode Schedule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Night Mode",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Automate display dimming and maximum burn-in protection during sleep hours",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = nightModeEnabled,
                        onCheckedChange = onNightModeEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                if (nightModeEnabled) {
                    // Status Badge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isNightModeActive)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (isNightModeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = if (isNightModeActive)
                                    "Night Mode is currently ACTIVE"
                                else
                                    "Night Mode is currently inactive (Daytime)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isNightModeActive)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "Schedule Times",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Night Start Time Card
                    Card(
                        onClick = { showStartTimePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        ),
                        border = borderStroke(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Night Start Time",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "When night OLED protection and dimming begins",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = formatTime(nightStartHour, nightStartMinute),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Wake Up Time Card
                    Card(
                        onClick = { showEndTimePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        ),
                        border = borderStroke(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Wake Up Time",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Releases brightness lock back to system control",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = formatTime(nightEndHour, nightEndMinute),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Right Column: Display Adjustments (OLED & Brightness)
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = borderStroke()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Night Display Adjustments",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // OLED Protection Section
                val percentage = (nightProtectionRatio.toFloat() / (nightProtectionRatio + 1) * 100).toInt()
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Night OLED Protection Strength",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Overrides standard OLED protection during night hours",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "$percentage% Pixels Off",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    val haptic = LocalHapticFeedback.current
                    Slider(
                        value = nightProtectionRatio.toFloat(),
                        onValueChange = { newValue ->
                            val newInt = newValue.toInt()
                            if (newInt != nightProtectionRatio) {
                                onNightProtectionRatioChange(newInt)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        valueRange = 1f..5f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            thumbColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Minimal (50%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Maximum (83%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Screen Brightness Section
                Text(
                    text = "Screen Brightness",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lower Brightness at Night",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Locks screen to low brightness during sleep hours",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = nightBrightnessEnabled,
                        onCheckedChange = onNightBrightnessEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                if (nightBrightnessEnabled) {
                    val brightnessPercent = (nightBrightnessValue * 100).toInt().coerceIn(1, 100)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Night Brightness Level",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$brightnessPercent%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        val haptic = LocalHapticFeedback.current
                        Slider(
                            value = nightBrightnessValue,
                            onValueChange = { newValue ->
                                onNightBrightnessValueChange(newValue)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            valueRange = 0.01f..0.50f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                thumbColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "1% (Dim)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "50%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "When wake up time is reached, the brightness lock is released automatically so your phone resumes control of the brightness.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showStartTimePicker) {
        TimeSelectionDialog(
            title = "Set Night Start Time",
            initialHour = nightStartHour,
            initialMinute = nightStartMinute,
            onConfirm = { h, m ->
                onNightStartTimeChange(h, m)
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        TimeSelectionDialog(
            title = "Set Wake Up Time",
            initialHour = nightEndHour,
            initialMinute = nightEndMinute,
            onConfirm = { h, m ->
                onNightEndTimeChange(h, m)
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSelectionDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimeInput(
                    state = timePickerState
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTime(hour: Int, minute: Int): String {
    val amPm = if (hour >= 12) "PM" else "AM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format("%02d:%02d %s (%02d:%02d)", displayHour, minute, amPm, hour, minute)
}

@Composable
fun ProviderSettingsTab(
    weatherLat: String,
    weatherLon: String,
    weatherCity: String,
    weatherUseGps: Boolean,
    weatherLastUpdate: Long,
    onWeatherLocationChange: (String, String, String) -> Unit,
    onWeatherUseGpsChange: (Boolean) -> Unit,
    onWeatherRefresh: () -> Unit,
    onSearchLocations: suspend (String) -> List<ProviderManager.GeocodingResult>
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = borderStroke()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Weather Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            var searchQuery by remember { mutableStateOf("") }
            var searchResults by remember { mutableStateOf<List<ProviderManager.GeocodingResult>>(emptyList()) }
            val coroutineScope = rememberCoroutineScope()
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { newValue ->
                        searchQuery = newValue
                        if (newValue.length >= 2) {
                            coroutineScope.launch {
                                searchResults = onSearchLocations(newValue)
                            }
                        } else {
                            searchResults = emptyList()
                        }
                    },
                    label = { Text("Search City/Region") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                if (searchResults.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(4.dp)
                    ) {
                        searchResults.forEach { result ->
                            TextButton(
                                onClick = {
                                    val displayName = "${result.name}${if (result.admin1 != null) ", ${result.admin1}" else ""}${if (result.country != null) ", ${result.country}" else ""}"
                                    onWeatherLocationChange(result.latitude.toString(), result.longitude.toString(), displayName)
                                    searchQuery = ""
                                    searchResults = emptyList()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Text(
                                        text = "${result.name}${if (result.admin1 != null) ", ${result.admin1}" else ""}${if (result.country != null) ", ${result.country}" else ""}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Active Location",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = weatherCity,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Lat: $weatherLat, Lon: $weatherLon",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onWeatherRefresh,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Refresh")
                    }
                }
                
                if (weatherLastUpdate > 0L) {
                    val formattedTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(weatherLastUpdate))
                    Text(
                        text = "Last updated: $formattedTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Not updated yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun borderStroke() = androidx.compose.foundation.BorderStroke(
    width = 1.dp,
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
)

@Preview(device = "spec:width=4000px,height=2424px")
@Composable
fun SettingsDialogPreview() {
    SettingsDialog(
        burnInProtectionEnabled = true,
        onBurnInProtectionEnabledChange = {},
        delayAfterInteraction = false,
        onDelayAfterInteractionChange = {},
        protectionRatio = 1,
        onProtectionRatioChange = {},
        serverEnabled = true,
        onServerEnabledChange = {},
        serverIp = "192.168.1.100",
        serverPort = 8080,
        serverPin = "1234",
        hideControlsOnIdle = true,
        onHideControlsOnIdleChange = {},
        lowRefreshRateEnabled = true,
        onLowRefreshRateEnabledChange = {},
        lowRefreshRateValue = 60,
        onLowRefreshRateValueChange = {},
        supportedRefreshRates = listOf(60, 90, 120),
        confirmImportEnabled = true,
        onConfirmImportEnabledChange = {},
        appWidgetsEnabled = true,
        onAppWidgetsEnabledChange = {},
        nightModeEnabled = true,
        onNightModeEnabledChange = {},
        nightStartHour = 22,
        nightStartMinute = 0,
        onNightStartTimeChange = { _, _ -> },
        nightEndHour = 7,
        nightEndMinute = 0,
        onNightEndTimeChange = { _, _ -> },
        nightProtectionRatio = 4,
        onNightProtectionRatioChange = {},
        nightBrightnessEnabled = true,
        onNightBrightnessEnabledChange = {},
        nightBrightnessValue = 0.05f,
        onNightBrightnessValueChange = {},
        isNightModeActive = false,
        weatherLat = "52.52",
        weatherLon = "13.41",
        weatherCity = "Berlin",
        weatherUseGps = false,
        weatherLastUpdate = 0L,
        onWeatherLocationChange = { _, _, _ -> },
        onWeatherUseGpsChange = {},
        onWeatherRefresh = {},
        onSearchLocations = { emptyList() },
        onDismissRequest = {}
    )
}

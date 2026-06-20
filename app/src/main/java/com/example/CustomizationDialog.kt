package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CustomizationDialog(
    activePage: StandbyPage?,
    onCustomizationValueChange: (String, String, String) -> Unit, // plugin id, variable name, variable value
    onDismissRequest: () -> Unit
) {
    if (activePage == null) return

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (activePage) {
                            is StandbyPage.FullWidth -> "Customize: ${activePage.plugin.name}"
                            is StandbyPage.HalfWidth -> "Customize Widgets"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Customization",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // content
            when (activePage) {
                is StandbyPage.FullWidth -> {
                    PluginCustomizationColumn(
                        plugin = activePage.plugin,
                        onCustomizationValueChange = onCustomizationValueChange,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
                is StandbyPage.HalfWidth -> {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PluginCustomizationColumn(
                            plugin = activePage.leftPlugin,
                            onCustomizationValueChange = onCustomizationValueChange,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        PluginCustomizationColumn(
                            plugin = activePage.rightPlugin,
                            onCustomizationValueChange = onCustomizationValueChange,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PluginCustomizationColumn(
    plugin: PluginModel,
    onCustomizationValueChange: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = plugin.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (plugin.customizations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "This widget has no customization settings.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                plugin.customizations.forEach { (key, option) ->
                    val currentValue = option.value ?: option.default
                    val isModified = option.value != null && option.value != option.default

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // variable header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = key.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            // reset button
                            if (isModified) {
                                TextButton(
                                    onClick = {
                                        onCustomizationValueChange(plugin.localId, key, option.default)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reset to Default",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Reset",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        
                        // variable input control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                when (option.type.lowercase().trim()) {
                                    "bool", "boolean" -> {
                                        val isChecked = currentValue.toBoolean()
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isChecked) "Enabled" else "Disabled",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Switch(
                                                checked = isChecked,
                                                onCheckedChange = { newValue ->
                                                    onCustomizationValueChange(plugin.localId, key, newValue.toString())
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                                )
                                            )
                                        }
                                    }
                                    "color" -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            val parsedColor = try {
                                                Color(android.graphics.Color.parseColor(currentValue))
                                            } catch (e: Exception) {
                                                Color.Gray
                                            }
                                            
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(parsedColor, RoundedCornerShape(8.dp))
                                                    .border(
                                                        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                            )
                                            
                                            OutlinedTextField(
                                                value = currentValue,
                                                onValueChange = { newValue ->
                                                    onCustomizationValueChange(plugin.localId, key, newValue)
                                                },
                                                placeholder = { Text("#RRGGBB") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    else -> {
                                        OutlinedTextField(
                                            value = currentValue,
                                            onValueChange = { newValue ->
                                                onCustomizationValueChange(plugin.localId, key, newValue)
                                            },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

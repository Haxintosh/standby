package com.example

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

data class AppWidgetDisplayItem(
    val providerInfo: AppWidgetProviderInfo,
    val appName: String,
    val widgetLabel: String,
    val appIcon: Drawable?,
    val previewImage: Drawable?,
    val minWidthDp: Int,
    val minHeightDp: Int
)

@Composable
fun AppWidgetPickerDialog(
    onSelectProvider: (AppWidgetProviderInfo) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val widgetItems = remember(context) {
        val pm = context.packageManager
        val density = context.resources.displayMetrics.density
        val providers = AppWidgetHostHelper.getInstalledProviders(context)

        providers.map { provider ->
            val appInfo = try {
                pm.getApplicationInfo(provider.provider.packageName, 0)
            } catch (e: Exception) {
                null
            }
            val appName = try {
                appInfo?.let { pm.getApplicationLabel(it).toString() } ?: provider.provider.packageName
            } catch (e: Exception) {
                provider.provider.packageName
            }
            val widgetLabel = try {
                provider.loadLabel(pm) ?: appName
            } catch (e: Exception) {
                appName
            }
            val appIcon = try {
                appInfo?.loadIcon(pm)
            } catch (e: Exception) {
                null
            }
            val previewImage = try {
                provider.loadPreviewImage(context, 0) ?: provider.loadIcon(context, 0)
            } catch (e: Exception) {
                null
            }
            val minWidthDp = try { (provider.minWidth / density).toInt() } catch (e: Exception) { 0 }
            val minHeightDp = try { (provider.minHeight / density).toInt() } catch (e: Exception) { 0 }

            AppWidgetDisplayItem(
                providerInfo = provider,
                appName = appName,
                widgetLabel = widgetLabel,
                appIcon = appIcon,
                previewImage = previewImage,
                minWidthDp = minWidthDp,
                minHeightDp = minHeightDp
            )
        }.sortedBy { it.appName.lowercase() }
    }

    val filteredItems = remember(widgetItems, searchQuery) {
        if (searchQuery.isBlank()) {
            widgetItems
        } else {
            val query = searchQuery.trim().lowercase()
            widgetItems.filter {
                it.appName.lowercase().contains(query) || it.widgetLabel.lowercase().contains(query)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Add Android App Widget",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Widget Picker",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search installed widgets...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Widgets List
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (widgetItems.isEmpty()) "No Android App Widgets found on this device." else "No widgets match \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems, key = { "${it.providerInfo.provider.flattenToString()}_${it.widgetLabel}" }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProvider(item.providerInfo) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // App Icon
                                if (item.appIcon != null) {
                                    val bitmap = remember(item.appIcon) {
                                        try {
                                            item.appIcon.toBitmap(width = 48, height = 48)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = item.appName,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.widgetLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = item.appName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "${item.minWidthDp}x${item.minHeightDp} dp",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
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

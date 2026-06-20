package com.example

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.animation.core.animateFloatAsState
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem

@Composable
fun LayoutsDialog(
    plugins: List<PluginModel>,
    standbyPages: List<StandbyPage>,
    onAddPageSlot: (String) -> Unit,
    onRemovePageSlot: (String) -> Unit,
    onMovePageSlot: (Int, Int) -> Unit, // callback to reorder slots
    onUpdatePageSlotPlugin: (String, Boolean, String) -> Unit, // pageId, isLeft, newPluginLocalId
    onUpdatePageSlotFull: (String, String) -> Unit, // pageId, newPluginLocalId
    onUpdatePageSlotType: (String, String) -> Unit, // pageId, type
    onDeletePlugin: (String) -> Unit, // delete plugin wrapper
    onImportPluginClick: () -> Unit,
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
            // Header Row: tabs inline next to + icon, with + import widget button on the right
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
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(360.dp)
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Configure Layouts", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Widgets Library", fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FilledTonalButton(
                        onClick = onImportPluginClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import Widget", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Layouts Dialog",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedTabIndex == 0) {
                    // TAB 1: Configure Screen Layouts
                    ConfigureLayoutsTab(
                        plugins = plugins,
                        standbyPages = standbyPages,
                        onAddPageSlot = onAddPageSlot,
                        onRemovePageSlot = onRemovePageSlot,
                        onMovePageSlot = onMovePageSlot,
                        onUpdatePageSlotPlugin = onUpdatePageSlotPlugin,
                        onUpdatePageSlotFull = onUpdatePageSlotFull,
                        onUpdatePageSlotType = onUpdatePageSlotType
                    )
                } else {
                    // TAB 2: Widgets Importer & Installed list
                    WidgetsLibraryTab(
                        plugins = plugins,
                        onDeletePlugin = onDeletePlugin
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConfigureLayoutsTab(
    plugins: List<PluginModel>,
    standbyPages: List<StandbyPage>,
    onAddPageSlot: (String) -> Unit,
    onRemovePageSlot: (String) -> Unit,
    onMovePageSlot: (Int, Int) -> Unit,
    onUpdatePageSlotPlugin: (String, Boolean, String) -> Unit,
    onUpdatePageSlotFull: (String, String) -> Unit,
    onUpdatePageSlotType: (String, String) -> Unit
) {
    val lazyListState = rememberLazyListState()
    var previousSize by remember { mutableStateOf(standbyPages.size) }

    // Smooth scroll to the newly created page slot when a slot is added
    LaunchedEffect(standbyPages.size) {
        if (standbyPages.size > previousSize && standbyPages.isNotEmpty()) {
            lazyListState.animateScrollToItem(standbyPages.size - 1)
        }
        previousSize = standbyPages.size
    }

    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMovePageSlot(from.index, to.index)
    }

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with Compact Single Add Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Standby Layout Slots",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                FilledTonalButton(
                    onClick = { onAddPageSlot("full") },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Page Slot", style = MaterialTheme.typography.labelMedium)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // LazyColumn supporting smooth animations
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (standbyPages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize().padding(top = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No standby pages configured. Add one above!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = standbyPages,
                        key = { _, page -> page.pageId }
                    ) { index, page ->
                        ReorderableItem(state = reorderableLazyListState, key = page.pageId) { isDragging ->
                            val scale by animateFloatAsState(targetValue = if (isDragging) 1.03f else 1f, label = "drag_scale")
                            val zIndex by animateFloatAsState(targetValue = if (isDragging) 1f else 0f, label = "drag_z_index")

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(scale)
                                    .zIndex(zIndex),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = if (isDragging) 0.85f else 0.5f
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = borderStroke()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Inline Layout Row (Drag Icon + Title + Switch + Delete)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Reorder Drag Handle (Calvin-LL)
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "Drag to reorder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .size(24.dp)
                                                .draggableHandle()
                                        )

                                        Text(
                                            text = "Page Slot ${index + 1}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Spacer(modifier = Modifier.weight(1f))

                                        // Compact Type Switcher
                                        val isFull = page is StandbyPage.FullWidth
                                        Row(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                                .padding(2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            val fullBg = if (isFull) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                            val fullColor = if (isFull) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            Box(
                                                modifier = Modifier
                                                    .background(fullBg, RoundedCornerShape(6.dp))
                                                    .clickable { onUpdatePageSlotType(page.pageId, "full") }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Full Width", style = MaterialTheme.typography.labelSmall, color = fullColor, fontWeight = FontWeight.Bold)
                                            }

                                            val splitBg = if (!isFull) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                            val splitColor = if (!isFull) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            Box(
                                                modifier = Modifier
                                                    .background(splitBg, RoundedCornerShape(6.dp))
                                                    .clickable { onUpdatePageSlotType(page.pageId, "half") }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Split Screen", style = MaterialTheme.typography.labelSmall, color = splitColor, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // Delete Page Slot Button (Grey out if only one slot left)
                                        val isDeleteEnabled = standbyPages.size > 1
                                        IconButton(
                                            onClick = { if (isDeleteEnabled) onRemovePageSlot(page.pageId) },
                                            enabled = isDeleteEnabled,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete page slot",
                                                tint = if (isDeleteEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Configuration selectors dropdown
                                    when (page) {
                                        is StandbyPage.FullWidth -> {
                                            val fullOptions = plugins.filter { it.isBuiltIn || it.size == "full" }
                                            PluginDropdown(
                                                label = "",
                                                selectedPluginId = page.plugin.localId,
                                                plugins = fullOptions,
                                                onPluginSelected = { newId ->
                                                    onUpdatePageSlotFull(page.pageId, newId)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        is StandbyPage.HalfWidth -> {
                                            val halfOptions = plugins.filter { it.size == "half" }.ifEmpty { plugins }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                PluginDropdown(
                                                    label = "",
                                                    selectedPluginId = page.leftPlugin.localId,
                                                    plugins = halfOptions,
                                                    onPluginSelected = { newId ->
                                                        onUpdatePageSlotPlugin(page.pageId, true, newId)
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                PluginDropdown(
                                                    label = "",
                                                    selectedPluginId = page.rightPlugin.localId,
                                                    plugins = halfOptions,
                                                    onPluginSelected = { newId ->
                                                        onUpdatePageSlotPlugin(page.pageId, false, newId)
                                                    },
                                                    modifier = Modifier.weight(1f)
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
    }
}

@Composable
fun WidgetsLibraryTab(
    plugins: List<PluginModel>,
    onDeletePlugin: (String) -> Unit
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Installed Widgets Library",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Scrollable List of Installed Widgets
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                if (plugins.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No widgets installed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    plugins.forEach { plugin ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    .copy(alpha = if (plugin.isBuiltIn) 0.2f else 0.4f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = borderStroke()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                              ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Widget details",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = plugin.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Size: ${plugin.size.replaceFirstChar { it.uppercase() }} Width  •  By ${plugin.author}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Suggestion Chip Badge
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            text = if (plugin.isBuiltIn) "Built-in" else "v${plugin.version}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.height(24.dp)
                                )

                                // Delete Widget Button (Only if it's NOT a built-in widget)
                                if (!plugin.isBuiltIn) {
                                    IconButton(
                                        onClick = { onDeletePlugin(plugin.localId) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete custom widget",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
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

@Composable
fun PluginDropdown(
    label: String,
    selectedPluginId: String,
    plugins: List<PluginModel>,
    onPluginSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPlugin = plugins.firstOrNull { it.localId == selectedPluginId }
    val displayText = selectedPlugin?.name ?: "Select Widget"

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayText,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown indicator",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                plugins.forEach { plugin ->
                    DropdownMenuItem(
                        text = { Text(plugin.name) },
                        onClick = {
                            onPluginSelected(plugin.localId)
                            expanded = false
                        }
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

@Preview(device = "spec:parent=pixel_9,orientation=landscape")
@Composable
fun LayoutsDialogPreview() {
    val mockPlugins = listOf(
        PluginModel(
            localId = "plugin1",
            manifestId = "com.example.analog",
            name = "very very very very very very very very very veryvery very very veryvery very very veryvery very very veryvery very very veryvery very very veryvery very very veryvery very very veryvery very very veryvery very very veryvery long name too",
            description = "For testing word wrap very very very very very very veryvery very very very very very veryvery very very very very very veryvery very very very very very veryvery very very very very very veryvery very very very very very veryvery very very very very very veryvery very very very very very veryvery very very very very very veryvery very very very very very very",
            author = "very very very very long author name with !@@#!@# complex chars very very very very long author name with !@@#!@# complex charsvery very very very long author name with !@@#!@# complex charsvery very very very long author name with !@@#!@# complex charsvery very very very long author name with !@@#!@# complex charsvery very very very long author name with !@@#!@# complex charsvery very very very long author name with !@@#!@# complex chars",
            version = "1.0.0",
            size = "half",
            permissions = emptyList(),
            networkWhitelist = emptyList(),
            minAppVersion = 1,
            directoryPath = null,
            htmlContent = "",
            isBuiltIn = true
        ),
        PluginModel(
            localId = "plugin2",
            manifestId = "com.example.stocks",
            name = "Stocks Tracker",
            description = "Track your favorite stocks",
            author = "honk honk",
            version = "1.0.2",
            size = "half",
            permissions = emptyList(),
            networkWhitelist = emptyList(),
            minAppVersion = 1,
            directoryPath = null,
            htmlContent = "",
            isBuiltIn = false
        ),
        PluginModel(
            localId = "plugin3",
            manifestId = "com.example.weather",
            name = "Weather Forecast Widget",
            description = "Local weather reports",
            author = "Google Team",
            version = "1.1.0",
            size = "full",
            permissions = emptyList(),
            networkWhitelist = emptyList(),
            minAppVersion = 1,
            directoryPath = null,
            htmlContent = "",
            isBuiltIn = false
        )
    )

    val mockPages = listOf(
        StandbyPage.FullWidth(
            plugin = mockPlugins[2],
            pageId = "page1"
        ),
        StandbyPage.HalfWidth(
            leftPlugin = mockPlugins[0],
            rightPlugin = mockPlugins[1],
            pageId = "page2"
        )
    )

    LayoutsDialog(
        plugins = mockPlugins,
        standbyPages = mockPages,
        onAddPageSlot = {},
        onRemovePageSlot = {},
        onMovePageSlot = { _, _ -> },
        onUpdatePageSlotPlugin = { _, _, _ -> },
        onUpdatePageSlotFull = { _, _ -> },
        onUpdatePageSlotType = { _, _ -> },
        onDeletePlugin = {},
        onImportPluginClick = {},
        onDismissRequest = {}
    )
}

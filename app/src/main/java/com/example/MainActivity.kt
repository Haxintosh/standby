package com.example

import android.os.Bundle
import android.os.Build
import android.view.Display
import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // keep screen on
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    
    // hide system bars
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    insetsController.hide(WindowInsetsCompat.Type.systemBars())
    
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme(darkTheme = true) {
        StandbyScreen(window = window)
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StandbyScreen(window: android.view.Window, viewModel: StandbyViewModel = viewModel()) {
    val plugins by viewModel.plugins.collectAsState()
    val standbyPages by viewModel.standbyPages.collectAsState()
    val pagerState = rememberPagerState(pageCount = { standbyPages.size })
    val context = LocalContext.current
    
    val serverIp by viewModel.serverIp.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val serverPin by viewModel.serverPin.collectAsState()
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    
    val burnInProtectionEnabled by viewModel.burnInProtectionEnabled.collectAsState()
    val delayAfterInteraction by viewModel.delayAfterInteraction.collectAsState()
    val protectionRatio by viewModel.protectionRatio.collectAsState()
    val hideControlsOnIdle by viewModel.hideControlsOnIdle.collectAsState()
    val lowRefreshRateEnabled by viewModel.lowRefreshRateEnabled.collectAsState()
    val lowRefreshRateValue by viewModel.lowRefreshRateValue.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCustomizationDialog by remember { mutableStateOf(false) }
    var showLayoutsDialog by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var isInactive by remember { mutableStateOf(true) }
    var isControlsInactive by remember { mutableStateOf(false) }

    val display = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }
    }
    
    val supportedRefreshRates = remember(display) {
        val modes = display?.supportedModes ?: emptyArray()
        modes.map { Math.round(it.refreshRate) }
            .distinct()
            .sorted()
    }

    // set refresh rate
    LaunchedEffect(supportedRefreshRates, lowRefreshRateValue) {
        if (supportedRefreshRates.isNotEmpty() && lowRefreshRateValue !in supportedRefreshRates) {
            viewModel.setLowRefreshRateValue(supportedRefreshRates.first())
        }
    }

    LaunchedEffect(lastInteractionTime) {
        isControlsInactive = false
        delay(5000L)
        isControlsInactive = true
    }

    LaunchedEffect(lastInteractionTime, delayAfterInteraction) {
        if (delayAfterInteraction) {
            isInactive = false
            val elapsed = System.currentTimeMillis() - lastInteractionTime
            val remaining = 5000L - elapsed
            if (remaining > 0) {
                delay(remaining)
            }
            isInactive = true
        } else {
            isInactive = true
        }
    }

    // refresh rate adjustment
    LaunchedEffect(lastInteractionTime, lowRefreshRateEnabled, lowRefreshRateValue) {
        if (lowRefreshRateEnabled) {
            // restore default refresh rate
            setWindowRefreshRate(window, 0)
            
            // wait for inactivity
            delay(5000L)
            
            // check low refresh rate mode
            val targetMode = display?.supportedModes?.firstOrNull { Math.round(it.refreshRate) == lowRefreshRateValue }
            if (targetMode != null) {
                setWindowRefreshRate(window, targetMode.modeId)
            }
        } else {
            // disable low refresh rate
            setWindowRefreshRate(window, 0)
        }
    }
    
    // plugin picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.loadPluginFromFile(context, it)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val standbyPage = standbyPages.getOrNull(page)
            Box(modifier = Modifier.fillMaxSize()) {
                if (standbyPage != null) {
                    when (standbyPage) {
                        is StandbyPage.FullWidth -> {
                            PluginWebView(
                                plugin = standbyPage.plugin,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is StandbyPage.HalfWidth -> {
                            Row(modifier = Modifier.fillMaxSize()) {
                                PluginWebView(
                                    plugin = standbyPage.leftPlugin,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                                PluginWebView(
                                    plugin = standbyPage.rightPlugin,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                    }
                }
                if (burnInProtectionEnabled && isInactive) {
                    PixelPerfectBurnInMask(
                        modifier = Modifier.fillMaxSize(),
                        protectionRatio = protectionRatio
                    )
                }
            }
        }

        // settings button
        AnimatedVisibility(
            visible = !hideControlsOnIdle || !isControlsInactive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(32.dp)
        ) {
            IconButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), CircleShape)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "OLED Protection Settings",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // layouts button
        AnimatedVisibility(
            visible = !hideControlsOnIdle || !isControlsInactive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp)
        ) {
            IconButton(
                onClick = { showLayoutsDialog = true },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), CircleShape)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Load Custom Plugin",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        val activePage = standbyPages.getOrNull(pagerState.currentPage)
        val hasCustomization = when (activePage) {
            is StandbyPage.FullWidth -> activePage.plugin.customizations.isNotEmpty()
            is StandbyPage.HalfWidth -> activePage.leftPlugin.customizations.isNotEmpty() || activePage.rightPlugin.customizations.isNotEmpty()
            null -> false
        }

        // customization button
        AnimatedVisibility(
            visible = activePage != null && hasCustomization && (!hideControlsOnIdle || !isControlsInactive),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(32.dp)
        ) {
            IconButton(
                onClick = { showCustomizationDialog = true },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), CircleShape)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Customize Widget",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        
        // status info
        AnimatedVisibility(
            visible = serverPort > 0 && (!hideControlsOnIdle || !isControlsInactive),
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .background(
                        color = Color(0xCC121214),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x40D0BCFF),
                                Color(0x10D0BCFF)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // status
//                    Box(
//                        modifier = Modifier
//                            .size(8.dp)
//                            .background(Color(0xFF8FFF9F), shape = CircleShape)
//                    )
                    
                    // address
                    Text(
                        text = "Uploader: http://$serverIp:$serverPort",
                        color = Color(0xFFE6E1E5),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Box(
                        modifier = Modifier
                            .height(16.dp)
                            .width(1.dp)
                            .background(Color(0x33E6E1E5))
                    )
                    
                    // pin
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "PIN:",
                            color = Color(0xFF938F99),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = serverPin,
                            color = Color(0xFFD0BCFF),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSettingsDialog,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            SettingsDialog(
                burnInProtectionEnabled = burnInProtectionEnabled,
                onBurnInProtectionEnabledChange = { viewModel.setBurnInProtectionEnabled(it) },
                delayAfterInteraction = delayAfterInteraction,
                onDelayAfterInteractionChange = { viewModel.setDelayAfterInteraction(it) },
                protectionRatio = protectionRatio,
                onProtectionRatioChange = { viewModel.setProtectionRatio(it) },
                serverEnabled = isServerRunning,
                onServerEnabledChange = { viewModel.setServerEnabled(it) },
                serverIp = serverIp,
                serverPort = serverPort,
                serverPin = serverPin,
                hideControlsOnIdle = hideControlsOnIdle,
                onHideControlsOnIdleChange = { viewModel.setHideControlsOnIdle(it) },
                lowRefreshRateEnabled = lowRefreshRateEnabled,
                onLowRefreshRateEnabledChange = { viewModel.setLowRefreshRateEnabled(it) },
                lowRefreshRateValue = lowRefreshRateValue,
                onLowRefreshRateValueChange = { viewModel.setLowRefreshRateValue(it) },
                supportedRefreshRates = supportedRefreshRates,
                onDismissRequest = { showSettingsDialog = false }
            )
        }

        AnimatedVisibility(
            visible = showCustomizationDialog,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            CustomizationDialog(
                activePage = activePage,
                onCustomizationValueChange = { localId, name, value ->
                    viewModel.updateCustomizationValue(localId, name, value)
                },
                onDismissRequest = { showCustomizationDialog = false }
            )
        }

        AnimatedVisibility(
            visible = showLayoutsDialog,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            LayoutsDialog(
                plugins = plugins,
                standbyPages = standbyPages,
                onAddPageSlot = { viewModel.addPageSlot(it) },
                onRemovePageSlot = { viewModel.removePageSlot(it) },
                onMovePageSlot = { fromIndex, toIndex -> viewModel.movePageSlot(fromIndex, toIndex) },
                onUpdatePageSlotPlugin = { pageId, isLeft, pluginId ->
                    viewModel.updatePageSlotPlugin(pageId, isLeft, pluginId)
                },
                onUpdatePageSlotFull = { pageId, pluginId ->
                    viewModel.updatePageSlotFull(pageId, pluginId)
                },
                onUpdatePageSlotType = { pageId, type ->
                    viewModel.updatePageSlotType(pageId, type)
                },
                onDeletePlugin = { localId -> viewModel.deletePlugin(localId) },
                onImportPluginClick = { filePickerLauncher.launch("*/*") },
                onDismissRequest = { showLayoutsDialog = false }
            )
        }

        // telemetry overlay
        TelemetryOverlay(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(32.dp)
        )
    }
}

@Composable
fun PixelPerfectBurnInMask(
    modifier: Modifier = Modifier,
    protectionRatio: Int = 1,
    shiftIntervalMs: Long = 10000L
) {
    val n = (protectionRatio + 1).coerceAtLeast(2)
    var shift by remember(n) { mutableStateOf(0) }

    LaunchedEffect(n, shiftIntervalMs) {
        while (true) {
            delay(shiftIntervalMs)
            shift = (shift + 1) % n
        }
    }

    val shaderPaint = remember(n, shift) {
        android.graphics.Paint().apply {
            val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val off = android.graphics.Color.BLACK
            val on = android.graphics.Color.TRANSPARENT

            for (y in 0 until n) {
                val activeX = (y + shift) % n
                for (x in 0 until n) {
                    if (x == activeX) {
                        bmp.setPixel(x, y, on)
                    } else {
                        bmp.setPixel(x, y, off)
                    }
                }
            }

            shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(
                0f, 0f, size.width, size.height, shaderPaint
            )
        }
    }
}

private fun setWindowRefreshRate(window: android.view.Window, modeId: Int) {
    try {
        val layoutParams = window.attributes
        if (layoutParams.preferredDisplayModeId != modeId) {
            layoutParams.preferredDisplayModeId = modeId
            window.attributes = layoutParams
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}


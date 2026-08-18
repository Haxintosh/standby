package com.example

import android.os.Bundle
import android.os.Build
import android.view.Display
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.util.Log
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

    companion object {
        const val REQUEST_BIND_APPWIDGET = 2001
        const val REQUEST_CONFIGURE_APPWIDGET = 2002
    }

    private var appWidgetResultListener: ((requestCode: Int, resultCode: Int, data: Intent?) -> Unit)? = null

    fun setAppWidgetResultListener(listener: ((requestCode: Int, resultCode: Int, data: Intent?) -> Unit)?) {
        this.appWidgetResultListener = listener
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        appWidgetResultListener?.invoke(requestCode, resultCode, data)
    }

    override fun onStart() {
        super.onStart()
        AppWidgetHostHelper.startListening(this)
    }

    override fun onStop() {
        super.onStop()
        AppWidgetHostHelper.stopListening()
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // keep screen on
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    
    // hide system bars
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    insetsController.hide(WindowInsetsCompat.Type.systemBars())
    
    // disable split motion because of crashes when adb screen mirroring
    // nvm this is more of an upstream aosp issue https://github.com/GrapheneOS/os-issue-tracker/issues/3781
//    if (BuildConfig.DEBUG) {
//        (window.decorView as? android.view.ViewGroup)?.isMotionEventSplittingEnabled = false
//        // maybe: recurse through the view and disable it for all children
//    }
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
    val pendingImport by viewModel.pendingImport.collectAsState()
    val confirmImportEnabled by viewModel.confirmImportEnabled.collectAsState()
    val appWidgetsEnabled by viewModel.appWidgetsEnabled.collectAsState()
    
    val burnInProtectionEnabled by viewModel.burnInProtectionEnabled.collectAsState()
    val delayAfterInteraction by viewModel.delayAfterInteraction.collectAsState()
    val protectionRatio by viewModel.protectionRatio.collectAsState()
    val hideControlsOnIdle by viewModel.hideControlsOnIdle.collectAsState()
    val lowRefreshRateEnabled by viewModel.lowRefreshRateEnabled.collectAsState()
    val lowRefreshRateValue by viewModel.lowRefreshRateValue.collectAsState()

    val nightModeEnabled by viewModel.nightModeEnabled.collectAsState()
    val nightStartHour by viewModel.nightStartHour.collectAsState()
    val nightStartMinute by viewModel.nightStartMinute.collectAsState()
    val nightEndHour by viewModel.nightEndHour.collectAsState()
    val nightEndMinute by viewModel.nightEndMinute.collectAsState()
    val nightProtectionRatio by viewModel.nightProtectionRatio.collectAsState()
    val nightBrightnessEnabled by viewModel.nightBrightnessEnabled.collectAsState()
    val nightBrightnessValue by viewModel.nightBrightnessValue.collectAsState()
    val isNightModeActive by viewModel.isNightModeActive.collectAsState()

    val weatherLat by viewModel.weatherLat.collectAsState()
    val weatherLon by viewModel.weatherLon.collectAsState()
    val weatherCity by viewModel.weatherCity.collectAsState()
    val weatherUseGps by viewModel.weatherUseGps.collectAsState()
    val weatherLastUpdate by viewModel.weatherLastUpdate.collectAsState()
    val pluginRefreshTriggers by viewModel.pluginRefreshTriggers.collectAsState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.setWeatherUseGps(true)
            } else {
                viewModel.setWeatherUseGps(false)
            }
        }
    )

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCustomizationDialog by remember { mutableStateOf(false) }
    var showLayoutsDialog by remember { mutableStateOf(false) }
    var selectedPluginLocalIdForInfo by remember { mutableStateOf<String?>(null) }
    val selectedPluginForInfo = plugins.firstOrNull { it.localId == selectedPluginLocalIdForInfo }
    var lastPendingImport by remember { mutableStateOf<PendingPluginImport?>(null) }
    LaunchedEffect(pendingImport) {
        if (pendingImport != null) {
            lastPendingImport = pendingImport
        }
    }
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
    
    // AppWidgetHost management
    val appWidgetHost = remember(context) { AppWidgetHostHelper.getHost(context) }
    val activity = context as? Activity
    val mainActivity = activity as? MainActivity

    LaunchedEffect(appWidgetsEnabled) {
        if (appWidgetsEnabled) {
            AppWidgetHostHelper.startListening(context)
        } else {
            AppWidgetHostHelper.stopListening()
        }
    }

    LaunchedEffect(isNightModeActive, nightBrightnessEnabled, nightBrightnessValue) {
        val layoutParams = window.attributes
        if (isNightModeActive && nightBrightnessEnabled) {
            layoutParams.screenBrightness = nightBrightnessValue.coerceIn(0.01f, 1.0f)
        } else {
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = layoutParams
    }

    DisposableEffect(Unit) {
        onDispose {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = layoutParams
        }
    }

    val view = LocalView.current
    var isFirstPageLoad by remember { mutableStateOf(true) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect {
                if (isFirstPageLoad) {
                    isFirstPageLoad = false
                } else {
                    performStrongHapticFeedback(context, view)
                }
            }
    }

    var showAppWidgetPicker by remember { mutableStateOf(false) }
    var pendingAppWidgetSlot by remember { mutableStateOf<Pair<String, Boolean?>?>(null) } // pageId, isLeft
    var pendingConfigAppWidgetId by remember { mutableStateOf<Int?>(null) }
    var pendingConfigProvider by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }
    var selectedAppWidgetForInfo by remember { mutableStateOf<StandbyItem.NativeAppWidget?>(null) }

    DisposableEffect(mainActivity) {
        mainActivity?.setAppWidgetResultListener { requestCode, resultCode, _ ->
            when (requestCode) {
                MainActivity.REQUEST_BIND_APPWIDGET -> {
                    val widgetId = pendingConfigAppWidgetId
                    val provider = pendingConfigProvider
                    val slot = pendingAppWidgetSlot
                    if (resultCode == Activity.RESULT_OK && widgetId != null && provider != null) {
                        if (provider.configure != null && activity != null) {
                            val launched = AppWidgetHostHelper.startAppWidgetConfigure(
                                activity,
                                widgetId,
                                MainActivity.REQUEST_CONFIGURE_APPWIDGET
                            )
                            if (!launched) {
                                if (slot != null) {
                                    viewModel.updatePageSlotWithAppWidget(slot.first, slot.second, widgetId)
                                } else {
                                    viewModel.addPageSlotWithAppWidget(widgetId, "full")
                                }
                                pendingConfigAppWidgetId = null
                                pendingConfigProvider = null
                                pendingAppWidgetSlot = null
                            }
                        } else {
                            if (slot != null) {
                                viewModel.updatePageSlotWithAppWidget(slot.first, slot.second, widgetId)
                            } else {
                                viewModel.addPageSlotWithAppWidget(widgetId, "full")
                            }
                            pendingConfigAppWidgetId = null
                            pendingConfigProvider = null
                            pendingAppWidgetSlot = null
                        }
                    } else if (widgetId != null) {
                        AppWidgetHostHelper.deleteAppWidgetId(context, widgetId)
                        pendingConfigAppWidgetId = null
                        pendingConfigProvider = null
                        pendingAppWidgetSlot = null
                    }
                }
                MainActivity.REQUEST_CONFIGURE_APPWIDGET -> {
                    val widgetId = pendingConfigAppWidgetId
                    val slot = pendingAppWidgetSlot
                    if (resultCode == Activity.RESULT_OK && widgetId != null) {
                        if (slot != null) {
                            viewModel.updatePageSlotWithAppWidget(slot.first, slot.second, widgetId)
                        } else {
                            viewModel.addPageSlotWithAppWidget(widgetId, "full")
                        }
                    } else if (widgetId != null) {
                        AppWidgetHostHelper.deleteAppWidgetId(context, widgetId)
                    }
                    pendingConfigAppWidgetId = null
                    pendingConfigProvider = null
                    pendingAppWidgetSlot = null
                }
            }
        }
        onDispose {
            mainActivity?.setAppWidgetResultListener(null)
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
                            when (val item = standbyPage.item) {
                                is StandbyItem.Plugin -> {
                                    PluginWebView(
                                        plugin = item.plugin,
                                        modifier = Modifier.fillMaxSize(),
                                        refreshTrigger = pluginRefreshTriggers[item.plugin.localId] ?: 0L,
                                        onLongClick = {
                                            selectedPluginLocalIdForInfo = item.plugin.localId
                                        }
                                    )
                                }
                                is StandbyItem.NativeAppWidget -> {
                                    AppWidgetView(
                                        appWidgetHost = appWidgetHost,
                                        appWidgetId = item.appWidgetId,
                                        providerInfo = item.providerInfo,
                                        modifier = Modifier.fillMaxSize(),
                                        onLongClick = {
                                            selectedAppWidgetForInfo = item
                                        }
                                    )
                                }
                            }
                        }
                        is StandbyPage.HalfWidth -> {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    when (val item = standbyPage.leftItem) {
                                        is StandbyItem.Plugin -> {
                                            PluginWebView(
                                                plugin = item.plugin,
                                                modifier = Modifier.fillMaxSize(),
                                                refreshTrigger = pluginRefreshTriggers[item.plugin.localId] ?: 0L,
                                                onLongClick = {
                                                    selectedPluginLocalIdForInfo = item.plugin.localId
                                                }
                                            )
                                        }
                                        is StandbyItem.NativeAppWidget -> {
                                            AppWidgetView(
                                                appWidgetHost = appWidgetHost,
                                                appWidgetId = item.appWidgetId,
                                                providerInfo = item.providerInfo,
                                                modifier = Modifier.fillMaxSize(),
                                                onLongClick = {
                                                    selectedAppWidgetForInfo = item
                                                }
                                            )
                                        }
                                    }
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    when (val item = standbyPage.rightItem) {
                                        is StandbyItem.Plugin -> {
                                            PluginWebView(
                                                plugin = item.plugin,
                                                modifier = Modifier.fillMaxSize(),
                                                refreshTrigger = pluginRefreshTriggers[item.plugin.localId] ?: 0L,
                                                onLongClick = {
                                                    selectedPluginLocalIdForInfo = item.plugin.localId
                                                }
                                            )
                                        }
                                        is StandbyItem.NativeAppWidget -> {
                                            AppWidgetView(
                                                appWidgetHost = appWidgetHost,
                                                appWidgetId = item.appWidgetId,
                                                providerInfo = item.providerInfo,
                                                modifier = Modifier.fillMaxSize(),
                                                onLongClick = {
                                                    selectedAppWidgetForInfo = item
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                val effectiveProtectionRatio = if (isNightModeActive) nightProtectionRatio else protectionRatio
                val effectiveBurnInProtection = burnInProtectionEnabled || isNightModeActive
                if (effectiveBurnInProtection && isInactive) {
                    PixelPerfectBurnInMask(
                        modifier = Modifier.fillMaxSize(),
                        protectionRatio = effectiveProtectionRatio
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
            is StandbyPage.FullWidth -> activePage.plugin?.customizations?.isNotEmpty() == true
            is StandbyPage.HalfWidth -> (activePage.leftPlugin?.customizations?.isNotEmpty() == true) || (activePage.rightPlugin?.customizations?.isNotEmpty() == true)
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

        // page indicator
        AnimatedVisibility(
            visible = (!hideControlsOnIdle || !isControlsInactive) && standbyPages.size > 1,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                repeat(standbyPages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 20.dp else 8.dp,
                        label = "page_indicator_width"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        label = "page_indicator_color"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .background(
                                color = color,
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
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
                confirmImportEnabled = confirmImportEnabled,
                onConfirmImportEnabledChange = { viewModel.setConfirmImportEnabled(it) },
                appWidgetsEnabled = appWidgetsEnabled,
                onAppWidgetsEnabledChange = { viewModel.setAppWidgetsEnabled(it) },
                nightModeEnabled = nightModeEnabled,
                onNightModeEnabledChange = { viewModel.setNightModeEnabled(it) },
                nightStartHour = nightStartHour,
                nightStartMinute = nightStartMinute,
                onNightStartTimeChange = { h, m -> viewModel.setNightStartTime(h, m) },
                nightEndHour = nightEndHour,
                nightEndMinute = nightEndMinute,
                onNightEndTimeChange = { h, m -> viewModel.setNightEndTime(h, m) },
                nightProtectionRatio = nightProtectionRatio,
                onNightProtectionRatioChange = { viewModel.setNightProtectionRatio(it) },
                nightBrightnessEnabled = nightBrightnessEnabled,
                onNightBrightnessEnabledChange = { viewModel.setNightBrightnessEnabled(it) },
                nightBrightnessValue = nightBrightnessValue,
                onNightBrightnessValueChange = { viewModel.setNightBrightnessValue(it) },
                isNightModeActive = isNightModeActive,
                weatherLat = weatherLat,
                weatherLon = weatherLon,
                weatherCity = weatherCity,
                weatherUseGps = weatherUseGps,
                weatherLastUpdate = weatherLastUpdate,
                onWeatherLocationChange = { lat, lon, city -> viewModel.setWeatherLocation(lat, lon, city) },
                onWeatherUseGpsChange = { enabled ->
                    if (enabled) {
                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        
                        if (hasPermission) {
                            viewModel.setWeatherUseGps(true)
                        } else {
                            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    } else {
                        viewModel.setWeatherUseGps(false)
                    }
                },
                onWeatherRefresh = { viewModel.triggerWeatherRefresh() },
                onSearchLocations = { viewModel.searchLocations(it) },
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
                appWidgetsEnabled = appWidgetsEnabled,
                onDeletePlugin = { localId -> viewModel.deletePlugin(localId) },
                onImportPluginClick = { filePickerLauncher.launch("*/*") },
                onRefreshWidgetsClick = { viewModel.refreshAllWidgets(context) },
                onPickAppWidget = { pageId, isLeft ->
                    pendingAppWidgetSlot = Pair(pageId, isLeft)
                    showAppWidgetPicker = true
                },
                onDismissRequest = { showLayoutsDialog = false }
            )
        }

        AnimatedVisibility(
            visible = showAppWidgetPicker,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            AppWidgetPickerDialog(
                onSelectProvider = { provider ->
                    showAppWidgetPicker = false
                    val appWidgetId = AppWidgetHostHelper.allocateAppWidgetId(context)
                    val bound = AppWidgetHostHelper.bindAppWidgetIdIfAllowed(context, appWidgetId, provider.provider)
                    if (!bound) {
                        pendingConfigAppWidgetId = appWidgetId
                        pendingConfigProvider = provider
                        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                        }
                        if (activity != null) {
                            try {
                                @Suppress("DEPRECATION")
                                activity.startActivityForResult(bindIntent, MainActivity.REQUEST_BIND_APPWIDGET)
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Failed to launch bind intent", e)
                                AppWidgetHostHelper.deleteAppWidgetId(context, appWidgetId)
                                pendingConfigAppWidgetId = null
                                pendingConfigProvider = null
                            }
                        }
                    } else if (provider.configure != null && activity != null) {
                        pendingConfigAppWidgetId = appWidgetId
                        pendingConfigProvider = provider
                        val launched = AppWidgetHostHelper.startAppWidgetConfigure(
                            activity,
                            appWidgetId,
                            MainActivity.REQUEST_CONFIGURE_APPWIDGET
                        )
                        if (!launched) {
                            val slot = pendingAppWidgetSlot
                            if (slot != null) {
                                viewModel.updatePageSlotWithAppWidget(slot.first, slot.second, appWidgetId)
                            } else {
                                viewModel.addPageSlotWithAppWidget(appWidgetId, "full")
                            }
                            pendingConfigAppWidgetId = null
                            pendingConfigProvider = null
                            pendingAppWidgetSlot = null
                        }
                    } else {
                        val slot = pendingAppWidgetSlot
                        if (slot != null) {
                            viewModel.updatePageSlotWithAppWidget(slot.first, slot.second, appWidgetId)
                        } else {
                            viewModel.addPageSlotWithAppWidget(appWidgetId, "full")
                        }
                        pendingAppWidgetSlot = null
                    }
                },
                onDismissRequest = {
                    showAppWidgetPicker = false
                    pendingAppWidgetSlot = null
                }
            )
        }

        AnimatedVisibility(
            visible = pendingImport != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            lastPendingImport?.let { pending ->
                ImportConfirmationDialog(
                    pendingImport = pending,
                    onConfirm = { customName -> viewModel.confirmImport(customName) },
                    onCancel = { viewModel.cancelImport() }
                )
            }
        }

        AnimatedVisibility(
            visible = selectedPluginForInfo != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            selectedPluginForInfo?.let { plugin ->
                PluginInfoDialog(
                    plugin = plugin,
                    onRenamePlugin = { localId, newName ->
                        viewModel.renamePlugin(localId, newName)
                    },
                    onRefreshPlugin = {
                        viewModel.refreshPlugin(plugin.localId)
                    },
                    onDismissRequest = { selectedPluginLocalIdForInfo = null }
                )
            }
        }

        AnimatedVisibility(
            visible = selectedAppWidgetForInfo != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            selectedAppWidgetForInfo?.let { widgetItem ->
                AppWidgetInfoDialog(
                    item = widgetItem,
                    onRefreshWidget = {
                        viewModel.refreshNativeAppWidget(
                            context = context,
                            appWidgetId = widgetItem.appWidgetId,
                            provider = widgetItem.providerInfo.provider
                        )
                    },
                    onConfigureWidget = if (widgetItem.providerInfo.configure != null && activity != null) {
                        {
                            AppWidgetHostHelper.startAppWidgetConfigure(
                                activity,
                                widgetItem.appWidgetId,
                                MainActivity.REQUEST_CONFIGURE_APPWIDGET
                            )
                        }
                    } else null,
                    onDismissRequest = { selectedAppWidgetForInfo = null }
                )
            }
        }
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

private fun performStrongHapticFeedback(context: Context, view: android.view.View?) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40L)
            }
            return
        }
    } catch (_: Exception) {}

    view?.performHapticFeedback(
        HapticFeedbackConstants.LONG_PRESS,
        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    )
}


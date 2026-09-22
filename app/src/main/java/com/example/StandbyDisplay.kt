package com.example

import android.content.Context
import android.view.HapticFeedbackConstants
import android.os.VibratorManager
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

/**
 * The standby pages themselves: the pager, the plugins, burn-in masking, night-mode
 * brightness, refresh rate, and the page indicator.
 *
 * Everything in here works from any host that owns a Window, so both MainActivity and
 * StandbyDreamService render it. Nothing here touches Activity, ActivityResult, or any
 * configuration UI. The host supplies its own chrome through [overlay].
 *
 * @param window the host's window. Brightness and refresh rate are plain
 *   WindowManager.LayoutParams writes, which work on a dream window as well as an
 *   Activity's. A DreamService's getWindow() is null until onAttachedToWindow, so do not
 *   compose this before then.
 * @param onPluginLongClick null in a host with nowhere to show plugin info. PluginWebView
 *   already no-ops a null long-click handler.
 * @param overlay host chrome drawn over the pages. Receives whether controls should
 *   currently be visible, which follows the same idle timer the pages use, and the
 *   page currently showing, so chrome can decorate it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StandbyDisplay(
    window: android.view.Window,
    viewModel: StandbyViewModel = viewModel(),
    onPluginLongClick: ((String) -> Unit)? = null,
    onWidgetLongClick: ((StandbyItem.NativeAppWidget) -> Unit)? = null,
    overlay: @Composable BoxScope.(controlsVisible: Boolean, currentPage: StandbyPage?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val view = LocalView.current

    val standbyPages by viewModel.standbyPages.collectAsState()
    val pagerState = rememberPagerState(pageCount = { standbyPages.size })
    val pluginRefreshTriggers by viewModel.pluginRefreshTriggers.collectAsState()

    val burnInProtectionEnabled by viewModel.burnInProtectionEnabled.collectAsState()
    val delayAfterInteraction by viewModel.delayAfterInteraction.collectAsState()
    val protectionRatio by viewModel.protectionRatio.collectAsState()
    val hideControlsOnIdle by viewModel.hideControlsOnIdle.collectAsState()
    val lowRefreshRateEnabled by viewModel.lowRefreshRateEnabled.collectAsState()
    val lowRefreshRateValue by viewModel.lowRefreshRateValue.collectAsState()

    val isNightModeActive by viewModel.isNightModeActive.collectAsState()
    val nightProtectionRatio by viewModel.nightProtectionRatio.collectAsState()
    val nightBrightnessEnabled by viewModel.nightBrightnessEnabled.collectAsState()
    val nightBrightnessValue by viewModel.nightBrightnessValue.collectAsState()

    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var isInactive by remember { mutableStateOf(true) }
    var isControlsInactive by remember { mutableStateOf(false) }

    val appWidgetHost = remember(context) { AppWidgetHostHelper.getHost(context) }

    // Taken from the View rather than the Context. Context.getDisplay() throws
    // UnsupportedOperationException on a context that is not display-associated, which a
    // bare Service's is not, so the old version would have crashed inside a dream.
    val display = rememberDisplay()

    val supportedRefreshRates = rememberSupportedRefreshRates()

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
            if (remaining > 0) delay(remaining)
            isInactive = true
        } else {
            isInactive = true
        }
    }

    LaunchedEffect(lastInteractionTime, lowRefreshRateEnabled, lowRefreshRateValue) {
        if (lowRefreshRateEnabled) {
            setWindowRefreshRate(window, 0)
            delay(5000L)
            val targetMode = display?.supportedModes
                ?.firstOrNull { Math.round(it.refreshRate) == lowRefreshRateValue }
            if (targetMode != null) setWindowRefreshRate(window, targetMode.modeId)
        } else {
            setWindowRefreshRate(window, 0)
        }
    }

    LaunchedEffect(isNightModeActive, nightBrightnessEnabled, nightBrightnessValue) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = if (isNightModeActive && nightBrightnessEnabled) {
            nightBrightnessValue.coerceIn(0.01f, 1.0f)
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
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

    var isFirstPageLoad by remember { mutableStateOf(true) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            if (isFirstPageLoad) isFirstPageLoad = false
            else performStrongHapticFeedback(context, view)
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
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val standbyPage = standbyPages.getOrNull(page)
            Box(modifier = Modifier.fillMaxSize()) {
                when (standbyPage) {
                    is StandbyPage.FullWidth -> StandbyItemView(
                        item = standbyPage.item,
                        appWidgetHost = appWidgetHost,
                        refreshTriggers = pluginRefreshTriggers,
                        onPluginLongClick = onPluginLongClick,
                        onWidgetLongClick = onWidgetLongClick,
                    )

                    is StandbyPage.HalfWidth -> {
                        val half: @Composable (StandbyItem) -> Unit = { item ->
                            StandbyItemView(
                                item = item,
                                appWidgetHost = appWidgetHost,
                                refreshTriggers = pluginRefreshTriggers,
                                onPluginLongClick = onPluginLongClick,
                                onWidgetLongClick = onWidgetLongClick,
                            )
                        }

                        // Side by side only makes sense while the page is wider than it
                        // is tall. A dream follows the device orientation and cannot
                        // refuse, so a phone docked upright would otherwise get two tall
                        // skinny columns. Measuring the page instead of reading the
                        // configuration also means rotating mid-dream re-lays out on its
                        // own, and that a host which ever renders this smaller than the
                        // screen still gets the right answer.
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            if (maxWidth >= maxHeight) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        half(standbyPage.leftItem)
                                    }
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        half(standbyPage.rightItem)
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        half(standbyPage.leftItem)
                                    }
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        half(standbyPage.rightItem)
                                    }
                                }
                            }
                        }
                    }

                    null -> Unit
                }

                val effectiveProtectionRatio =
                    if (isNightModeActive) nightProtectionRatio else protectionRatio
                val effectiveBurnInProtection = burnInProtectionEnabled || isNightModeActive
                if (effectiveBurnInProtection && isInactive) {
                    PixelPerfectBurnInMask(
                        modifier = Modifier.fillMaxSize(),
                        protectionRatio = effectiveProtectionRatio,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = (!hideControlsOnIdle || !isControlsInactive) && standbyPages.size > 1,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                repeat(standbyPages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 20.dp else 8.dp,
                        label = "page_indicator_width",
                    )
                    val color by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        label = "page_indicator_color",
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .background(color = color, shape = RoundedCornerShape(4.dp)),
                    )
                }
            }
        }

        overlay(
            !hideControlsOnIdle || !isControlsInactive,
            standbyPages.getOrNull(pagerState.currentPage),
        )
    }
}

/** One slot's content. The same three lines appeared three times before this. */
@Composable
private fun StandbyItemView(
    item: StandbyItem,
    appWidgetHost: android.appwidget.AppWidgetHost,
    refreshTriggers: Map<String, Long>,
    onPluginLongClick: ((String) -> Unit)?,
    onWidgetLongClick: ((StandbyItem.NativeAppWidget) -> Unit)?,
) {
    when (item) {
        is StandbyItem.Plugin -> PluginWebView(
            plugin = item.plugin,
            modifier = Modifier.fillMaxSize(),
            refreshTrigger = refreshTriggers[item.plugin.localId] ?: 0L,
            onLongClick = onPluginLongClick?.let { cb -> { cb(item.plugin.localId) } },
        )

        is StandbyItem.NativeAppWidget -> AppWidgetView(
            appWidgetHost = appWidgetHost,
            appWidgetId = item.appWidgetId,
            providerInfo = item.providerInfo,
            modifier = Modifier.fillMaxSize(),
            onLongClick = onWidgetLongClick?.let { cb -> { cb(item) } },
        )
    }
}

internal fun setWindowRefreshRate(window: android.view.Window, modeId: Int) {
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

internal fun performStrongHapticFeedback(context: Context, view: android.view.View?) {
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

/**
 * Refresh rates this display supports.
 *
 * Reads the display from the View, not the Context. Context.getDisplay() throws
 * UnsupportedOperationException on a context that is not display-associated, and a bare
 * Service's context is not, so the Context version would crash inside a dream.
 */
@Composable
internal fun rememberDisplay(): android.view.Display? {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view) {
        view.display
            ?: (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    }
}

@Composable
internal fun rememberSupportedRefreshRates(): List<Int> {
    val display = rememberDisplay()
    return remember(display) {
        (display?.supportedModes ?: emptyArray())
            .map { Math.round(it.refreshRate) }
            .distinct()
            .sorted()
    }
}

package com.example

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AppWidgetView(
    appWidgetHost: AppWidgetHost,
    appWidgetId: Int,
    providerInfo: AppWidgetProviderInfo,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val currentOnLongClick = rememberUpdatedState(onLongClick)

    key(appWidgetId) {
        BoxWithConstraints(
            modifier = modifier.fillMaxSize()
        ) {
            val widthDp = maxWidth.value.toInt().coerceAtLeast(40)
            val heightDp = maxHeight.value.toInt().coerceAtLeast(40)

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val options = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                    }
                    try {
                        AppWidgetManager.getInstance(context).updateAppWidgetOptions(appWidgetId, options)
                    } catch (e: Exception) {
                        Log.w("AppWidgetView", "Error updating widget options in factory for id $appWidgetId", e)
                    }

                    appWidgetHost.createView(context, appWidgetId, providerInfo).apply {
                        setAppWidget(appWidgetId, providerInfo)
                        try {
                            updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
                        } catch (e: Exception) {
                            // ignore if not supported on platform
                        }
                        setOnLongClickListener {
                            if (currentOnLongClick.value != null) {
                                currentOnLongClick.value?.invoke()
                                true
                            } else {
                                false
                            }
                        }
                    }
                },
                update = { hostView ->
                    if (hostView.appWidgetId != appWidgetId) {
                        hostView.setAppWidget(appWidgetId, providerInfo)
                    }
                    val options = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                    }
                    try {
                        hostView.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
                    } catch (e: Exception) {
                        // ignore
                    }
                    hostView.setOnLongClickListener {
                        if (currentOnLongClick.value != null) {
                            currentOnLongClick.value?.invoke()
                            true
                        } else {
                            false
                        }
                    }
                }
            )
        }
    }
}

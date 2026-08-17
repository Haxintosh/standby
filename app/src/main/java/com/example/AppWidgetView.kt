package com.example

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            appWidgetHost.createView(context, appWidgetId, providerInfo).apply {
                setAppWidget(appWidgetId, providerInfo)
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

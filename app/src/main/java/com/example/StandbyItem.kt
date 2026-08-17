package com.example

import android.appwidget.AppWidgetProviderInfo

sealed class StandbyItem {
    abstract val localId: String
    abstract val displayName: String

    data class Plugin(val plugin: PluginModel) : StandbyItem() {
        override val localId: String get() = plugin.localId
        override val displayName: String get() = plugin.name
    }

    data class NativeAppWidget(
        val appWidgetId: Int,
        val providerInfo: AppWidgetProviderInfo,
        val label: String,
        val packageName: String
    ) : StandbyItem() {
        override val localId: String get() = "appwidget:$appWidgetId"
        override val displayName: String get() = label
    }
}

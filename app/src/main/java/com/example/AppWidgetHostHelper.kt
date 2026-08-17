package com.example

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.util.Log

object AppWidgetHostHelper {
    private const val TAG = "AppWidgetHostHelper"
    const val APPWIDGET_HOST_ID = 2048

    private var host: AppWidgetHost? = null

    @Synchronized
    fun getHost(context: Context): AppWidgetHost {
        if (host == null) {
            host = AppWidgetHost(context.applicationContext, APPWIDGET_HOST_ID)
        }
        return host!!
    }

    fun startListening(context: Context) {
        try {
            getHost(context).startListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AppWidgetHost listening", e)
        }
    }

    fun stopListening() {
        try {
            host?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AppWidgetHost listening", e)
        }
    }

    fun allocateAppWidgetId(context: Context): Int {
        return getHost(context).allocateAppWidgetId()
    }

    fun deleteAppWidgetId(context: Context, appWidgetId: Int) {
        try {
            getHost(context).deleteAppWidgetId(appWidgetId)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting appWidgetId $appWidgetId", e)
        }
    }

    fun getInstalledProviders(context: Context): List<AppWidgetProviderInfo> {
        val manager = AppWidgetManager.getInstance(context)
        return manager.installedProviders ?: emptyList()
    }

    fun getAppWidgetInfo(context: Context, appWidgetId: Int): AppWidgetProviderInfo? {
        val manager = AppWidgetManager.getInstance(context)
        return try {
            manager.getAppWidgetInfo(appWidgetId)
        } catch (e: Exception) {
            null
        }
    }

    fun bindAppWidgetIdIfAllowed(context: Context, appWidgetId: Int, provider: ComponentName): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return try {
            manager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
        } catch (e: Exception) {
            Log.e(TAG, "Error binding appWidgetId $appWidgetId", e)
            false
        }
    }
}

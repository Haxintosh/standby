package com.example

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppWidgetHostIntegrationTest {

    private fun createTestPlugin(
        localId: String,
        name: String,
        size: String = "full"
    ): PluginModel {
        return PluginModel(
            localId = localId,
            manifestId = localId,
            name = name,
            description = "Desc",
            author = "Author",
            version = "1.0",
            size = size,
            permissions = emptyList(),
            networkWhitelist = emptyList(),
            minAppVersion = 1,
            directoryPath = null,
            htmlContent = "<html></html>"
        )
    }

    @Test
    fun testAppWidgetHostHelperAllocationAndLifecycle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val host = AppWidgetHostHelper.getHost(context)
        assertNotNull(host)

        AppWidgetHostHelper.startListening(context)

        val id1 = AppWidgetHostHelper.allocateAppWidgetId(context)
        assertTrue(id1 >= 0)

        AppWidgetHostHelper.deleteAppWidgetId(context, id1)
        AppWidgetHostHelper.stopListening()
    }

    @Test
    fun testStandbyItemHierarchy() {
        val plugin = createTestPlugin("com.example.test", "Test Plugin", "full")
        val pluginItem = StandbyItem.Plugin(plugin)
        assertEquals("com.example.test", pluginItem.localId)
        assertEquals("Test Plugin", pluginItem.displayName)

        val providerInfo = AppWidgetProviderInfo().apply {
            provider = ComponentName("com.example.otherapp", "com.example.otherapp.WidgetProvider")
        }
        val widgetItem = StandbyItem.NativeAppWidget(
            appWidgetId = 42,
            providerInfo = providerInfo,
            label = "External Music Widget",
            packageName = "com.example.otherapp"
        )
        assertEquals("appwidget:42", widgetItem.localId)
        assertEquals("External Music Widget", widgetItem.displayName)
        assertEquals(42, widgetItem.appWidgetId)
    }

    @Test
    fun testStandbyPageWithAppWidgets() {
        val plugin = createTestPlugin("com.example.test", "Test Plugin", "half")
        val pluginItem = StandbyItem.Plugin(plugin)

        val providerInfo = AppWidgetProviderInfo().apply {
            provider = ComponentName("com.example.otherapp", "com.example.otherapp.WidgetProvider")
        }
        val widgetItem = StandbyItem.NativeAppWidget(
            appWidgetId = 99,
            providerInfo = providerInfo,
            label = "Calendar Widget",
            packageName = "com.example.otherapp"
        )

        val splitPage = StandbyPage.HalfWidth(
            leftItem = pluginItem,
            rightItem = widgetItem,
            pageId = "test_split_page"
        )

        assertEquals("test_split_page", splitPage.pageId)
        assertEquals(plugin, splitPage.leftPlugin)
        assertNull(splitPage.rightPlugin) // right slot is an AppWidget, not a PluginModel
        assertTrue(splitPage.rightItem is StandbyItem.NativeAppWidget)
        assertEquals(99, (splitPage.rightItem as StandbyItem.NativeAppWidget).appWidgetId)
    }
}

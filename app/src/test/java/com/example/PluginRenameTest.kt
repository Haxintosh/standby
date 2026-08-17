package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PluginRenameTest {

    @Test
    fun testRenameBuiltInPlugin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("standby_settings", Context.MODE_PRIVATE)

        val initialClock = DefaultPlugins.getBuiltInClockPlugin(prefs)
        assertEquals("Default Clock", initialClock.name)

        DefaultPlugins.renameBuiltInPlugin(prefs, "com.example.builtin.clock", "My Custom Clock")

        val renamedClock = DefaultPlugins.getBuiltInClockPlugin(prefs)
        assertEquals("My Custom Clock", renamedClock.name)
    }

    @Test
    fun testRenameCustomPlugin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pluginsDir = PluginManager.getPluginsDir(context)
        val folder = File(pluginsDir, "plugin_test_rename")
        folder.mkdirs()

        val manifestFile = File(folder, "plugin_manifest.json")
        val initialManifest = JSONObject().apply {
            put("id", "com.example.test")
            put("name", "Old Plugin Name")
            put("description", "Test Description")
            put("author", "Tester")
            put("version", "1.0.0")
            put("permissions", org.json.JSONArray())
        }
        manifestFile.writeText(initialManifest.toString(2))

        val htmlFile = File(folder, "plugin.html")
        htmlFile.writeText("<html><body>Test</body></html>")

        val registryEntry = PluginManager.RegistryEntry(
            localId = "local_test_id",
            manifestId = "com.example.test",
            name = "Old Plugin Name",
            folderName = "plugin_test_rename",
            installTimestamp = System.currentTimeMillis()
        )
        PluginManager.saveRegistry(context, listOf(registryEntry))

        // Perform rename
        val success = PluginManager.renamePlugin(context, "local_test_id", "New Plugin Name")
        assertTrue(success)

        // Verify registry updated
        val updatedRegistry = PluginManager.loadRegistry(context)
        assertEquals(1, updatedRegistry.size)
        assertEquals("New Plugin Name", updatedRegistry[0].name)

        // Verify manifest file updated
        val updatedManifest = JSONObject(manifestFile.readText())
        assertEquals("New Plugin Name", updatedManifest.getString("name"))

        // Verify loadPluginDirectory reflects new name
        val loaded = PluginManager.loadPluginDirectory(context, "plugin_test_rename", "local_test_id")
        assertNotNull(loaded)
        assertEquals("New Plugin Name", loaded?.name)
    }
}

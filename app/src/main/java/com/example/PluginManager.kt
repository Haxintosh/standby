package com.example

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

object PluginManager {
    private const val TAG = "PluginManager"
    private const val REGISTRY_FILE = "plugins_registry.json"

    data class RegistryEntry(
        val localId: String,
        val manifestId: String,
        val name: String,
        val folderName: String,
        val installTimestamp: Long
    )

    fun getPluginsDir(context: Context): File {
        val dir = File(context.filesDir, "plugins")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getRegistryFile(context: Context): File {
        return File(getPluginsDir(context), REGISTRY_FILE)
    }

    fun loadRegistry(context: Context): List<RegistryEntry> {
        val file = getRegistryFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            val json = JSONObject(content)
            val array = json.optJSONArray("plugins") ?: JSONArray()
            val list = mutableListOf<RegistryEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RegistryEntry(
                        localId = obj.getString("local_id"),
                        manifestId = obj.getString("manifest_id"),
                        name = obj.getString("name"),
                        folderName = obj.getString("folder_name"),
                        installTimestamp = obj.getLong("install_timestamp")
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading registry", e)
            emptyList()
        }
    }

    fun saveRegistry(context: Context, registry: List<RegistryEntry>) {
        val file = getRegistryFile(context)
        try {
            val json = JSONObject()
            val array = JSONArray()
            for (entry in registry) {
                val obj = JSONObject().apply {
                    put("local_id", entry.localId)
                    put("manifest_id", entry.manifestId)
                    put("name", entry.name)
                    put("folder_name", entry.folderName)
                    put("install_timestamp", entry.installTimestamp)
                }
                array.put(obj)
            }
            json.put("plugins", array)
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving registry", e)
        }
    }

    fun unzip(inputStream: InputStream, targetDir: File) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                // prevent directory traversal
                if (!file.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Arbitrary file write hazard in zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { output ->
                        zip.copyTo(output)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    fun loadPluginDirectory(context: Context, folderName: String, localId: String): PluginModel? {
        val pluginsDir = getPluginsDir(context)
        val pluginDir = File(pluginsDir, folderName)
        if (!pluginDir.exists()) return null

        return try {
            val manifestFile = File(pluginDir, "plugin_manifest.json")
            if (!manifestFile.exists()) return null
            val manifestContent = manifestFile.readText()
            val manifestJson = JSONObject(manifestContent)

            val manifestId = manifestJson.getString("id")
            val name = manifestJson.optString("name", "Unnamed Plugin")
            val description = manifestJson.optString("description", "")
            val author = manifestJson.optString("author", "Unknown")
            val version = manifestJson.optString("version", "1.0.0")
            val size = manifestJson.optString("size", "full")

            val permissions = mutableListOf<String>()
            val permissionsArray = manifestJson.optJSONArray("permissions")
            if (permissionsArray != null) {
                for (i in 0 until permissionsArray.length()) {
                    permissions.add(permissionsArray.getString(i))
                }
            }

            val providers = mutableListOf<String>()
            val providersArray = manifestJson.optJSONArray("providers")
            if (providersArray != null) {
                for (i in 0 until providersArray.length()) {
                    providers.add(providersArray.getString(i))
                }
            }

            val networkWhitelist = mutableListOf<String>()
            val whitelistArray = manifestJson.optJSONArray("network_whitelist")
            if (whitelistArray != null) {
                for (i in 0 until whitelistArray.length()) {
                    networkWhitelist.add(whitelistArray.getString(i))
                }
            }

            val minAppVersion = manifestJson.optInt("min_app_version", 1)

            val htmlFile = File(pluginDir, "plugin.html")
            if (!htmlFile.exists()) return null
            val htmlContent = htmlFile.readText()

            val customizations = mutableMapOf<String, CustomizationOption>()
            val customizationFile = File(pluginDir, "customization.json")
            if (customizationFile.exists()) {
                val custContent = customizationFile.readText()
                val custJson = JSONObject(custContent)
                val keys = custJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val varJson = custJson.getJSONObject(key)
                    val type = varJson.optString("type", "string")
                    val default = varJson.optString("default", "")
                    val target = if (varJson.has("target") && !varJson.isNull("target")) varJson.getString("target") else null
                    val value = if (varJson.has("value") && !varJson.isNull("value")) varJson.getString("value") else null
                    customizations[key] = CustomizationOption(type, default, target, value)
                }
            }

            PluginModel(
                localId = localId,
                manifestId = manifestId,
                name = name,
                description = description,
                author = author,
                version = version,
                size = size,
                permissions = permissions,
                providers = providers,
                networkWhitelist = networkWhitelist,
                minAppVersion = minAppVersion,
                directoryPath = pluginDir.absolutePath,
                htmlContent = htmlContent,
                customizations = customizations,
                isBuiltIn = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading plugin from $folderName", e)
            null
        }
    }

    fun saveCustomizationValue(context: Context, localId: String, folderName: String, varName: String, varValue: String) {
        val pluginsDir = getPluginsDir(context)
        val pluginDir = File(pluginsDir, folderName)
        if (!pluginDir.exists()) return

        val customizationFile = File(pluginDir, "customization.json")
        try {
            val json = if (customizationFile.exists()) {
                JSONObject(customizationFile.readText())
            } else {
                JSONObject()
            }

            val varObj = json.optJSONObject(varName) ?: JSONObject().apply {
                put("type", "string")
                put("default", "")
            }
            varObj.put("value", varValue)
            json.put(varName, varObj)

            customizationFile.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving customization value for $varName in $localId", e)
        }
    }

    fun prepareZipPluginImport(context: Context, zipStream: InputStream, originalFileName: String): PendingPluginImport {
        val timestamp = System.currentTimeMillis()
        val tempDir = File(context.cacheDir, "temp_plugin_pending_$timestamp")
        tempDir.mkdirs()

        try {
            unzip(zipStream, tempDir)
            val manifestFile = File(tempDir, "plugin_manifest.json")
            if (!manifestFile.exists()) {
                throw IllegalArgumentException("Missing plugin_manifest.json in ZIP file")
            }
            val manifestJson = JSONObject(manifestFile.readText())
            val manifestId = manifestJson.getString("id")
            if (manifestId.isBlank()) {
                throw IllegalArgumentException("Blank plugin id in manifest")
            }
            val name = manifestJson.optString("name", "Imported Plugin")
            val description = manifestJson.optString("description", "")
            val author = manifestJson.optString("author", "Unknown")
            val version = manifestJson.optString("version", "1.0.0")
            val size = manifestJson.optString("size", "full")

            val permissions = mutableListOf<String>()
            val permissionsArray = manifestJson.optJSONArray("permissions")
            if (permissionsArray != null) {
                for (i in 0 until permissionsArray.length()) {
                    permissions.add(permissionsArray.getString(i))
                }
            }

            val providers = mutableListOf<String>()
            val providersArray = manifestJson.optJSONArray("providers")
            if (providersArray != null) {
                for (i in 0 until providersArray.length()) {
                    providers.add(providersArray.getString(i))
                }
            }

            val networkWhitelist = mutableListOf<String>()
            val whitelistArray = manifestJson.optJSONArray("network_whitelist")
            if (whitelistArray != null) {
                for (i in 0 until whitelistArray.length()) {
                    networkWhitelist.add(whitelistArray.getString(i))
                }
            }

            val minAppVersion = manifestJson.optInt("min_app_version", 1)

            return PendingPluginImport(
                name = name,
                description = description,
                author = author,
                version = version,
                size = size,
                permissions = permissions,
                providers = providers,
                networkWhitelist = networkWhitelist,
                minAppVersion = minAppVersion,
                isZip = true,
                tempDir = tempDir,
                originalFileName = originalFileName
            )
        } catch (e: Exception) {
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            throw e
        }
    }

    // will be deprecated in the future
    fun prepareHtmlPluginImport(context: Context, htmlContent: String, displayName: String): PendingPluginImport {
        val timestamp = System.currentTimeMillis()
        val tempDir = File(context.cacheDir, "temp_plugin_pending_$timestamp")
        tempDir.mkdirs()

        try {
            // write html
            File(tempDir, "plugin.html").writeText(htmlContent)

            return PendingPluginImport(
                name = displayName,
                description = "Imported HTML widget file",
                author = "Local Import",
                version = "1.0.0",
                size = "full",
                permissions = listOf("battery"),
                providers = emptyList(),
                networkWhitelist = emptyList(),
                minAppVersion = 1,
                isZip = false,
                tempDir = tempDir,
                originalFileName = displayName
            )
        } catch (e: Exception) {
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            throw e
        }
    }

    fun completePendingImport(context: Context, pending: PendingPluginImport, customName: String): PluginModel {
        val timestamp = System.currentTimeMillis()
        val tempDir = pending.tempDir
        
        try {
            val manifestFile = File(tempDir, "plugin_manifest.json")
            val manifestId = if (pending.isZip) {
                val manifestJson = JSONObject(manifestFile.readText())
                if (customName != pending.name) {
                    manifestJson.put("name", customName)
                    manifestFile.writeText(manifestJson.toString(2))
                }
                manifestJson.getString("id")
            } else {
                val mId = "imported.html." + pending.originalFileName.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                val manifestObj = JSONObject().apply {
                    put("id", mId)
                    put("name", customName)
                    put("description", pending.description)
                    put("author", pending.author)
                    put("version", pending.version)
                    put("permissions", JSONArray().apply {
                        pending.permissions.forEach { put(it) }
                    })
                    put("providers", JSONArray().apply {
                        pending.providers.forEach { put(it) }
                    })
                    put("network_whitelist", JSONArray())
                    put("min_app_version", pending.minAppVersion)
                }
                manifestFile.writeText(manifestObj.toString(2))
                
                // write empty customization.json
                File(tempDir, "customization.json").writeText("{}")
                mId
            }

            if (manifestId.isBlank()) {
                throw IllegalArgumentException("Blank plugin id in manifest")
            }

            val localId = "${manifestId}_$timestamp"
            val folderName = "plugin_$localId"
            val targetDir = File(getPluginsDir(context), folderName)

            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            
            if (!tempDir.renameTo(targetDir)) {
                tempDir.copyRecursively(targetDir, overwrite = true)
                tempDir.deleteRecursively()
            }

            val registry = loadRegistry(context).toMutableList()
            registry.add(
                RegistryEntry(
                    localId = localId,
                    manifestId = manifestId,
                    name = customName,
                    folderName = folderName,
                    installTimestamp = timestamp
                )
            )
            saveRegistry(context, registry)

            return loadPluginDirectory(context, folderName, localId)
                ?: throw IllegalStateException("Failed to load imported plugin directory")
        } catch (e: Exception) {
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            throw e
        }
    }

    fun importZipPlugin(context: Context, zipStream: InputStream): PluginModel {
        val pending = prepareZipPluginImport(context, zipStream, "imported_plugin.zip")
        return completePendingImport(context, pending, pending.name)
    }

    fun importHtmlPlugin(context: Context, htmlContent: String, displayName: String): PluginModel {
        val pending = prepareHtmlPluginImport(context, htmlContent, displayName)
        return completePendingImport(context, pending, displayName)
    }

    private fun getLayoutFile(context: Context): File {
        return File(getPluginsDir(context), "pages_layout.json")
    }

    data class LayoutEntry(
        val type: String,
        val pluginLocalId: String?,
        val leftLocalId: String?,
        val rightLocalId: String?,
        val pageId: String
    )

    fun loadLayoutConfig(context: Context): List<LayoutEntry> {
        val file = getLayoutFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            val json = JSONObject(content)
            val array = json.optJSONArray("pages") ?: JSONArray()
            val list = mutableListOf<LayoutEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    LayoutEntry(
                        type = obj.getString("type"),
                        pluginLocalId = if (obj.has("plugin_local_id") && !obj.isNull("plugin_local_id")) obj.getString("plugin_local_id") else null,
                        leftLocalId = if (obj.has("left_local_id") && !obj.isNull("left_local_id")) obj.getString("left_local_id") else null,
                        rightLocalId = if (obj.has("right_local_id") && !obj.isNull("right_local_id")) obj.getString("right_local_id") else null,
                        pageId = if (obj.has("page_id") && !obj.isNull("page_id")) obj.getString("page_id") else java.util.UUID.randomUUID().toString()
                    )
                )
            }
            list
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error loading layout config", e)
            emptyList()
        }
    }

    fun saveLayoutConfig(context: Context, layout: List<LayoutEntry>) {
        val file = getLayoutFile(context)
        try {
            val json = JSONObject()
            val array = JSONArray()
            for (entry in layout) {
                val obj = JSONObject().apply {
                    put("type", entry.type)
                    put("plugin_local_id", entry.pluginLocalId ?: JSONObject.NULL)
                    put("left_local_id", entry.leftLocalId ?: JSONObject.NULL)
                    put("right_local_id", entry.rightLocalId ?: JSONObject.NULL)
                    put("page_id", entry.pageId)
                }
                array.put(obj)
            }
            json.put("pages", array)
            file.writeText(json.toString(2))
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error saving layout config", e)
        }
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    fun deletePlugin(context: Context, localId: String): Boolean {
        return try {
            val registry = loadRegistry(context).toMutableList()
            val entryIndex = registry.indexOfFirst { it.localId == localId }
            if (entryIndex == -1) return false
            
            val entry = registry[entryIndex]
            registry.removeAt(entryIndex)
            saveRegistry(context, registry)
            
            val folder = File(getPluginsDir(context), entry.folderName)
            if (folder.exists()) {
                folder.deleteRecursively()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting plugin $localId", e)
            false
        }
    }
}

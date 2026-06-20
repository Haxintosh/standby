package com.example

data class PluginModel(
    val localId: String,
    val manifestId: String,
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val permissions: List<String>,
    val networkWhitelist: List<String>,
    val minAppVersion: Int,
    val directoryPath: String?, // null if built-in
    val htmlContent: String,
    val customizations: Map<String, CustomizationOption> = emptyMap(),
    val isBuiltIn: Boolean = false
)

data class CustomizationOption(
    val type: String,
    val default: String,
    val target: String?, // "css" or "js"
    val value: String? = null
)

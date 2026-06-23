package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable
fun PluginWebView(
    plugin: PluginModel,
    modifier: Modifier = Modifier
) {
    // generate customization json
    val customizationsJson = generateCustomizationsJson(plugin.customizations)
    val currentCustomizations = rememberUpdatedState(customizationsJson)
    val currentPlugin = rememberUpdatedState(plugin) // fix: stale plugin issues webview
//    Log.d("PluginWebView", "render ${plugin.name}")
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler(
                    "/plugins/",
                    WebViewAssetLoader.InternalStoragePathHandler(context, PluginManager.getPluginsDir(context))
                )
                .build()

            WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.parseColor("#0A0A0A"))
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowFileAccessFromFileURLs = false
                    allowUniversalAccessFromFileURLs = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    useWideViewPort = false
                    loadWithOverviewMode = false
                    setSupportZoom(false)
                }
                
                // set sensor bridge
                addJavascriptInterface(
                    SensorBridge(context, { currentPlugin.value.permissions }) { currentCustomizations.value },
                    "AndroidSensors"
                )
                
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d("PluginWebView", "[${currentPlugin.value.name}] " + (consoleMessage?.message() ?: ""))
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url ?: return null
                        val scheme = url.scheme
                        val host = url.host
                        
                        val response = assetLoader.shouldInterceptRequest(url)
                        if (response != null) {
                            return response
                        }
                        
                        // allow local assets
                        if (scheme == "file" || host == "local.app" || host == "appassets.androidplatform.net") {
                            return null
                        }
                        
                        // check whitelist
                        val isAllowed = currentPlugin.value.networkWhitelist.any { allowedDomain ->
                            host != null && (host == allowedDomain || host.endsWith(".$allowedDomain"))
                        }
                        
                        if (!isAllowed) {
                            Log.w("PluginWebView", "Blocked unwhitelisted network request to $url")
                            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        }
                        
                        return null
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // run injection script
                        val initScript = generateCustomizationInjectionScript(currentPlugin.value.customizations)
                        view?.evaluateJavascript(initScript, null)
                    }
                }
                
                val dirPath = currentPlugin.value.directoryPath
                val baseUrl = if (dirPath != null) {
                    val folderName = java.io.File(dirPath).name
                    "https://appassets.androidplatform.net/plugins/$folderName/"
                } else {
                    "https://local.app/"
                }
                loadDataWithBaseURL(baseUrl, currentPlugin.value.htmlContent, "text/html", "UTF-8", null)
                tag = Pair(currentPlugin.value.htmlContent, customizationsJson)
            }
        },
        update = { webView ->
//            Log.d("PluginWebView", "update ${plugin.name}")
            val baseUrl = if (plugin.directoryPath != null) {
                val folderName = java.io.File(plugin.directoryPath).name
                "https://appassets.androidplatform.net/plugins/$folderName/"
            } else {
                "https://local.app/"
            }
            val tagPair = webView.tag as? Pair<*, *>
            val currentContent = tagPair?.first as? String
            val currentCustomization = tagPair?.second as? String
            
            if (currentContent != plugin.htmlContent) {
                Log.d("PluginWebView", "Updating WebView with new content for ${plugin.name}")
                webView.loadDataWithBaseURL(baseUrl, plugin.htmlContent, "text/html", "UTF-8", null)
                webView.tag = Pair(plugin.htmlContent, customizationsJson)
            } else if (currentCustomization != customizationsJson) {
                Log.d("PluginWebView", "Injecting dynamic customization updates for ${plugin.name}")
                val updateScript = generateCustomizationInjectionScript(plugin.customizations)
                webView.evaluateJavascript(updateScript, null)
                webView.tag = Pair(plugin.htmlContent, customizationsJson)
            }
        }
    )
}

private fun generateCustomizationsJson(customizations: Map<String, CustomizationOption>): String {
    val json = org.json.JSONObject()
    customizations.forEach { (name, option) ->
        val activeValue = option.value ?: option.default
        when (option.type.lowercase().trim()) {
            "bool", "boolean" -> json.put(name, activeValue.toBoolean())
            "number" -> json.put(name, activeValue.toDoubleOrNull() ?: 0.0)
            else -> json.put(name, activeValue)
        }
    }
    return json.toString()
}

private fun generateCustomizationInjectionScript(customizations: Map<String, CustomizationOption>): String {
    val jsBuilder = java.lang.StringBuilder()
    val changeObjectBuilder = java.lang.StringBuilder("{")
    
    customizations.forEach { (name, option) ->
        val activeValue = option.value ?: option.default
        val escapedValue = when (option.type.lowercase().trim()) {
            "bool", "boolean" -> activeValue.toBoolean().toString()
            "number" -> activeValue.toDoubleOrNull()?.toString() ?: "0"
            else -> "\"${activeValue.replace("\"", "\\\"")}\""
        }
        
        changeObjectBuilder.append("\"$name\": $escapedValue,")

        if (option.target?.lowercase() == "css") {
            jsBuilder.append("document.documentElement.style.setProperty('--$name', $escapedValue);\n")
        } else {
            jsBuilder.append("window.$name = $escapedValue;\n")
        }
    }
    
    if (changeObjectBuilder.length > 1) {
        changeObjectBuilder.deleteCharAt(changeObjectBuilder.length - 1)
    }
    changeObjectBuilder.append("}")
    
    jsBuilder.append("window.onCustomizationChanged?.($changeObjectBuilder);\n")
    return jsBuilder.toString()
}

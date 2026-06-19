package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PluginWebView(
    htmlContent: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Use transparent or the theme background color
                setBackgroundColor(android.graphics.Color.parseColor("#0A0A0A"))
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // Enable file access for complex plugins that might load local assets
                    allowFileAccess = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    
                    // These can cause issues with 1:1 mobile scaling
                    useWideViewPort = false
                    loadWithOverviewMode = false
                    setSupportZoom(false)
                }
                
                // Add the secure bridge
                addJavascriptInterface(SensorBridge(context), "AndroidSensors")
                
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        Log.d("PluginWebView", consoleMessage?.message() ?: "")
                        return true
                    }
                }
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Trigger initial update from JS if needed
                    }
                }
                
                // Load initial content
                loadDataWithBaseURL("https://local.app/", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            // Only reload if the content has actually changed to avoid unnecessary reloads
            val currentContent = webView.tag as? String
            if (currentContent != htmlContent) {
                Log.d("PluginWebView", "Updating WebView with new content")
                webView.loadDataWithBaseURL("https://local.app/", htmlContent, "text/html", "UTF-8", null)
                webView.tag = htmlContent
            }
        }
    )
}

package com.example

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets

class PluginServer(
    private val context: Context,
    private val onPluginReceived: (java.io.File, String) -> Unit
) {
    private var server: MyNanoHttpd? = null
    var port: Int = 0
        private set
    val pin: String = (1000..9999).random().toString()
    var ipAddress: String = "127.0.0.1"
        private set

    init {
        ipAddress = getLocalIpAddress()
    }

    fun start(): Boolean {
        return try {
            val s = MyNanoHttpd(0)
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = s
            port = s.listeningPort
            Log.d("PluginServer", "Server started successfully on IP $ipAddress and port $port with PIN $pin")
            true
        } catch (e: Exception) {
            Log.e("PluginServer", "Failed to start server", e)
            false
        }
    }

    fun stop() {
        try {
            server?.stop()
            server = null
            Log.d("PluginServer", "Server stopped")
        } catch (e: Exception) {
            Log.e("PluginServer", "Failed to stop server", e)
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val ip = address.hostAddress ?: continue
                        // return first lan ip
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PluginServer", "Error getting IP address", e)
        }
        return "127.0.0.1"
    }

    private fun getUploaderHtml(): String {
        return try {
            context.assets.open("uploader.html").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.e("PluginServer", "Error reading uploader.html from assets", e)
            "Error: Could not load uploader.html"
        }
    }

    private inner class MyNanoHttpd(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method

            // cors headers helper
            fun createResponse(status: Response.IStatus, mimeType: String, message: String): Response {
                val response = newFixedLengthResponse(status, mimeType, message)
                response.addHeader("Access-Control-Allow-Origin", "*")
                response.addHeader("Access-Control-Allow-Methods", "POST, OPTIONS, GET")
                response.addHeader("Access-Control-Allow-Headers", "X-PIN, Content-Type")
                return response
            }

            if (Method.OPTIONS == method) {
                return createResponse(Response.Status.NO_CONTENT, "text/plain", "")
            }

            if ("/" == uri && Method.GET == method) {
                return createResponse(Response.Status.OK, "text/html; charset=utf-8", getUploaderHtml())
            }

            if ("/upload" == uri && Method.POST == method) {
                val requestPin = session.headers["x-pin"] ?: session.headers["X-PIN"]
                if (requestPin == null || requestPin != pin) {
                    return createResponse(Response.Status.UNAUTHORIZED, "text/plain; charset=utf-8", "Incorrect PIN/Passcode. Please try again.")
                }

                return try {
                    val contentLengthStr = session.headers["content-length"] ?: session.headers["Content-Length"]
                    val contentLength = contentLengthStr?.toIntOrNull() ?: 0
                    val contentType = session.headers["content-type"] ?: session.headers["Content-Type"] ?: "text/html"

                    if (contentLength <= 0) {
                        createResponse(Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "Empty upload content.")
                    } else {
                        // create temp file
                        val tempFile = java.io.File.createTempFile("upload_", ".tmp", context.cacheDir)
                        tempFile.outputStream().use { output ->
                            val input = session.inputStream
                            val buffer = ByteArray(8192)
                            var bytesRemaining = contentLength
                            while (bytesRemaining > 0) {
                                val toRead = Math.min(buffer.size, bytesRemaining)
                                val read = input.read(buffer, 0, toRead)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesRemaining -= read
                            }
                        }

                        onPluginReceived(tempFile, contentType)
                        
                        val prefs = context.getSharedPreferences("standby_settings", Context.MODE_PRIVATE)
                        val confirmEnabled = prefs.getBoolean("confirm_plugin_import", true)
                        val responseMsg = if (confirmEnabled) {
                            "Upload received!"
                        } else {
                            "Success! Plugin updated."
                        }
                        createResponse(Response.Status.OK, "text/plain; charset=utf-8", responseMsg)
                    }
                } catch (e: Exception) {
                    Log.e("PluginServer", "Error handling upload", e)
                    createResponse(Response.Status.INTERNAL_ERROR, "text/plain; charset=utf-8", "Server error occurred.")
                }
            }

            return createResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "Not Found")
        }
    }

    companion object {
    }
}

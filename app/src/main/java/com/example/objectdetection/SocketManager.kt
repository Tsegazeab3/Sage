package com.example.objectdetection

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.net.Socket

object SocketManager {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(serverAddress: String, port: Int) {
        scope.launch {
            try {
                socket = Socket(serverAddress, port)
                writer = PrintWriter(socket!!.getOutputStream(), true)
                Log.d("SocketManager", "Connected to server")
            } catch (e: Exception) {
                Log.e("SocketManager", "Error connecting to server", e)
            }
        }
    }

    fun sendSettings(settings: Settings) {
        scope.launch {
            try {
                // For now, we'll just log the settings.
                // Later, we can serialize this to JSON and send it.
                val settingsJson = "{\"frontDistanceThreshold\":${settings.frontDistanceThreshold}, \"overheadDistanceThreshold\":${settings.overheadDistanceThreshold}}"
                writer?.println(settingsJson)
                Log.d("SocketManager", "Sent settings: $settingsJson")
            } catch (e: Exception) {
                Log.e("SocketManager", "Error sending settings", e)
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                writer?.close()
                socket?.close()
                Log.d("SocketManager", "Disconnected from server")
            } catch (e: Exception) {
                Log.e("SocketManager", "Error disconnecting from server", e)
            }
        }
    }
}

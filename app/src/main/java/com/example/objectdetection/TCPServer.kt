package com.example.objectdetection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

class TCPServer {

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    private var serverSocket: ServerSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun start() {
        coroutineScope.launch {
            try {
                // Using a port above 1024 is recommended for non-root Android apps.
                serverSocket = ServerSocket(8080)
                while (serverSocket?.isClosed == false) {
                    val clientSocket = serverSocket!!.accept()
                    handleClient(clientSocket)
                }
            } catch (e: Exception) {
                // Handle exceptions, e.g., port already in use
                e.printStackTrace()
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        coroutineScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val message = reader.readLine()
                if (message != null) {
                    _messages.emit(message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                clientSocket.close()
            }
        }
    }

    fun stop() {
        serverSocket?.close()
    }
}

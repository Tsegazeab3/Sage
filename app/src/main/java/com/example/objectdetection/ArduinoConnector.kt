package com.example.objectdetection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ArduinoConnector {

    private val tcpServer = TCPServer()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _arduinoIpAddress = MutableStateFlow("")
    val arduinoIpAddress = _arduinoIpAddress.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    fun start() {
        tcpServer.start()
        coroutineScope.launch {
            tcpServer.messages.collect { message ->
                if (message.startsWith("IP:")) {
                    val newIp = message.substringAfter("IP:")
                    _arduinoIpAddress.value = newIp
                } else {
                    _messages.emit(message)
                }
            }
        }
    }

    fun stop() {
        tcpServer.stop()
    }

    suspend fun sendMessage(message: String) {
        val ipAddress = _arduinoIpAddress.value
        if (ipAddress.isBlank()) {
            // Optionally handle the case where IP is not known yet
            println("Arduino IP not available. Cannot send message: $message")
            return
        }
        withContext(Dispatchers.IO) {
            try {
                val socket = java.net.Socket(ipAddress, 8081)
                val writer = java.io.PrintWriter(socket.getOutputStream(), true)
                writer.println(message)
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

package com.example.objectdetection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

object ArduinoConnector {

    private val tcpServer = TCPServer()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    fun start() {
        tcpServer.start()
        coroutineScope.launch {
            tcpServer.messages.collect { message ->
                when {
                    message.startsWith("BUTTON:") -> {
                        val buttonMessage = message.substringAfter("BUTTON:")
                        _messages.emit(buttonMessage)
                    }
                    message.startsWith("ULTRASONIC:") -> {
                        val ultrasonicMessage = message.substringAfter("ULTRASONIC:")
                        when (ultrasonicMessage) {
                            "DANGER" -> sendMessage("BUTTON", "BUZZ")
                            "SAFE" -> sendMessage("BUTTON", "STOP_BUZZ")
                        }
                    }
                    message.startsWith("CLIENT_DISCONNECTED:") -> {
                        val disconnectedClient = message.substringAfter("CLIENT_DISCONNECTED:")
                        if (disconnectedClient == "ULTRASONIC") {
                            sendMessage("BUTTON", "STOP_BUZZ")
                        }
                    }
                    // IAM messages can be ignored here or logged if needed
                }
            }
        }
    }

    fun stop() {
        tcpServer.stop()
    }

    fun sendMessage(clientName: String, message: String) {
        tcpServer.sendMessage(clientName, message)
    }

    fun sendThresholds(settings: Settings) {
        val thresholdMessage = "THRESHOLDS:${settings.frontDistanceThreshold}:${settings.overheadDistanceThreshold}"
        sendMessage("ULTRASONIC", thresholdMessage)
    }
}

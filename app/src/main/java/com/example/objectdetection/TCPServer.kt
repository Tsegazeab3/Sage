package com.example.objectdetection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class TCPServer {

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    private var serverSocket: ServerSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val clients = ConcurrentHashMap<String, Socket>()

    fun start() {
        coroutineScope.launch {
            try {
                serverSocket = ServerSocket(8080)
                while (serverSocket?.isClosed == false) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        handleClient(clientSocket)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        coroutineScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                var clientName: String? = null
                while (clientSocket.isConnected) {
                    val message = reader.readLine() ?: break // Connection closed
                    if (clientName == null && message.startsWith("IAM:")) {
                        clientName = message.substringAfter("IAM:")
                        clients[clientName] = clientSocket
                        _messages.emit(message) // Propagate the IAM message
                    } else if (clientName != null) {
                        // Prefix message with client name to distinguish in collector
                        _messages.emit("$clientName:$message")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Find and remove client from map
                val clientNameToRemove = clients.entries.find { it.value == clientSocket }?.key
                if (clientNameToRemove != null) {
                    clients.remove(clientNameToRemove)
                }
                clientSocket.close()
            }
        }
    }

    fun sendMessage(clientName: String, message: String) {
        coroutineScope.launch {
            clients[clientName]?.let { socket ->
                try {
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println(message)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun stop() {
        serverSocket?.close()
        clients.forEach { (_, socket) -> socket.close() }
        clients.clear()
    }
}
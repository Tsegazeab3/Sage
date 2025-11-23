
package com.example.objectdetection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket

private const val UDP_PORT = 44444
private const val MAX_UDP_PACKET_SIZE = 1500 // Should be larger than ESP32's MAX_PACKET_SIZE + header

// Data class to hold the state of a frame being reassembled
private data class FrameBuffer(
    var frameId: Int = -1,
    var totalPackets: Int = 0,
    val packets: MutableMap<Int, ByteArray> = mutableMapOf()
) {
    fun reset(newFrameId: Int, newTotalPackets: Int) {
        frameId = newFrameId
        totalPackets = newTotalPackets
        packets.clear()
    }
}

class UdpStreamReceiver {

    private val _bitmapFlow = MutableStateFlow<Bitmap?>(null)
    val bitmapFlow: StateFlow<Bitmap?> = _bitmapFlow

    private var job: Job? = null
    private var socket: DatagramSocket? = null
    
    // Buffer for the current frame being assembled
    private val currentFrameBuffer = FrameBuffer()

    fun start() {
        if (job?.isActive == true) return
        
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                socket = DatagramSocket(UDP_PORT)
                socket?.broadcast = true
                Log.d("UdpStreamReceiver", "UDP Socket started on port $UDP_PORT")

                val buffer = ByteArray(MAX_UDP_PACKET_SIZE)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        // This is a blocking call
                        socket?.receive(packet)

                        val receivedBytes = packet.data.copyOf(packet.length)
                        
                        // --- Frame Reassembly Logic ---
                        if (receivedBytes.size < 4) {
                            Log.w("UdpStreamReceiver", "Received packet too small to be a valid frame part.")
                            continue
                        }

                        val frameId = ((receivedBytes[0].toInt() and 0xFF) shl 8) or (receivedBytes[1].toInt() and 0xFF)
                        val packetNum = receivedBytes[2].toInt() and 0xFF
                        val totalPackets = receivedBytes[3].toInt() and 0xFF
                        val payload = receivedBytes.sliceArray(4 until receivedBytes.size)

                        // If we're starting to receive a new frame, clear the old buffer
                        if (frameId != currentFrameBuffer.frameId) {
                            currentFrameBuffer.reset(frameId, totalPackets)
                        }

                        // Add packet to buffer if it's not already there
                        if (!currentFrameBuffer.packets.containsKey(packetNum)) {
                            currentFrameBuffer.packets[packetNum] = payload
                        }

                        // If we have all packets, reassemble the frame
                        if (currentFrameBuffer.packets.size == totalPackets) {
                            val fullFrameData = ByteArrayOutputStream()
                            for (i in 0 until totalPackets) {
                                val chunk = currentFrameBuffer.packets[i]
                                if (chunk != null) {
                                    fullFrameData.write(chunk)
                                } else {
                                    // If any chunk is missing, we can't build the frame.
                                    Log.w("UdpStreamReceiver", "Frame $frameId is missing packet $i, discarding.")
                                    currentFrameBuffer.reset(frameId, 0) // Reset to be safe
                                    break
                                }
                            }
                            
                            // Check if the frame was fully assembled
                            if (fullFrameData.size() > 0) {
                                val byteArray = fullFrameData.toByteArray()
                                val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                                if (bitmap != null) {
                                    _bitmapFlow.value = bitmap
                                    Log.d("UdpStreamReceiver", "Successfully decoded frame $frameId")
                                } else {
                                    Log.e("UdpStreamReceiver", "Failed to decode frame $frameId")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e("UdpStreamReceiver", "Error receiving UDP packet", e)
                        }
                    }
                }
            } catch (e: Exception) {
                 Log.e("UdpStreamReceiver", "Socket creation failed", e)
            } finally {
                socket?.close()
                Log.d("UdpStreamReceiver", "UDP Socket closed.")
            }
        }
    }

    fun stop() {
        job?.cancel()
        socket?.close()
        job = null
        socket = null
    }
}

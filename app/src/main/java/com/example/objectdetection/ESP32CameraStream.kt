
package com.example.objectdetection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class StreamState {
    CONNECTING,
    CONNECTED,
    ERROR
}

@Composable
fun ESP32CameraStream(
    streamUrl: String, // The URL is now a parameter
    onFrame: (Bitmap) -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var streamState by remember { mutableStateOf(StreamState.CONNECTING) }
    var retryTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(streamUrl, retryTrigger) {
        streamState = StreamState.CONNECTING
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(streamUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doInput = true
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = BufferedInputStream(connection.inputStream)
                    val boundary = "--" + connection.contentType.substringAfter("boundary=").trim()
                    val boundaryBytes = boundary.toByteArray(Charsets.UTF_8)
                    
                    streamState = StreamState.CONNECTED

                    while (isActive) {
                        // Find the start of the next part
                        val frameBytes = findNextFrame(inputStream, boundaryBytes)
                        if (frameBytes != null) {
                            // Find the start of the JPEG image data
                            val jpegStart = findJpegStart(frameBytes)
                            if (jpegStart != -1) {
                                val newBitmap = BitmapFactory.decodeByteArray(frameBytes, jpegStart, frameBytes.size - jpegStart)
                                if (newBitmap != null) {
                                    bitmap = newBitmap
                                    onFrame(newBitmap)
                                }
                            }
                        } else {
                            // End of stream
                            break
                        }
                    }
                } else {
                    streamState = StreamState.ERROR
                }
            } catch (e: Exception) {
                e.printStackTrace()
                streamState = StreamState.ERROR
            } finally {
                connection?.disconnect()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (streamState) {
            StreamState.CONNECTING -> {
                CircularProgressIndicator()
                Text("Connecting to ESP32-CAM...", modifier = Modifier.padding(top = 80.dp))
            }
            StreamState.CONNECTED -> {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "ESP32 Camera Stream",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            StreamState.ERROR -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Failed to connect to stream.")
                    Text("Is the ESP32-CAM on the same network?")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { retryTrigger++ }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

private fun findNextFrame(inputStream: BufferedInputStream, boundary: ByteArray): ByteArray? {
    val buffer = ByteArrayOutputStream()
    var matchCount = 0
    var b: Int

    while (true) {
        b = inputStream.read()
        if (b == -1) {
            return null // End of stream
        }
        buffer.write(b)

        if (b == boundary[matchCount].toInt()) {
            matchCount++
            if (matchCount == boundary.size) {
                // Found a boundary, return the frame data collected so far (excluding the boundary)
                val frameData = buffer.toByteArray()
                return frameData.copyOfRange(0, frameData.size - boundary.size)
            }
        } else {
            matchCount = 0
        }
    }
}

private fun findJpegStart(data: ByteArray): Int {
    for (i in 0 until data.size - 1) {
        if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte()) {
            return i
        }
    }
    return -1
}

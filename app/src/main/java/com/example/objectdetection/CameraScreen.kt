
package com.example.objectdetection

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun CameraScreen(
    onFrame: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val serviceDiscovery = remember { ServiceDiscovery(context) }
    val discoveryState by serviceDiscovery.discoveryState.collectAsState()

    // Start and stop discovery based on the composable's lifecycle
    DisposableEffect(Unit) {
        serviceDiscovery.startDiscovery()
        onDispose {
            serviceDiscovery.stopDiscovery()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (discoveryState.first) {
            DiscoveryStatus.SEARCHING -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Searching for esp32-camera on the network...")
                }
            }
            DiscoveryStatus.FOUND -> {
                val service = discoveryState.second
                if (service != null) {
                    // We found the service, now connect to the stream
                    ESP32CameraStream(streamUrl = service.getStreamUrl(), onFrame = onFrame)
                } else {
                    // This case should ideally not happen if status is FOUND
                    ErrorView(message = "Service found but information is missing.") {
                        serviceDiscovery.startDiscovery()
                    }
                }
            }
            DiscoveryStatus.NOT_FOUND, DiscoveryStatus.ERROR -> {
                ErrorView(message = "Could not find esp32-camera.") {
                    serviceDiscovery.startDiscovery()
                }
            }
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = message)
        Text(text = "Please ensure the ESP32 is on the same Wi-Fi network.")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry Search")
        }
    }
}

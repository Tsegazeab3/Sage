package com.example.objectdetection

import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner

@Composable
fun rememberCameraState(
    tts: TextToSpeech
): CameraState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    return remember(tts, screenWidthPx, screenHeightPx) {
        CameraState(
            tts = tts,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx
        )
    }
}

class CameraState(
    private val tts: TextToSpeech,
    val screenWidthPx: Float,
    val screenHeightPx: Float
) {
    var detections by mutableStateOf<List<DetectionResult>>(emptyList())
    var bitmapWidth by mutableStateOf(1)
    var bitmapHeight by mutableStateOf(1)
    var rotationDegrees by mutableStateOf(0)

    var selectedObject by mutableStateOf("cell phone")
    var expanded by mutableStateOf(false)

    var lastSpokenWarning by mutableStateOf("") // Moved here

    val dangerousItems = listOf("knife", "fork") // Moved here

    val filteredDetections: List<DetectionResult>
        get() = filterDetections(detections, listOf(selectedObject))

    fun onDetections(newDetections: List<DetectionResult>) {
        detections = newDetections
    }
}
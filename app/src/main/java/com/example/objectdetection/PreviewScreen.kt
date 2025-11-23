package com.example.objectdetection

import android.graphics.RectF
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import android.util.Log
import com.example.objectdetection.TrackedObject
import kotlin.math.abs
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.Socket

val DangerRed = Color(0xFFB00020) // Define DangerRed here or import from ui.theme

fun speakLabels(tts: TextToSpeech, labels: List<String>) {
    val message = labels.joinToString(separator = ", ")
    tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
}

@Composable
fun PreviewScreen(
    detector: YOLODetector,
    onDismiss: () -> Unit,
    isPreview: Boolean,
    dangerousItems: List<String>,
    initialSelectedItem: String,
    selectedCamera: Camera
) {
    var detections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var selectedItem by remember { mutableStateOf(initialSelectedItem) }
    val context = LocalContext.current
    val tts = remember {
        TextToSpeech(context, null)
    }
    var highlightAll by remember { mutableStateOf(false) }
    var triggerSpeak by remember { mutableStateOf(false) }
    var rotationDegrees by remember { mutableStateOf(0) }
    var trackedObjects by remember { mutableStateOf<List<TrackedObject>>(emptyList()) }
    val screenWidth = LocalContext.current.resources.displayMetrics.widthPixels
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isPreview) {
        ArduinoConnector.messages.collect { message ->
            when (message) {
                "BUTTON_1_PRESSED" -> {
                    if (isPreview) {
                        highlightAll = !highlightAll
                    } else {
                        val currentIndex = HOUSE_CLASSES.indexOf(selectedItem)
                        val nextIndex = if (currentIndex > 0) currentIndex - 1 else HOUSE_CLASSES.size - 1
                        selectedItem = HOUSE_CLASSES[nextIndex]
                    }
                }
                "BUTTON_2_PRESSED" -> {
                    if (isPreview) {
                        triggerSpeak = true
                    } else {
                        val currentIndex = HOUSE_CLASSES.indexOf(selectedItem)
                        val nextIndex = if (currentIndex < HOUSE_CLASSES.size - 1) currentIndex + 1 else 0
                        selectedItem = HOUSE_CLASSES[nextIndex]
                    }
                }
                "BUTTON_3_PRESSED" -> {
                    onDismiss()
                }
            }
        }
    }

    LaunchedEffect(triggerSpeak) {
        if (triggerSpeak) {
            val transformedBoxes = detections.map {
                transformCoordinates(
                    det = it,
                    srcWidth = 640,
                    srcHeight = 640,
                    rotationDegrees = rotationDegrees,
                    targetWidth = screenWidth.toFloat(),
                    targetHeight = screenWidth.toFloat() // Assuming a square preview for simplicity
                )
            }
            val groupedDetections = transformedBoxes.groupBy { it.labelText.substringBefore(" ") }
            val messages = groupedDetections.map { (label, boxes) ->
                val count = boxes.size
                val direction = boxes.first().direction
                if (count > 1) {
                    "I see $count ${label}s $direction"
                } else {
                    "I see a $label $direction"
                }
            }
            val finalMessage = messages.joinToString(separator = ". ")
            tts.speak(finalMessage, TextToSpeech.QUEUE_FLUSH, null, null)
            triggerSpeak = false
        }
    }

    LaunchedEffect(detections, selectedItem) {
        val allTransformedBoxes = detections.map {
            transformCoordinates(
                det = it,
                srcWidth = 640,
                srcHeight = 640,
                rotationDegrees = rotationDegrees,
                targetWidth = screenWidth.toFloat(),
                targetHeight = screenWidth.toFloat()
            ) to it
        }

        val newTrackedObjects = allTransformedBoxes.map { (box, det) ->
            TrackedObject.fromTransformedBox(box, det)
        }

        val tolerance = screenWidth / 3f
        val objectsToAnnounce = allTransformedBoxes.filter { (box, det) ->
            val newObj = TrackedObject.fromTransformedBox(box, det)
            trackedObjects.none { oldObj ->
                val distance = kotlin.math.sqrt(
                    (newObj.centerX - oldObj.centerX).pow(2) + (newObj.centerY - oldObj.centerY).pow(2)
                )
                oldObj.classId == newObj.classId && distance < tolerance
            }
        }

        val announcements = mutableListOf<String>()

        // Announce dangerous items
        val dangerousObjects = objectsToAnnounce.filter { (box, det) ->
            dangerousItems.contains(getLabel(det.classId))
        }
        if (dangerousObjects.isNotEmpty()) {
            coroutineScope.launch {
                ArduinoConnector.sendMessage("BUTTON", "DANGER_DETECTED")
            }
            val messages = dangerousObjects.map { (box, det) ->
                val label = getLabel(det.classId)
                "Danger, I see a $label ${box.direction}"
            }
            announcements.addAll(messages)
        }

        // Announce selected item in search mode
        if (!isPreview) {
            val selectedObjects = objectsToAnnounce.filter { (box, det) ->
                getLabel(det.classId) == selectedItem && !dangerousItems.contains(getLabel(det.classId))
            }
            if (selectedObjects.isNotEmpty()) {
                val messages = selectedObjects.map { (box, det) ->
                    val label = getLabel(det.classId)
                    "I see a $label ${box.direction}"
                }
                announcements.addAll(messages)
            }
        }

        if (announcements.isNotEmpty()) {
            val finalMessage = announcements.joinToString(separator = ". ")
            tts.speak(finalMessage, TextToSpeech.QUEUE_FLUSH, null, null)
        }

        trackedObjects = newTrackedObjects
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (selectedCamera) {
            Camera.PHONE -> {
                CameraPreview(
                    detector = detector,
                    selectedObject = selectedItem,
                    onDetections = { dets, rotation ->
                        detections = dets
                        rotationDegrees = rotation
                    }
                )
            }
            Camera.ESP32 -> {
                CameraScreen(onFrame = { bitmap ->
                    coroutineScope.launch(Dispatchers.Default) {
                        val result = detector.detect(bitmap)
                        withContext(Dispatchers.Main) {
                            detections = result
                        }
                    }
                })
            }
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            detections.forEach { det ->
                val label = getLabel(det.classId)
                val shouldHighlight = highlightAll || label == selectedItem || dangerousItems.contains(label)
                if (shouldHighlight) {
                    val transformedBox = transformCoordinates(
                        det = det,
                        srcWidth = 640,
                        srcHeight = 640,
                        rotationDegrees = rotationDegrees,
                        targetWidth = size.width,
                        targetHeight = size.height
                    )
                    drawRect(
                        color = if (dangerousItems.contains(label)) DangerRed else Color.Green,
                        topLeft = Offset(transformedBox.left, transformedBox.top),
                        size = Size(transformedBox.width, transformedBox.height),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }

        if (isPreview) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Switch(
                        checked = highlightAll,
                        onCheckedChange = { highlightAll = it }
                    )
                }
                Button(onClick = { triggerSpeak = true }) {
                    Text("Trigger Detection")
                }
            }
        } else {
            // Search flow
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Text("Looking for $selectedItem")
                SearchSection(onSearch = { selectedItem = it }, color = Color.Transparent, showSearchButton = false)
            }
        }
    }
}
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
    tcpServer: TCPServer
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

    LaunchedEffect(tcpServer, isPreview) {
        tcpServer.messages.collect { message ->
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
        if (!isPreview) {
            val foundDetections = detections.filter { getLabel(it.classId) == selectedItem }
            val transformedBoxes = foundDetections.map {
                transformCoordinates(
                    det = it,
                    srcWidth = 640,
                    srcHeight = 640,
                    rotationDegrees = rotationDegrees,
                    targetWidth = screenWidth.toFloat(),
                    targetHeight = screenWidth.toFloat()
                ) to it
            }

            val newTrackedObjects = transformedBoxes.map { (box, det) ->
                TrackedObject.fromTransformedBox(box, det)
            }

            val tolerance = screenWidth / 3f
            val objectsToAnnounce = transformedBoxes.filter { (box, det) ->
                val newObj = TrackedObject.fromTransformedBox(box, det)
                trackedObjects.none { oldObj ->
                    val distance = kotlin.math.sqrt(
                        (newObj.centerX - oldObj.centerX).pow(2) + (newObj.centerY - oldObj.centerY).pow(2)
                    )
                    oldObj.classId == newObj.classId && distance < tolerance
                }
            }

            if (objectsToAnnounce.isNotEmpty()) {
                val messages = objectsToAnnounce.map { (box, det) ->
                    val label = getLabel(det.classId)
                    "I see a $label ${box.direction}"
                }
                val finalMessage = messages.joinToString(separator = ". ")
                tts.speak(finalMessage, TextToSpeech.QUEUE_FLUSH, null, null)
            }
            trackedObjects = newTrackedObjects
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(detector = detector, selectedObject = selectedItem, onDetections = { dets, rotation ->
            detections = dets
            rotationDegrees = rotation
        })
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
                        color = if (dangerousItems.contains(label)) Color.Red else Color.Green,
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
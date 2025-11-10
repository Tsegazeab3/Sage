package com.example.objectdetection

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

fun speak(tts: TextToSpeech, labels: List<String>) {
    val message = labels.joinToString(separator = ", ")
    tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
}

@Composable

fun PreviewScreen(

    detector: YOLODetector,

    onDismiss: () -> Unit,

    isPreview: Boolean,

    dangerousItems: List<String>,

    initialSelectedItem: String

) {

    var detections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }

    var selectedItem by remember { mutableStateOf(initialSelectedItem) }

    val context = LocalContext.current

    val tts = remember {

        TextToSpeech(context, null)

    }

    var highlightAll by remember { mutableStateOf(false) }

    var triggerSpeak by remember { mutableStateOf(false) }



    LaunchedEffect(triggerSpeak) {

        if (triggerSpeak) {

            val labels = detections.map { getLabel(it.classId) }

            speak(tts, labels)

            triggerSpeak = false

        }

    }



    LaunchedEffect(detections, selectedItem) {

        if (!isPreview) {

            val foundDetections = detections.filter { getLabel(it.classId) == selectedItem }

            if (foundDetections.isNotEmpty()) {

                val labels = foundDetections.map { getLabel(it.classId) }

                speak(tts, labels.map { "$it found" })

            }

        }

    }



    Box(modifier = Modifier.fillMaxSize()) {

        CameraPreview(detector = detector, selectedObject = selectedItem, onDetections = {

            detections = it

        })

        Canvas(modifier = Modifier.fillMaxSize()) {

            detections.forEach { det ->

                if (highlightAll || getLabel(det.classId) == selectedItem || dangerousItems.contains(getLabel(det.classId))) {

                    drawRect(

                        color = if (dangerousItems.contains(getLabel(det.classId))) Color.Red else Color.Green,

                        topLeft = Offset(det.x, det.y),

                        size = Size(det.width, det.height),

                        style = Stroke(width = 3.dp.toPx())

                    )

                }

            }

        }



        Column(

            modifier = Modifier

                .fillMaxSize()

                .padding(16.dp),

            verticalArrangement = Arrangement.SpaceBetween,

            horizontalAlignment = Alignment.CenterHorizontally

        ) {

            if (isPreview) {

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

            } else {

                // Search flow

                Column(

                    modifier = Modifier.fillMaxSize(),

                    verticalArrangement = Arrangement.Center,

                    horizontalAlignment = Alignment.CenterHorizontally

                ) {

                    CircularProgressIndicator()

                    Text("Looking for $selectedItem")

                    SearchSection(onSearch = { selectedItem = it }, color = Color.Transparent)

                }

            }

        }

    }

}
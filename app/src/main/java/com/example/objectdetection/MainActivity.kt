package com.example.objectdetection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.objectdetection.ui.theme.ObjectDetectionTheme
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.content.ContextCompat.getMainExecutor
import java.util.concurrent.Executor
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

class MainActivity : ComponentActivity() {
    private lateinit var detector: YOLODetector
    private var hasCameraPermission by mutableStateOf(false)

    // Camera permission request
    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize detector
        detector = YOLODetector()
        val success = detector.initialize(assets)

        // Request camera permission
        requestCameraPermission()

        setContent {
            ObjectDetectionTheme {
                if (hasCameraPermission) {
                    CameraPreview(detector = detector)
                } else {
                    Text(
                        text = "Requesting camera permission...",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                hasCameraPermission = true
            }
            else -> {
                requestPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

@Composable
fun CameraPreview(detector: YOLODetector) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tts = remember {
        TextToSpeech(context, null)
    }
    val cameraState = rememberCameraState(tts = tts)

    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    LaunchedEffect(cameraState.detections) {
        val dangerousDetections = cameraState.detections.filter { cameraState.dangerousItems.contains(getLabel(it.classId)) }
        if (dangerousDetections.isNotEmpty()) {
            val warning = dangerousDetections.joinToString(separator = ", ") { det ->
                val transformedBox = transformCoordinates(
                    det = det,
                    srcWidth = cameraState.bitmapWidth,
                    srcHeight = cameraState.bitmapHeight,
                    rotationDegrees = cameraState.rotationDegrees,
                    targetWidth = cameraState.screenWidthPx,
                    targetHeight = cameraState.screenHeightPx
                )
                "Warning, ${getLabel(det.classId)} on the ${transformedBox.direction}"
            }
            if (warning != cameraState.lastSpokenWarning) {
                tts.speak(warning, TextToSpeech.QUEUE_FLUSH, null, null)
                cameraState.lastSpokenWarning = warning
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(surfaceProvider)

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(getMainExecutor(context)) { imageProxy ->
                            cameraState.bitmapWidth = imageProxy.width
                            cameraState.bitmapHeight = imageProxy.height
                            cameraState.rotationDegrees = imageProxy.imageInfo.rotationDegrees

                            val bitmap = imageProxy.toBitmap()
                            val results = detector.detect(bitmap)
                            cameraState.onDetections(results)

                            imageProxy.close()
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (exc: Exception) {
                            Log.e("Camera", "Use case binding failed", exc)
                        }
                    }, getMainExecutor(context))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            cameraState.filteredDetections.forEach { det ->
                val transformedBox = transformCoordinates(
                    det = det,
                    srcWidth = cameraState.bitmapWidth,
                    srcHeight = cameraState.bitmapHeight,
                    rotationDegrees = cameraState.rotationDegrees,
                    targetWidth = canvasWidth,
                    targetHeight = canvasHeight
                )

                drawRect(
                    color = Color.Red,
                    topLeft = Offset(transformedBox.left, transformedBox.top),
                    size = Size(transformedBox.width, transformedBox.height),
                    style = Stroke(width = 3.dp.toPx())
                )

                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 30f
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawRect(
                        transformedBox.left,
                        transformedBox.top - 40f,
                        transformedBox.left + (transformedBox.labelText.length * 15f),
                        transformedBox.top,
                        android.graphics.Paint().apply { color = android.graphics.Color.RED }
                    )
                    drawText(
                        "${transformedBox.labelText} ${transformedBox.direction}",
                        transformedBox.left,
                        transformedBox.top - 10f,
                        paint
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Button(onClick = { cameraState.expanded = true }) {
                    Text(text = cameraState.selectedObject)
                }
                DropdownMenu(
                    expanded = cameraState.expanded,
                    onDismissRequest = { cameraState.expanded = false }
                ) {
                    YOLO_CLASSES.forEach { label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                cameraState.selectedObject = label
                                cameraState.expanded = false
                            }
                        )
                    }
                }
            }

            Button(onClick = {
                tts.language = Locale.US
                val detectedObjects = cameraState.filteredDetections.joinToString(separator = ", ") { det ->
                    val transformedBox = transformCoordinates(
                        det = det,
                        srcWidth = cameraState.bitmapWidth,
                        srcHeight = cameraState.bitmapHeight,
                        rotationDegrees = cameraState.rotationDegrees,
                        targetWidth = cameraState.screenWidthPx,
                        targetHeight = cameraState.screenHeightPx
                    )
                    "${getLabel(det.classId)} on the ${transformedBox.direction}"
                }
                if (detectedObjects.isNotEmpty()) {
                    tts.speak(detectedObjects, TextToSpeech.QUEUE_FLUSH, null, null)
                } else {
                    tts.speak("No objects detected", TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }) {
                Text("Speak")
            }
        }
    }
}

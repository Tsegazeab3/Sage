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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

// --- Class Labels Data ---
private val YOLO_CLASSES = listOf(
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
    "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
    "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
    "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
    "tennis racket", "water bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
    "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
    "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
    "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
    "hair drier", "toothbrush"
)

/**
 * Maps the class ID (index) to a human-readable label.
 */
private fun getLabel(classId: Int): String {
    return YOLO_CLASSES.getOrElse(classId) { "Unknown($classId)" }
}

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

// Data class for transformed coordinates
private data class TransformedBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val labelText: String,
    val direction: String
)

/**
 * Transforms the bounding box coordinates from the detector's coordinate system
 * (based on the rotated bitmap) to the screen's coordinate system (Canvas overlay).
 * * @param det The raw detection result.
 * @param srcWidth The width of the source bitmap (ImageProxy.width).
 * @param srcHeight The height of the source bitmap (ImageProxy.height).
 * @param rotationDegrees The rotation of the image (ImageProxy.imageInfo.rotationDegrees).
 * @param targetWidth The width of the Canvas overlay.
 * @param targetHeight The height of the Canvas overlay.
 */
private fun transformCoordinates(
    det: DetectionResult,
    srcWidth: Int,
    srcHeight: Int,
    rotationDegrees: Int,
    targetWidth: Float,
    targetHeight: Float
): TransformedBox {
    // 1. Normalize Bounding Box coordinates based on source dimensions
    val normalizedLeft = det.x / srcWidth
    val normalizedTop = det.y / srcHeight
    val normalizedRight = (det.x + det.width) / srcWidth
    val normalizedBottom = (det.y + det.height) / srcHeight

    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f

    // 2. Rotate the Normalized Coordinates
    when (rotationDegrees) {
        0 -> { // No rotation
            left = normalizedLeft
            top = normalizedTop
            right = normalizedRight
            bottom = normalizedBottom
        }
        90 -> { // Rotated 90 degrees clockwise (Image is taller)
            left = 1f - normalizedBottom
            top = normalizedLeft
            right = 1f - normalizedTop
            bottom = normalizedRight
        }
        180 -> { // Rotated 180 degrees
            left = 1f - normalizedRight
            top = 1f - normalizedBottom
            right = 1f - normalizedLeft
            bottom = 1f - normalizedTop
        }
        270 -> { // Rotated 270 degrees clockwise (Image is taller)
            left = normalizedTop
            top = 1f - normalizedRight
            right = normalizedBottom
            bottom = 1f - normalizedLeft
        }
    }

    // 3. Scale the Rotated Normalized Coordinates to the Canvas Size
    val finalLeft = left * targetWidth
    val finalTop = top * targetHeight
    val finalWidth = (right - left) * targetWidth
    val finalHeight = (bottom - top) * targetHeight

    // Calculate center X for direction
    val centerX = finalLeft + (finalWidth / 2)
    val direction = getDirection(centerX, targetWidth)

    // 4. Construct the label with the class name
    val labelName = getLabel(det.classId)

    return TransformedBox(
        left = finalLeft,
        top = finalTop,
        width = finalWidth,
        height = finalHeight,
        labelText = "$labelName ${(det.confidence * 100).toInt()}%",
        direction = direction
    )
}

private fun filterDetections(detections: List<DetectionResult>, desiredLabels: List<String>): List<DetectionResult> {
    return detections.filter { det -> desiredLabels.contains(getLabel(det.classId)) }
}

private fun getDirection(centerX: Float, screenWidth: Float): String {
    val oneThird = screenWidth / 3
    val twoThirds = 2 * screenWidth / 3

    return when {
        centerX < oneThird -> "left"
        centerX > twoThirds -> "right"
        else -> "center"
    }
}

@Composable
fun CameraPreview(detector: YOLODetector) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tts = remember {
        TextToSpeech(context, null)
    }
    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // State variables for detection results and image metadata
    var detections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var bitmapWidth by remember { mutableStateOf(1) } // Default to 1 to avoid division by zero
    var bitmapHeight by remember { mutableStateOf(1) }
    var rotationDegrees by remember { mutableStateOf(0) }

    // Dropdown state
    val houseObjects = listOf(
        "person", "cat", "dog", "water bottle", "cup", "fork", "knife", "spoon", "bowl",
        "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop",
        "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
        "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    )
    var selectedObject by remember { mutableStateOf("cell phone") }
    var expanded by remember { mutableStateOf(false) }

    // Filtered detections derived from the raw detections
    val filteredDetections = remember(detections, selectedObject) {
        val desiredLabels = listOf(selectedObject)
        filterDetections(detections, desiredLabels)
    }

    // Automatic speaking for dangerous items
    val dangerousItems = listOf("knife", "fork")
    var lastSpokenWarning by remember { mutableStateOf("") }

    LaunchedEffect(detections) {
        val dangerousDetections = detections.filter { dangerousItems.contains(getLabel(it.classId)) }
        if (dangerousDetections.isNotEmpty()) {
            val warning = dangerousDetections.joinToString(separator = ", ") { det ->
                val transformedBox = transformCoordinates(
                    det = det,
                    srcWidth = bitmapWidth,
                    srcHeight = bitmapHeight,
                    rotationDegrees = rotationDegrees,
                    targetWidth = screenWidthPx,
                    targetHeight = screenHeightPx
                )
                "Warning, ${getLabel(det.classId)} on the ${transformedBox.direction}"
            }
            if (warning != lastSpokenWarning) {
                tts.speak(warning, TextToSpeech.QUEUE_FLUSH, null, null)
                lastSpokenWarning = warning
            }
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {

        // Camera preview
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
                            // Update image metadata
                            bitmapWidth = imageProxy.width
                            bitmapHeight = imageProxy.height
                            rotationDegrees = imageProxy.imageInfo.rotationDegrees

                            // Run detection
                            val bitmap = imageProxy.toBitmap()
                            val results = detector.detect(bitmap)
                            detections = results

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

        // Draw bounding boxes overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Use the collected image dimensions and rotation for transformation
            filteredDetections.forEach { det ->
                val transformedBox = transformCoordinates(
                    det = det,
                    srcWidth = bitmapWidth,
                    srcHeight = bitmapHeight,
                    rotationDegrees = rotationDegrees,
                    targetWidth = canvasWidth,
                    targetHeight = canvasHeight
                )

                // Draw box
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(transformedBox.left, transformedBox.top),
                    size = Size(transformedBox.width, transformedBox.height),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Draw label using native canvas for text
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 30f
                        style = android.graphics.Paint.Style.FILL
                    }
                    // Draw a background rectangle for the text for better contrast (optional but recommended)
                    drawRect(
                        transformedBox.left,
                        transformedBox.top - 40f,
                        transformedBox.left + (transformedBox.labelText.length * 15f), // Simplified width estimate
                        transformedBox.top,
                        android.graphics.Paint().apply { color = android.graphics.Color.RED }
                    )

                    drawText(
                        "${transformedBox.labelText} ${transformedBox.direction}",
                        transformedBox.left,
                        transformedBox.top - 10f, // Position text slightly above the box
                        paint
                    )
                }
            }
        }

        // UI Elements
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dropdown Menu
            Box {
                Button(onClick = { expanded = true }) {
                    Text(text = selectedObject)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    houseObjects.forEach { label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedObject = label
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Speak Button
            Button(onClick = {
                tts.language = Locale.US
                val detectedObjects = filteredDetections.joinToString(separator = ", ") { det ->
                    val transformedBox = transformCoordinates(
                        det = det,
                        srcWidth = bitmapWidth,
                        srcHeight = bitmapHeight,
                        rotationDegrees = rotationDegrees,
                        targetWidth = screenWidthPx,
                        targetHeight = screenHeightPx
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
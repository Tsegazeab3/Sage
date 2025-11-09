package com.example.objectdetection

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset

// --- Class Labels Data ---
val YOLO_CLASSES = listOf(
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
fun getLabel(classId: Int): String {
    return YOLO_CLASSES.getOrElse(classId) { "Unknown($classId)" }
}

data class TransformedBox(
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
fun transformCoordinates(
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

fun filterDetections(detections: List<DetectionResult>, desiredLabels: List<String>): List<DetectionResult> {
    return detections.filter { det -> desiredLabels.contains(getLabel(det.classId)) }
}

fun getDirection(centerX: Float, screenWidth: Float): String {
    val oneThird = screenWidth / 3
    val twoThirds = 2 * screenWidth / 3

    return when {
        centerX < oneThird -> "left"
        centerX > twoThirds -> "right"
        else -> "center"
    }
}

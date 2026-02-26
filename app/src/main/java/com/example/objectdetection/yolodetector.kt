package com.example.objectdetection

import android.content.res.AssetManager
import android.graphics.Bitmap

class YOLODetector {
    private var nativePtr: Long = 0

    external fun initDetector(): Long
    external fun loadModel(nativePtr: Long, assetManager: AssetManager, paramPath: String, binPath: String): Boolean
    external fun detectFromBitmap(nativePtr: Long, bitmap: Bitmap): Array<DetectionResult>
    external fun releaseDetector(nativePtr: Long)

    fun initialize(assetManager: AssetManager): Boolean {
        nativePtr = initDetector()
        return loadModel(nativePtr, assetManager, "model.ncnn.param", "model.ncnn.bin")
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        return detectFromBitmap(nativePtr,bitmap).toList()
    }

    fun release() {
        releaseDetector(nativePtr)
    }

    companion object {
        init {
            System.loadLibrary("yolo11ncnn")
        }
    }

}




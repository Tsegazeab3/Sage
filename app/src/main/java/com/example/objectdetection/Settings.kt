package com.example.objectdetection

import androidx.compose.runtime.Immutable

@Immutable
data class Settings(
    val frontDistanceThreshold: Float = 100.0f, // Default to 100 cm for front distance
    val overheadDistanceThreshold: Float = 50.0f // Default to 50 cm for overhead obstacles
)
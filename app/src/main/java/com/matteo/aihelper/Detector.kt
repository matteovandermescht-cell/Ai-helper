package com.matteo.aihelper

import android.graphics.Bitmap

/** Coordinates are pixel coordinates in the exact bitmap passed to detect(). */
data class Detection(
    val label: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/** Implementations must return only predictions actually produced by their model. */
interface Detector {
    val displayName: String
    val isModelLoaded: Boolean
    fun detect(bitmap: Bitmap): List<Detection>
}

/** Explicit blocker: no compatible trained local model is present in this repository. */
class NoModelDetector : Detector {
    override val displayName: String = "Kein Modell geladen"
    override val isModelLoaded: Boolean = false
    override fun detect(bitmap: Bitmap): List<Detection> = emptyList()
}

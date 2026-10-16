package com.matteo.aihelper

import android.graphics.Bitmap

data class Detection(val label: String, val confidence: Float, val left: Float, val top: Float, val right: Float, val bottom: Float)

interface Detector {
    val displayName: String
    fun detect(bitmap: Bitmap): List<Detection>
}

/** No model is bundled yet. Never fabricates detections. */
class NoModelDetector : Detector {
    override val displayName: String = "Kein Modell geladen"
    override fun detect(bitmap: Bitmap): List<Detection> = emptyList()
}

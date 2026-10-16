package com.matteo.aihelper

/** Advisory-only decision layer. It cannot inject input or control another app. */
class DecisionEngine {
    fun hints(detections: List<Detection>): List<String> =
        if (detections.isEmpty()) listOf("Keine Erkennungen verfügbar. Modell laden, um Hinweise zu erhalten.")
        else detections.map { "Erkannt: ${it.label} (${(it.confidence * 100).toInt()} %)" }
}

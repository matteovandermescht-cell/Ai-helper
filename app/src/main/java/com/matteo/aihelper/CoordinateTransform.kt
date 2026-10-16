package com.matteo.aihelper

/**
 * Maps box edges from the exact model-input bitmap into a target view.
 * Uses a centered fit (letterboxing) rather than stretching when aspect ratios differ.
 * Coordinates are pixels, with x/y increasing right/down.
 */
object CoordinateTransform {
    fun mapBox(
        detection: Detection,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): FloatArray? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return null
        val scale = minOf(
            targetWidth.toFloat() / sourceWidth.toFloat(),
            targetHeight.toFloat() / sourceHeight.toFloat()
        )
        val offsetX = (targetWidth - sourceWidth * scale) / 2f
        val offsetY = (targetHeight - sourceHeight * scale) / 2f
        return floatArrayOf(
            offsetX + detection.left * scale,
            offsetY + detection.top * scale,
            offsetX + detection.right * scale,
            offsetY + detection.bottom * scale
        )
    }
}

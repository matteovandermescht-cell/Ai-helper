package com.matteo.aihelper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateTransformTest {
    @Test
    fun mapsCenterPreservingAspectRatio() {
        val box = Detection("enemy", 0.9f, 100f, 50f, 300f, 150f)
        // 400x200 fitted into 400x400 gives a 400x200 image with 100px top/bottom padding.
        val mapped = CoordinateTransform.mapBox(box, 400, 200, 400, 400)
        assertArrayEquals(floatArrayOf(100f, 150f, 300f, 250f), mapped!!, 0.001f)
    }

    @Test
    fun mapsWideTargetWithoutDistortingBox() {
        val box = Detection("player", 0.8f, 0f, 0f, 100f, 100f)
        val mapped = CoordinateTransform.mapBox(box, 100, 100, 300, 200)
        assertArrayEquals(floatArrayOf(50f, 0f, 250f, 200f), mapped!!, 0.001f)
    }

    @Test
    fun rejectsInvalidDimensions() {
        val box = Detection("enemy", 0.9f, 1f, 1f, 2f, 2f)
        assertNull(CoordinateTransform.mapBox(box, 0, 100, 100, 100))
    }
}

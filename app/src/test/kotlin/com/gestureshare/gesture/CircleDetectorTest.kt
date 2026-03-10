package com.gestureshare.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleDetectorTest {

    @Test
    fun `isCircle should return true for a valid circle gesture`() {
        val circleDetector = CircleDetector()
        // Simulate a circle gesture
        for (i in 0..360 step 10) {
            val angle = Math.toRadians(i.toDouble())
            val x = (150 * Math.cos(angle)).toFloat() + 300
            val y = (150 * Math.sin(angle)).toFloat() + 300
            circleDetector.addPoint(x, y)
        }
        assertTrue(circleDetector.isCircle())
    }

    @Test
    fun `isCircle should return false for a non-circle gesture`() {
        val circleDetector = CircleDetector()
        // Simulate a line gesture
        for (i in 0..100 step 10) {
            circleDetector.addPoint(i.toFloat(), i.toFloat())
        }
        assertFalse(circleDetector.isCircle())
    }
}

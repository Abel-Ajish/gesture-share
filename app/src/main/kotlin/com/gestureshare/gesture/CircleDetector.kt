package com.gestureshare.gesture

import android.util.Log
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * CircleDetector is responsible for detecting a circle gesture with production-level noise filtering.
 */
class CircleDetector(
    private val minRadius: Float = 100f, 
    private val closeThreshold: Float = 250f,
    private val maxVarianceRatio: Float = 0.35f
) {

    private val TAG = "CircleDetector"
    private val points = mutableListOf<Pair<Float, Float>>()
    private var lastPoint: Pair<Float, Float>? = null

    /**
     * Resets the current gesture tracking.
     */
    fun reset() {
        points.clear()
        lastPoint = null
    }

    /**
     * Adds a point to the current gesture path with simple distance filtering.
     */
    fun addPoint(x: Float, y: Float) {
        Log.v(TAG, "addPoint: ($x, $y)")
        val currentPoint = x to y
        val lp = lastPoint
        if (lp == null) {
            points.add(currentPoint)
            lastPoint = currentPoint
        } else {
            // Only add point if it moved significantly (noise reduction)
            val dist = calculateDistance(lp.first, lp.second, x, y)
            if (dist > 5f) { 
                points.add(currentPoint)
                lastPoint = currentPoint
            }
        }
    }

    /**
     * Determines if the tracked points form a circle gesture.
     */
    fun isCircle(): Boolean {
        // Minimum points for a circle
        if (points.size < 15) return false

        val start = points.first()
        val end = points.last()

        // 1. Check if start and end are close enough (closed loop)
        val distanceStartEnd = calculateDistance(start.first, start.second, end.first, end.second)
        if (distanceStartEnd > closeThreshold) {
            return false
        }

        // 2. Calculate bounding box and potential center
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for ((x, y) in points) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        val width = maxX - minX
        val height = maxY - minY
        
        // 3. Aspect ratio check (must be roughly square)
        val aspectRatio = if (width > height) height / width else width / height
        if (aspectRatio < 0.65f) return false

        val centerX = (minX + maxX) / 2
        val centerY = (minY + maxY) / 2
        val radius = (width + height) / 4

        // 4. Minimum size check
        if (radius < minRadius) return false

        // 5. Circularity check (Variance of distances from center)
        var totalDistance = 0f
        for ((x, y) in points) {
            totalDistance += calculateDistance(x, y, centerX, centerY)
        }
        val avgDistance = totalDistance / points.size
        
        var variance = 0f
        for ((x, y) in points) {
            val dist = calculateDistance(x, y, centerX, centerY)
            variance += (dist - avgDistance).pow(2)
        }
        val normalizedVariance = sqrt(variance / points.size) / avgDistance

        Log.d(TAG, "isCircle check: size=${points.size}, startEndDist=$distanceStartEnd, aspectRatio=$aspectRatio, radius=$radius, variance=$normalizedVariance")

        return normalizedVariance < maxVarianceRatio
        val stdDev = sqrt(variance / points.size)
        val relativeStdDev = stdDev / avgDistance

        // If points are roughly equidistant from center, it's a circle
        if (relativeStdDev > maxVarianceRatio) {
            Log.d(TAG, "Not circular enough: relativeStdDev = $relativeStdDev")
            return false
        }

        Log.d(TAG, "Circle gesture confirmed! Radius: $radius")
        return true
    }

    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }
}

package com.aircontrol.app.gesture

import com.aircontrol.app.model.GestureType
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs

/**
 * Tracks hand position across frames to detect motion-based gestures:
 * SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT.
 *
 * Uses a sliding window of wrist positions over time.
 */
class MotionTracker(
    private val windowSize: Int = 8,
    private val swipeThreshold: Float = 0.18f,   // normalised units
    private val minAspectRatio: Float = 2.0f      // ensure directional dominance
) {
    private val history = ArrayDeque<Pair<Float, Float>>(windowSize) // (cx, cy) of wrist

    fun update(landmarks: List<NormalizedLandmark>): GestureType? {
        if (landmarks.size < 21) return null

        // Track centroid of palm (wrist + knuckles average)
        val cx = (landmarks[0].x() + landmarks[5].x() + landmarks[9].x() +
                  landmarks[13].x() + landmarks[17].x()) / 5f
        val cy = (landmarks[0].y() + landmarks[5].y() + landmarks[9].y() +
                  landmarks[13].y() + landmarks[17].y()) / 5f

        history.addLast(cx to cy)
        if (history.size > windowSize) history.removeFirst()
        if (history.size < 4) return null

        val oldest = history.first()
        val newest = history.last()
        val dx = newest.first  - oldest.first
        val dy = newest.second - oldest.second
        val adx = abs(dx)
        val ady = abs(dy)

        // Need minimum displacement
        val magnitude = adx + ady
        if (magnitude < swipeThreshold) return null

        // Directional dominance check
        return when {
            ady > adx * minAspectRatio && dy < 0 -> GestureType.SWIPE_UP
            ady > adx * minAspectRatio && dy > 0 -> GestureType.SWIPE_DOWN
            adx > ady * minAspectRatio && dx < 0 -> GestureType.SWIPE_LEFT
            adx > ady * minAspectRatio && dx > 0 -> GestureType.SWIPE_RIGHT
            else -> null
        }
    }

    fun reset() {
        history.clear()
    }
}

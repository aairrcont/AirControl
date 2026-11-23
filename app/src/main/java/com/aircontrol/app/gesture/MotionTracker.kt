package com.aircontrol.app.gesture

import com.aircontrol.app.model.GestureType
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs

/**
 * Tracks palm velocity and trajectory over a sliding window.
 * Detects natural, fluid hand waves (UP, DOWN, LEFT, RIGHT).
 */
class MotionTracker(
    private val windowSize: Int = 6,
    private val swipeThreshold: Float = 0.10f,   // 10% screen displacement for effortless waving
    private val minAspectRatio: Float = 1.35f    // directional dominance
) {
    private val history = ArrayDeque<Pair<Float, Float>>(windowSize)

    fun update(landmarks: List<NormalizedLandmark>): GestureType? {
        if (landmarks.size < 21) return null

        // Track centroid of palm
        val cx = (landmarks[0].x() + landmarks[5].x() + landmarks[9].x() +
                  landmarks[13].x() + landmarks[17].x()) / 5f
        val cy = (landmarks[0].y() + landmarks[5].y() + landmarks[9].y() +
                  landmarks[13].y() + landmarks[17].y()) / 5f

        history.addLast(cx to cy)
        if (history.size > windowSize) history.removeFirst()
        if (history.size < 3) return null

        val oldest = history.first()
        val newest = history.last()
        val dx = newest.first  - oldest.first
        val dy = newest.second - oldest.second
        val adx = abs(dx)
        val ady = abs(dy)

        val magnitude = adx + ady
        if (magnitude < swipeThreshold) return null

        return when {
            ady > adx * minAspectRatio && dy < 0 -> GestureType.SWIPE_UP
            ady > adx * minAspectRatio && dy > 0 -> GestureType.SWIPE_DOWN
            // Front camera is mirrored: moving hand to the right moves image coords left
            adx > ady * minAspectRatio && dx < 0 -> GestureType.SWIPE_RIGHT
            adx > ady * minAspectRatio && dx > 0 -> GestureType.SWIPE_LEFT
            else -> null
        }
    }

    fun reset() {
        history.clear()
    }
}

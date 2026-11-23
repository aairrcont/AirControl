package com.aircontrol.app.gesture

import com.aircontrol.app.model.GestureResult
import com.aircontrol.app.model.GestureType
import com.aircontrol.app.model.HandLandmarks
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.hypot

/**
 * Geometric AI classifier: translates 21 MediaPipe NormalizedLandmarks
 * into instant, reliable gesture recognition.
 */
object GestureClassifier {

    fun classify(
        landmarks: List<NormalizedLandmark>,
        handedness: String = "Right"
    ): GestureResult {
        if (landmarks.size < 21) {
            return GestureResult(GestureType.UNKNOWN, 0f, landmarks.size, handedness)
        }

        val wrist        = landmarks[HandLandmarks.WRIST]
        val thumbTip     = landmarks[HandLandmarks.THUMB_TIP]
        val thumbMcp     = landmarks[HandLandmarks.THUMB_MCP]
        val indexTip     = landmarks[HandLandmarks.INDEX_TIP]
        val indexPip     = landmarks[HandLandmarks.INDEX_PIP]
        val middleTip    = landmarks[HandLandmarks.MIDDLE_TIP]
        val middlePip    = landmarks[HandLandmarks.MIDDLE_PIP]
        val ringTip      = landmarks[HandLandmarks.RING_TIP]
        val ringPip      = landmarks[HandLandmarks.RING_PIP]
        val pinkyTip     = landmarks[HandLandmarks.PINKY_TIP]
        val pinkyPip     = landmarks[HandLandmarks.PINKY_PIP]

        val handSize = handSpan(landmarks)

        // 1. Check vertical extension of fingers
        val indexUp  = indexTip.y()  < indexPip.y()  - handSize * 0.04f
        val middleUp = middleTip.y() < middlePip.y() - handSize * 0.04f
        val ringUp   = ringTip.y()   < ringPip.y()   - handSize * 0.04f
        val pinkyUp  = pinkyTip.y()  < pinkyPip.y()  - handSize * 0.04f

        // 2. Vertical thumb detection (Thumb Up vs Thumb Down)
        val thumbPointingUp = thumbTip.y() < thumbMcp.y() - handSize * 0.15f
        val thumbPointingDown = thumbTip.y() > thumbMcp.y() + handSize * 0.15f

        // 3. Pinch detection (index tip close to thumb tip)
        val pinchDist = dist(indexTip, thumbTip)
        val isPinch = pinchDist < handSize * 0.22f

        return when {
            // PINCH: index & thumb close together
            isPinch -> {
                GestureResult(GestureType.PINCH, 0.92f, 21, handedness)
            }
            // OPEN PALM: all 4 fingers spread open & upright
            indexUp && middleUp && ringUp && pinkyUp -> {
                GestureResult(GestureType.OPEN_PALM, 0.95f, 21, handedness)
            }
            // TWO FINGERS UP (Peace / Victory sign)
            indexUp && middleUp && !ringUp && !pinkyUp -> {
                GestureResult(GestureType.TWO_FINGERS_UP, 0.92f, 21, handedness)
            }
            // THUMB UP: thumb points up, other fingers curled
            thumbPointingUp && !indexUp && !middleUp && !ringUp && !pinkyUp -> {
                GestureResult(GestureType.THUMB_UP, 0.90f, 21, handedness)
            }
            // THUMB DOWN: thumb points down, other fingers curled
            thumbPointingDown && !indexUp && !middleUp && !ringUp && !pinkyUp -> {
                GestureResult(GestureType.THUMB_DOWN, 0.90f, 21, handedness)
            }
            // CLOSED FIST: all fingers curled
            !indexUp && !middleUp && !ringUp && !pinkyUp && !thumbPointingUp && !thumbPointingDown -> {
                GestureResult(GestureType.CLOSED_FIST, 0.90f, 21, handedness)
            }
            else -> GestureResult(GestureType.UNKNOWN, 0.4f, 21, handedness)
        }
    }

    private fun handSpan(lm: List<NormalizedLandmark>): Float {
        return dist(lm[HandLandmarks.WRIST], lm[HandLandmarks.MIDDLE_MCP]).coerceAtLeast(0.05f)
    }

    private fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return hypot(dx, dy)
    }
}

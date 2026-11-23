package com.aircontrol.app.gesture

import com.aircontrol.app.model.GestureResult
import com.aircontrol.app.model.GestureType
import com.aircontrol.app.model.HandLandmarks
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Stateless classifier: given a list of 21 MediaPipe NormalizedLandmarks,
 * returns the best-guess GestureType and a confidence score.
 *
 * All positions are normalised [0,1] in image space (x=right, y=down).
 */
object GestureClassifier {

    /**
     * Classify a hand pose from its 21 landmarks.
     * @param landmarks  Normalized landmarks from MediaPipe (index matches HandLandmarks constants)
     * @param handedness "Left" or "Right" as reported by MediaPipe
     */
    fun classify(
        landmarks: List<NormalizedLandmark>,
        handedness: String = "Right"
    ): GestureResult {
        if (landmarks.size < 21) {
            return GestureResult(GestureType.UNKNOWN, 0f, landmarks.size, handedness)
        }

        // ---- extract key y-coordinates (normalized image: y=0 is top) ----
        val wrist        = landmarks[HandLandmarks.WRIST]
        val thumbTip     = landmarks[HandLandmarks.THUMB_TIP]
        val thumbIp      = landmarks[HandLandmarks.THUMB_IP]
        val thumbMcp     = landmarks[HandLandmarks.THUMB_MCP]
        val indexTip     = landmarks[HandLandmarks.INDEX_TIP]
        val indexPip     = landmarks[HandLandmarks.INDEX_PIP]
        val middleTip    = landmarks[HandLandmarks.MIDDLE_TIP]
        val middlePip    = landmarks[HandLandmarks.MIDDLE_PIP]
        val ringTip      = landmarks[HandLandmarks.RING_TIP]
        val ringPip      = landmarks[HandLandmarks.RING_PIP]
        val pinkyTip     = landmarks[HandLandmarks.PINKY_TIP]
        val pinkyPip     = landmarks[HandLandmarks.PINKY_PIP]

        val handSize     = handSpan(landmarks) // normalised palm width

        // ---- determine which fingers are extended (tip above pip) --------
        val indexUp  = indexTip.y()  < indexPip.y()  - handSize * 0.05f
        val middleUp = middleTip.y() < middlePip.y() - handSize * 0.05f
        val ringUp   = ringTip.y()   < ringPip.y()   - handSize * 0.05f
        val pinkyUp  = pinkyTip.y()  < pinkyPip.y()  - handSize * 0.05f

        // Thumb: compare tip x vs MCP x (flipped for left hand)
        val thumbFactor = if (handedness == "Left") -1f else 1f
        val thumbUp   = (thumbTip.x() - thumbMcp.x()) * thumbFactor < -handSize * 0.1f
        val thumbDown = (thumbTip.x() - thumbMcp.x()) * thumbFactor >  handSize * 0.1f

        // ---- pinch: index tip close to thumb tip -------------------------
        val pinchDist = dist(indexTip, thumbTip)
        val isPinch = pinchDist < handSize * 0.25f

        // ---- classify pose -----------------------------------------------
        return when {
            // OPEN PALM: all 4 fingers + thumb extended
            indexUp && middleUp && ringUp && pinkyUp && !isPinch -> {
                GestureResult(GestureType.OPEN_PALM, 0.95f, 21, handedness)
            }
            // CLOSED FIST: no fingers extended
            !indexUp && !middleUp && !ringUp && !pinkyUp && !isPinch -> {
                GestureResult(GestureType.CLOSED_FIST, 0.90f, 21, handedness)
            }
            // TWO FINGERS UP (peace sign): index + middle up, ring + pinky down
            indexUp && middleUp && !ringUp && !pinkyUp -> {
                GestureResult(GestureType.TWO_FINGERS_UP, 0.90f, 21, handedness)
            }
            // THUMB UP: thumb up, all other fingers curled
            thumbUp && !indexUp && !middleUp && !ringUp && !pinkyUp -> {
                GestureResult(GestureType.THUMB_UP, 0.88f, 21, handedness)
            }
            // THUMB DOWN: thumb down, all other fingers curled
            thumbDown && !indexUp && !middleUp && !ringUp && !pinkyUp -> {
                GestureResult(GestureType.THUMB_DOWN, 0.88f, 21, handedness)
            }
            // PINCH: index tip near thumb tip
            isPinch -> {
                GestureResult(GestureType.PINCH, 0.85f, 21, handedness)
            }
            else -> GestureResult(GestureType.UNKNOWN, 0.4f, 21, handedness)
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Approximate palm width from wrist to middle MCP. */
    private fun handSpan(lm: List<NormalizedLandmark>): Float {
        return dist(lm[HandLandmarks.WRIST], lm[HandLandmarks.MIDDLE_MCP]).coerceAtLeast(0.05f)
    }

    private fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return hypot(dx, dy)
    }
}

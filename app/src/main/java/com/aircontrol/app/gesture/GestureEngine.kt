package com.aircontrol.app.gesture

import com.aircontrol.app.model.GestureResult
import com.aircontrol.app.model.GestureType
import com.aircontrol.app.model.PhoneAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 3 gesture engine:
 *   - Debounces rapid repeat triggers (cooldown)
 *   - Requires N consecutive frames of the same pose before confirming
 *   - Fuses static (pose) and dynamic (motion/swipe) gestures
 *   - Emits (GestureType, PhoneAction) via StateFlow for downstream services
 */
class GestureEngine(
    private val confirmFrames: Int = 4,       // consecutive frames needed
    private val cooldownMs: Long  = 800L,     // ms between actions
    private var gestureMap: Map<GestureType, PhoneAction> = defaultMap()
) {
    private val motionTracker = MotionTracker()

    // Public state observable
    private val _gestures = MutableStateFlow<Pair<GestureType, PhoneAction>?>(null)
    val gestures: StateFlow<Pair<GestureType, PhoneAction>?> = _gestures

    private var pendingGesture: GestureType = GestureType.UNKNOWN
    private var pendingCount:   Int          = 0
    private var lastActionMs:   Long         = 0L

    /**
     * Feed each frame's classifier result + landmarks into the engine.
     * Returns the confirmed (gesture, action) pair or null if still pending.
     */
    fun process(result: GestureResult,
                rawLandmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>
    ): Pair<GestureType, PhoneAction>? {

        val nowMs = System.currentTimeMillis()

        // 1. Try motion-based gesture (swipe overrides pose)
        val swipe = motionTracker.update(rawLandmarks)
        val candidate = swipe ?: result.gesture

        // 2. Confidence gate
        if (result.confidence < 0.65f && swipe == null) {
            pendingCount = 0
            return null
        }

        // 3. Consecutive frame accumulation
        if (candidate == pendingGesture) {
            pendingCount++
        } else {
            pendingGesture = candidate
            pendingCount   = 1
        }

        if (pendingCount < confirmFrames) return null

        // 4. Cooldown check
        if (nowMs - lastActionMs < cooldownMs) return null

        // 5. Only swipe gestures reset motion tracker to allow re-trigger
        if (candidate in listOf(
                GestureType.SWIPE_UP, GestureType.SWIPE_DOWN,
                GestureType.SWIPE_LEFT, GestureType.SWIPE_RIGHT
            )) {
            motionTracker.reset()
        }

        // 6. Resolve action
        val action = gestureMap[candidate] ?: PhoneAction.NONE
        if (action == PhoneAction.NONE) return null

        lastActionMs = nowMs
        pendingCount = 0
        val output = candidate to action
        _gestures.value = output
        return output
    }

    fun updateMap(newMap: Map<GestureType, PhoneAction>) {
        gestureMap = newMap
    }

    fun reset() {
        pendingGesture = GestureType.UNKNOWN
        pendingCount   = 0
        motionTracker.reset()
        _gestures.value = null
    }

    companion object {
        /** Default gesture → action mapping matching the design specification */
        fun defaultMap(): Map<GestureType, PhoneAction> = mapOf(
            GestureType.SWIPE_UP        to PhoneAction.SCROLL_UP,
            GestureType.SWIPE_DOWN      to PhoneAction.SCROLL_DOWN,
            GestureType.SWIPE_LEFT      to PhoneAction.PREVIOUS,
            GestureType.SWIPE_RIGHT     to PhoneAction.NEXT,
            GestureType.OPEN_PALM       to PhoneAction.PAUSE,
            GestureType.CLOSED_FIST     to PhoneAction.PLAY,
            GestureType.TWO_FINGERS_UP  to PhoneAction.BACK,
            GestureType.THUMB_UP        to PhoneAction.VOLUME_UP,
            GestureType.THUMB_DOWN      to PhoneAction.VOLUME_DOWN,
            GestureType.PINCH           to PhoneAction.SELECT
        )
    }
}

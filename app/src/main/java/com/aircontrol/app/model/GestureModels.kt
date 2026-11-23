package com.aircontrol.app.model

import kotlinx.serialization.Serializable

/**
 * All recognized gesture types from hand landmark analysis.
 */
enum class GestureType {
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    OPEN_PALM,
    CLOSED_FIST,
    TWO_FINGERS_UP,
    THUMB_UP,
    THUMB_DOWN,
    PINCH,
    UNKNOWN
}

/**
 * All phone actions that can be triggered by a gesture.
 */
enum class PhoneAction {
    SCROLL_UP,
    SCROLL_DOWN,
    NEXT,
    PREVIOUS,
    PAUSE,
    PLAY,
    BACK,
    VOLUME_UP,
    VOLUME_DOWN,
    SELECT,
    NONE
}

/**
 * Maps a GestureType to a PhoneAction; supports custom user remapping.
 */
@Serializable
data class GestureBinding(
    val gesture: String,       // GestureType.name()
    val action: String,        // PhoneAction.name()
    val enabled: Boolean = true
)

/**
 * Result of a single gesture recognition frame analysis.
 */
data class GestureResult(
    val gesture: GestureType,
    val confidence: Float,          // 0.0 – 1.0
    val landmarkCount: Int,         // should be 21 for a full hand
    val handedness: String          // "Left" or "Right"
)

/**
 * Runtime state shared across the gesture pipeline.
 */
data class GestureState(
    val isRunning: Boolean = false,
    val currentGesture: GestureType = GestureType.UNKNOWN,
    val lastAction: PhoneAction = PhoneAction.NONE,
    val fps: Float = 0f,
    val landmarksVisible: Boolean = false
)

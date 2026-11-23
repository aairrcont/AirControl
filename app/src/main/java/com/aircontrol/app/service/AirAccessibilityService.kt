package com.aircontrol.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import com.aircontrol.app.model.PhoneAction

/**
 * Phase 4 – Phone controls via AccessibilityService.
 *
 * All gesture → action dispatches arrive through execute(PhoneAction).
 * No automatic or background action happens without a confirmed gesture.
 *
 * Actions supported:
 *   SCROLL_UP / SCROLL_DOWN  → screen swipe gesture
 *   NEXT / PREVIOUS          → horizontal swipe
 *   BACK                     → GLOBAL_ACTION_BACK
 *   PAUSE / PLAY             → AudioManager media key (KeyEvent.KEYCODE_MEDIA_*)
 *   VOLUME_UP / VOLUME_DOWN  → AudioManager adjustVolume
 *   SELECT                   → simulate tap at screen centre
 */
class AirAccessibilityService : AccessibilityService() {

    private lateinit var audioManager: AudioManager

    companion object {
        /** Singleton reference so GestureService can call execute() directly */
        var instance: AirAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // -----------------------------------------------------------------------
    // Public dispatch entry-point
    // -----------------------------------------------------------------------

    fun execute(action: PhoneAction) {
        hapticPulse()
        when (action) {
            PhoneAction.SCROLL_UP    -> swipe(Direction.UP)
            PhoneAction.SCROLL_DOWN  -> swipe(Direction.DOWN)
            PhoneAction.NEXT         -> swipe(Direction.LEFT)
            PhoneAction.PREVIOUS     -> swipe(Direction.RIGHT)
            PhoneAction.BACK         -> performGlobalAction(GLOBAL_ACTION_BACK)
            PhoneAction.PAUSE        -> adjustMedia(false)
            PhoneAction.PLAY         -> adjustMedia(true)
            PhoneAction.VOLUME_UP    -> audioManager.adjustVolume(
                AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            PhoneAction.VOLUME_DOWN  -> audioManager.adjustVolume(
                AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            PhoneAction.SELECT       -> tapCenter()
            PhoneAction.NONE         -> Unit
        }
    }

    // -----------------------------------------------------------------------
    // Screen gesture helpers
    // -----------------------------------------------------------------------

    private enum class Direction { UP, DOWN, LEFT, RIGHT }

    private fun swipe(direction: Direction) {
        val cx = resources.displayMetrics.widthPixels  / 2f
        val cy = resources.displayMetrics.heightPixels / 2f

        val (x1, y1, x2, y2) = when (direction) {
            Direction.UP    -> floatArrayOf(cx, cy + 400f, cx, cy - 400f)
            Direction.DOWN  -> floatArrayOf(cx, cy - 400f, cx, cy + 400f)
            Direction.LEFT  -> floatArrayOf(cx + 400f, cy, cx - 400f, cy)
            Direction.RIGHT -> floatArrayOf(cx - 400f, cy, cx + 400f, cy)
        }
        dispatchSwipe(x1, y1, x2, y2, durationMs = 250)
    }

    private fun tapCenter() {
        val cx = resources.displayMetrics.widthPixels  / 2f
        val cy = resources.displayMetrics.heightPixels / 2f
        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    // -----------------------------------------------------------------------
    // Media playback control via AudioManager transport
    // -----------------------------------------------------------------------

    private fun adjustMedia(play: Boolean) {
        val keyCode = if (play) android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                      else      android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
        audioManager.dispatchMediaKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
    }

    // -----------------------------------------------------------------------
    // Haptic feedback
    // -----------------------------------------------------------------------

    private fun hapticPulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(40, 80))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(40, 80))
            }
        } catch (_: Exception) {}
    }
}

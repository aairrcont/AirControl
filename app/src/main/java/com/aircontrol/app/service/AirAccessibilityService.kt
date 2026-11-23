package com.aircontrol.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import com.aircontrol.app.model.PhoneAction

/**
 * Phase 4 – Phone controls via AccessibilityService.
 * Executes system gestures with futuristic sci-fi audio and tactile haptic feedback.
 */
class AirAccessibilityService : AccessibilityService() {

    private lateinit var audioManager: AudioManager
    private var toneGenerator: ToneGenerator? = null

    companion object {
        var instance: AirAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, 65)
        } catch (_: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        toneGenerator?.release()
        toneGenerator = null
        instance = null
        super.onDestroy()
    }

    // -----------------------------------------------------------------------
    // Public dispatch entry-point
    // -----------------------------------------------------------------------

    fun execute(action: PhoneAction) {
        futuristicFeedback()
        when (action) {
            PhoneAction.SCROLL_UP    -> scrollPage(up = true)
            PhoneAction.SCROLL_DOWN  -> scrollPage(up = false)
            PhoneAction.NEXT         -> swipeHorizontal(next = true)
            PhoneAction.PREVIOUS     -> swipeHorizontal(next = false)
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

    private fun scrollPage(up: Boolean) {
        val cx = resources.displayMetrics.widthPixels / 2f
        val cy = resources.displayMetrics.heightPixels / 2f

        // When scrolling down, finger swipes upward: bottom -> top
        val (y1, y2) = if (up) (cy - 500f to cy + 500f) else (cy + 500f to cy - 500f)
        dispatchSwipe(cx, y1, cx, y2, durationMs = 220)
    }

    private fun swipeHorizontal(next: Boolean) {
        val cx = resources.displayMetrics.widthPixels / 2f
        val cy = resources.displayMetrics.heightPixels / 2f

        val (x1, x2) = if (next) (cx + 350f to cx - 350f) else (cx - 350f to cx + 350f)
        dispatchSwipe(x1, cy, x2, cy, durationMs = 220)
    }

    private fun tapCenter() {
        val cx = resources.displayMetrics.widthPixels  / 2f
        val cy = resources.displayMetrics.heightPixels / 2f
        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    // -----------------------------------------------------------------------
    // Media playback control
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
    // Futuristic sci-fi sound beep & haptic pulse
    // -----------------------------------------------------------------------

    private fun futuristicFeedback() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
        } catch (_: Exception) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(35, 120))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(35, 120))
            }
        } catch (_: Exception) {}
    }
}

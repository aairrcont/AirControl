package com.aircontrol.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.aircontrol.app.R
import com.aircontrol.app.camera.HandTrackingManager
import com.aircontrol.app.gesture.GestureEngine
import com.aircontrol.app.model.GestureResult
import com.aircontrol.app.model.GestureState
import com.aircontrol.app.model.GestureType
import com.aircontrol.app.model.PhoneAction
import com.aircontrol.app.ui.FloatingHudManager
import com.aircontrol.app.ui.MainActivity
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Foreground Service:
 * - Hosts CameraX + MediaPipe in the background continuously.
 * - Displays the Futuristic Floating HUD Orb over any app via WindowManager.
 * - Bridges confirmed gestures to AirAccessibilityService.
 */
class GestureService : LifecycleService() {

    private val engine = GestureEngine()
    private lateinit var tracker: HandTrackingManager
    private var floatingHud: FloatingHudManager? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val ACTION_STOP_SERVICE = "com.aircontrol.app.STOP"
        const val CHANNEL_ID = "aircontrol_gesture"
        const val NOTIF_ID = 1

        private val _state = MutableStateFlow(GestureState())
        val state: StateFlow<GestureState> = _state

        var instance: GestureService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Touchless AirControl Active"))

        tracker = HandTrackingManager(this, engine, ::onGestureFrame)

        // Start camera analysis immediately in background bound to this LifecycleService
        tracker.start(this, null)

        // Initialize Futuristic Floating HUD if overlay permission is granted
        if (Settings.canDrawOverlays(this)) {
            floatingHud = FloatingHudManager(this).also { it.show() }
        }

        // Listen for confirmed gestures and trigger phone actions
        serviceScope.launch {
            engine.gestures.collect { pair ->
                pair ?: return@collect
                val (gesture, action) = pair
                updateState(gesture, action)
                floatingHud?.onGestureTriggered(gesture, action)
                AirAccessibilityService.instance?.execute(action)
            }
        }

        _state.value = GestureState(isRunning = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        floatingHud?.hide()
        floatingHud = null
        tracker.stop()
        engine.reset()
        _state.value = GestureState(isRunning = false)
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    // -----------------------------------------------------------------------
    // Camera attachment when MainActivity is in foreground
    // -----------------------------------------------------------------------

    fun attachPreview(previewView: PreviewView) {
        tracker.attachPreview(previewView)
    }

    fun detachPreview() {
        tracker.detachPreview()
    }

    // -----------------------------------------------------------------------
    // Frame callback from MediaPipe
    // -----------------------------------------------------------------------

    private fun onGestureFrame(result: GestureResult, raw: HandLandmarkerResult) {
        val hasLandmarks = raw.landmarks().isNotEmpty()
        floatingHud?.updateHandPresence(hasLandmarks)
        _state.value = _state.value.copy(
            currentGesture = result.gesture,
            landmarksVisible = hasLandmarks
        )
    }

    private fun updateState(gesture: GestureType, action: PhoneAction) {
        _state.value = _state.value.copy(currentGesture = gesture, lastAction = action)
        updateNotification("⚡ ${gesture.name} → ${action.name.replace('_', ' ')}")
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(contentText: String): Notification {
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, GestureService::class.java).setAction(ACTION_STOP_SERVICE),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gesture_indicator)
            .setContentTitle("AirControl // Futuristic Gesture HUD")
            .setContentText(contentText)
            .setContentIntent(openPi)
            .addAction(R.drawable.ic_stop, "Stop", stopPi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID,
            "AirControl Active",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Active while hand tracking and floating HUD are running." }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }
}

package com.aircontrol.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
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
import com.aircontrol.app.ui.MainActivity
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Foreground Service (Phase 1 + 2 + 3):
 *   - Runs with foregroundServiceType=camera so Android permits background camera use.
 *   - Hosts CameraX + MediaPipe via HandTrackingManager.
 *   - Bridges confirmed gestures → AirAccessibilityService.
 *   - Exposes GestureState via singleton StateFlow for the UI.
 *
 * Start:  Intent(context, GestureService::class.java).also { startForegroundService(it) }
 * Stop:   ACTION_STOP_SERVICE or stopService(intent)
 */
class GestureService : LifecycleService() {

    private val engine  = GestureEngine()
    private lateinit var tracker: HandTrackingManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // -----------------------------------------------------------------------
    // Static companion: singleton observable state for UI
    // -----------------------------------------------------------------------
    companion object {
        const val ACTION_STOP_SERVICE = "com.aircontrol.app.STOP"
        const val CHANNEL_ID          = "aircontrol_gesture"
        const val NOTIF_ID            = 1

        private val _state = MutableStateFlow(GestureState())
        val state: StateFlow<GestureState> = _state

        var instance: GestureService? = null
            private set
    }

    // -----------------------------------------------------------------------
    // Service lifecycle
    // -----------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Gesture tracking active"))

        tracker = HandTrackingManager(this, engine, ::onGestureFrame)

        // Collect confirmed gestures and dispatch to Accessibility Service
        serviceScope.launch {
            engine.gestures.collect { pair ->
                pair ?: return@collect
                val (gesture, action) = pair
                updateState(gesture, action)
                AirAccessibilityService.instance?.execute(action)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Start camera here — called from UI via bound camera view
        // The UI binds a PreviewView via startCamera(previewView)
        _state.value = GestureState(isRunning = true)
        return START_STICKY
    }

    override fun onDestroy() {
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
    // Camera attachment (called from MainActivity after service is started)
    // -----------------------------------------------------------------------

    fun attachPreview(previewView: PreviewView) {
        tracker.start(this, previewView)
    }

    // -----------------------------------------------------------------------
    // Frame callback
    // -----------------------------------------------------------------------

    private fun onGestureFrame(result: GestureResult, raw: HandLandmarkerResult) {
        _state.value = _state.value.copy(
            currentGesture    = result.gesture,
            landmarksVisible  = raw.landmarks().isNotEmpty()
        )
    }

    // -----------------------------------------------------------------------
    // State & notification helpers
    // -----------------------------------------------------------------------

    private fun updateState(gesture: GestureType, action: PhoneAction) {
        _state.value = _state.value.copy(currentGesture = gesture, lastAction = action)
        updateNotification("Action: ${action.name.replace('_', ' ')}")
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
            .setContentTitle("AirControl")
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
            "Gesture Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Active while hand gesture control is running." }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }
}

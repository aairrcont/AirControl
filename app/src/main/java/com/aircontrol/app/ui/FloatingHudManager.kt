package com.aircontrol.app.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.aircontrol.app.R
import com.aircontrol.app.model.GestureType
import com.aircontrol.app.model.PhoneAction
import com.aircontrol.app.service.GestureService
import kotlin.math.abs

/**
 * Manages the Futuristic Floating HUD Orb that floats over any app on the screen.
 * - Draggable anywhere on the display
 * - Displays active gestures with glowing futuristic animations
 * - Expands on tap to show detailed status and quick actions
 */
class FloatingHudManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hudView: View? = null
    private var isExpanded = false

    private lateinit var layoutParams: WindowManager.LayoutParams
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (hudView != null) return

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.layout_floating_hud, null)
        hudView = view

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 250
        }

        val bubbleView = view.findViewById<View>(R.id.bubbleView)
        val panelExpanded = view.findViewById<View>(R.id.panelExpanded)
        val btnCollapse = view.findViewById<View>(R.id.btnCollapseHud)
        val btnOpenApp = view.findViewById<Button>(R.id.btnHudOpenApp)
        val btnStop = view.findViewById<Button>(R.id.btnHudStop)

        // Setup touch drag listener for bubble
        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(hudView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < 15 && diffY < 15) {
                        // Click detected: toggle expand
                        toggleExpand(bubbleView, panelExpanded)
                    }
                    true
                }
                else -> false
            }
        }

        btnCollapse.setOnClickListener {
            toggleExpand(bubbleView, panelExpanded)
        }

        btnOpenApp.setOnClickListener {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
            collapse(bubbleView, panelExpanded)
        }

        btnStop.setOnClickListener {
            val stopIntent = Intent(context, GestureService::class.java).apply {
                action = GestureService.ACTION_STOP_SERVICE
            }
            context.startService(stopIntent)
            hide()
        }

        try {
            windowManager.addView(view, layoutParams)
            pulseAnimation(bubbleView)
        } catch (e: Exception) {
            e.printStackTrace()
            hudView = null
        }
    }

    private fun toggleExpand(bubble: View, panel: View) {
        if (isExpanded) {
            collapse(bubble, panel)
        } else {
            expand(bubble, panel)
        }
    }

    private fun expand(bubble: View, panel: View) {
        bubble.visibility = View.GONE
        panel.visibility = View.VISIBLE
        isExpanded = true
    }

    private fun collapse(bubble: View, panel: View) {
        panel.visibility = View.GONE
        bubble.visibility = View.VISIBLE
        isExpanded = false
    }

    fun onGestureTriggered(gesture: GestureType, action: PhoneAction) {
        mainHandler.post {
            val view = hudView ?: return@post
            val tvIcon = view.findViewById<TextView>(R.id.tvHudIcon)
            val bubble = view.findViewById<View>(R.id.bubbleView)
            val tvLastAction = view.findViewById<TextView>(R.id.tvHudLastAction)

            val icon = when (gesture) {
                GestureType.SWIPE_UP -> "⬆️"
                GestureType.SWIPE_DOWN -> "⬇️"
                GestureType.SWIPE_LEFT -> "⏮️"
                GestureType.SWIPE_RIGHT -> "⏭️"
                GestureType.OPEN_PALM -> "✋"
                GestureType.CLOSED_FIST -> "✊"
                GestureType.TWO_FINGERS_UP -> "✌️"
                GestureType.THUMB_UP -> "👍"
                GestureType.THUMB_DOWN -> "👎"
                GestureType.PINCH -> "🤏"
                GestureType.UNKNOWN -> "⚡"
            }

            tvIcon.text = icon
            tvLastAction?.text = "ACTION: ${action.name.replace('_', ' ')}"

            // Futuristic bounce & pulse
            val pvhX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.35f, 1.0f)
            val pvhY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.35f, 1.0f)
            ObjectAnimator.ofPropertyValuesHolder(bubble, pvhX, pvhY).apply {
                duration = 350
                start()
            }

            // Reset back to default hand icon after 2 seconds
            mainHandler.removeCallbacks(resetIconRunnable)
            mainHandler.postDelayed(resetIconRunnable, 2000)
        }
    }

    private val resetIconRunnable = Runnable {
        hudView?.findViewById<TextView>(R.id.tvHudIcon)?.text = "🖐️"
    }

    fun updateHandPresence(detected: Boolean) {
        mainHandler.post {
            val dot = hudView?.findViewById<View>(R.id.viewTrackingDot) ?: return@post
            dot.alpha = if (detected) 1.0f else 0.25f
        }
    }

    private fun pulseAnimation(target: View) {
        val pvhX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.95f, 1.05f)
        val pvhY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.95f, 1.05f)
        ObjectAnimator.ofPropertyValuesHolder(target, pvhX, pvhY).apply {
            duration = 1200
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    fun hide() {
        hudView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            hudView = null
        }
    }
}

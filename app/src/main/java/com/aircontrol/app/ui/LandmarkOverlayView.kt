package com.aircontrol.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Custom View drawn on top of the CameraX PreviewView.
 * Renders all 21 hand landmarks and the skeleton connections as neon overlay.
 *
 * Usage: Call updateResult(result, imageWidth, imageHeight) from the frame callback.
 */
class LandmarkOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var result: HandLandmarkerResult? = null
    private var imageWidth  = 1
    private var imageHeight = 1

    // Connection pairs for hand skeleton
    private val connections = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4,
        0 to 5, 5 to 6, 6 to 7, 7 to 8,
        0 to 9, 9 to 10, 10 to 11, 11 to 12,
        0 to 13, 13 to 14, 14 to 15, 15 to 16,
        0 to 17, 17 to 18, 18 to 19, 19 to 20,
        5 to 9, 9 to 13, 13 to 17
    )

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color  = Color.parseColor("#FF4FCB6E")
        style  = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#CCFFFFFF")
        style     = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color  = Color.parseColor("#FFFF5370")
        style  = Paint.Style.FILL
    }

    private val tipIndices = setOf(4, 8, 12, 16, 20)

    fun updateResult(newResult: HandLandmarkerResult, imgW: Int, imgH: Int) {
        result      = newResult
        imageWidth  = imgW
        imageHeight = imgH
        invalidate()
    }

    fun clear() {
        result = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = result ?: return
        if (r.landmarks().isEmpty()) return

        val landmarks = r.landmarks()[0]
        val scaleX = width.toFloat()  / imageWidth
        val scaleY = height.toFloat() / imageHeight

        // Map all landmarks to view coordinates
        val pts = landmarks.map { lm ->
            // Front camera is mirrored — flip X
            PointF((1f - lm.x()) * scaleX, lm.y() * scaleY)
        }

        // Draw skeleton lines
        for ((a, b) in connections) {
            if (a < pts.size && b < pts.size) {
                canvas.drawLine(pts[a].x, pts[a].y, pts[b].x, pts[b].y, linePaint)
            }
        }

        // Draw landmark dots
        for ((i, pt) in pts.withIndex()) {
            val paint = if (i in tipIndices) tipPaint else dotPaint
            val radius = if (i in tipIndices) 10f else 7f
            canvas.drawCircle(pt.x, pt.y, radius, paint)
        }
    }
}

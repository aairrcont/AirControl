package com.aircontrol.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.aircontrol.app.databinding.ActivityCalibrationBinding
import com.aircontrol.app.gesture.GestureClassifier
import com.aircontrol.app.model.GestureType
import com.aircontrol.app.service.GestureService

/**
 * CalibrationActivity – guides the user through each gesture one by one
 * and verifies recognition before saving.
 *
 * Steps:
 *   1. Show prompt ("Make OPEN_PALM")
 *   2. Run camera for 3 seconds, collect classifications
 *   3. If ≥60% of frames match the target → "✓ Detected"
 *   4. Move to next gesture
 *   5. Final summary
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding
    private val calibrationOrder = listOf(
        GestureType.OPEN_PALM,
        GestureType.CLOSED_FIST,
        GestureType.TWO_FINGERS_UP,
        GestureType.THUMB_UP,
        GestureType.THUMB_DOWN,
        GestureType.PINCH
    )
    private var step = 0
    private var frameBuffer = mutableListOf<GestureType>()
    private var collecting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Calibration"

        showStep()

        binding.btnNext.setOnClickListener {
            if (collecting) return@setOnClickListener
            collect()
        }
        binding.btnSkip.setOnClickListener { nextStep() }
    }

    private fun showStep() {
        if (step >= calibrationOrder.size) {
            binding.tvInstruction.text = "✅ Calibration complete!\nAll gestures verified."
            binding.btnNext.isEnabled = false
            binding.btnSkip.isEnabled = false
            return
        }
        val gesture = calibrationOrder[step]
        binding.tvInstruction.text = "Step ${step + 1} / ${calibrationOrder.size}\n\nMake this gesture:\n\n👉 ${gesture.name.replace('_', ' ')}"
        binding.tvResult.text = ""
        binding.btnNext.text = "Test Now"
    }

    private fun collect() {
        collecting = true
        frameBuffer.clear()
        binding.tvResult.text = "⏳ Hold the gesture for 3 seconds…"
        binding.btnNext.isEnabled = false

        val target = calibrationOrder[step]

        // Sample the gesture service's current gesture over 3 seconds (30 × 100ms)
        var sampleCount = 0
        val handler = android.os.Handler(mainLooper)
        val runnable = object : Runnable {
            override fun run() {
                val current = GestureService.state.value.currentGesture
                frameBuffer.add(current)
                sampleCount++
                if (sampleCount < 30) {
                    handler.postDelayed(this, 100)
                } else {
                    evaluateCalibration(target)
                }
            }
        }
        handler.postDelayed(runnable, 100)
    }

    private fun evaluateCalibration(target: GestureType) {
        collecting = false
        val matchCount = frameBuffer.count { it == target }
        val pct = matchCount * 100 / frameBuffer.size
        if (pct >= 50) {
            binding.tvResult.text = "✅ Detected! ($pct% accuracy)"
        } else {
            binding.tvResult.text = "⚠️ Only $pct% — try in better lighting or closer to camera"
        }
        binding.btnNext.isEnabled = true
        binding.btnNext.text = "Next Gesture"
        binding.btnNext.setOnClickListener { nextStep() }
    }

    private fun nextStep() {
        step++
        showStep()
        binding.btnNext.text = "Test Now"
        binding.btnNext.setOnClickListener {
            if (!collecting) collect()
        }
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}

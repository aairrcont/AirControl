package com.aircontrol.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aircontrol.app.databinding.ActivityMainBinding
import com.aircontrol.app.model.GestureState
import com.aircontrol.app.service.GestureService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Main Activity — Futuristic Gesture Control Panel
 * Features automatic permission onboarding and live HUD preview.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()

    // -------------------------------------------------------------------------
    // Permission launchers
    // -------------------------------------------------------------------------

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updatePermissionStatus()
        if (granted) {
            showToast("Camera permission granted ✓")
            checkAndAutoRequestPermissions()
        } else {
            showToast("Camera permission is required for touchless control")
        }
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updatePermissionStatus()
        checkAndAutoRequestPermissions()
    }

    // -------------------------------------------------------------------------
    // Activity lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeState()
        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()

        if (GestureService.instance != null) {
            GestureService.instance?.attachPreview(binding.previewView)
        } else {
            checkAndAutoRequestPermissions()
        }
    }

    override fun onPause() {
        super.onPause()
        // Detach preview when leaving screen; background tracking continues smoothly!
        GestureService.instance?.detachPreview()
    }

    // -------------------------------------------------------------------------
    // Automatic Permission Walkthrough
    // -------------------------------------------------------------------------

    private fun checkAndAutoRequestPermissions() {
        if (!cameraGranted()) {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted()) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("⚡ Futuristic Floating HUD")
                .setMessage("Allow 'Display over other apps' so the glowing gesture orb can float over your screen and control your phone across any app!")
                .setPositiveButton("Allow") { _, _ ->
                    startActivity(Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ))
                }
                .setNegativeButton("Later", null)
                .show()
            return
        }

        if (!accessibilityEnabled()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("🖐️ Enable AirControl Service")
                .setMessage("Tap 'Enable' and toggle 'AirControl Gesture Service' ON to unlock touchless gestures!")
                .setPositiveButton("Enable") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Later", null)
                .show()
            return
        }

        // All permissions granted: auto-start gesture control!
        if (GestureService.instance == null) {
            startGestureService()
        }
    }

    // -------------------------------------------------------------------------
    // Click listeners
    // -------------------------------------------------------------------------

    private fun setupClickListeners() {
        binding.btnStart.setOnClickListener { startGestureService() }
        binding.btnStop.setOnClickListener  { stopGestureService() }

        binding.btnGrantCamera.setOnClickListener {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
        binding.btnGrantAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnGrantOverlay.setOnClickListener {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }
        binding.btnGestureConfig.setOnClickListener {
            startActivity(Intent(this, GestureConfigActivity::class.java))
        }
        binding.btnCalibrate.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
    }

    // -------------------------------------------------------------------------
    // Observe GestureState
    // -------------------------------------------------------------------------

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.gestureState.collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: GestureState) {
        val serviceRunning = state.isRunning

        binding.btnStart.isEnabled = !serviceRunning
        binding.btnStop.isEnabled  = serviceRunning

        binding.chipGesture.text = if (serviceRunning) {
            "⚡ ${state.currentGesture.name.replace('_', ' ')}  →  ${state.lastAction.name.replace('_', ' ')}"
        } else {
            "Gesture tracking standby"
        }

        binding.tvFps.text = if (serviceRunning && state.landmarksVisible)
            "● LIVE HAND TRACKED" else if (serviceRunning) "○ SCANNING..." else ""
    }

    // -------------------------------------------------------------------------
    // Service control
    // -------------------------------------------------------------------------

    private fun startGestureService() {
        if (!cameraGranted()) {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        if (!accessibilityEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        val intent = Intent(this, GestureService::class.java)
        startForegroundService(intent)

        binding.previewView.postDelayed({
            GestureService.instance?.attachPreview(binding.previewView)
        }, 500)

        showToast("⚡ AirControl HUD Online! Wave your hand!")
    }

    private fun stopGestureService() {
        val intent = Intent(this, GestureService::class.java).apply {
            action = GestureService.ACTION_STOP_SERVICE
        }
        startService(intent)
        showToast("AirControl offline")
    }

    // -------------------------------------------------------------------------
    // Permission checks
    // -------------------------------------------------------------------------

    private fun cameraGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun notifGranted() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        else true

    private fun accessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains("com.aircontrol.app/.service.AirAccessibilityService")
    }

    private fun updatePermissionStatus() {
        val cam = cameraGranted()
        val acc = accessibilityEnabled()
        val ovl = Settings.canDrawOverlays(this)

        binding.chipCamera.apply {
            isChecked = cam
            text = if (cam) "Camera ✓" else "Camera ✗"
        }
        binding.chipAccessibility.apply {
            isChecked = acc
            text = if (acc) "Accessibility ✓" else "Accessibility ✗"
        }
        binding.chipOverlay.apply {
            isChecked = ovl
            text = if (ovl) "Floating HUD ✓" else "Floating HUD ✗"
        }

        binding.btnGrantCamera.visibility = if (cam) android.view.View.GONE else android.view.View.VISIBLE
        binding.btnGrantAccessibility.visibility = if (acc) android.view.View.GONE else android.view.View.VISIBLE
        binding.btnGrantOverlay.visibility = if (ovl) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

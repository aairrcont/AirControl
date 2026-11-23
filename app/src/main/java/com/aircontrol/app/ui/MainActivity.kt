package com.aircontrol.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aircontrol.app.R
import com.aircontrol.app.databinding.ActivityMainBinding
import com.aircontrol.app.model.GestureState
import com.aircontrol.app.service.GestureService
import kotlinx.coroutines.launch

/**
 * Main Activity — the user control panel.
 *
 * Layout sections:
 *   1. Live camera preview (front camera, landmarks overlaid)
 *   2. Gesture status chip (current gesture + last action)
 *   3. START / STOP gesture control button
 *   4. Permission status indicators (camera, accessibility, overlay)
 *   5. Quick links: Gesture Config, Calibration
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
        if (granted) showToast("Camera permission granted ✓")
        else showToast("Camera permission required for hand tracking")
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updatePermissionStatus() }

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
        // If service is running, re-attach preview
        if (GestureService.instance != null) {
            GestureService.instance?.attachPreview(binding.previewView)
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
            "👋 ${state.currentGesture.name.replace('_', ' ')}  →  ${state.lastAction.name.replace('_', ' ')}"
        } else {
            "Gesture tracking stopped"
        }

        binding.tvFps.text = if (serviceRunning && state.landmarksVisible)
            "Hand detected" else if (serviceRunning) "No hand detected" else ""
    }

    // -------------------------------------------------------------------------
    // Service control
    // -------------------------------------------------------------------------

    private fun startGestureService() {
        if (!cameraGranted()) {
            showToast("Grant camera permission first")
            return
        }
        if (!accessibilityEnabled()) {
            showToast("Enable AirControl in Accessibility Settings first")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!notifGranted()) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        val intent = Intent(this, GestureService::class.java)
        startForegroundService(intent)
        // Attach preview after short delay (service needs to start first)
        binding.previewView.postDelayed({
            GestureService.instance?.attachPreview(binding.previewView)
        }, 500)
        showToast("AirControl started – wave your hand!")
    }

    private fun stopGestureService() {
        val intent = Intent(this, GestureService::class.java)
            .setAction(GestureService.ACTION_STOP_SERVICE)
        startService(intent)
        showToast("AirControl stopped")
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
        // Check if our service is listed as running
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains("com.aircontrol.app/.service.AirAccessibilityService")
    }

    private fun updatePermissionStatus() {
        binding.chipCamera.apply {
            isChecked = cameraGranted()
            text = if (cameraGranted()) "Camera ✓" else "Camera ✗"
        }
        binding.chipAccessibility.apply {
            val ok = accessibilityEnabled()
            isChecked = ok
            text = if (ok) "Accessibility ✓" else "Accessibility ✗"
        }
        binding.chipOverlay.apply {
            val ok = Settings.canDrawOverlays(this@MainActivity)
            isChecked = ok
            text = if (ok) "Overlay ✓" else "Overlay ✗"
        }
        // Show/hide permission grant buttons
        binding.btnGrantCamera.visibility =
            if (cameraGranted()) android.view.View.GONE else android.view.View.VISIBLE
        binding.btnGrantAccessibility.visibility =
            if (accessibilityEnabled()) android.view.View.GONE else android.view.View.VISIBLE
        binding.btnGrantOverlay.visibility =
            if (Settings.canDrawOverlays(this)) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

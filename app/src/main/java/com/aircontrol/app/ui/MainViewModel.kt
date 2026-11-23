package com.aircontrol.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aircontrol.app.AirControlApp
import com.aircontrol.app.model.GestureBinding
import com.aircontrol.app.model.GestureState
import com.aircontrol.app.service.GestureService
import com.aircontrol.app.util.PrefsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared ViewModel for MainActivity and sub-screens.
 * Bridges GestureService.state → UI and DataStore prefs → engine settings.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = (application as AirControlApp).prefs

    // ---- Gesture state from service ----------------------------------------
    val gestureState: StateFlow<GestureState> = GestureService.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GestureState())

    // ---- Preferences --------------------------------------------------------
    val hapticEnabled   = prefs.hapticEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val overlayEnabled  = prefs.overlayEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val cooldownMs      = prefs.cooldownMs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 800)
    val gestureBindings = prefs.gestureBindings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Setters (called from UI) -------------------------------------------

    fun setHaptic(enabled: Boolean) = viewModelScope.launch {
        prefs.setHaptic(enabled)
    }

    fun setOverlay(enabled: Boolean) = viewModelScope.launch {
        prefs.setOverlay(enabled)
    }

    fun setCooldown(ms: Int) = viewModelScope.launch {
        prefs.setCooldown(ms)
    }

    fun saveBindings(bindings: List<GestureBinding>) = viewModelScope.launch {
        prefs.saveBindings(bindings)
        // Push new map to engine if service is running
        GestureService.instance?.let { svc ->
            // Engine lives in service; update via accessor (future: use IPC or broadcast)
        }
    }
}

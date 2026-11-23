package com.aircontrol.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aircontrol.app.model.GestureBinding
import com.aircontrol.app.model.GestureType
import com.aircontrol.app.model.PhoneAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aircontrol_prefs")

/**
 * Thin wrapper around DataStore Preferences.
 * Stores:
 *   - gesture sensitivity (cooldown, confirm frames)
 *   - gesture→action binding map (JSON serialised)
 *   - haptic on/off
 *   - overlay bubble on/off
 */
class PrefsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object Keys {
        val COOLDOWN_MS     = intPreferencesKey("cooldown_ms")
        val CONFIRM_FRAMES  = intPreferencesKey("confirm_frames")
        val HAPTIC_ENABLED  = booleanPreferencesKey("haptic_enabled")
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val GESTURE_MAP_JSON = stringPreferencesKey("gesture_map_json")
    }

    // ---- Default gesture binding list (matches GestureEngine.defaultMap) ----
    private val defaultBindings: List<GestureBinding> = listOf(
        GestureBinding("SWIPE_UP",       "SCROLL_UP"),
        GestureBinding("SWIPE_DOWN",     "SCROLL_DOWN"),
        GestureBinding("SWIPE_LEFT",     "PREVIOUS"),
        GestureBinding("SWIPE_RIGHT",    "NEXT"),
        GestureBinding("OPEN_PALM",      "PAUSE"),
        GestureBinding("CLOSED_FIST",    "PLAY"),
        GestureBinding("TWO_FINGERS_UP", "BACK"),
        GestureBinding("THUMB_UP",       "VOLUME_UP"),
        GestureBinding("THUMB_DOWN",     "VOLUME_DOWN"),
        GestureBinding("PINCH",          "SELECT")
    )

    // -----------------------------------------------------------------------
    // Reactive flows
    // -----------------------------------------------------------------------

    val cooldownMs: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[COOLDOWN_MS] ?: 800
    }

    val confirmFrames: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[CONFIRM_FRAMES] ?: 4
    }

    val hapticEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HAPTIC_ENABLED] ?: true
    }

    val overlayEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[OVERLAY_ENABLED] ?: true
    }

    val gestureBindings: Flow<List<GestureBinding>> = context.dataStore.data.map { prefs ->
        prefs[GESTURE_MAP_JSON]?.let {
            runCatching { json.decodeFromString<List<GestureBinding>>(it) }.getOrNull()
        } ?: defaultBindings
    }

    // -----------------------------------------------------------------------
    // Setters
    // -----------------------------------------------------------------------

    suspend fun setCooldown(ms: Int) = context.dataStore.edit { it[COOLDOWN_MS] = ms }
    suspend fun setConfirmFrames(n: Int) = context.dataStore.edit { it[CONFIRM_FRAMES] = n }
    suspend fun setHaptic(enabled: Boolean) = context.dataStore.edit { it[HAPTIC_ENABLED] = enabled }
    suspend fun setOverlay(enabled: Boolean) = context.dataStore.edit { it[OVERLAY_ENABLED] = enabled }

    suspend fun saveBindings(bindings: List<GestureBinding>) {
        val encoded = json.encodeToString(bindings)
        context.dataStore.edit { it[GESTURE_MAP_JSON] = encoded }
    }

    // -----------------------------------------------------------------------
    // Convert binding list → engine map
    // -----------------------------------------------------------------------

    fun toEngineMap(bindings: List<GestureBinding>): Map<GestureType, PhoneAction> =
        bindings.filter { it.enabled }
            .mapNotNull { b ->
                runCatching {
                    GestureType.valueOf(b.gesture) to PhoneAction.valueOf(b.action)
                }.getOrNull()
            }.toMap()
}

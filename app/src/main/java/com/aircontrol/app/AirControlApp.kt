package com.aircontrol.app

import android.app.Application
import com.aircontrol.app.util.PrefsRepository

/**
 * Application class: single source of truth for app-level dependencies.
 */
class AirControlApp : Application() {

    val prefs: PrefsRepository by lazy { PrefsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // No third-party SDKs to init; MediaPipe is loaded on demand.
    }
}

# AirControl – Touchless Android Gesture Control

> Control your phone with hand gestures via the front camera. No touch needed.

**Version:** 1.0.0 · **Platform:** Android 8.0+ (API 26+) · **Language:** Kotlin

---

## ✅ What's Built (All 6 Phases)

| Phase | Feature | Status |
|-------|---------|--------|
| 1 | Android foundation: manifest, Accessibility Service, permissions | ✅ Complete |
| 2 | CameraX front camera preview + MediaPipe Hand Landmarker (21 landmarks) | ✅ Complete |
| 3 | Gesture engine: pose classifier + motion tracker + debounce cooldown | ✅ Complete |
| 4 | Phone controls: scroll, swipe, back, media play/pause, volume, select | ✅ Complete |
| 5 | Gesture config screen, calibration wizard, DataStore preferences | ✅ Complete |
| 6 | ProGuard rules, dark theme, notification, haptic feedback | ✅ Complete |

---

## 🏗 Project Structure

```
AirControl/
├── app/
│   ├── src/main/
│   │   ├── java/com/aircontrol/app/
│   │   │   ├── AirControlApp.kt          # Application class
│   │   │   ├── camera/
│   │   │   │   └── HandTrackingManager.kt # CameraX + MediaPipe integration
│   │   │   ├── gesture/
│   │   │   │   ├── GestureClassifier.kt  # Static pose recognition (21 landmarks)
│   │   │   │   ├── MotionTracker.kt      # Swipe direction from wrist movement
│   │   │   │   └── GestureEngine.kt      # Debounce + cooldown + StateFlow output
│   │   │   ├── model/
│   │   │   │   ├── GestureModels.kt      # GestureType, PhoneAction, GestureState
│   │   │   │   └── HandLandmarks.kt      # MediaPipe landmark index constants
│   │   │   ├── service/
│   │   │   │   ├── GestureService.kt     # Foreground service (camera type)
│   │   │   │   ├── AirAccessibilityService.kt  # All phone action execution
│   │   │   │   └── MediaButtonReceiver.kt      # Media key broadcasts
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt       # Main control panel
│   │   │   │   ├── MainViewModel.kt      # Shared ViewModel
│   │   │   │   ├── GestureConfigActivity.kt    # Gesture remapping UI
│   │   │   │   ├── CalibrationActivity.kt      # Step-by-step calibration
│   │   │   │   └── LandmarkOverlayView.kt      # Custom view: 21-point skeleton
│   │   │   └── util/
│   │   │       └── PrefsRepository.kt    # DataStore: all user settings
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml
│   │   │   │   ├── activity_gesture_config.xml
│   │   │   │   ├── activity_calibration.xml
│   │   │   │   └── item_gesture_row.xml
│   │   │   ├── drawable/
│   │   │   │   ├── ic_launcher.xml
│   │   │   │   ├── ic_launcher_round.xml
│   │   │   │   ├── ic_gesture_indicator.xml
│   │   │   │   └── ic_stop.xml
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   ├── colors.xml
│   │   │   │   └── styles.xml
│   │   │   └── xml/
│   │   │       └── accessibility_service_config.xml
│   │   └── assets/
│   │       └── hand_landmarker.task   ← DOWNLOAD THIS (see below)
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

---

## 🖐 Gesture → Action Map (default, fully configurable)

| Hand Gesture | Phone Action |
|---|---|
| Move hand upward | Scroll up |
| Move hand downward | Scroll down |
| Swipe left | Previous |
| Swipe right | Next |
| Open palm (all fingers out) | Pause |
| Closed fist | Play |
| Two fingers up (peace) | Go Back |
| Thumb up | Volume up |
| Thumb down | Volume down |
| Pinch (index + thumb) | Select / tap |

All gestures can be remapped from the **Gesture Settings** screen inside the app.

---

## 🔧 Build Instructions

### Requirements
- Android Studio Giraffe (2023.3.1) or newer
- JDK 17
- Android SDK 35 (compileSdk)
- Minimum device: Android 8.0 (API 26)

### Steps

1. **Open in Android Studio**
   - `File → Open` → select the `AirControl/` folder
   - Wait for Gradle sync to complete

2. **Download the MediaPipe model**
   - Visit: https://ai.google.dev/edge/mediapipe/solutions/vision/hand_landmarker
   - Download `hand_landmarker.task` (≈8.7 MB)
   - Place it at: `app/src/main/assets/hand_landmarker.task`
   - Delete the `.PLACEHOLDER.txt` file

3. **Build the APK**
   - Debug: `Build → Build Bundle(s) / APK(s) → Build APK(s)`
   - Release: `Build → Generate Signed Bundle / APK`
   - APK location: `app/build/outputs/apk/debug/app-debug.apk`

4. **Install on device**
   - Connect phone via USB with Developer Mode enabled
   - `Run → Run 'app'` or drag APK to phone

---

## 📱 First-Time Setup on Device

After installing, the app will guide you to:

1. **Grant Camera permission** — tap the button in the app
2. **Enable Accessibility Service** — tap the button → find "AirControl" in list → enable
3. **(Optional) Allow display over other apps** — for the floating indicator bubble
4. **Tap START** — the front camera activates and hand tracking begins
5. **Run Calibration** — optional but recommended for best accuracy

---

## 🔒 Privacy

- No video or images leave your device
- Camera only runs while the foreground service is active (you explicitly start it)
- Android shows a camera privacy indicator dot while camera is active
- No network permission requested
- Accessibility Service only dispatches programmatic gestures — it does not read text or screen content

---

## ⚙️ Advanced Settings

- **Cooldown:** Minimum time between gesture triggers (default 800ms)
- **Confirm frames:** How many consecutive matching frames before triggering (default 4)
- **Haptic feedback:** Vibrate on each detected action (default on)

---

## 🐛 Troubleshooting

| Problem | Solution |
|---|---|
| App crashes immediately | Check `hand_landmarker.task` is in assets/ |
| Gestures not detected | Ensure good lighting, hand 30–70cm from camera |
| Gestures triggering randomly | Increase confirm frames in settings |
| Scroll/back not working | Confirm Accessibility Service is enabled |
| Volume control not working | Grant MODIFY_AUDIO_SETTINGS in app info |

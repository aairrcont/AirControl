# ============================================================
# AirControl ProGuard Rules
# ============================================================

# MediaPipe Tasks — keep all public classes and methods
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# DataStore / Protobuf
-keep class androidx.datastore.** { *; }
-dontwarn com.google.protobuf.**

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
}

# Keep model data classes (used in serialization)
-keep class com.aircontrol.app.model.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# General Android
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

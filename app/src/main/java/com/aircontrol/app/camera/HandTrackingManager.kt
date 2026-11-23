package com.aircontrol.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.aircontrol.app.gesture.GestureClassifier
import com.aircontrol.app.gesture.GestureEngine
import com.aircontrol.app.model.GestureResult
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors

/**
 * Manages CameraX front-camera + MediaPipe Hand Landmarker inference.
 * Supports running in background (ImageAnalysis only) or foreground with a PreviewView.
 */
class HandTrackingManager(
    private val context: Context,
    private val engine: GestureEngine,
    private val onResult: (GestureResult, HandLandmarkerResult) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var landmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var frameTimestamp = 0L

    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentPreviewView: PreviewView? = null

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView? = null) {
        currentLifecycleOwner = lifecycleOwner
        currentPreviewView = previewView
        initLandmarker()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()
            bindCamera(lifecycleOwner, previewView)
        }, ContextCompat.getMainExecutor(context))
    }

    fun attachPreview(previewView: PreviewView) {
        currentPreviewView = previewView
        currentLifecycleOwner?.let { owner ->
            bindCamera(owner, previewView)
        }
    }

    fun detachPreview() {
        currentPreviewView = null
        currentLifecycleOwner?.let { owner ->
            bindCamera(owner, null)
        }
    }

    fun stop() {
        cameraProvider?.unbindAll()
        landmarker?.close()
        landmarker = null
        currentLifecycleOwner = null
        currentPreviewView = null
    }

    // -----------------------------------------------------------------------
    // MediaPipe initialisation
    // -----------------------------------------------------------------------

    private fun initLandmarker() {
        if (landmarker != null) return
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.55f)
            .setMinHandPresenceConfidence(0.55f)
            .setMinTrackingConfidence(0.55f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(::handleLandmarkerResult)
            .setErrorListener { error -> error.printStackTrace() }
            .build()
        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    // -----------------------------------------------------------------------
    // CameraX binding: works with or without a PreviewView (Background safe!)
    // -----------------------------------------------------------------------

    private fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView? = null) {
        val provider = cameraProvider ?: return
        val selector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(executor, ::analyzeFrame) }

        try {
            provider.unbindAll()
            if (previewView != null) {
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } else {
                // Background mode: only ImageAnalysis bound to lifecycle
                provider.bindToLifecycle(lifecycleOwner, selector, analysis)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // -----------------------------------------------------------------------
    // Frame analysis
    // -----------------------------------------------------------------------

    private fun analyzeFrame(imageProxy: ImageProxy) {
        val lm = landmarker
        if (lm == null) {
            imageProxy.close()
            return
        }

        val bitmap = imageProxy.toBitmap()
        val rotated = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())
        val mpImage = BitmapImageBuilder(rotated).build()

        frameTimestamp = System.currentTimeMillis()
        lm.detectAsync(mpImage, frameTimestamp)
        imageProxy.close()
    }

    private fun handleLandmarkerResult(result: HandLandmarkerResult, input: com.google.mediapipe.framework.image.MPImage) {
        if (result.landmarks().isEmpty()) {
            val emptyResult = GestureResult(com.aircontrol.app.model.GestureType.UNKNOWN, 0f, 0, "None")
            onResult(emptyResult, result)
            return
        }

        val landmarks = result.landmarks()[0]
        val handedness = result.handednesses().firstOrNull()
            ?.firstOrNull()?.categoryName() ?: "Right"

        val classResult = GestureClassifier.classify(landmarks, handedness)
        engine.process(classResult, landmarks)
        onResult(classResult, result)
    }

    // -----------------------------------------------------------------------
    // Bitmap helpers
    // -----------------------------------------------------------------------

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}

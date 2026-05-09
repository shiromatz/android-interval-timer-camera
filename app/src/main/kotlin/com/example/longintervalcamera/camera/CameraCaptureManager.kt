package com.example.longintervalcamera.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.longintervalcamera.data.CaptureResult
import com.example.longintervalcamera.storage.ImageCaptureTarget
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraCaptureManager(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = java.util.concurrent.Executor { command -> mainHandler.post(command) }
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    suspend fun capture(target: ImageCaptureTarget, jpegQuality: Int): CameraCaptureOutcome {
        return try {
            if (target is ImageCaptureTarget.FileTarget) {
                target.file.parentFile?.mkdirs()
            }
            withContext(Dispatchers.Main) {
                val provider = ProcessCameraProvider.getInstance(context).await(mainExecutor)
                val lifecycleOwner = OneShotLifecycleOwner()
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(jpegQuality.coerceIn(1, 100))
                    .build()

                provider.unbindAll()
                lifecycleOwner.start()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    imageCapture
                )

                imageCapture.takePictureAwait(target, provider, lifecycleOwner)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            CameraCaptureOutcome(
                result = CaptureResult.FAILED_CAMERA_INIT,
                file = target.file,
                errorType = exception::class.java.simpleName,
                errorMessage = exception.message ?: "Camera initialization failed"
            )
        }
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }

    private suspend fun ImageCapture.takePictureAwait(
        target: ImageCaptureTarget,
        provider: ProcessCameraProvider,
        lifecycleOwner: OneShotLifecycleOwner
    ): CameraCaptureOutcome {
        val outputOptions = when (target) {
            is ImageCaptureTarget.FileTarget -> ImageCapture.OutputFileOptions.Builder(target.file).build()
            is ImageCaptureTarget.MediaStoreTarget -> ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                target.collectionUri,
                target.contentValues
            ).build()
        }
        return suspendCancellableCoroutine { continuation ->
            val cleanup = {
                provider.unbindAll()
                lifecycleOwner.destroy()
            }

            takePicture(
                outputOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        mainHandler.post {
                            cleanup()
                            if (continuation.isActive) {
                                val savedUri = outputFileResults.savedUri
                                clearPending(target, savedUri)
                                continuation.resume(
                                    CameraCaptureOutcome(
                                        result = CaptureResult.SUCCESS,
                                        file = target.file,
                                        uri = savedUri
                                    )
                                )
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        mainHandler.post {
                            cleanup()
                            if (continuation.isActive) {
                                continuation.resume(
                                    CameraCaptureOutcome(
                                        result = if (exception.imageCaptureError == ImageCapture.ERROR_FILE_IO) {
                                            CaptureResult.FAILED_SAVE
                                        } else {
                                            CaptureResult.FAILED_CAPTURE
                                        },
                                        file = target.file,
                                        errorType = exception::class.java.simpleName,
                                        errorMessage = exception.message ?: "Capture failed"
                                    )
                                )
                            }
                        }
                    }
                }
            )

            continuation.invokeOnCancellation {
                mainHandler.post { cleanup() }
            }
        }
    }

    private fun clearPending(target: ImageCaptureTarget, savedUri: Uri?) {
        if (target !is ImageCaptureTarget.MediaStoreTarget || savedUri == null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        runCatching { context.contentResolver.update(savedUri, values, null, null) }
    }

    private suspend fun <T> ListenableFuture<T>.await(executor: java.util.concurrent.Executor): T {
        return suspendCancellableCoroutine { continuation ->
            addListener(
                {
                    try {
                        continuation.resume(get())
                    } catch (exception: Exception) {
                        continuation.resumeWithException(exception)
                    }
                },
                executor
            )
            continuation.invokeOnCancellation { cancel(true) }
        }
    }

    private class OneShotLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = registry

        fun start() {
            registry.currentState = Lifecycle.State.CREATED
            registry.currentState = Lifecycle.State.STARTED
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

data class CameraCaptureOutcome(
    val result: CaptureResult,
    val file: File?,
    val uri: Uri? = null,
    val errorType: String? = null,
    val errorMessage: String? = null
)

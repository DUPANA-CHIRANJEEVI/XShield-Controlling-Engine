package com.example.xshield.childagent

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

class VideoRecordingManager(private val context: Context, private val lifecycleOwner: LifecycleOwner) {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var currentFile: File? = null
    
    private var onRecordingStopped: ((File?) -> Unit)? = null

    fun initialize(cameraType: String, onInitialized: () -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD)) // 720p
                .build()
            
            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = if (cameraType == "front") {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    videoCapture
                )
                onInitialized()
            } catch (e: Exception) {
                Log.e("VideoRecording", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startRecording(onStopped: (File?) -> Unit) {
        this.onRecordingStopped = onStopped
        
        val videoCapture = this.videoCapture ?: return
        
        val cacheDir = File(context.cacheDir, "videos")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        val videoFile = File(cacheDir, "record_${System.currentTimeMillis()}.mp4")
        this.currentFile = videoFile

        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        try {
            // ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) should be granted
            recording = videoCapture.output
                .prepareRecording(context, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                    when(recordEvent) {
                        is VideoRecordEvent.Start -> {
                            Log.d("VideoRecording", "Recording started")
                        }
                        is VideoRecordEvent.Finalize -> {
                            cleanup() // Release camera first
                            if (!recordEvent.hasError()) {
                                Log.d("VideoRecording", "Recording finished successfully.")
                                onRecordingStopped?.invoke(currentFile)
                            } else {
                                Log.e("VideoRecording", "Recording error: ${recordEvent.error}")
                                onRecordingStopped?.invoke(null)
                            }
                        }
                    }
                }
        } catch (e: SecurityException) {
            Log.e("VideoRecording", "Audio permission missing", e)
            onRecordingStopped?.invoke(null)
        }
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
    }

    fun cleanup() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProviderFuture.get().unbindAll()
        }, ContextCompat.getMainExecutor(context))
    }
}

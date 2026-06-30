package com.example.xshield.childagent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class ScreenShareActivity : AppCompatActivity() {

    private val TAG = "ScreenShareActivity"
    private val REQUEST_MEDIA_PROJECTION = 1001
    private var deviceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        deviceId = intent.getStringExtra("deviceId")
        if (deviceId.isNullOrBlank()) {
            finish()
            return
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Log.d(TAG, "MediaProjection permission granted")
                
                // Start Foreground Service and pass the result data
                val serviceIntent = Intent(this, ScreenSharingService::class.java).apply {
                    putExtra("deviceId", deviceId)
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                    action = "START_SCREEN_SHARE"
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } else {
                Log.e(TAG, "MediaProjection permission denied")
                deviceId?.let {
                    FirebaseDatabase.getInstance().getReference("devices/$it/screenShare/status").setValue("DENIED")
                }
            }
            finish()
        }
    }
}

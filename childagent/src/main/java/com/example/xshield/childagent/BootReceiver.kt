package com.example.xshield.childagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Restarts the MonitoringService after device reboot or if it was killed.
 * Registered for:
 *  - android.intent.action.BOOT_COMPLETED
 *  - android.intent.action.QUICKBOOT_POWERON  (HTC / some OEMs)
 *  - com.example.xshield.childagent.RESTART_SERVICE  (self-restart from onDestroy)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
        val prefs = safeContext.getSharedPreferences("xshield_prefs", Context.MODE_PRIVATE)
        val isActivated = prefs.getBoolean("activated", false)
        val deviceId = prefs.getString("device_id", "") ?: ""

        // Only restart if parent has previously activated the agent
        if (isActivated) {
            AgentStateManager.initialize(safeContext, deviceId)
            val serviceIntent = Intent(safeContext, MonitoringService::class.java)
            try {
                ContextCompat.startForegroundService(safeContext, serviceIntent)
            } catch (e: Exception) {
                // In Android 12+, starting a foreground service from the background
                // (e.g. via RESTART_SERVICE broadcast) throws an exception.
                e.printStackTrace()
            }
        }
    }
}

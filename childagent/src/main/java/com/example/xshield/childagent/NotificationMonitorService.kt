package com.example.xshield.childagent

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationMonitorService : NotificationListenerService() {

    private val TAG = "NotificationMonitor"
    
    // Variables for deduplication
    private var lastCapturedText = ""
    private var lastCapturedSender = ""
    private var lastCapturedTime = 0L

    companion object {
        var syncManager: MessageSyncManager? = null
        
        fun initSyncManager(context: Context, deviceId: String) {
            if (syncManager == null) {
                syncManager = MessageSyncManager(context.applicationContext, deviceId)
                syncManager?.startPeriodicSync()
            }
        }
    }

    private val supportedPackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b", // WhatsApp Business
        "com.instagram.android",
        "org.telegram.messenger",
        "com.snapchat.android",
        "com.facebook.katana",
        "com.facebook.orca", // Messenger
        "com.twitter.android",
        "com.google.android.youtube"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        val packageName = sbn.packageName

        // Suppress Default SMS App Notification for Xshield Commands
        val defaultSmsPackage = android.provider.Telephony.Sms.getDefaultSmsPackage(this)
        if (packageName == defaultSmsPackage) {
            val extras = sbn.notification.extras
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            if (text.contains("#XSHIELD#", ignoreCase = true) || bigText.contains("#XSHIELD#", ignoreCase = true) || title.contains("#XSHIELD#", ignoreCase = true)) {
                Log.i(TAG, "Suppressed SMS notification containing #XSHIELD# command.")
                try {
                    cancelNotification(sbn.key)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to cancel SMS command notification", e)
                }
                return
            }
        }

        if (!supportedPackages.contains(packageName)) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        
        if (title.isBlank() && text.isBlank()) return

        // Exclude generic notifications like "Checking for new messages"
        if (text.contains("Checking for new messages", ignoreCase = true) || 
            text.contains("WhatsApp Web is currently active", ignoreCase = true)) {
            return
        }

        val timestamp = sbn.postTime

        var finalDirection = "INCOMING"
        var finalSender = title
        var finalText = text

        // Handle group / self replies
        if (text.startsWith("You: ", ignoreCase = true)) {
            finalDirection = "OUTGOING"
            finalText = text.removePrefix("You: ").trim()
        } else if (text.startsWith("You\n", ignoreCase = true)) {
            finalDirection = "OUTGOING"
            finalText = text.removePrefix("You\n").trim()
        } else if (title.equals("You", ignoreCase = true)) {
            finalDirection = "OUTGOING"
            // Try to find the real contact name from notification extras
            val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            if (!convTitle.isNullOrBlank()) {
                finalSender = convTitle
            }
        }

        val message = InstantMessage(
            app = packageName,
            sender = finalSender,
            message = finalText,
            direction = finalDirection,
            timestamp = timestamp
        )

        // Deduplication check
        if (finalText == lastCapturedText && finalSender == lastCapturedSender && (System.currentTimeMillis() - lastCapturedTime) < 3000) {
            // Ignore duplicate rapid notification
            return
        }
        
        lastCapturedText = finalText
        lastCapturedSender = finalSender
        lastCapturedTime = System.currentTimeMillis()

        Log.d(TAG, "Captured Notification: $message")
        
        if (syncManager == null) {
            val prefs = getSharedPreferences("xshield_prefs", Context.MODE_PRIVATE)
            val deviceId = prefs.getString("device_id", "") ?: ""
            if (deviceId.isNotEmpty()) {
                initSyncManager(this, deviceId)
            }
        }

        syncManager?.addMessageIfEnabled(message)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}

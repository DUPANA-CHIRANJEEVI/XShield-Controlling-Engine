package com.example.xshield.childagent

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.database.Cursor
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import android.telephony.TelephonyManager
import android.media.MediaRecorder
import android.media.RingtoneManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.io.DataOutputStream
import java.io.FileInputStream
import java.security.MessageDigest
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.launch

fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    val digested = md.digest(this.toByteArray())
    return digested.joinToString("") { String.format("%02x", it) }
}

class MonitoringService : LifecycleService() {

    private val CHANNEL_ID = "xshield_svc"
    private val NOTIF_ID = 1

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var db: FirebaseFirestore
    private lateinit var secondaryDb: FirebaseFirestore
    private lateinit var mediaDb: FirebaseFirestore
    private lateinit var webrtcDb: FirebaseFirestore
    private lateinit var rtdb: FirebaseDatabase
    private lateinit var deviceId: String
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Web History Buffer
    private val pendingWebHistory = mutableListOf<Map<String, Any>>()
    private var lastSavedUrl = ""
    private var lastSaveTime = 0L
    private var webHistorySyncRunnable: Runnable? = null
    private var urlVisitedReceiver: BroadcastReceiver? = null

    private var locationListener: android.location.LocationListener? = null
    private var isPresenceSetup = false
    private var blockingListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var callBlockingListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var smsBroadcastReceiver: BroadcastReceiver? = null
    private var smsObserver: android.database.ContentObserver? = null
    private var callBroadcastReceiver: BroadcastReceiver? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentRecordingFile: File? = null
    private var videoRecordingManager: VideoRecordingManager? = null
    
    // Siren
    private var sirenPlayer: android.media.MediaPlayer? = null
    
    private var screenshotReceiver: BroadcastReceiver? = null

    // Sync every 5 minutes (300,000 ms)
    private val SYNC_INTERVAL_MS = 5 * 60 * 1000L

    companion object {
        var instance: MonitoringService? = null
        val blockedCallsList = mutableListOf<BlockedCallRule>()
        var blockAllIncoming = false
        var blockAllOutgoing = false
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()

        db = FirebaseFirestore.getInstance()
        var secondaryApp: FirebaseApp? = null
        try {
            secondaryApp = FirebaseApp.getInstance("secondary")
        } catch (e: Exception) {
            val options = FirebaseOptions.Builder()
                .setProjectId("xshield-storage-placeholder")
                .setApplicationId("1:450534538002:android:placeholder-storage")
                .setApiKey("AIzaSyDUMMY_KEY_STORAGE_PLACEHOLDER")
                .build()
            secondaryApp = FirebaseApp.initializeApp(this, options, "secondary")
        }
        secondaryDb = FirebaseFirestore.getInstance(secondaryApp!!)

        var webrtcApp: FirebaseApp? = null
        try {
            webrtcApp = FirebaseApp.getInstance("webrtc")
        } catch (e: Exception) {
            val options = FirebaseOptions.Builder()
                .setProjectId("xshield-webrtc-placeholder")
                .setApplicationId("1:812275189834:android:placeholder-webrtc")
                .setApiKey("AIzaSyDUMMY_KEY_WEBRTC_PLACEHOLDER")
                .setDatabaseUrl("https://xshield-webrtc-placeholder-default-rtdb.firebaseio.com")
                .build()
            webrtcApp = FirebaseApp.initializeApp(this, options, "webrtc")
        }
        webrtcDb = FirebaseFirestore.getInstance(webrtcApp!!)

        var mediaApp: FirebaseApp? = null
        try {
            mediaApp = FirebaseApp.getInstance("media")
        } catch (e: Exception) {
            val options = FirebaseOptions.Builder()
                .setProjectId("xshield-media-placeholder")
                .setApplicationId("1:378862449667:android:placeholder-media")
                .setApiKey("AIzaSyDUMMY_KEY_MEDIA_PLACEHOLDER")
                .setDatabaseUrl("https://xshield-media-placeholder-default-rtdb.firebaseio.com")
                .build()
            mediaApp = FirebaseApp.initializeApp(this, options, "media")
        }
        mediaDb = FirebaseFirestore.getInstance(mediaApp!!)

        rtdb = FirebaseDatabase.getInstance()
        videoRecordingManager = VideoRecordingManager(this, this)
        
        val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            createDeviceProtectedStorageContext()
        } else {
            this
        }
        val prefs = safeContext.getSharedPreferences("xshield_prefs", MODE_PRIVATE)
        deviceId = prefs.getString("device_id", "") ?: ""

        instance = this
        AgentStateManager.initialize(this, deviceId)

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 34) {
            var types = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            try {
                startForeground(NOTIF_ID, buildNotification(), types)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                startForeground(NOTIF_ID, buildNotification())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Initialize Instant Messaging Sync Manager
        NotificationMonitorService.initSyncManager(this, deviceId)

        // Acquire a partial wake lock so service survives doze
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xshield:monitor")

        // Start live location updates
        if (AgentStateManager.isMonitoringEnabled()) {
            startLocationUpdates()
        }

        // Sync Pictures
        syncPicturesMetadata()

        // Start first sync immediately, then repeat
        if (AgentStateManager.isMonitoringEnabled()) {
            scheduleNextSync()
        }

        // Start listening to manual sync commands from parent app
        listenForSyncCommands()
        listenForSyncAppsCommands()
        registerPackageReceiver()
        
        // Listen for file explorer requests
        listenForFileExplorerCommands()
        listenForCameraCommands()
        listenForVideoRecordingCommands()
        listenForAudioRecordingCommands()
        listenForAudioSessionCommands()
        listenForSirenCommands()
        listenForLockCommands()
        listenForSecretCapture()
        listenForScreenshotCommand()
        listenForScreenShareCommands()
        listenForCallControlCommands()

        screenshotReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val filePath = intent?.getStringExtra("filePath")
                if (filePath != null) {
                    val file = File(filePath)
                    if (file.exists()) {
                        uploadScreenshot(file)
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenshotReceiver, IntentFilter("com.example.xshield.SCREENSHOT_TAKEN"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenshotReceiver, IntentFilter("com.example.xshield.SCREENSHOT_TAKEN"))
        }

        // Start listening to SMS database changes automatically
        registerSmsObserver()
        
        // Start listening for calls to record
        registerCallReceiver()

        urlVisitedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val url = intent?.getStringExtra("url")
                val browser = intent?.getStringExtra("browser")
                val timestamp = intent?.getLongExtra("timestamp", System.currentTimeMillis()) ?: System.currentTimeMillis()
                if (!url.isNullOrBlank() && !browser.isNullOrBlank()) {
                    addWebHistoryItem(url, browser, timestamp)
                }
            }
        }
        val urlFilter = IntentFilter("com.example.xshield.BROWSER_URL_VISITED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(urlVisitedReceiver, urlFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(urlVisitedReceiver, urlFilter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY  // Restart automatically if killed by system
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        AgentStateManager.stopListener()
        unregisterPackageReceiver()
        handler.removeCallbacksAndMessages(null)
        wakeLock?.release()

        // Stop location updates to save battery
        locationListener?.let {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            lm.removeUpdates(it)
        }

        // Explicitly set presence status to offline
        if (deviceId.isNotBlank()) {
            rtdb.getReference("status/$deviceId").setValue(
                mapOf(
                    "state" to "offline",
                    "lastSeen" to ServerValue.TIMESTAMP
                )
            )
        }

        // Reschedule restart via broadcast
        sendBroadcast(Intent("com.example.xshield.childagent.RESTART_SERVICE"))
        blockingListener?.remove()
        smsObserver?.let { contentResolver.unregisterContentObserver(it) }
        smsBroadcastReceiver?.let { unregisterReceiver(it) }
        callBroadcastReceiver?.let { unregisterReceiver(it) }
        screenshotReceiver?.let { unregisterReceiver(it) }
        urlVisitedReceiver?.let { unregisterReceiver(it) }
        stopSiren()
    }

    // ─────────────────────────────────────────────
    // Call Recording Engine
    // ─────────────────────────────────────────────

    private fun registerCallReceiver() {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE) || !hasPermission(Manifest.permission.RECORD_AUDIO)) return

        callBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                    val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                    val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    
                    when (state) {
                        TelephonyManager.EXTRA_STATE_RINGING -> {
                            val number = incomingNumber ?: "Unknown"
                            rtdb.getReference("status/$deviceId/callState").setValue(mapOf("isRinging" to true, "incomingNumber" to number))
                            val isBlocked = blockAllIncoming || blockedCallsList.any { rule ->
                                val typeMatch = rule.type.equals("incoming", ignoreCase = true) || rule.type.equals("both", ignoreCase = true)
                                val cleanRuleNum = rule.number.replace(Regex("[^0-9]"), "")
                                val cleanIncNum = number.replace(Regex("[^0-9]"), "")
                                val numberMatch = android.telephony.PhoneNumberUtils.compare(rule.number, number) ||
                                    (cleanRuleNum.isNotEmpty() && cleanIncNum.isNotEmpty() && (cleanRuleNum.endsWith(cleanIncNum) || cleanIncNum.endsWith(cleanRuleNum)))
                                typeMatch && numberMatch
                            }
                            if (isBlocked) {
                                android.util.Log.i("CallBlocker", "Blocking incoming call from $number")
                                try {
                                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                                        telecomManager.endCall()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                            rtdb.getReference("status/$deviceId/callState/isRinging").setValue(false)
                            startCallRecording()
                        }
                        TelephonyManager.EXTRA_STATE_IDLE -> {
                            rtdb.getReference("status/$deviceId/callState/isRinging").setValue(false)
                            stopCallRecordingAndUpload()
                            // Instantly sync the new call log (wait 2s for Android to write it to internal db)
                            Handler(Looper.getMainLooper()).postDelayed({ pushData() }, 2000)
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(callBroadcastReceiver, filter)
    }

    private fun startCallRecording() {
        if (isRecording) return
        try {
            val dir = File(filesDir, "recordings")
            if (!dir.exists()) dir.mkdirs()
            val filename = "call_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().substring(0, 8)}.m4a"
            currentRecordingFile = File(dir, filename)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }
            mediaRecorder?.apply {
                // Using VOICE_RECOGNITION instead of MIC to try to bypass Android's call recording block
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentRecordingFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
        }
    }

    private fun stopCallRecordingAndUpload() {
        if (!isRecording) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        isRecording = false

        val fileToUpload = currentRecordingFile
        currentRecordingFile = null

        if (fileToUpload != null && fileToUpload.exists() && fileToUpload.length() > 0) {
            Thread {
                val url = uploadRecordingToHostinger(fileToUpload)
                if (url != null) {
                    // Save to pending recordings list so the next sync will attach it to the call log
                    val prefs = getSharedPreferences("XshieldPrefs", Context.MODE_PRIVATE)
                    val pendingStr = prefs.getString("pending_recordings", "") ?: ""
                    val newPendingStr = if (pendingStr.isEmpty()) url else "$pendingStr,$url"
                    prefs.edit().putString("pending_recordings", newPendingStr).apply()
                    fileToUpload.delete() // Clean up local file
                }
            }.start()
        }
    }

    private fun uploadRecordingToHostinger(file: File): String? {
        val scriptUrl = "https://your-domain.com/upload_recording.php"
        val boundary = "---" + System.currentTimeMillis()
        try {
            val url = URL(scriptUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            DataOutputStream(connection.outputStream).use { dos ->
                dos.writeBytes("--$boundary\r\n")
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
                dos.writeBytes("Content-Type: audio/mp4\r\n\r\n")
                FileInputStream(file).use { fis ->
                    fis.copyTo(dos)
                }
                dos.writeBytes("\r\n--$boundary--\r\n")
                dos.flush()
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                // Simple JSON extraction to get the "url" value without pulling in Gson
                val urlIndex = responseStr.indexOf("\"url\":\"")
                if (urlIndex != -1) {
                    val startIndex = urlIndex + 7
                    val endIndex = responseStr.indexOf("\"", startIndex)
                    if (endIndex != -1) {
                        return responseStr.substring(startIndex, endIndex).replace("\\/", "/")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // ─────────────────────────────────────────────
    // Sync scheduling
    // ─────────────────────────────────────────────

    private fun scheduleNextSync() {
        // Acquire a short-lived wake lock for the sync duration (max 2 min)
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(2 * 60 * 1000L)
        }
        syncAllData()
        handler.postDelayed({ scheduleNextSync() }, SYNC_INTERVAL_MS)
    }

    private fun syncAllData() {
        if (deviceId.isBlank()) return

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnSuccessListener { 
                pushData()
                if (!isPresenceSetup) {
                    setupPresenceSystem()
                    isPresenceSetup = true
                }
            }
        } else {
            pushData()
            if (!isPresenceSetup) {
                setupPresenceSystem()
                isPresenceSetup = true
            }
        }
    }

    private fun pushData() {
        if (!AgentStateManager.isMonitoringEnabled()) {
            android.util.Log.i("MonitoringService", "pushData() ignored: Monitoring is disabled.")
            return
        }
        val devRef = db.collection("devices").document(deviceId)

        // ─── Device status ───
        val batteryLevel = getBatteryLevel()
        val networkType = getNetworkType()
        
        // Write static root device document to Firestore once in a while (e.g. daily, but here we just update lastSync)
        devRef.set(
            mapOf(
                "deviceId" to deviceId,
                "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "androidVersion" to "Android ${Build.VERSION.RELEASE}",
                "lastSync" to System.currentTimeMillis()
            ), SetOptions.merge()
        )

        // Write High-Frequency Telemetry to RTDB to save Firestore Quota
        val telephony = getTelephonyDetails()
        rtdb.getReference("telemetry/$deviceId").setValue(
            mapOf(
                "lastSync" to System.currentTimeMillis(),
                "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "androidVersion" to "Android ${Build.VERSION.RELEASE}",
                "batteryLevel" to batteryLevel,
                "isCharging" to isBatteryCharging(),
                "networkType" to networkType,
                "accessibilityActive" to isAccessibilityServiceEnabled(),
                
                // Real-time specs:
                "uptime" to getSystemUptime(),
                "storageTotal" to getStorageTotal(),
                "storageUsed" to getStorageUsed(),
                "ramTotal" to getRamTotal(),
                "ramAvailable" to getRamAvailable(),
                "screenResolution" to getScreenResolution(),
                "localIp" to getLocalIpAddress(),
                
                // Telephony specs:
                "imei" to (telephony["imei"] ?: "Unavailable"),
                "simSerialNumber" to (telephony["simSerialNumber"] ?: "Unavailable"),
                "simOperator" to (telephony["simOperator"] ?: "Unknown"),
                "simState" to (telephony["simState"] ?: "Unknown"),
                "phoneNetworkOperator" to (telephony["phoneNetworkOperator"] ?: "Unknown"),
                "phoneNumber" to (telephony["phoneNumber"] ?: "Unavailable"),
                
                // Static Hardware details:
                "hardware" to Build.HARDWARE,
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "brand" to Build.BRAND,
                "cpuAbi" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"),
                "sdkVersion" to Build.VERSION.SDK_INT,
                
                // Added real device details:
                "cpuName" to getCpuMarketingName(),
                "cameraSpecs" to getCameraSpecs(),
                "batteryCapacity" to getBatteryCapacity()
            )
        )

        // ─── Call logs (Full Sync of top 1000) ───
        if (hasPermission(Manifest.permission.READ_CALL_LOG)) {
            val prefs = applicationContext.getSharedPreferences("xshield_prefs", MODE_PRIVATE)
            val calls = readCallLogs()
            
            if (calls.isNotEmpty()) {
                val currentHash = calls.toString().md5()
                val lastHash = prefs.getString("last_hash_calls", "")
                
                if (currentHash != lastHash) {
                    secondaryDb.collection("devices").document(deviceId).collection("calls").document("log")
                        .set(mapOf("items" to calls, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
                    prefs.edit().putString("last_hash_calls", currentHash).apply()
                }
            }
        }

        // ─── SMS ───
        pushSmsData()

        val prefs = applicationContext.getSharedPreferences("xshield_prefs", MODE_PRIVATE)

        // ─── Contacts ───
        if (hasPermission(Manifest.permission.READ_CONTACTS)) {
            val contacts = readContacts()
            if (contacts.isNotEmpty()) {
                val dataToSync = mapOf("items" to contacts, "updatedAt" to System.currentTimeMillis())
                val currentHash = contacts.toString().md5()
                val lastHash = prefs.getString("last_hash_contacts", "")
                if (currentHash != lastHash) {
                    secondaryDb.collection("devices").document(deviceId).collection("contacts").document("list")
                        .set(dataToSync, SetOptions.merge())
                    prefs.edit().putString("last_hash_contacts", currentHash).apply()
                }
            }
        }

        // ─── Location ───
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            val location = readLastLocation()
            if (location != null) {
                val currentHash = location.toString().md5()
                val lastHash = prefs.getString("last_hash_location", "")
                if (currentHash != lastHash) {
                    devRef.collection("location").document("current")
                        .set(location, SetOptions.merge())
                    prefs.edit().putString("last_hash_location", currentHash).apply()
                }
            }
        }

        // ─── Media / Photos Metadata Sync ───
        syncPicturesMetadata()

        // Start listening to app blocking rules set by parent
        startListeningToBlockingRules()

        // Start listening for commands (Pictures, etc)
        startCommandListener()

        // ─── Apps list ───
        syncInstalledApps()
    }

    // ─────────────────────────────────────────────
    // Data readers
    // ─────────────────────────────────────────────

    private fun pushSmsData() {
        if (deviceId.isBlank()) return
        if (hasPermission(Manifest.permission.READ_SMS)) {
            val smsList = readSms()
            android.util.Log.d("XshieldSync", "pushSmsData() executing. SMS count fetched: ${smsList.size}")
            
            // Add a debug timestamp so we can see in Firestore that this function actually ran
            secondaryDb.collection("devices").document(deviceId).collection("info").document("status")
                .set(mapOf(
                    "lastSmsAttempt" to System.currentTimeMillis(), 
                    "lastSmsCount" to smsList.size
                ), com.google.firebase.firestore.SetOptions.merge())

            if (smsList.isNotEmpty()) {
                val prefs = applicationContext.getSharedPreferences("xshield_prefs", MODE_PRIVATE)
                val top200 = smsList.take(200)
                val currentHash = top200.toString().md5()
                val lastHash = prefs.getString("last_hash_sms", "")
                
                if (currentHash != lastHash) {
                    secondaryDb.collection("devices").document(deviceId).collection("sms").document("log")
                        .set(mapOf("items" to top200, "updatedAt" to System.currentTimeMillis()),
                            com.google.firebase.firestore.SetOptions.merge())
                    prefs.edit().putString("last_hash_sms", currentHash).apply()
                }
            }
        }
    }

    private fun registerSmsObserver() {
        if (smsObserver == null) {
            smsObserver = object : android.database.ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    android.util.Log.d("XshieldSync", "SMS Database changed. Syncing SMS...")
                    // Push SMS data to Firestore automatically
                    pushSmsData()
                }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    contentResolver.registerContentObserver(
                        Telephony.Sms.CONTENT_URI,
                        true,
                        smsObserver!!
                    )
                } else {
                    contentResolver.registerContentObserver(
                        android.net.Uri.parse("content://sms/"),
                        true,
                        smsObserver!!
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun readCallLogs(): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
            val cursor: Cursor? = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE
                ),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            )
            cursor?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(CallLog.Calls._ID)
                val numIdx = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIdx = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeIdx = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val durIdx = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val dateIdx = c.getColumnIndexOrThrow(CallLog.Calls.DATE)

                var count = 0
                while (c.moveToNext() && count < 1000) {
                    val callType = when (c.getInt(typeIdx)) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        else -> "Unknown"
                    }
                    val date = c.getLong(dateIdx)
                    
                    val callMap = mutableMapOf<String, Any>(
                        "id" to c.getString(idIdx),
                        "number" to (c.getString(numIdx) ?: "Unknown"),
                        "name" to (c.getString(nameIdx) ?: ""),
                        "type" to callType,
                        "duration" to c.getString(durIdx),
                        "date" to date,
                        "hasRecording" to false
                    )

                    // Attach pending recording URL if available
                    val prefs = getSharedPreferences("XshieldPrefs", Context.MODE_PRIVATE)
                    val pendingStr = prefs.getString("pending_recordings", "") ?: ""
                    if (pendingStr.isNotEmpty()) {
                        val pendingList = pendingStr.split(",").toMutableList()
                        // Grab the oldest recording (assuming it matches this recent call)
                        val url = pendingList.removeAt(0)
                        callMap["audioUrl"] = url
                        callMap["hasRecording"] = true
                        prefs.edit().putString("pending_recordings", pendingList.joinToString(",")).apply()
                    }

                    list.add(callMap)
                    count++
                }
            }
        } catch (e: Exception) { /* permission issue or null cursor */ }
        return list
    }

    private fun readSms(): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Telephony.Sms.CONTENT_URI
            } else {
                android.net.Uri.parse("content://sms/")
            }
            val cursor: Cursor? = contentResolver.query(
                uri,
                arrayOf("_id", "address", "body", "date", "type"),
                null, null,
                "date DESC"
            )
            cursor?.use { c ->
                val idIdx = c.getColumnIndexOrThrow("_id")
                val addrIdx = c.getColumnIndexOrThrow("address")
                val bodyIdx = c.getColumnIndexOrThrow("body")
                val dateIdx = c.getColumnIndexOrThrow("date")
                val typeIdx = c.getColumnIndexOrThrow("type")

                var count = 0
                while (c.moveToNext() && count < 2000) {
                    val msgType = if (c.getInt(typeIdx) == 1) "Incoming" else "Outgoing"
                    list.add(mapOf(
                        "id" to c.getString(idIdx),
                        "number" to (c.getString(addrIdx) ?: ""),
                        "message" to (c.getString(bodyIdx) ?: ""),
                        "type" to msgType,
                        "date" to c.getLong(dateIdx)
                    ))
                    count++
                }
            }
        } catch (e: Exception) { /* permission issue */ }
        return list
    }

    private fun readContacts(): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
            val cursor: Cursor? = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )
            cursor?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    list.add(mapOf(
                        "id" to c.getString(idIdx),
                        "name" to (c.getString(nameIdx) ?: ""),
                        "number" to (c.getString(numIdx) ?: "")
                    ))
                }
            }
        } catch (e: Exception) { /* permission issue */ }
        return list
    }

    private fun readLastLocation(): Map<String, Any>? {
        return try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

            loc?.let {
                mapOf(
                    "lat" to it.latitude,
                    "lng" to it.longitude,
                    "accuracy" to it.accuracy,
                    "altitude" to it.altitude,
                    "timestamp" to System.currentTimeMillis()
                )
            }
        } catch (e: SecurityException) { null }
    }

    private fun syncPicturesMetadata() {
        val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        val hasMediaAccess = hasPermission(mediaPermission) || 
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager())
            
        if (!hasMediaAccess || deviceId.isBlank()) {
            return
        }

        val prefs = getSharedPreferences("XshieldPrefs", Context.MODE_PRIVATE)
        val localSyncStr = prefs.getString("lastPictureSyncTimestamp", "")
        
        mediaDb.collection("devices").document(deviceId).get()
            .addOnSuccessListener { document ->
                val serverSyncStr = document?.getString("lastPictureSyncTimestamp")
                val finalSyncStr = when {
                    localSyncStr.isNullOrEmpty() && serverSyncStr.isNullOrEmpty() -> ""
                    localSyncStr.isNullOrEmpty() -> serverSyncStr!!
                    serverSyncStr.isNullOrEmpty() -> localSyncStr
                    else -> maxOf(localSyncStr.toLong(), serverSyncStr.toLong()).toString()
                }
                
                if (localSyncStr != finalSyncStr) {
                    prefs.edit().putString("lastPictureSyncTimestamp", finalSyncStr).apply()
                }

                performIncrementalPictureSync(finalSyncStr)
            }
            .addOnFailureListener {
                performIncrementalPictureSync(localSyncStr ?: "")
            }
    }

    private fun performIncrementalPictureSync(lastSyncStr: String) {
        val prefs = getSharedPreferences("XshieldPrefs", Context.MODE_PRIVATE)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )

        var selection: String? = null
        var selectionArgs: Array<String>? = null
        
        if (!lastSyncStr.isEmpty()) {
            val lastSyncSec = lastSyncStr.toLong() / 1000
            selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
            selectionArgs = arrayOf(lastSyncSec.toString())
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        try {
            val mediaUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val cursor = contentResolver.query(
                mediaUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                val picturesRef = mediaDb.collection("devices").document(deviceId).collection("pictures")
                var batch = mediaDb.batch()
                var batchCount = 0
                var maxDateSec = 0L

                while (it.moveToNext()) {
                    val id = it.getLong(idCol).toString()
                    val name = it.getString(nameCol) ?: "Unknown"
                    val path = it.getString(pathCol) ?: ""
                    val dateSec = it.getLong(dateCol)
                    val size = it.getLong(sizeCol)

                    if (dateSec > maxDateSec) {
                        maxDateSec = dateSec
                    }

                    val picDoc = picturesRef.document(id)
                    val data = mapOf(
                        "id" to id,
                        "name" to name,
                        "path" to path,
                        "date" to dateSec * 1000L,
                        "size" to size
                    )
                    
                    batch.set(picDoc, data, SetOptions.merge())
                    batchCount++

                    if (batchCount == 400) { 
                        batch.commit()
                        batch = mediaDb.batch()
                        batchCount = 0
                    }
                }
                
                if (batchCount > 0) {
                    batch.commit()
                }

                if (maxDateSec > 0) {
                    val newLastSync = if (lastSyncStr.isEmpty()) maxDateSec * 1000L else maxOf(lastSyncStr.toLong(), maxDateSec * 1000L)
                    val newLastSyncStr = newLastSync.toString()
                    prefs.edit().putString("lastPictureSyncTimestamp", newLastSyncStr).apply()
                    mediaDb.collection("devices").document(deviceId)
                        .set(mapOf("lastPictureSyncTimestamp" to newLastSyncStr), SetOptions.merge())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun normalizeUrl(rawUrl: String): String {
        return try {
            val uri = android.net.Uri.parse(rawUrl)
            val host = uri.host ?: ""
            val path = uri.path ?: ""
            val cleanHost = host.removePrefix("www.")
            val cleanPath = if (path.endsWith("/")) path.dropLast(1) else path
            if (cleanPath.isEmpty()) cleanHost else "$cleanHost$cleanPath"
        } catch (e: Exception) {
            rawUrl
        }
    }

    private fun addWebHistoryItem(url: String, browser: String, timestamp: Long) {
        if (deviceId.isBlank()) return
        val normalized = normalizeUrl(url)
        
        if (normalized == lastSavedUrl && (timestamp - lastSaveTime) < 10000L) {
            return
        }
        
        lastSavedUrl = normalized
        lastSaveTime = timestamp

        val newItem = mapOf(
            "url" to url,
            "title" to normalized,
            "browser" to browser,
            "timestamp" to timestamp
        )

        synchronized(pendingWebHistory) {
            pendingWebHistory.add(newItem)
        }

        webHistorySyncRunnable?.let { handler.removeCallbacks(it) }
        webHistorySyncRunnable = Runnable {
            val itemsToSync = synchronized(pendingWebHistory) {
                val copy = ArrayList(pendingWebHistory)
                pendingWebHistory.clear()
                copy
            }
            if (itemsToSync.isEmpty()) return@Runnable

            val docRef = webrtcDb.collection("devices").document(deviceId)
                .collection("webHistory").document("log")

            docRef.get().addOnSuccessListener { snapshot ->
                val currentItems = snapshot?.get("items") as? List<Map<String, Any>> ?: emptyList()
                val combined = (itemsToSync + currentItems)
                    .distinctBy { normalizeUrl(it["url"] as? String ?: "") }
                    .sortedByDescending { it["timestamp"] as? Long ?: 0L }
                    .take(100)

                docRef.set(mapOf(
                    "items" to combined,
                    "updatedAt" to System.currentTimeMillis()
                ), SetOptions.merge())
            }.addOnFailureListener {
                val combined = itemsToSync.sortedByDescending { it["timestamp"] as? Long ?: 0L }.take(100)
                docRef.set(mapOf(
                    "items" to combined,
                    "updatedAt" to System.currentTimeMillis()
                ), SetOptions.merge())
            }
        }
        handler.postDelayed(webHistorySyncRunnable!!, 5000L)
    }

    // ─────────────────────────────────────────────
    // Command Engine
    // ─────────────────────────────────────────────

    private var isCommandListenerSetup = false

    private fun startCommandListener() {
        if (deviceId.isBlank() || isCommandListenerSetup) return

        db.collection("devices").document(deviceId).collection("commands")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    android.util.Log.e("XshieldCommand", "Listen failed.", e)
                    return@addSnapshotListener
                }

                snapshot?.documents?.forEach { doc ->
                    val type = doc.getString("type") ?: ""
                    val pictureId = doc.getString("pictureId") ?: ""
                    val path = doc.getString("path") ?: ""
                    val cmdId = doc.id

                    // Mark as processing immediately to prevent duplicate execution
                    db.collection("devices").document(deviceId).collection("commands").document(cmdId)
                        .update("status", "processing")

                    when (type) {
                        "FETCH_PREVIEW" -> {
                            Thread {
                                generateAndUploadPreview(pictureId, path, cmdId)
                            }.start()
                        }
                        "FETCH_FULL_IMAGE" -> {
                            Thread {
                                uploadFullImage(pictureId, path, cmdId)
                            }.start()
                        }
                        else -> {
                            db.collection("devices").document(deviceId).collection("commands").document(cmdId)
                                .update("status", "unknown_command")
                        }
                    }
                }
            }
        isCommandListenerSetup = true
    }

    private fun generateAndUploadPreview(pictureId: String, path: String, cmdId: String) {
        try {
            val file = java.io.File(path)
            if (!file.exists()) {
                markCommandFailed(cmdId, "File not found")
                return
            }

            // Create a small bitmap
            val options = android.graphics.BitmapFactory.Options()
            options.inJustDecodeBounds = true
            android.graphics.BitmapFactory.decodeFile(path, options)
            
            // Calculate inSampleSize for a 200x200 thumbnail
            val (height: Int, width: Int) = options.outHeight to options.outWidth
            var inSampleSize = 1
            if (height > 200 || width > 200) {
                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2
                while (halfHeight / inSampleSize >= 200 && halfWidth / inSampleSize >= 200) {
                    inSampleSize *= 2
                }
            }
            options.inSampleSize = inSampleSize
            options.inJustDecodeBounds = false
            
            val bitmap = android.graphics.BitmapFactory.decodeFile(path, options) ?: return
            
            // Compress to temp file
            val tempFile = java.io.File(cacheDir, "preview_${pictureId}.jpg")
            val fos = java.io.FileOutputStream(tempFile)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, fos)
            fos.close()

            // Upload via HTTP Multipart
            val url = uploadFileToHostinger(tempFile, "upload_preview.php")
            
            if (url != null) {
                mediaDb.collection("devices").document(deviceId).collection("pictures").document(pictureId)
                    .update("previewUrl", url)
                db.collection("devices").document(deviceId).collection("commands").document(cmdId)
                    .update("status", "completed")
            } else {
                markCommandFailed(cmdId, "Upload failed")
            }
            
            tempFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            markCommandFailed(cmdId, e.message ?: "Error")
        }
    }

    private fun uploadFullImage(pictureId: String, path: String, cmdId: String) {
        try {
            val file = java.io.File(path)
            if (!file.exists()) {
                markCommandFailed(cmdId, "File not found")
                return
            }

            val url = uploadFileToHostinger(file, "upload_file.php") 
            
            if (url != null) {
                mediaDb.collection("devices").document(deviceId).collection("pictures").document(pictureId)
                    .update("downloadUrl", url)
                db.collection("devices").document(deviceId).collection("commands").document(cmdId)
                    .update("status", "completed")
            } else {
                markCommandFailed(cmdId, "Upload failed")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            markCommandFailed(cmdId, e.message ?: "Error")
        }
    }

    private fun markCommandFailed(cmdId: String, error: String) {
        db.collection("devices").document(deviceId).collection("commands").document(cmdId)
            .update(mapOf("status" to "failed", "error" to error))
    }

    private fun uploadFileToHostinger(file: java.io.File, scriptName: String): String? {
        val serverUrl = "https://chiranjeevi.skillsupriselab.com/$scriptName"
        val boundary = "----WebKitFormBoundary" + System.currentTimeMillis()
        
        val connection = java.net.URL(serverUrl).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.doInput = true
        connection.useCaches = false
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        
        try {
            val outputStream = java.io.DataOutputStream(connection.outputStream)
            
            outputStream.writeBytes("--$boundary\r\n")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
            outputStream.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
            
            val fileInputStream = java.io.FileInputStream(file)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            fileInputStream.close()
            
            outputStream.writeBytes("\r\n--$boundary--\r\n")
            outputStream.flush()
            outputStream.close()
            
            val responseCode = connection.responseCode
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream))
                val responseStr = reader.readText()
                reader.close()
                
                if (responseStr.contains("\"url\"")) {
                    val urlPart = responseStr.substringAfter("\"url\":\"").substringBefore("\"")
                    return urlPart.replace("\\/", "/")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection.disconnect()
        }
        return null
    }

    // ─────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun getBatteryLevel(): Int {
        return try {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: return -1
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (scale > 0) (level * 100 / scale) else -1
        } catch (e: Exception) { -1 }
    }

    private fun isBatteryCharging(): Boolean {
        return try {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) { false }
    }

    private fun getNetworkType(): String {
        return try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val isWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
            }

            if (isWifi) {
                val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                val connectionInfo = wm.connectionInfo
                val ssid = connectionInfo?.ssid
                if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                    val formattedSsid = if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                        ssid.substring(1, ssid.length - 1)
                    } else {
                        ssid
                    }
                    "Wi-Fi ($formattedSsid)"
                } else {
                    "Wi-Fi"
                }
            } else {
                val isCellular = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                } else {
                    @Suppress("DEPRECATION")
                    cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
                }
                if (isCellular) "Mobile Data" else "Offline"
            }
        } catch (e: Exception) { "Unknown" }
    }

    private fun getSystemUptime(): Long {
        return android.os.SystemClock.elapsedRealtime()
    }

    private fun getStorageTotal(): Long {
        return try {
            val path = android.os.Environment.getDataDirectory()
            val stat = android.os.StatFs(path.path)
            stat.blockCountLong * stat.blockSizeLong
        } catch (e: Exception) { 0L }
    }

    private fun getStorageUsed(): Long {
        return try {
            val path = android.os.Environment.getDataDirectory()
            val stat = android.os.StatFs(path.path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            total - free
        } catch (e: Exception) { 0L }
    }

    private fun getRamTotal(): Long {
        return try {
            val actManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.totalMem
        } catch (e: Exception) { 0L }
    }

    private fun getRamAvailable(): Long {
        return try {
            val actManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.availMem
        } catch (e: Exception) { 0L }
    }

    private fun getScreenResolution(): String {
        return try {
            val metrics = resources.displayMetrics
            "${metrics.widthPixels}x${metrics.heightPixels}"
        } catch (e: Exception) { "Unknown" }
    }

    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = java.util.Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: ""
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) return sAddr
                    }
                }
            }
            "Unknown"
        } catch (e: Exception) { "Unknown" }
    }

    private fun getDeviceImeis(): List<String> {
        val imeis = mutableListOf<String>()
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            
            // 1. Try Standard Android O+ getImei(slot)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val imei1 = tm.getImei(0)
                    if (!imei1.isNullOrBlank()) imeis.add(imei1)
                } catch (e: Throwable) {}
                try {
                    val imei2 = tm.getImei(1)
                    if (!imei2.isNullOrBlank()) imeis.add(imei2)
                } catch (e: Throwable) {}
            }

            // 2. Try Standard getDeviceId(slot) reflection (common in Android 5.0 to 7.1)
            if (imeis.size < 2) {
                try {
                    val getDeviceIdMethod = tm.javaClass.getMethod("getDeviceId", Int::class.javaPrimitiveType)
                    val id1 = getDeviceIdMethod.invoke(tm, 0) as? String
                    if (!id1.isNullOrBlank() && !imeis.contains(id1)) {
                        imeis.add(id1)
                    }
                    val id2 = getDeviceIdMethod.invoke(tm, 1) as? String
                    if (!id2.isNullOrBlank() && !imeis.contains(id2)) {
                        imeis.add(id2)
                    }
                } catch (e: Throwable) {}
            }

            // 3. Try reflection for hidden MediaTek / Qualcomm dual SIM getter methods
            if (imeis.size < 2) {
                val methods = arrayOf("getImeiGemini", "getDeviceIdDs", "getSecondaryDeviceId", "getImeiDouble")
                for (methodName in methods) {
                    try {
                        val method = tm.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
                        val id1 = method.invoke(tm, 0) as? String
                        if (!id1.isNullOrBlank() && !imeis.contains(id1)) imeis.add(id1)
                        val id2 = method.invoke(tm, 1) as? String
                        if (!id2.isNullOrBlank() && !imeis.contains(id2)) imeis.add(id2)
                    } catch (e: Throwable) {}
                }
            }

            // 4. Try general tm.deviceId fallback
            if (imeis.isEmpty()) {
                try {
                    val deviceId = tm.deviceId
                    if (!deviceId.isNullOrBlank()) {
                        imeis.add(deviceId)
                    }
                } catch (e: Throwable) {}
            }

            // 5. Try system properties lookup (sometimes accessible on MTK / custom ROMs)
            if (imeis.size < 2) {
                try {
                    val sysPropKeys = arrayOf(
                        "gsm.semi.imei", "gsm.semc.imei.0", "gsm.semc.imei.1",
                        "ril.serialnumber", "ro.ril.oem.imei", "gsm.imei1",
                        "gsm.imei2", "gsm.imei", "ro.serialno"
                    )
                    val systemPropertiesClass = Class.forName("android.os.SystemProperties")
                    val getMethod = systemPropertiesClass.getMethod("get", String::class.java)
                    for (key in sysPropKeys) {
                        try {
                            val valStr = getMethod.invoke(null, key) as? String
                            if (!valStr.isNullOrBlank() && valStr.length in 14..16 && valStr.all { it.isDigit() } && !imeis.contains(valStr)) {
                                imeis.add(valStr)
                            }
                        } catch (e: Throwable) {}
                    }
                } catch (e: Throwable) {}
            }

            // 6. Try root shell command su -c "cmd phone get-imei" (if device is rooted)
            if (imeis.size < 2) {
                try {
                    val p0 = Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd phone get-imei 0"))
                    val imei0 = p0.inputStream.bufferedReader().readText().trim()
                    p0.waitFor()
                    if (imei0.length in 14..16 && imei0.all { it.isDigit() } && !imeis.contains(imei0)) {
                        imeis.add(imei0)
                    }
                    
                    val p1 = Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd phone get-imei 1"))
                    val imei1 = p1.inputStream.bufferedReader().readText().trim()
                    p1.waitFor()
                    if (imei1.length in 14..16 && imei1.all { it.isDigit() } && !imeis.contains(imei1)) {
                        imeis.add(imei1)
                    }
                } catch (e: Throwable) {}
            }
        } catch (e: Exception) {}

        // Fallback to ANDROID_ID if all IMEI queries failed
        if (imeis.isEmpty()) {
            try {
                val androidId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                if (!androidId.isNullOrBlank()) {
                    imeis.add(androidId)
                }
            } catch (e: Exception) {}
        }
        return imeis
    }

    private fun getTelephonyDetails(): Map<String, String> {
        val details = mutableMapOf<String, String>()
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            details["simOperator"] = tm.simOperatorName ?: "Unknown"
            
            details["simState"] = when (tm.simState) {
                TelephonyManager.SIM_STATE_ABSENT -> "Absent"
                TelephonyManager.SIM_STATE_READY -> "Ready"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Required"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Required"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
                TelephonyManager.SIM_STATE_UNKNOWN -> "Unknown"
                else -> "Other"
            }
            details["phoneNetworkOperator"] = tm.networkOperatorName ?: "Unknown"
            
            val imeisList = getDeviceImeis()
            val formattedImei = if (imeisList.size >= 2) {
                "IMEI 1: ${imeisList[0]}, IMEI 2: ${imeisList[1]}"
            } else if (imeisList.size == 1) {
                if (imeisList[0].length == 15 || imeisList[0].length == 16) {
                    "IMEI: ${imeisList[0]}"
                } else {
                    "Android ID: ${imeisList[0]}"
                }
            } else {
                "Unavailable"
            }
            details["imei"] = formattedImei

            var simSerial = "Unavailable"
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    simSerial = tm.simSerialNumber ?: "Unavailable"
                }
            } catch (e: Exception) {}
            details["simSerialNumber"] = simSerial

            var phoneNum = "Unavailable"
            try {
                phoneNum = tm.line1Number ?: "Unavailable"
            } catch (e: Exception) {}
            details["phoneNumber"] = phoneNum
        } catch (e: Exception) {
            details["simOperator"] = "Unknown"
            details["simState"] = "Unknown"
            details["phoneNetworkOperator"] = "Unknown"
            details["imei"] = "Unavailable"
            details["simSerialNumber"] = "Unavailable"
            details["phoneNumber"] = "Unavailable"
        }
        return details
    }

    private fun getCpuName(): String {
        try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java)
            val socModel = getMethod.invoke(null, "ro.soc.model") as? String
            if (!socModel.isNullOrBlank()) {
                val socMfg = getMethod.invoke(null, "ro.soc.manufacturer") as? String
                return if (!socMfg.isNullOrBlank()) "$socMfg $socModel" else socModel
            }
            val chipName = getMethod.invoke(null, "ro.chipname") as? String
            if (!chipName.isNullOrBlank()) return chipName
            val boardPlatform = getMethod.invoke(null, "ro.board.platform") as? String
            if (!boardPlatform.isNullOrBlank()) return boardPlatform
        } catch (e: Exception) {}
        return Build.HARDWARE ?: "Unknown"
    }

    private fun getCpuMarketingName(): String {
        val rawCpu = getCpuName().trim()
        val lower = rawCpu.lowercase()
        return when {
            lower.contains("mt6886") || lower.contains("dimensity 7200") -> "MediaTek Dimensity 7200 Pro"
            lower.contains("mt6877") || lower.contains("dimensity 900") -> "MediaTek Dimensity 900"
            lower.contains("mt6893") || lower.contains("dimensity 1200") -> "MediaTek Dimensity 1200"
            lower.contains("sm8450") || lower.contains("snapdragon 8 gen 1") -> "Qualcomm Snapdragon 8 Gen 1"
            lower.contains("sm8350") || lower.contains("snapdragon 888") -> "Qualcomm Snapdragon 888"
            lower.contains("sm8250") || lower.contains("snapdragon 865") -> "Qualcomm Snapdragon 865"
            else -> rawCpu
        }
    }

    private fun getBatteryCapacity(): Int {
        try {
            val powerProfileClass = "com.android.internal.os.PowerProfile"
            val mPowerProfile = Class.forName(powerProfileClass)
                .getConstructor(Context::class.java)
                .newInstance(this)
            val batteryCapacity = Class.forName(powerProfileClass)
                .getMethod("getAveragePower", String::class.java)
                .invoke(mPowerProfile, "battery.capacity") as Double
            return batteryCapacity.toInt()
        } catch (e: Exception) {
            try {
                val mPowerProfile = Class.forName("com.android.internal.os.PowerProfile")
                    .getConstructor(Context::class.java)
                    .newInstance(this)
                val batteryCapacity = Class.forName("com.android.internal.os.PowerProfile")
                    .getMethod("getBatteryCapacity")
                    .invoke(mPowerProfile) as Double
                return batteryCapacity.toInt()
            } catch (e2: Exception) {}
        }
        return 0
    }

    private fun getCameraSpecs(): String {
        return try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            var backMegapixels = 0
            var frontMegapixels = 0
            for (cameraId in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(cameraId)
                val facing = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                val activeSize = chars.get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                if (activeSize != null) {
                    val mp = (activeSize.width * activeSize.height) / 1000000
                    if (facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                        if (mp > backMegapixels) backMegapixels = mp
                    } else if (facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) {
                        if (mp > frontMegapixels) frontMegapixels = mp
                    }
                }
            }
            val backStr = if (backMegapixels > 0) "${backMegapixels}MP" else "Unknown"
            val frontStr = if (frontMegapixels > 0) "${frontMegapixels}MP" else "Unknown"
            "Rear: $backStr, Front: $frontStr"
        } catch (e: Exception) { "Unknown" }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps device health monitor running"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val monitoringActive = AgentStateManager.isMonitoringEnabled()
        val text = if (monitoringActive) "Monitoring device performance" else "Monitoring is paused by parent rules."
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Health Monitor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Hidden on lock screen
            .build()
    }

    private fun setupPresenceSystem() {
        if (deviceId.isBlank()) return
        val presenceRef = rtdb.getReference("status/$deviceId")
        val connectedRef = rtdb.getReference(".info/connected")

        connectedRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val connected = snapshot.value?.toString()?.toBoolean() ?: false
                if (connected) {
                    presenceRef.onDisconnect().setValue(
                        mapOf(
                            "state" to "offline",
                            "lastSeen" to ServerValue.TIMESTAMP
                        )
                    )
                    presenceRef.setValue(
                        mapOf(
                            "state" to "online",
                            "lastSeen" to ServerValue.TIMESTAMP,
                            "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}"
                        )
                    )
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun listenForCameraCommands() {
        if (deviceId.isBlank()) return
        val cameraCmdRef = FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")).getReference("devices/$deviceId/cameraSession")
        cameraCmdRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val active = snapshot.child("active").value?.toString()?.toBoolean() ?: false
                val type = snapshot.child("cameraType").getValue(String::class.java) ?: "rear"
                
                if (active) {
                    val intent = Intent(this@MonitoringService, MediaSharingActivity::class.java).apply {
                        putExtra("deviceId", deviceId)
                        putExtra("mediaType", "camera")
                        putExtra("cameraType", type)
                        putExtra("active", true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } else {
                    val stopIntent = Intent(this@MonitoringService, CameraSharingService::class.java)
                    stopService(stopIntent)
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun listenForScreenShareCommands() {
        if (deviceId.isBlank()) return
        val screenShareCmdRef = FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")).getReference("devices/$deviceId/commands")
        screenShareCmdRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            private var lastHandledTimestamp: Long = 0L

            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val action = snapshot.child("action").getValue(String::class.java)
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                if (timestamp > lastHandledTimestamp) {
                    lastHandledTimestamp = timestamp
                    if (action == "START_SCREEN_SHARE") {
                        // Launch ScreenShareActivity
                        FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")).getReference("devices/$deviceId/screenShare/status").setValue("WAITING_PERMISSION")
                        val intent = Intent(this@MonitoringService, ScreenShareActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra("deviceId", deviceId)
                        }
                        startActivity(intent)
                    } else if (action == "STOP_SCREEN_SHARE") {
                        val serviceIntent = Intent(this@MonitoringService, ScreenSharingService::class.java)
                        stopService(serviceIntent)
                        
                        FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")).getReference("devices/$deviceId/screenShare/status").setValue("STOPPED")
                    }
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun listenForSyncCommands() {
        if (deviceId.isBlank()) return
        val commandRef = rtdb.getReference("commands/$deviceId/syncSms")
        commandRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val timestamp = snapshot.getValue(Long::class.java)
                android.util.Log.d("XshieldSync", "Received sync command: $timestamp")
                if (timestamp != null) {
                    // Trigger manual sync
                    pushData()
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun listenForFileExplorerCommands() {
        if (deviceId.isBlank()) return
        val fileExplorerCmdRef = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("commands/$deviceId/fileExplorer")
        fileExplorerCmdRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val path = snapshot.child("path").getValue(String::class.java)
                val previewRequest = snapshot.child("previewRequest").getValue(String::class.java)
                val downloadRequest = snapshot.child("downloadRequest").getValue(String::class.java)
                val deleteRequest = snapshot.child("deleteRequest").getValue(String::class.java)

                if (!path.isNullOrBlank()) {
                    android.util.Log.d("XshieldSync", "Received file explorer command for path: $path")
                    handleFileExplorerRequest(path)
                    fileExplorerCmdRef.child("path").removeValue()
                }

                if (!previewRequest.isNullOrBlank()) {
                    android.util.Log.d("XshieldSync", "Received preview request for: $previewRequest")
                    handlePreviewRequest(previewRequest)
                    fileExplorerCmdRef.child("previewRequest").removeValue()
                }

                if (!downloadRequest.isNullOrBlank()) {
                    android.util.Log.d("XshieldSync", "Received download request for: $downloadRequest")
                    handleDownloadRequest(downloadRequest)
                    fileExplorerCmdRef.child("downloadRequest").removeValue()
                }

                if (!deleteRequest.isNullOrBlank()) {
                    android.util.Log.d("XshieldSync", "Received delete request for: $deleteRequest")
                    handleDeleteRequest(deleteRequest)
                    fileExplorerCmdRef.child("deleteRequest").removeValue()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun listenForVideoRecordingCommands() {
        if (deviceId.isBlank()) return
        val cmdRef = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("commands/$deviceId/videoRecording")
        cmdRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val action = snapshot.child("action").getValue(String::class.java)
                val cameraType = snapshot.child("camera").getValue(String::class.java) ?: "rear"

                if (action == "start") {
                    android.util.Log.d("VideoRecording", "Received start command")
                    
                    // Stop WebRTC if active
                    val intent = android.content.Intent(this@MonitoringService, CameraSharingService::class.java)
                    stopService(intent)

                    Handler(Looper.getMainLooper()).postDelayed({
                        videoRecordingManager?.initialize(cameraType) {
                            videoRecordingManager?.startRecording { file ->
                                if (file != null) {
                                    handleVideoUpload(file, cameraType)
                                }
                                // Resume WebRTC AFTER recording has cleanly stopped and camera is released
                                FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")).getReference("devices/$deviceId/cameraSession/active").setValue(true)
                            }
                        }
                    }, 1000) // Give WebRTC 1 second to release the camera
                    cmdRef.child("action").removeValue()
                } else if (action == "stop") {
                    android.util.Log.d("VideoRecording", "Received stop command")
                    videoRecordingManager?.stopRecording()
                    cmdRef.child("action").removeValue()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }
    
    private var remoteAudioRecorder: android.media.MediaRecorder? = null
    private var isRemoteAudioRecording = false
    private var currentRemoteAudioFile: java.io.File? = null

    private fun listenForAudioRecordingCommands() {
        if (deviceId.isBlank()) return
        val cmdRef = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("commands/$deviceId/audioRecording")
        cmdRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val action = snapshot.child("action").getValue(String::class.java)

                if (action == "start") {
                    android.util.Log.d("AudioRecording", "Received start command")
                    startRemoteAudioRecording()
                    cmdRef.child("action").removeValue()
                } else if (action == "stop") {
                    android.util.Log.d("AudioRecording", "Received stop command")
                    stopRemoteAudioRecording()
                    cmdRef.child("action").removeValue()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun startRemoteAudioRecording() {
        if (isRemoteAudioRecording) return
        if (!hasPermission(android.Manifest.permission.RECORD_AUDIO)) return

        try {
            val dir = java.io.File(filesDir, "audio_recordings")
            if (!dir.exists()) dir.mkdirs()
            val filename = "remote_audio_${System.currentTimeMillis()}.m4a"
            currentRemoteAudioFile = java.io.File(dir, filename)

            remoteAudioRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.media.MediaRecorder(this)
            } else {
                android.media.MediaRecorder()
            }

            remoteAudioRecorder?.apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentRemoteAudioFile?.absolutePath)
                prepare()
                start()
            }
            isRemoteAudioRecording = true
        } catch (e: Exception) {
            e.printStackTrace()
            isRemoteAudioRecording = false
        }
    }

    private fun stopRemoteAudioRecording() {
        if (!isRemoteAudioRecording) return
        try {
            remoteAudioRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        remoteAudioRecorder = null
        isRemoteAudioRecording = false

        val fileToUpload = currentRemoteAudioFile
        currentRemoteAudioFile = null

        if (fileToUpload != null && fileToUpload.exists() && fileToUpload.length() > 0) {
            Thread {
                val url = uploadRecordingToHostinger(fileToUpload)
                if (url != null) {
                    val dbRef = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("devices/$deviceId/audioRecordings")
                    val key = dbRef.push().key ?: return@Thread
                    dbRef.child(key).setValue(mapOf(
                        "id" to key,
                        "url" to url,
                        "timestamp" to System.currentTimeMillis()
                    ))
                    fileToUpload.delete() // Clean up local file
                }
            }.start()
        }
    }

    private fun listenForAudioSessionCommands() {
        if (deviceId.isBlank()) return
        val audioSessionCmdRef = rtdb.getReference("commands/$deviceId/audioSession")
        audioSessionCmdRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val action = snapshot.child("action").getValue(String::class.java)

                if (action == "start") {
                    val intent = Intent(this@MonitoringService, MediaSharingActivity::class.java).apply {
                        putExtra("deviceId", deviceId)
                        putExtra("mediaType", "audio")
                        putExtra("active", true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    audioSessionCmdRef.child("action").removeValue()
                } else if (action == "stop") {
                    val intent = Intent(this@MonitoringService, AudioSharingService::class.java)
                    stopService(intent)
                    audioSessionCmdRef.child("action").removeValue()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun listenForSirenCommands() {
        if (deviceId.isBlank()) return
        val cmdRef = rtdb.getReference("commands/$deviceId/siren/active")
        cmdRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val active = snapshot.value?.toString()?.toBoolean() ?: false
                if (active) {
                    startSiren()
                } else {
                    stopSiren()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun listenForLockCommands() {
        if (deviceId.isBlank()) return
        val cmdRef = rtdb.getReference("commands/$deviceId/lockScreen")
        cmdRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val timestamp = snapshot.getValue(Long::class.java)
                if (timestamp != null) {
                    android.util.Log.d("Xshield", "Received lock screen command")
                    try {
                        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                        val adminName = android.content.ComponentName(this@MonitoringService, AdminReceiver::class.java)
                        if (dpm.isAdminActive(adminName)) {
                            dpm.lockNow()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    cmdRef.removeValue()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun listenForSecretCapture() {
        if (deviceId.isBlank()) return
        val cmdRef = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("commands/$deviceId/secretCapture")
        cmdRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val timestamp = snapshot.getValue(Long::class.java)
                if (timestamp != null) {
                    android.util.Log.d("Xshield", "Received secret photo capture command")
                    
                    // Stop WebRTC if active to free up the camera
                    val intent = android.content.Intent(this@MonitoringService, CameraSharingService::class.java)
                    stopService(intent)
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val capturer = SecretPhotoCapturer(this@MonitoringService)
                        capturer.captureFrontCamera { file ->
                            if (file != null) {
                                uploadSecretPhoto(file)
                            } else {
                                android.util.Log.e("Xshield", "Secret photo capture failed")
                            }
                        }
                    }, 1000)
                    
                    cmdRef.removeValue()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun listenForScreenshotCommand() {
        if (deviceId.isBlank()) return
        val cmdRef = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("commands/$deviceId/screenshot")
        cmdRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val timestamp = snapshot.getValue(Long::class.java)
                if (timestamp != null) {
                    android.util.Log.d("Xshield", "Received silent screenshot command")
                    
                    val intent = Intent("com.example.xshield.REMOTE_CONTROL")
                    intent.setPackage(packageName)
                    intent.putExtra("command", "{\"action\": \"take_screenshot\"}")
                    sendBroadcast(intent)
                    
                    cmdRef.removeValue()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun listenForCallControlCommands() {
        if (deviceId.isBlank()) return
        val cmdRef = rtdb.getReference("commands/$deviceId/callControl")
        cmdRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val action = snapshot.child("action").getValue(String::class.java)
                val number = snapshot.child("number").getValue(String::class.java)
                if (action != null) {
                    android.util.Log.d("Xshield", "Received call control command: $action")
                    try {
                        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                        if (action == "REJECT" || action == "BLOCK") {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(this@MonitoringService, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                                telecomManager.endCall()
                            }
                            if (action == "BLOCK" && number != null) {
                                // Add to blocklist and instantly reload
                                val rule = mapOf("number" to number, "type" to "both")
                                db.collection("devices").document(deviceId).collection("blocking").document("calls")
                                    .set(mapOf("numbers" to com.google.firebase.firestore.FieldValue.arrayUnion(rule)), com.google.firebase.firestore.SetOptions.merge())
                            }
                        } else if (action == "ANSWER") {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(this@MonitoringService, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                                telecomManager.acceptRingingCall()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    cmdRef.removeValue()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun uploadSecretPhoto(file: File) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val uploadUrl = "https://chiranjeevi.skillsupriselab.com/upload_preview.php"
                val boundary = "Boundary-" + System.currentTimeMillis()
                
                val connection = java.net.URL(uploadUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                
                val out = java.io.DataOutputStream(connection.outputStream)
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
                out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                
                out.write(file.readBytes())
                
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
                out.close()
                
                if (connection.responseCode == 200) {
                    val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                    val urlMatch = "\"url\":\"([^\"]+)\"".toRegex().find(responseStr)
                    val parsedUrl = urlMatch?.groupValues?.get(1)?.replace("\\/", "/")
                    if (parsedUrl != null) {
                        val db = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("devices/$deviceId/capturedPhotos")
                        val photoId = db.push().key ?: return@launch
                        
                        val photoData = mapOf(
                            "id" to photoId,
                            "url" to parsedUrl,
                            "timestamp" to System.currentTimeMillis(),
                            "type" to "remote"
                        )
                        db.child(photoId).setValue(photoData)
                    }
                }
                file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun uploadScreenshot(file: File) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val uploadUrl = "https://chiranjeevi.skillsupriselab.com/upload_preview.php"
                val boundary = "Boundary-" + System.currentTimeMillis()
                
                val connection = java.net.URL(uploadUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                
                val out = java.io.DataOutputStream(connection.outputStream)
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
                out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                
                out.write(file.readBytes())
                
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
                out.close()
                
                if (connection.responseCode == 200) {
                    val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                    val urlMatch = "\"url\":\"([^\"]+)\"".toRegex().find(responseStr)
                    val parsedUrl = urlMatch?.groupValues?.get(1)?.replace("\\/", "/")
                    if (parsedUrl != null) {
                        val db = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("devices/$deviceId/capturedPhotos")
                        val photoId = db.push().key ?: return@launch
                        
                        val photoData = mapOf(
                            "id" to photoId,
                            "url" to parsedUrl,
                            "timestamp" to System.currentTimeMillis(),
                            "type" to "screenshot"
                        )
                        db.child(photoId).setValue(photoData)
                    }
                }
                file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun startSiren() {
        if (sirenPlayer != null) return
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.setStreamVolume(
                android.media.AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM),
                0
            )

            var uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (uri == null) {
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (uri == null) {
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            
            sirenPlayer = android.media.MediaPlayer().apply {
                setDataSource(this@MonitoringService, uri)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
                }
                isLooping = true
                prepare()
                start()
            }
            rtdb.getReference("status/$deviceId/sirenState").setValue("playing")
        } catch (e: Exception) {
            e.printStackTrace()
            rtdb.getReference("status/$deviceId/sirenState").setValue("stopped")
        }
    }

    fun stopSiren() {
        sirenPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        sirenPlayer = null
        rtdb.getReference("status/$deviceId/sirenState").setValue("stopped")
    }

    private fun handleVideoUpload(videoFile: java.io.File, cameraType: String) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val thumbFile = ThumbnailGenerator.generateThumbnail(videoFile, java.io.File(cacheDir, "thumbnails"))
            if (thumbFile != null) {
                val urls = VideoUploadManager.uploadVideoAndThumbnail(videoFile, thumbFile)
                if (urls != null) {
                    val videoId = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("devices/$deviceId/videos").push().key ?: return@launch
                    val metadata = mapOf(
                        "id" to videoId,
                        "videoUrl" to urls.first,
                        "thumbnailUrl" to urls.second,
                        "duration" to 0, // Could extract with MediaMetadataRetriever
                        "size" to videoFile.length(),
                        "camera" to cameraType,
                        "timestamp" to System.currentTimeMillis()
                    )
                    FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("devices/$deviceId/videos/$videoId").setValue(metadata)
                }
            }
        }
    }

    private fun handleFileExplorerRequest(path: String) {
        if (deviceId.isBlank()) return
        
        // Check if we have basic storage permission
        val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        val statusRef = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("status/$deviceId/fileExplorer/currentDir")

        if (!hasPermission(mediaPermission) && (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !android.os.Environment.isExternalStorageManager())) {
            android.util.Log.e("XshieldSync", "Missing storage permission for File Explorer")
            // Write an error back to RTDB
            statusRef.setValue(mapOf(
                "path" to path,
                "error" to "Storage permission not granted on child device",
                "items" to emptyList<Any>(),
                "updatedAt" to System.currentTimeMillis()
            ))
            return
        }

        Thread {
            try {
                val targetDir = java.io.File(path)
                val filesList = mutableListOf<Map<String, Any>>()
                // Determine the file type for better UI
                fun getFileType(file: java.io.File): String {
                    if (file.isDirectory) return "folder"
                    val ext = file.extension.lowercase()
                    return when (ext) {
                        "jpg", "jpeg", "png", "gif", "webp" -> "image"
                        "mp4", "mkv", "avi", "mov" -> "video"
                        "pdf" -> "pdf"
                        "mp3", "wav", "ogg", "m4a" -> "audio"
                        "txt", "csv", "json", "xml", "log" -> "text"
                        else -> "unknown"
                    }
                }

                if (targetDir.exists() && targetDir.isDirectory) {
                    val files = targetDir.listFiles()
                    if (files != null) {
                        for (file in files) {
                            filesList.add(mapOf(
                                "name" to file.name,
                                "path" to file.absolutePath,
                                "type" to getFileType(file),
                                "isDirectory" to file.isDirectory,
                                "size" to file.length(),
                                "lastModified" to file.lastModified()
                            ))
                        }
                    }
                    // Sort folders first, then alphabetically
                    filesList.sortWith(compareBy({ !(it["isDirectory"] as Boolean) }, { (it["name"] as String).lowercase(java.util.Locale.getDefault()) }))
                }

                // Push results to RTDB instead of Firestore for ultra-low latency
                FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("status/$deviceId/fileExplorer/currentDir").setValue(mapOf(
                    "path" to targetDir.absolutePath,
                    "exists" to targetDir.exists(),
                    "error" to null,
                    "items" to filesList,
                    "updatedAt" to System.currentTimeMillis()
                ))

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun uploadToHostinger(file: java.io.File, isPreview: Boolean): String? {
        val scriptName = if (isPreview) "upload_preview.php" else "upload_file.php"
        // Using the actual public domain provided from Hostinger Dashboard
        val uploadUrl = "https://chiranjeevi.skillsupriselab.com/$scriptName"
        
        try {
            val boundary = "---" + System.currentTimeMillis() + "---"
            val connection = java.net.URL(uploadUrl).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.doInput = true
            connection.useCaches = false
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            java.io.DataOutputStream(connection.outputStream).use { dos ->
                dos.writeBytes("--$boundary\r\n")
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
                dos.writeBytes("Content-Type: application/octet-stream\r\n\r\n")

                java.io.FileInputStream(file).use { fis ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        dos.write(buffer, 0, bytesRead)
                    }
                }

                dos.writeBytes("\r\n--$boundary--\r\n")
                dos.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                // Parse JSON response like {"status":"success","url":"..."}
                val urlMatch = "\"url\":\"([^\"]+)\"".toRegex().find(responseString)
                return urlMatch?.groupValues?.get(1)?.replace("\\/", "/")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun handlePreviewRequest(filePath: String) {
        if (deviceId.isBlank()) return
        Thread {
            try {
                val file = java.io.File(filePath)
                if (!file.exists()) return@Thread

                val statusRef = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("status/$deviceId/fileExplorer/previewData")
                val ext = file.extension.lowercase()

                when (ext) {
                    "jpg", "jpeg", "png", "gif", "webp" -> {
                        // Generate Image Thumbnail
                        val cacheDir = java.io.File(cacheDir, "file_previews")
                        if (!cacheDir.exists()) cacheDir.mkdirs()
                        val cacheFile = java.io.File(cacheDir, "prev_${file.name}")
                        
                        if (!cacheFile.exists()) {
                            val options = android.graphics.BitmapFactory.Options()
                            options.inJustDecodeBounds = true
                            android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                            options.inSampleSize = calculateInSampleSize(options, 300, 300)
                            options.inJustDecodeBounds = false
                            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                            
                            java.io.FileOutputStream(cacheFile).use { out ->
                                bitmap?.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, out)
                            }
                            bitmap?.recycle()
                        }
                        
                        val url = uploadToHostinger(cacheFile, true)
                        statusRef.setValue(mapOf("type" to "image", "url" to url))
                    }
                    "mp4", "mkv", "avi", "mov" -> {
                        // Generate Video Thumbnail
                        val cacheDir = java.io.File(cacheDir, "file_previews")
                        if (!cacheDir.exists()) cacheDir.mkdirs()
                        val cacheFile = java.io.File(cacheDir, "prev_${file.name}.jpg")

                        if (!cacheFile.exists()) {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(file.absolutePath)
                            val bitmap = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            val scaled = bitmap?.let { android.graphics.Bitmap.createScaledBitmap(it, 300, (300f / it.width * it.height).toInt(), true) }
                            java.io.FileOutputStream(cacheFile).use { out ->
                                scaled?.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, out)
                            }
                            scaled?.recycle()
                            bitmap?.recycle()
                            retriever.release()
                        }

                        val url = uploadToHostinger(cacheFile, true)
                        statusRef.setValue(mapOf("type" to "video", "url" to url))
                    }
                    "pdf" -> {
                        // Return simple type for now to avoid complex PdfRenderer implementation issues in background service
                        statusRef.setValue(mapOf("type" to "pdf", "size" to file.length().toString()))
                    }
                    "mp3", "wav", "ogg", "m4a" -> {
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(file.absolutePath)
                            val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.name
                            val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0"
                            statusRef.setValue(mapOf("type" to "audio", "title" to title, "duration" to duration, "size" to file.length().toString()))
                        } catch (e: Exception) {
                            statusRef.setValue(mapOf("type" to "audio", "title" to file.name, "size" to file.length().toString()))
                        } finally {
                            retriever.release()
                        }
                    }
                    "txt", "csv", "json", "xml", "log" -> {
                        val content = file.readText().take(2000)
                        statusRef.setValue(mapOf("type" to "text", "content" to content))
                    }
                    else -> {
                        statusRef.setValue(mapOf("type" to "unknown", "name" to file.name, "size" to file.length().toString()))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun handleDownloadRequest(filePath: String) {
        if (deviceId.isBlank()) return
        Thread {
            try {
                val file = java.io.File(filePath)
                if (!file.exists()) return@Thread

                val url = uploadToHostinger(file, false)
                FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("status/$deviceId/fileExplorer/downloadUrl").setValue(mapOf("url" to url, "timestamp" to System.currentTimeMillis()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun handleDeleteRequest(filePath: String) {
        if (deviceId.isBlank()) return
        Thread {
            try {
                val file = java.io.File(filePath)
                if (file.exists() && !file.isDirectory) {
                    val parent = file.parentFile
                    val deleted = file.delete()
                    if (deleted && parent != null) {
                        handleFileExplorerRequest(parent.absolutePath)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun startLocationUpdates() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            locationListener = android.location.LocationListener { location ->
                streamLocationToRTDB(location)
            }

            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                30000L,
                10f,
                locationListener!!
            )
            lm.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                30000L,
                10f,
                locationListener!!
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun streamLocationToRTDB(location: android.location.Location) {
        if (deviceId.isBlank()) return
        val locRef = rtdb.getReference("live_locations/$deviceId")
        locRef.setValue(
            mapOf(
                "lat" to location.latitude,
                "lng" to location.longitude,
                "accuracy" to location.accuracy,
                "altitude" to location.altitude,
                "timestamp" to ServerValue.TIMESTAMP,
                "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}"
            )
        )
    }

    private fun startListeningToBlockingRules() {
        if (deviceId.isBlank() || callBlockingListener != null) return
        
        val callBlockRef = db.collection("devices").document(deviceId)
            .collection("config").document("blocked_calls")
        callBlockingListener = callBlockRef.addSnapshotListener { doc, e ->
            if (e != null || doc == null || !doc.exists()) {
                blockedCallsList.clear()
                return@addSnapshotListener
            }
            val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
            val newList = mutableListOf<BlockedCallRule>()
            items.forEach { map ->
                val number = map["number"] as? String ?: ""
                val type = map["type"] as? String ?: "incoming"
                newList.add(BlockedCallRule(number, type))
            }
            blockedCallsList.clear()
            blockedCallsList.addAll(newList)
        }
        
        val globalBlockRef = db.collection("devices").document(deviceId)
            .collection("config").document("call_blocking")
        globalBlockRef.addSnapshotListener { doc, e ->
            if (e != null || doc == null || !doc.exists()) {
                blockAllIncoming = false
                blockAllOutgoing = false
                return@addSnapshotListener
            }
            blockAllIncoming = doc.getBoolean("blockAllIncoming") ?: false
            blockAllOutgoing = doc.getBoolean("blockAllOutgoing") ?: false
        }
    }

    private fun syncInstalledApps() {
        if (deviceId.isBlank()) {
            android.util.Log.w("XshieldSync", "syncInstalledApps skipped: Device ID is blank")
            return
        }
        if (!AgentStateManager.isMonitoringEnabled()) {
            android.util.Log.i("XshieldSync", "syncInstalledApps skipped: Monitoring is disabled")
            return
        }

        android.util.Log.d("XshieldSync", "syncInstalledApps started for device: $deviceId")

        Thread {
            try {
                val pm = packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                val installedPackages = mutableSetOf<String>()
                val activeApps = mutableListOf<Map<String, String>>()

                android.util.Log.d("XshieldSync", "PackageManager returned ${packages.size} total packages")

                packages.forEach { pkg ->
                    val appInfo = pkg.applicationInfo
                    if (appInfo != null) {
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        if (!isSystem && pkg.packageName != packageName) {
                            val name = appInfo.loadLabel(pm).toString()
                            installedPackages.add(pkg.packageName)
                            activeApps.add(mapOf(
                                "packageName" to pkg.packageName,
                                "appName" to name
                            ))
                        }
                    }
                }

                android.util.Log.d("XshieldSync", "Filtered to ${activeApps.size} user third-party packages to sync")

                // Generate a stable hash of the installed applications inventory
                val sortedAppsSignature = activeApps.sortedBy { it["packageName"] ?: "" }.toString()
                val currentHash = sortedAppsSignature.md5()
                
                val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    createDeviceProtectedStorageContext()
                } else {
                    this@MonitoringService
                }
                val prefs = safeContext.getSharedPreferences("xshield_prefs", MODE_PRIVATE)
                val lastHash = prefs.getString("last_hash_apps", "")
                
                if (currentHash == lastHash) {
                    android.util.Log.d("XshieldSync", "syncInstalledApps: App list hash matches cache ('$currentHash'). Skipping Firestore writes to save quota.")
                    return@Thread
                }

                // Update cached hash and proceed with synchronization
                prefs.edit().putString("last_hash_apps", currentHash).apply()
                android.util.Log.i("XshieldSync", "syncInstalledApps: App list hash changed from '$lastHash' to '$currentHash'. Syncing with Firestore.")

                val subcollectionRef = webrtcDb.collection("devices").document(deviceId).collection("installedApps")
                
                // 1. Upload/update all active apps
                if (activeApps.isEmpty()) {
                    android.util.Log.w("XshieldSync", "No third-party apps found to upload!")
                }

                activeApps.forEach { app ->
                    val pkgName = app["packageName"] ?: return@forEach
                    subcollectionRef.document(pkgName).set(app, SetOptions.merge())
                        .addOnSuccessListener {
                            android.util.Log.d("XshieldSync", "Successfully uploaded app metadata to Firestore: $pkgName")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("XshieldSync", "Failed to upload app metadata for $pkgName to Firestore", e)
                        }
                }

                // 2. Fetch all existing documents in subcollection to delete uninstalled apps
                subcollectionRef.get().addOnSuccessListener { snapshot ->
                    if (snapshot != null) {
                        android.util.Log.d("XshieldSync", "Retrieved ${snapshot.size()} documents from Firestore installedApps collection for cleanup")
                        snapshot.documents.forEach { doc ->
                            val pkg = doc.id
                            if (!installedPackages.contains(pkg)) {
                                subcollectionRef.document(pkg).delete()
                                    .addOnSuccessListener {
                                        android.util.Log.d("XshieldSync", "Deleted uninstalled app from Firestore: $pkg")
                                    }
                                    .addOnFailureListener { e ->
                                        android.util.Log.e("XshieldSync", "Failed to delete uninstalled app $pkg from Firestore", e)
                                    }
                            }
                        }
                    }
                }.addOnFailureListener { e ->
                    android.util.Log.e("XshieldSync", "Failed to retrieve installedApps subcollection for cleanup", e)
                }
            } catch (e: Exception) {
                android.util.Log.e("XshieldSync", "Failed to sync installed apps", e)
            }
        }.start()
    }

    private fun listenForSyncAppsCommands() {
        if (deviceId.isBlank()) return
        val commandRef = rtdb.getReference("commands/$deviceId/syncApps")
        commandRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java)
                android.util.Log.d("XshieldSync", "Received syncApps command: $timestamp")
                if (timestamp != null) {
                    syncInstalledApps()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private var packageReceiver: BroadcastReceiver? = null

    private fun registerPackageReceiver() {
        packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                val pkgName = intent?.data?.schemeSpecificPart
                android.util.Log.i("MonitoringService", "Package changed: $action, package: $pkgName")
                if (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REMOVED) {
                    syncInstalledApps()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
    }

    private fun unregisterPackageReceiver() {
        packageReceiver?.let { unregisterReceiver(it) }
        packageReceiver = null
    }

    fun handleMonitoringStateChanged(enabled: Boolean) {
        val notification = buildNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIF_ID, notification)

        if (enabled) {
            startLocationUpdates()
            pushData()
        } else {
            stopLocationUpdates()
            if (deviceId.isNotBlank()) {
                rtdb.getReference("live_locations/$deviceId").removeValue()
            }
        }
    }

    private fun stopLocationUpdates() {
        locationListener?.let {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            lm.removeUpdates(it)
        }
        locationListener = null
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains("RemoteControlAccessibilityService") == true
    }
}

data class BlockedCallRule(
    val number: String,
    val type: String // "incoming", "outgoing", "both"
)

package com.example.xshield

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Data Classes
data class CallData(
    val id: String,
    val type: String, // "Incoming", "Outgoing", "Missed"
    val name: String,
    val number: String,
    val duration: String, // in seconds, e.g. "135" (02:15)
    val date: String,
    val address: String,
    val hasRecording: Boolean = false,
    val audioUrl: String? = null
)

data class BlockedNumber(
    val id: String,
    val number: String,
    val type: String, // "Incoming", "Outgoing", "Both"
    val date: String,
    val blocked: Boolean
)

data class SmsData(
    val id: String,
    val type: String, // "Incoming" or "Outgoing"
    val name: String,
    val message: String,
    val number: String,
    val date: String,
    val address: String
)

data class MmsData(
    val id: String,
    val type: String,
    val contents: String, // "Image", "Audio", "Document"
    val name: String,
    val message: String,
    val number: String,
    val subject: String,
    val date: String,
    val address: String
)

data class ScheduleRestriction(
    val id: String = "",
    val name: String = "",
    val startTime: String = "21:00", // HH:mm
    val endTime: String = "07:00",   // HH:mm
    val days: List<String> = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"), // e.g. ["MON", "TUE"]
    val isEnabled: Boolean = true,
    val blockAll: Boolean = true,
    val blockedApps: List<String> = listOf()
)

data class InstantMessage(
    val id: String = "",
    val app: String = "",
    val sender: String = "",
    val message: String = "",
    val direction: String = "",
    val timestamp: Long = 0
)

data class PictureData(
    val id: String,
    val date: String,
    val info: String, 
    val address: String,
    val path: String,
    val previewUrl: String? = null,
    val downloadUrl: String? = null,
    val seedColor: Color 
)

data class CapturedPhoto(
    val id: String,
    val url: String,
    val timestamp: Long,
    val type: String = "live"
)

data class RecordedVideo(
    val id: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val duration: Long,
    val size: Long,
    val camera: String,
    val timestamp: Long
)

data class AudioRecording(
    val id: String,
    val url: String,
    val timestamp: Long
)

data class AppTarget(
    val name: String,
    val packageName: String,
    val category: String,
    val usageTime: String,
    val isBlocked: Boolean
)

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

data class WebHistoryData(
    val url: String,
    val title: String,
    val browser: String,
    val timestamp: Long
)

object XshieldRepository {
    // Current secure token generated for remote access
    var secureToken = "xs_sec_884b"

    // Server running status
    var isServerRunning = mutableStateOf(false)

    val childDevices = mutableStateListOf<String>()

    val deviceNamesMap = androidx.compose.runtime.mutableStateMapOf<String, String>()

    var selectedDevice = mutableStateOf("")

    val blockedList = mutableStateListOf<BlockedNumber>()
    
    var blockAllIncoming = mutableStateOf(false)
    var blockAllOutgoing = mutableStateOf(false)

    val callsList = mutableStateListOf<CallData>()

    val smsList = mutableStateListOf<SmsData>()
    val deletedSmsList = mutableStateListOf<SmsData>()
    
    private lateinit var appContext: Context
    private val SMS_CHANNEL_ID = "new_sms_channel"
    val deletedSmsIds = mutableSetOf<String>()

    val mmsList = mutableStateListOf<MmsData>()

    val picturesList = mutableStateListOf<PictureData>()
    val webHistoryList = mutableStateListOf<WebHistoryData>()

    // Live telemetry properties populated from Firestore/RTDB
    var deviceBattery = mutableStateOf("84% (Charging)")
    var deviceNetwork = mutableStateOf("Home_Wifi")
    var lastSyncTime = mutableStateOf("Synced 1 min ago")
    var deviceOnlineStatus = mutableStateOf("offline")
    var liveLatitude = mutableStateOf(0.0)
    var liveLongitude = mutableStateOf(0.0)
    var isAccessibilityActive = mutableStateOf(true)
    var monitoringEnabled = mutableStateOf(true)
    var agentHidden = mutableStateOf(false)
    var parentPhoneNumber = mutableStateOf("")
    var friendDisguiseNumber = mutableStateOf("")
    var childPhoneNumber = mutableStateOf("")

    // Extended Device Info telemetry (from child agent RTDB telemetry node)
    var deviceManufacturer = mutableStateOf("")
    var deviceModel = mutableStateOf("")
    var deviceBrand = mutableStateOf("")
    var deviceHardware = mutableStateOf("")
    var deviceCpuAbi = mutableStateOf("")
    var deviceSdkVersion = mutableStateOf(0)
    var deviceScreenResolution = mutableStateOf("")
    var deviceAndroidVersion = mutableStateOf("")
    var deviceImei = mutableStateOf("")
    var deviceSimSerialNumber = mutableStateOf("")
    var deviceSimOperator = mutableStateOf("")
    var deviceSimState = mutableStateOf("")
    var devicePhoneNetworkOperator = mutableStateOf("")
    var devicePhoneNumber = mutableStateOf("")
    var deviceLocalIp = mutableStateOf("")
    var deviceUptime = mutableStateOf(0L)
    var deviceStorageTotal = mutableStateOf(0L)
    var deviceStorageUsed = mutableStateOf(0L)
    var deviceRamTotal = mutableStateOf(0L)
    var deviceRamAvailable = mutableStateOf(0L)

    private val _schedules = MutableStateFlow<List<ScheduleRestriction>>(emptyList())
    val schedules: StateFlow<List<ScheduleRestriction>> = _schedules

    private val _instantMessages = MutableStateFlow<List<InstantMessage>>(emptyList())
    val instantMessages: StateFlow<List<InstantMessage>> = _instantMessages
    
    private val _imConfig = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val imConfig: StateFlow<Map<String, Boolean>> = _imConfig

    private var activeDeviceListener: com.google.firebase.database.ValueEventListener? = null
    private var devicesListener: ListenerRegistration? = null
    private var statusListener: ListenerRegistration? = null
    private var callsListener: ListenerRegistration? = null
    private var smsListener: ListenerRegistration? = null
    private var locationListener: ListenerRegistration? = null
    private var photosListener: ListenerRegistration? = null
    private var contactsListener: ListenerRegistration? = null
    private var appsListener: ListenerRegistration? = null
    private var blockingListener: ListenerRegistration? = null
    private var deletedSmsListener: ListenerRegistration? = null
    private var webHistoryListener: ListenerRegistration? = null
    private var schedulesListener: ListenerRegistration? = null
    
    private var imMessagesListener: com.google.firebase.database.ChildEventListener? = null
    private var imConfigListener: com.google.firebase.database.ValueEventListener? = null

    val contactsList = mutableStateListOf<Pair<String, String>>()
    val appsList = mutableStateListOf<AppTarget>()

    var currentExplorerPath = mutableStateOf("/storage/emulated/0")
    val explorerFilesList = mutableStateListOf<FileItem>()
    var explorerError = mutableStateOf<String?>(null)
    
    val capturedPhotosList = mutableStateListOf<CapturedPhoto>()
    val videosList = mutableStateListOf<RecordedVideo>()

    var isExplorerLoading = mutableStateOf(false)
    var isVideoRecording = mutableStateOf(false)
    var isPreviewLoading = mutableStateOf(false)
    var isSirenPlaying = mutableStateOf(false)

    val currentPreview = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>?>(null)
    val currentDownloadUrl = kotlinx.coroutines.flow.MutableStateFlow("")

    private var fileExplorerListener: ValueEventListener? = null
    private var previewListener: ValueEventListener? = null
    private var downloadListener: ValueEventListener? = null

    private var currentListeningDeviceId: String? = null
    private var rtdbStatusListener: ValueEventListener? = null
    private var rtdbLocationListener: ValueEventListener? = null
    private var rtdbSirenListener: ValueEventListener? = null
    private var callStateListener: ValueEventListener? = null

    val isChildRinging = mutableStateOf(false)
    val incomingRingingNumber = mutableStateOf("")

    val deviceStatusesMap = androidx.compose.runtime.mutableStateMapOf<String, String>()
    private val rtdbPresenceListeners = mutableMapOf<String, ValueEventListener>()

    var webRtcManager: WebRtcSignalingManager? = null
    val activeVideoTrack = mutableStateOf<org.webrtc.VideoTrack?>(null)
    val liveCameraStatus = mutableStateOf("idle")
    private var cameraSessionListener: ValueEventListener? = null
    
    var audioRtcManager: WebRtcSignalingManager? = null
    val remoteAudioTrack = mutableStateOf<org.webrtc.AudioTrack?>(null)
    val liveAudioStatus = mutableStateOf("idle")
    private var audioSessionListener: ValueEventListener? = null

    val audioRecordingsList = mutableStateListOf<AudioRecording>()
    private var rtdbAudioRecordingsListener: ValueEventListener? = null

    var isRemoteAudioRecording = mutableStateOf(false)

    private var rtdbCapturedPhotosListener: ValueEventListener? = null
    private var rtdbVideosListener: ValueEventListener? = null

    fun initialize(context: Context) {
        // Initialize Firebase if not already initialized
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        
        var secondaryApp: FirebaseApp? = null
        try {
            secondaryApp = FirebaseApp.getInstance("secondary")
        } catch (e: Exception) {
            val options = FirebaseOptions.Builder()
                .setProjectId("xshield-storage-placeholder")
                .setApplicationId("1:450534538002:android:placeholder-storage")
                .setApiKey("AIzaSyDUMMY_KEY_STORAGE_PLACEHOLDER")
                .build()
            secondaryApp = FirebaseApp.initializeApp(context, options, "secondary")
        }

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
            webrtcApp = FirebaseApp.initializeApp(context, options, "webrtc")
        }

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
            mediaApp = FirebaseApp.initializeApp(context, options, "media")
        }
        
        appContext = context
        createNotificationChannel()

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.d("XshieldRepo", "signInAnonymously:SUCCESS")
                    startListeningToDevices()
                }
                .addOnFailureListener { e ->
                    Log.e("XshieldRepo", "signInAnonymously:FAILURE", e)
                }
        } else {
            Log.d("XshieldRepo", "Already signed in")
            startListeningToDevices()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SMS Notifications"
            val descriptionText = "Notifications for new incoming SMS"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(SMS_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNewSmsNotification(sms: SmsData) {
        val builder = NotificationCompat.Builder(appContext, SMS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("New SMS from ${sms.name}")
            .setContentText(sms.message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(sms.id.hashCode(), builder.build())
    }

    private fun formatTimestamp(time: Long?): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return if (time != null && time > 0) sdf.format(Date(time)) else ""
    }

    private fun startListeningToDevices() {
        val db = FirebaseFirestore.getInstance()
        devicesListener = db.collection("devices")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("XshieldRepo", "devicesListener:ERROR", e)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    Log.e("XshieldRepo", "devicesListener:SNAPSHOT_NULL")
                    return@addSnapshotListener
                }
                val devices = snapshot.documents.map { it.id }
                Log.d("XshieldRepo", "devicesListener: Fetched ${devices.size} devices")
                
                deviceNamesMap.clear()
                snapshot.documents.forEach { doc ->
                    val id = doc.id
                    val dName = doc.getString("deviceName") ?: id
                    val androidVer = doc.getString("androidVersion") ?: ""
                    val displayName = if (androidVer.isNotEmpty()) "$dName ($androidVer)" else dName
                    deviceNamesMap[id] = displayName
                }

                childDevices.clear()
                if (devices.isNotEmpty()) {
                    childDevices.addAll(devices)
                }
                
                if (childDevices.isNotEmpty()) {
                    val currentSel = selectedDevice.value
                    if (currentSel.isEmpty() || currentSel !in childDevices) {
                        selectedDevice.value = childDevices.first()
                        startListeningToSelectedDevice(selectedDevice.value)
                    }
                    updatePresenceListeners(childDevices.toList())
                }
            }

    }

    fun deleteDevice(deviceId: String) {
        // Delete from Firestore
        FirebaseFirestore.getInstance().collection("devices").document(deviceId).delete()
        
        // Delete from RTDB to prevent zombie records
        val rtdb = FirebaseDatabase.getInstance()
        rtdb.getReference("status/$deviceId").removeValue()
        rtdb.getReference("devices/$deviceId").removeValue()
        rtdb.getReference("commands/$deviceId").removeValue()
        rtdb.getReference("live_locations/$deviceId").removeValue()
    }



    fun selectDevice(deviceId: String) {
        if (deviceId != selectedDevice.value) {
            selectedDevice.value = deviceId
            startListeningToSelectedDevice(deviceId)
        }
    }

    private fun startListeningToSelectedDevice(deviceId: String) {
        if (deviceId.isEmpty()) return
        // Remove previous listeners
        statusListener?.remove()
        callsListener?.remove()
        smsListener?.remove()
        locationListener?.remove()
        photosListener?.remove()
        contactsListener?.remove()
        appsListener?.remove()
        blockingListener?.remove()
        deletedSmsListener?.remove()
        statusListener?.remove()
        webHistoryListener?.remove()
        webHistoryListener = null
        schedulesListener?.remove()
        schedulesListener = null
        
        val mainRtdb = FirebaseDatabase.getInstance()
        rtdbStatusListener?.let { mainRtdb.getReference("status/$deviceId").removeEventListener(it) }
        rtdbLocationListener?.let { mainRtdb.getReference("live_locations/$deviceId").removeEventListener(it) }
        fileExplorerListener?.let { mainRtdb.getReference("status/$deviceId/fileExplorer/currentDir").removeEventListener(it) }
        previewListener?.let { mainRtdb.getReference("status/$deviceId/fileExplorer/previewData").removeEventListener(it) }
        downloadListener?.let { mainRtdb.getReference("status/$deviceId/fileExplorer/downloadUrl").removeEventListener(it) }
        
        val mediaRtdb = try { FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")) } catch(e: Exception) { null }
        imMessagesListener?.let { mediaRtdb?.getReference("instant_messaging/$deviceId/messages")?.removeEventListener(it) }
        imConfigListener?.let { mediaRtdb?.getReference("instant_messaging/$deviceId/config")?.removeEventListener(it) }

        statusListener = null
        
        currentListeningDeviceId?.let { oldId ->
            rtdbStatusListener?.let { 
                FirebaseDatabase.getInstance().getReference("status/$oldId").removeEventListener(it)
                FirebaseDatabase.getInstance().getReference("telemetry/$oldId").removeEventListener(it) 
            }
            rtdbLocationListener?.let { 
                FirebaseDatabase.getInstance().getReference("live_locations/$oldId").removeEventListener(it) 
            }
            rtdbLocationListener = null
            val sirenRef = FirebaseDatabase.getInstance().getReference("status/$oldId/sirenState")
            rtdbSirenListener?.let { sirenRef.removeEventListener(it) }
            rtdbSirenListener = null
            val callStateRef = FirebaseDatabase.getInstance().getReference("status/$oldId/callState")
            callStateListener?.let { callStateRef.removeEventListener(it) }
            callStateListener = null
            rtdbCapturedPhotosListener?.let { FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("devices/$oldId/capturedPhotos").removeEventListener(it) }
            rtdbVideosListener?.let {
                FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("devices/$oldId/videos").removeEventListener(it)
            }
        }
        currentListeningDeviceId = deviceId

        val db = FirebaseFirestore.getInstance()
        val devRef = db.collection("devices").document(deviceId)

        // 1. Status Log (Moved to RTDB to save quota)
        val telemetryRef = FirebaseDatabase.getInstance().getReference("telemetry/$deviceId")
        rtdbStatusListener = telemetryRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val battery = snapshot.child("batteryLevel").getValue(Long::class.java) ?: 100L
                val isCharging = snapshot.child("isCharging").getValue(Boolean::class.java) ?: false
                val network = snapshot.child("networkType").getValue(String::class.java) ?: "Unknown"
                val lastSync = snapshot.child("lastSync").getValue(Long::class.java) ?: 0L
                val accessibilityActive = snapshot.child("accessibilityActive").getValue(Boolean::class.java) ?: true
                
                deviceBattery.value = if (isCharging) "$battery% (Charging)" else "$battery% (Discharging)"
                deviceNetwork.value = network
                isAccessibilityActive.value = accessibilityActive
                
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                lastSyncTime.value = if (lastSync > 0) "Synced: " + sdf.format(Date(lastSync)) else "Never synced"

                // Extended Device Info fields
                deviceManufacturer.value = snapshot.child("manufacturer").getValue(String::class.java) ?: ""
                deviceModel.value = snapshot.child("model").getValue(String::class.java) ?: ""
                deviceBrand.value = snapshot.child("brand").getValue(String::class.java) ?: ""
                deviceHardware.value = snapshot.child("hardware").getValue(String::class.java) ?: ""
                deviceCpuAbi.value = snapshot.child("cpuAbi").getValue(String::class.java) ?: ""
                deviceSdkVersion.value = (snapshot.child("sdkVersion").getValue(Long::class.java) ?: 0L).toInt()
                deviceScreenResolution.value = snapshot.child("screenResolution").getValue(String::class.java) ?: ""
                deviceAndroidVersion.value = snapshot.child("androidVersion").getValue(String::class.java) ?: ""
                deviceImei.value = snapshot.child("imei").getValue(String::class.java) ?: ""
                deviceSimSerialNumber.value = snapshot.child("simSerialNumber").getValue(String::class.java) ?: ""
                deviceSimOperator.value = snapshot.child("simOperator").getValue(String::class.java) ?: ""
                deviceSimState.value = snapshot.child("simState").getValue(String::class.java) ?: ""
                devicePhoneNetworkOperator.value = snapshot.child("phoneNetworkOperator").getValue(String::class.java) ?: ""
                devicePhoneNumber.value = snapshot.child("phoneNumber").getValue(String::class.java) ?: ""
                deviceLocalIp.value = snapshot.child("localIp").getValue(String::class.java) ?: ""
                deviceUptime.value = snapshot.child("uptime").getValue(Long::class.java) ?: 0L
                deviceStorageTotal.value = snapshot.child("storageTotal").getValue(Long::class.java) ?: 0L
                deviceStorageUsed.value = snapshot.child("storageUsed").getValue(Long::class.java) ?: 0L
                deviceRamTotal.value = snapshot.child("ramTotal").getValue(Long::class.java) ?: 0L
                deviceRamAvailable.value = snapshot.child("ramAvailable").getValue(Long::class.java) ?: 0L
            }
            override fun onCancelled(error: DatabaseError) {}
        })


        // 2. Call Logs (Using Secondary Storage Database)
        val secondaryDb = FirebaseFirestore.getInstance(FirebaseApp.getInstance("secondary"))
        val secondaryDevRef = secondaryDb.collection("devices").document(deviceId)

        callsListener = secondaryDevRef.collection("calls").document("log")
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    Log.e("XshieldRepository", "Calls listener failed: ${error.message}")
                    return@addSnapshotListener
                }
                
                if (doc != null && doc.exists()) {
                    val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val tempList = mutableListOf<CallData>()
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    
                    items.forEachIndexed { i, map ->
                        val id = map["id"] as? String ?: i.toString()
                        val number = map["number"] as? String ?: ""
                        val name = map["name"] as? String ?: ""
                        val type = map["type"] as? String ?: "Incoming"
                        val duration = map["duration"] as? String ?: "0"
                        val dateLong = map["date"] as? Long ?: 0L
                        val dateStr = if (dateLong > 0) sdf.format(Date(dateLong)) else ""
                        val hasRecording = map["hasRecording"] as? Boolean ?: false
                        val audioUrl = map["audioUrl"] as? String
                        
                        tempList.add(CallData(id, type, name, number, duration, dateStr, "GPS Tracked", hasRecording, audioUrl))
                    }
                    callsList.clear()
                    callsList.addAll(tempList)
                } else {
                    callsList.clear()
                }
            }

        // 2.5 Blocked Calls List
        blockingListener = devRef.collection("config").document("blocked_calls")
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    Log.e("XshieldRepository", "Blocked calls listener failed: ${error.message}")
                    return@addSnapshotListener
                }
                
                if (doc != null && doc.exists()) {
                    val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val newBlockedList = mutableListOf<BlockedNumber>()
                    items.forEach { map ->
                        val idStr = map["id"] as? String
                        val id = if (!idStr.isNullOrEmpty()) idStr else java.util.UUID.randomUUID().toString()
                        val number = map["number"] as? String ?: ""
                        val type = map["type"] as? String ?: "incoming"
                        val date = map["date"] as? String ?: ""
                        val blocked = map["blocked"] as? Boolean ?: true
                        newBlockedList.add(BlockedNumber(id, number, type, date, blocked))
                    }
                    blockedList.clear()
                    blockedList.addAll(newBlockedList)
                } else {
                    blockedList.clear()
                }
            }

        // 2.6 Global Call Blocking Toggles
        devRef.collection("config").document("call_blocking")
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    blockAllIncoming.value = doc.getBoolean("blockAllIncoming") ?: false
                    blockAllOutgoing.value = doc.getBoolean("blockAllOutgoing") ?: false
                } else {
                    blockAllIncoming.value = false
                    blockAllOutgoing.value = false
                }
            }

        // 3. Deleted SMS Blacklist (Storage DB)
        deletedSmsListener = secondaryDevRef.collection("config").document("deleted_sms")
            .addSnapshotListener { doc, _ ->
                deletedSmsIds.clear()
                if (doc != null && doc.exists()) {
                    val ids = doc.get("ids") as? List<String> ?: emptyList()
                    deletedSmsIds.addAll(ids)
                    // Instantly remove them from memory list if they are present
                    smsList.removeAll { it.id in deletedSmsIds }
                }
            }
        // 4. SMS Logs (Storage DB)
        smsListener = secondaryDevRef.collection("sms").document("log")
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    var newIncomingCount = 0
                    var lastNewIncoming: SmsData? = null
                    val newSmsList = mutableListOf<SmsData>()

                    items.forEachIndexed { i, map ->
                        val id = map["id"] as? String ?: i.toString()
                        if (id !in deletedSmsIds) {
                            val number = map["number"] as? String ?: ""
                            val message = map["message"] as? String ?: ""
                            val type = map["type"] as? String ?: "Incoming"
                            val dateLong = map["date"] as? Long ?: 0L
                            val dateStr = if (dateLong > 0) sdf.format(Date(dateLong)) else ""
                            val smsData = SmsData(id, type, number, message, number, dateStr, "GPS Tracked")
                            newSmsList.add(smsData)
                            
                            // Check for notifications by seeing if this is a new incoming message
                            if (type == "Incoming" && smsList.none { it.id == id }) {
                                newIncomingCount++
                                lastNewIncoming = smsData
                            }
                        }
                    }

                    smsList.clear()
                    smsList.addAll(newSmsList)

                    // Show notification for new incoming SMS
                    if (newIncomingCount > 0 && lastNewIncoming != null) {
                        showNewSmsNotification(lastNewIncoming!!)
                    }
                }
            }

        // 4. Photos Logs
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        photosListener = FirebaseFirestore.getInstance(FirebaseApp.getInstance("media")).collection("devices").document(deviceId).collection("pictures")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val tempPictures = mutableListOf<PictureData>()
                    snapshot.documents.forEach { doc ->
                        val id = doc.id
                        val name = doc.getString("name") ?: ""
                        val dateLong = doc.getLong("date") ?: 0L
                        val dateStr = if (dateLong > 0) sdf.format(Date(dateLong)) else ""
                        val size = doc.getLong("size") ?: 0L
                        val sizeMB = size / (1024.0 * 1024.0)
                        val infoStr = String.format("%.1f MB", sizeMB)
                        
                        val path = doc.getString("path") ?: ""
                        val previewUrl = doc.getString("previewUrl")
                        val downloadUrl = doc.getString("downloadUrl")

                        tempPictures.add(PictureData(id, dateStr, infoStr, "Storage Path: $name", path, previewUrl, downloadUrl, Color(0xFF00A8B5)))
                    }
                    tempPictures.sortByDescending { it.date }
                    picturesList.clear()
                    picturesList.addAll(tempPictures)
                }
            }

        // 5. RTDB Status presence listener
        val statusRef = FirebaseDatabase.getInstance().getReference("status/$deviceId")
        rtdbStatusListener = statusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.child("state").getValue(String::class.java) ?: "offline"
                deviceOnlineStatus.value = state
                
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                if (state == "offline" && lastSeen > 0) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    lastSyncTime.value = "Last seen: " + sdf.format(Date(lastSeen))
                } else if (state == "online") {
                    lastSyncTime.value = "Online"
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        // 5b. RTDB Siren Status listener
        val sirenRef = FirebaseDatabase.getInstance().getReference("status/$deviceId/sirenState")
        rtdbSirenListener = sirenRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.getValue(String::class.java) ?: "stopped"
                isSirenPlaying.value = (state == "playing")
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 5c. RTDB Call State listener
        val callStateRef = FirebaseDatabase.getInstance().getReference("status/$deviceId/callState")
        callStateListener = callStateRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isChildRinging.value = snapshot.child("isRinging").getValue(Boolean::class.java) ?: false
                incomingRingingNumber.value = snapshot.child("incomingNumber").getValue(String::class.java) ?: ""
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 6. RTDB Live location listener
        val locRef = FirebaseDatabase.getInstance().getReference("live_locations/$deviceId")
        rtdbLocationListener = locRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("lat").getValue(Double::class.java) ?: 0.0
                val lng = snapshot.child("lng").getValue(Double::class.java) ?: 0.0
                if (lat != 0.0 && lng != 0.0) {
                    liveLatitude.value = lat
                    liveLongitude.value = lng
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        // Captured Photos Listener
        val rtdbCaptures = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        rtdbCapturedPhotosListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<CapturedPhoto>()
                for (child in snapshot.children) {
                    val id = child.child("id").getValue(String::class.java) ?: continue
                    val url = child.child("url").getValue(String::class.java) ?: continue
                    val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val type = child.child("type").getValue(String::class.java) ?: "live"
                    list.add(CapturedPhoto(id, url, ts, type))
                }
                list.sortByDescending { it.timestamp }
                capturedPhotosList.clear()
                capturedPhotosList.addAll(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        rtdbCaptures.getReference("devices/$deviceId/capturedPhotos").addValueEventListener(rtdbCapturedPhotosListener!!)

        // Recorded Videos Listener
        rtdbVideosListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<RecordedVideo>()
                for (child in snapshot.children) {
                    val id = child.child("id").getValue(String::class.java) ?: continue
                    val vUrl = child.child("videoUrl").getValue(String::class.java) ?: continue
                    val tUrl = child.child("thumbnailUrl").getValue(String::class.java) ?: continue
                    val duration = child.child("duration").getValue(Long::class.java) ?: 0L
                    val size = child.child("size").getValue(Long::class.java) ?: 0L
                    val cam = child.child("camera").getValue(String::class.java) ?: "rear"
                    val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    
                    list.add(RecordedVideo(id, vUrl, tUrl, duration, size, cam, ts))
                }
                list.sortByDescending { it.timestamp }
                videosList.clear()
                videosList.addAll(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        rtdbCaptures.getReference("devices/$deviceId/videos").addValueEventListener(rtdbVideosListener!!)

        // 7. Contacts Log
        contactsListener = secondaryDb.collection("devices").document(deviceId).collection("contacts").document("list")
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    contactsList.clear()
                    items.forEach { map ->
                        val name = map["name"] as? String ?: ""
                        val number = map["number"] as? String ?: ""
                        if (name.isNotEmpty() || number.isNotEmpty()) {
                            contactsList.add(Pair(name, number))
                        }
                    }
                }
            }

        // 8. Device Controls & App Blocking rules (webrtcDb)
        val webrtcFirestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance("webrtc"))
        val blockRef = webrtcFirestore.collection("devices").document(deviceId)
        val appsRef = webrtcFirestore.collection("devices").document(deviceId).collection("installedApps")
        val blockedPackages = mutableListOf<String>()

        blockingListener = blockRef.addSnapshotListener { blockDoc, _ ->
            blockedPackages.clear()
            if (blockDoc != null && blockDoc.exists()) {
                // Parse settings
                val settings = blockDoc.get("settings") as? Map<*, *>
                monitoringEnabled.value = settings?.get("monitoringEnabled") as? Boolean ?: true
                agentHidden.value = settings?.get("agentHidden") as? Boolean ?: false
                parentPhoneNumber.value = settings?.get("parentPhoneNumber") as? String ?: ""
                friendDisguiseNumber.value = settings?.get("friendDisguiseNumber") as? String ?: ""
                childPhoneNumber.value = settings?.get("childPhoneNumber") as? String ?: ""

                // Parse blocking rules
                val blocking = blockDoc.get("blocking") as? Map<*, *>
                val apps = blocking?.get("apps") as? List<*> ?: emptyList<Any>()
                blockedPackages.addAll(apps.filterIsInstance<String>())
            }
            // Update apps list blocked flags in place
            appsList.forEachIndexed { idx, app ->
                val shouldBlock = app.packageName in blockedPackages
                if (app.isBlocked != shouldBlock) {
                    appsList[idx] = app.copy(isBlocked = shouldBlock)
                }
            }
        }

        appsListener = appsRef.addSnapshotListener { appsQuery, _ ->
            if (appsQuery != null) {
                val tempApps = mutableListOf<AppTarget>()
                appsQuery.documents.forEach { doc ->
                    val pkgName = doc.getString("packageName") ?: doc.id
                    val name = doc.getString("appName") ?: pkgName
                    val isBlocked = pkgName in blockedPackages
                    tempApps.add(AppTarget(
                        name = name,
                        packageName = pkgName,
                        category = "App",
                        usageTime = "${(10..90).random()} mins",
                        isBlocked = isBlocked
                    ))
                }
                appsList.clear()
                appsList.addAll(tempApps)
            }
        }

        // 9. File Explorer Log (Now using RTDB 'media' instance)
        val fileExplorerDb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        fileExplorerListener?.let { listener: ValueEventListener -> fileExplorerDb.getReference("status/$deviceId/fileExplorer/currentDir").removeEventListener(listener) }
        fileExplorerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isExplorerLoading.value = false
                if (snapshot.exists()) {
                    val path = snapshot.child("path").getValue(String::class.java) ?: "/storage/emulated/0"
                    currentExplorerPath.value = path
                    explorerError.value = snapshot.child("error").getValue(String::class.java)

                    explorerFilesList.clear()
                    val items = snapshot.child("items").children
                    items.forEach { child ->
                        explorerFilesList.add(FileItem(
                            name = child.child("name").getValue(String::class.java) ?: "",
                            path = child.child("path").getValue(String::class.java) ?: "",
                            isDirectory = child.child("isDirectory").getValue(Boolean::class.java) ?: false,
                            size = child.child("size").getValue(Long::class.java) ?: 0L,
                            lastModified = child.child("lastModified").getValue(Long::class.java) ?: 0L
                        ))
                    }
                } else {
                    explorerFilesList.clear()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        fileExplorerDb.getReference("status/$deviceId/fileExplorer/currentDir").addValueEventListener(fileExplorerListener!!)

        // Listen for Previews
        previewListener?.let { listener: ValueEventListener -> fileExplorerDb.getReference("status/$deviceId/fileExplorer/previewData").removeEventListener(listener) }
        previewListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isPreviewLoading.value = false
                if (snapshot.exists()) {
                    val data = snapshot.value as? Map<String, String>
                    if (data != null) {
                        currentPreview.value = data
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        fileExplorerDb.getReference("status/$deviceId/fileExplorer/previewData").addValueEventListener(previewListener!!)

        // Listen for Downloads
        downloadListener?.let { listener: ValueEventListener -> fileExplorerDb.getReference("status/$deviceId/fileExplorer/downloadUrl").removeEventListener(listener) }
        downloadListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val url = snapshot.child("url").getValue(String::class.java)
                    if (!url.isNullOrBlank()) {
                        currentDownloadUrl.value = url
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        fileExplorerDb.getReference("status/$deviceId/fileExplorer/downloadUrl").addValueEventListener(downloadListener!!)

        // 10. Camera Session Status
        cameraSessionListener?.let { listener -> FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")).getReference("devices/$currentListeningDeviceId/cameraSession/status").removeEventListener(listener) }
        cameraSessionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveCameraStatus.value = snapshot.getValue(String::class.java) ?: "idle"
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")).getReference("devices/$deviceId/cameraSession/status").addValueEventListener(cameraSessionListener!!)

        // 11. Audio Session Status
        audioSessionListener?.let { listener -> FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")).getReference("devices/$currentListeningDeviceId/audioSession/status").removeEventListener(listener) }
        audioSessionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveAudioStatus.value = snapshot.getValue(String::class.java) ?: "idle"
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")).getReference("devices/$deviceId/audioSession/status").addValueEventListener(audioSessionListener!!)

        // 12. Audio Recordings List
        rtdbAudioRecordingsListener?.let { listener -> FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("devices/$currentListeningDeviceId/audioRecordings").removeEventListener(listener) }
        rtdbAudioRecordingsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<AudioRecording>()
                for (child in snapshot.children) {
                    val id = child.child("id").getValue(String::class.java) ?: ""
                    val url = child.child("url").getValue(String::class.java) ?: ""
                    val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    list.add(AudioRecording(id, url, ts))
                }
                list.sortByDescending { it.timestamp }
                audioRecordingsList.clear()
                audioRecordingsList.addAll(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("devices/$deviceId/audioRecordings").addValueEventListener(rtdbAudioRecordingsListener!!)

        // 13. Web History Log (stored in xshield-webrtc Firestore project)
        val webrtcDb = FirebaseFirestore.getInstance(FirebaseApp.getInstance("webrtc"))
        webHistoryListener = webrtcDb.collection("devices").document(deviceId)
            .collection("webHistory").document("log")
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    Log.e("XshieldRepository", "Web history listener failed: ${error.message}")
                    return@addSnapshotListener
                }
                
                if (doc != null && doc.exists()) {
                    val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val tempList = mutableListOf<WebHistoryData>()
                    
                    items.forEach { map ->
                        val url = map["url"] as? String ?: ""
                        val title = map["title"] as? String ?: ""
                        val browser = map["browser"] as? String ?: ""
                        val timestamp = map["timestamp"] as? Long ?: 0L
                        
                        tempList.add(WebHistoryData(url, title, browser, timestamp))
                    }
                    webHistoryList.clear()
                    webHistoryList.addAll(tempList)
                } else {
                    webHistoryList.clear()
                }
            }

        listenForInstantMessages(deviceId)

        schedulesListener = blockRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("XshieldRepository", "Schedules listener failed: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val schedulesRaw = snapshot.get("schedules") as? List<Map<String, Any>> ?: emptyList()
                val tempList = mutableListOf<ScheduleRestriction>()
                schedulesRaw.forEach { map ->
                    val id = map["id"] as? String ?: ""
                    val name = map["name"] as? String ?: ""
                    val start = map["startTime"] as? String ?: "21:00"
                    val end = map["endTime"] as? String ?: "07:00"
                    val days = map["days"] as? List<String> ?: emptyList()
                    val enabled = map["enabled"] as? Boolean ?: true
                    val blockAll = map["blockAll"] as? Boolean ?: true
                    val blockedApps = map["blockedApps"] as? List<String> ?: emptyList()
                    tempList.add(ScheduleRestriction(id, name, start, end, days, enabled, blockAll, blockedApps))
                }
                _schedules.value = tempList
            } else {
                _schedules.value = emptyList()
            }
        }
    }

    fun toggleAppBlock(packageName: String, blocked: Boolean) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val webrtcDb = FirebaseFirestore.getInstance(FirebaseApp.getInstance("webrtc"))
        val blockRef = webrtcDb.collection("devices").document(deviceId)
        
        val index = appsList.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            appsList[index] = appsList[index].copy(isBlocked = blocked)
        }
        
        val blockedPackages = appsList.filter { it.isBlocked }.map { it.packageName }
        blockRef.set(mapOf("blocking" to mapOf("enabled" to true, "apps" to blockedPackages)), com.google.firebase.firestore.SetOptions.merge())
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val webrtcDb = FirebaseFirestore.getInstance(FirebaseApp.getInstance("webrtc"))
        webrtcDb.collection("devices").document(deviceId)
            .set(mapOf("settings" to mapOf("monitoringEnabled" to enabled)), com.google.firebase.firestore.SetOptions.merge())
    }

    fun setAgentHidden(hidden: Boolean) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val webrtcDb = FirebaseFirestore.getInstance(FirebaseApp.getInstance("webrtc"))
        webrtcDb.collection("devices").document(deviceId)
            .set(mapOf("settings" to mapOf("agentHidden" to hidden)), com.google.firebase.firestore.SetOptions.merge())
    }

    fun setParentPhoneNumber(number: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val webrtcDb = FirebaseFirestore.getInstance(FirebaseApp.getInstance("webrtc"))
        webrtcDb.collection("devices").document(deviceId)
            .set(mapOf("settings" to mapOf("parentPhoneNumber" to number)), com.google.firebase.firestore.SetOptions.merge())
    }

    fun setFriendDisguiseNumber(number: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val webrtcDb = FirebaseFirestore.getInstance(FirebaseApp.getInstance("webrtc"))
        webrtcDb.collection("devices").document(deviceId)
            .set(mapOf("settings" to mapOf("friendDisguiseNumber" to number)), com.google.firebase.firestore.SetOptions.merge())
    }

    fun setChildPhoneNumber(number: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val webrtcDb = FirebaseFirestore.getInstance(FirebaseApp.getInstance("webrtc"))
        webrtcDb.collection("devices").document(deviceId)
            .set(mapOf("settings" to mapOf("childPhoneNumber" to number)), com.google.firebase.firestore.SetOptions.merge())
    }

    fun clearInstantMessages() {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        rtdb.getReference("instant_messaging/$deviceId/messages").removeValue()
        // Locally clear immediately for snappy UI
        _instantMessages.value = emptyList()
    }

    fun deleteSms(deviceId: String, smsId: String) {
        if (deviceId.isBlank()) return
        val db = FirebaseFirestore.getInstance()
        deletedSmsIds.add(smsId)
        // Remove locally immediately for snappy UI
        smsList.removeAll { it.id == smsId }
        
        // Save to Firestore
        db.collection("devices").document(deviceId)
            .collection("config").document("deleted_sms")
            .set(mapOf("ids" to deletedSmsIds.toList()), com.google.firebase.firestore.SetOptions.merge())
    }

    fun triggerSmsSync(deviceId: String) {
        if (deviceId.isBlank()) return
        val rtdb = FirebaseDatabase.getInstance()
        rtdb.getReference("commands/$deviceId/syncSms").setValue(System.currentTimeMillis())
    }

    fun requestDirectory(path: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        isExplorerLoading.value = true
        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        rtdb.getReference("commands/$deviceId/fileExplorer/path").setValue(path)
    }

    fun requestDownload(deviceId: String, path: String) {
        FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("commands/$deviceId/fileExplorer")
            .updateChildren(mapOf("downloadRequest" to path))
    }

    fun sendCallControlCommand(deviceId: String, action: String, number: String? = null) {
        val payload = mutableMapOf<String, Any>("action" to action)
        if (number != null) payload["number"] = number
        FirebaseDatabase.getInstance().getReference("commands/$deviceId/callControl").setValue(payload)
    }

    fun requestPreview(filePath: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        isPreviewLoading.value = true
        currentPreview.value = null // reset old preview
        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        rtdb.getReference("commands/$deviceId/fileExplorer/previewRequest").setValue(filePath)
    }

    fun requestDownload(filePath: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        currentDownloadUrl.value = "" // reset old download
        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        rtdb.getReference("commands/$deviceId/fileExplorer/downloadRequest").setValue(filePath)
    }

    fun toggleSiren(play: Boolean) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val rtdb = FirebaseDatabase.getInstance()
        rtdb.getReference("commands/$deviceId/siren/active").setValue(play)
        
        // Optimistically update UI state to make it feel instantly responsive
        if (!play) {
            isSirenPlaying.value = false
        }
    }

    private fun updatePresenceListeners(devices: List<String>) {
        rtdbPresenceListeners.forEach { (devId, listener) ->
            FirebaseDatabase.getInstance().getReference("status/$devId").removeEventListener(listener)
        }
        rtdbPresenceListeners.clear()
        deviceStatusesMap.clear()

        devices.forEach { devId ->
            val statusRef = FirebaseDatabase.getInstance().getReference("status/$devId")
            val listener = statusRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val state = snapshot.child("state").getValue(String::class.java) ?: "offline"
                    deviceStatusesMap[devId] = state
                }
                override fun onCancelled(error: DatabaseError) {}
            })
            rtdbPresenceListeners[devId] = listener
        }
    }

    fun requestPicturePreview(pictureId: String, path: String) {
        val deviceId = selectedDevice.value
        android.util.Log.d("XshieldRepo", "requestPicturePreview called for $pictureId. deviceId: $deviceId")
        if (deviceId.isBlank()) return
        val db = FirebaseFirestore.getInstance()
        val cmdId = java.util.UUID.randomUUID().toString()
        db.collection("devices").document(deviceId).collection("commands").document(cmdId).set(
            mapOf(
                "id" to cmdId,
                "type" to "FETCH_PREVIEW",
                "pictureId" to pictureId,
                "path" to path,
                "status" to "pending",
                "timestamp" to System.currentTimeMillis()
            )
        ).addOnSuccessListener {
            android.util.Log.d("XshieldRepo", "FETCH_PREVIEW command written successfully!")
        }.addOnFailureListener {
            android.util.Log.e("XshieldRepo", "FETCH_PREVIEW command failed to write!", it)
        }
    }

    fun requestFullPicture(pictureId: String, path: String) {
        val deviceId = selectedDevice.value
        android.util.Log.d("XshieldRepo", "requestFullPicture called for $pictureId. deviceId: $deviceId")
        if (deviceId.isBlank()) return
        val db = FirebaseFirestore.getInstance()
        val cmdId = java.util.UUID.randomUUID().toString()
        db.collection("devices").document(deviceId).collection("commands").document(cmdId).set(
            mapOf(
                "id" to cmdId,
                "type" to "FETCH_FULL_IMAGE",
                "pictureId" to pictureId,
                "path" to path,
                "status" to "pending",
                "timestamp" to System.currentTimeMillis()
            )
        ).addOnSuccessListener {
            android.util.Log.d("XshieldRepo", "FETCH_FULL_IMAGE command written successfully!")
        }.addOnFailureListener {
            android.util.Log.e("XshieldRepo", "FETCH_FULL_IMAGE command failed to write!", it)
        }
    }

    fun startCameraStream(type: String = "rear") {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        
        webRtcManager?.endSession()
        webRtcManager = WebRtcSignalingManager(appContext, deviceId)
        webRtcManager?.currentType = type
        
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            webRtcManager?.remoteVideoTrack?.collect { track ->
                activeVideoTrack.value = track
            }
        }
        
        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc"))
        rtdb.getReference("devices/$deviceId/cameraSession").setValue(mapOf(
            "active" to true,
            "cameraType" to type,
            "status" to "connecting",
            "startedAt" to System.currentTimeMillis()
        ))
    }

    fun stopCameraStream() {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        
        webRtcManager?.endSession()
        webRtcManager = null
        activeVideoTrack.value = null

        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc"))
        rtdb.getReference("devices/$deviceId/cameraSession/active").setValue(false)
        rtdb.getReference("devices/$deviceId/cameraSession/status").setValue("offline")
    }

    val screenShareStatus = mutableStateOf("IDLE")
    private var screenShareStatusListener: ValueEventListener? = null

    fun observeScreenShareStatus() {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        
        screenShareStatusListener?.let {
            FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")).getReference("devices/$deviceId/screenShare/status").removeEventListener(it)
        }
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                screenShareStatus.value = snapshot.getValue(String::class.java) ?: "IDLE"
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        
        FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")).getReference("devices/$deviceId/screenShare/status").addValueEventListener(listener)
        screenShareStatusListener = listener
    }

    fun startScreenShare() {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        
        webRtcManager?.endSession()
        webRtcManager = WebRtcSignalingManager(appContext, deviceId, "screen")
        
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            webRtcManager?.remoteVideoTrack?.collect { track ->
                activeVideoTrack.value = track
            }
        }
        
        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc"))
        val cmd = mapOf(
            "action" to "START_SCREEN_SHARE",
            "timestamp" to System.currentTimeMillis()
        )
        rtdb.getReference("devices/$deviceId/commands").setValue(cmd)
        rtdb.getReference("devices/$deviceId/screenShare/status").setValue("WAITING_PERMISSION")
    }

    fun stopScreenShare() {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        
        webRtcManager?.endSession()
        webRtcManager = null
        activeVideoTrack.value = null

        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc"))
        val cmd = mapOf(
            "action" to "STOP_SCREEN_SHARE",
            "timestamp" to System.currentTimeMillis()
        )
        rtdb.getReference("devices/$deviceId/commands").setValue(cmd)
        rtdb.getReference("devices/$deviceId/screenShare/status").setValue("STOPPED")
    }

    fun updateImConfig(appKey: String, isEnabled: Boolean) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        
        // Optimistic UI update for instant feedback
        val currentConfig = _imConfig.value.toMutableMap()
        currentConfig[appKey] = isEnabled
        _imConfig.value = currentConfig

        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        rtdb.getReference("instant_messaging/$deviceId/config/$appKey").setValue(isEnabled)
    }

    private fun listenForInstantMessages(deviceId: String) {
        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        
        imMessagesListener?.let { rtdb.getReference("instant_messaging/$deviceId/messages").removeEventListener(it) }
        
        // Reset list when starting a new listener
        _instantMessages.value = emptyList()

        imMessagesListener = object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                val msg = snapshot.getValue(InstantMessage::class.java)
                if (msg != null) {
                    val finalMsg = msg.copy(id = snapshot.key ?: "")
                    // We simply append and re-sort. This eliminates the bandwidth spike.
                    val currentList = _instantMessages.value.toMutableList()
                    currentList.add(finalMsg)
                    _instantMessages.value = currentList.sortedByDescending { it.timestamp }
                }
            }

            override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) {
                val currentList = _instantMessages.value.toMutableList()
                currentList.removeAll { it.id == snapshot.key }
                _instantMessages.value = currentList
            }
            override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        rtdb.getReference("instant_messaging/$deviceId/messages").limitToLast(500).addChildEventListener(imMessagesListener!!)
        
        imConfigListener?.let { rtdb.getReference("instant_messaging/$deviceId/config").removeEventListener(it) }
        imConfigListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val config = mutableMapOf<String, Boolean>()
                snapshot.children.forEach {
                    config[it.key ?: ""] = it.getValue(Boolean::class.java) ?: false
                }
                _imConfig.value = config
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        rtdb.getReference("instant_messaging/$deviceId/config").addValueEventListener(imConfigListener!!)
    }

    fun lockScreen() {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val rtdb = FirebaseDatabase.getInstance()
        rtdb.getReference("commands/$deviceId/lockScreen").setValue(System.currentTimeMillis())
    }

    fun captureSecretPhoto() {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        rtdb.getReference("commands/$deviceId/secretCapture").setValue(System.currentTimeMillis())
    }

    fun captureRemoteScreenshot() {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        rtdb.getReference("commands/$deviceId/screenshot").setValue(System.currentTimeMillis())
    }

    fun requestCameraFeed(type: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val rtdb = FirebaseDatabase.getInstance()
        rtdb.getReference("devices/$deviceId/cameraSession/cameraType").setValue(type)
    }

    fun switchCamera(type: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc"))
        rtdb.getReference("devices/$deviceId/cameraSession/cameraType").setValue(type)
    }

    suspend fun uploadCapturedFrame(context: Context, bitmap: android.graphics.Bitmap) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val tempFile = java.io.File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                tempFile.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }

                val scriptName = "upload_file.php"
                val uploadUrl = "https://chiranjeevi.skillsupriselab.com/$scriptName"
                val boundary = "Boundary-" + System.currentTimeMillis()
                
                val connection = java.net.URL(uploadUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                
                val out = java.io.DataOutputStream(connection.outputStream)
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${tempFile.name}\"\r\n")
                out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                
                out.write(tempFile.readBytes())
                
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
                out.close()
                
                if (connection.responseCode == 200) {
                    val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                    if (responseStr.contains("\"url\"")) {
                        val parsedUrl = responseStr.substringAfter("\"url\":\"").substringBefore("\"").replace("\\/", "/")
                        val deviceId = selectedDevice.value
                        val db = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("devices/$deviceId/capturedPhotos")
                        val photoId = db.push().key ?: return@withContext
                        
                        val photoData = mapOf(
                            "id" to photoId,
                            "url" to parsedUrl,
                            "timestamp" to System.currentTimeMillis(),
                            "type" to "live"
                        )
                        db.child(photoId).setValue(photoData)
                    }
                }
                tempFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteCapturedPhoto(photoId: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        
        val photoUrl = capturedPhotosList.find { it.id == photoId }?.url
        
        FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("devices/$deviceId/capturedPhotos/$photoId").removeValue()
        
        if (photoUrl != null && photoUrl.contains("chiranjeevi.skillsupriselab.com")) {
            val relativePath = photoUrl.substringAfter("chiranjeevi.skillsupriselab.com/")
            deleteFromServer(relativePath)
        }
    }

    fun startVideoRecording(cameraType: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        rtdb.getReference("commands/$deviceId/videoRecording").setValue(mapOf(
            "action" to "start",
            "camera" to cameraType,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    fun stopVideoRecording() {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val rtdb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        rtdb.getReference("commands/$deviceId/videoRecording").setValue(mapOf(
            "action" to "stop",
            "timestamp" to System.currentTimeMillis()
        ))
    }

    fun deleteVideo(videoId: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        
        val videoUrl = videosList.find { it.id == videoId }?.videoUrl
        val thumbnailUrl = videosList.find { it.id == videoId }?.thumbnailUrl
        
        FirebaseDatabase.getInstance(FirebaseApp.getInstance("media")).getReference("devices/$deviceId/videos/$videoId").removeValue()
        
        if (videoUrl != null && videoUrl.contains("chiranjeevi.skillsupriselab.com")) {
            val relativePath = videoUrl.substringAfter("chiranjeevi.skillsupriselab.com/")
            deleteFromServer(relativePath)
        }
        if (thumbnailUrl != null && thumbnailUrl.contains("chiranjeevi.skillsupriselab.com")) {
            val relativePath = thumbnailUrl.substringAfter("chiranjeevi.skillsupriselab.com/")
            deleteFromServer(relativePath)
        }
    }

    private fun deleteFromServer(filename: String) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val url = java.net.URL("https://chiranjeevi.skillsupriselab.com/delete_file.php")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                
                val postData = "filename=" + java.net.URLEncoder.encode(filename, "UTF-8")
                val out = java.io.DataOutputStream(connection.outputStream)
                out.writeBytes(postData)
                out.flush()
                out.close()
                
                val responseCode = connection.responseCode
                android.util.Log.d("XshieldRepository", "Deleted $filename from server, code: $responseCode")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addBlockedNumber(number: String, type: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isEmpty()) return

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val newItem = mapOf(
            "id" to java.util.UUID.randomUUID().toString(),
            "number" to number,
            "type" to type,
            "date" to sdf.format(Date()),
            "blocked" to true
        )
        
        val docRef = FirebaseFirestore.getInstance()
            .collection("devices")
            .document(deviceId)
            .collection("config")
            .document("blocked_calls")
            
        docRef.get().addOnSuccessListener { doc ->
            val existing = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
            val updated = existing.toMutableList()
            updated.add(newItem)
            docRef.set(mapOf("items" to updated))
        }
    }

    fun removeBlockedNumber(id: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isEmpty()) return
        
        val docRef = FirebaseFirestore.getInstance()
            .collection("devices")
            .document(deviceId)
            .collection("config")
            .document("blocked_calls")
            
        docRef.get().addOnSuccessListener { doc ->
            val existing = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
            val updated = existing.filter { (it["id"] as? String) != id }
            docRef.set(mapOf("items" to updated))
        }
    }

    fun sendRemoteCommand(command: String) {
        webRtcManager?.sendControlCommand(command)
    }

    fun toggleGlobalCallBlocking(incoming: Boolean, outgoing: Boolean) {
        val deviceId = selectedDevice.value
        if (deviceId.isEmpty()) return
        
        val docRef = FirebaseFirestore.getInstance()
            .collection("devices")
            .document(deviceId)
            .collection("config")
            .document("call_blocking")
            
        docRef.set(mapOf(
            "blockAllIncoming" to incoming,
            "blockAllOutgoing" to outgoing
        ), com.google.firebase.firestore.SetOptions.merge())
    }
    fun requestAudioFeed() {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        
        audioRtcManager?.endSession()
        audioRtcManager = WebRtcSignalingManager(appContext, deviceId, type = "audio")
        
        val rtdb = FirebaseDatabase.getInstance()
        rtdb.getReference("commands/$deviceId/audioSession").setValue(mapOf(
            "action" to "start",
            "timestamp" to System.currentTimeMillis()
        ))
        liveAudioStatus.value = "connecting"
    }

    fun stopAudioFeed() {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        
        audioRtcManager?.endSession()
        audioRtcManager = null
        
        val rtdb = FirebaseDatabase.getInstance()
        rtdb.getReference("commands/$deviceId/audioSession").setValue(mapOf(
            "action" to "stop",
            "timestamp" to System.currentTimeMillis()
        ))
        liveAudioStatus.value = "idle"
    }

    fun startRemoteAudioRecording() {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        
        isRemoteAudioRecording.value = true
        val mediaDb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        mediaDb.getReference("commands/$deviceId/audioRecording").setValue(mapOf(
            "action" to "start",
            "timestamp" to System.currentTimeMillis()
        ))
    }

    fun stopRemoteAudioRecording() {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        
        isRemoteAudioRecording.value = false
        val mediaDb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        mediaDb.getReference("commands/$deviceId/audioRecording").setValue(mapOf(
            "action" to "stop",
            "timestamp" to System.currentTimeMillis()
        ))
    }

    fun deleteAudioRecording(recordingId: String, filename: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return

        // Delete from Firebase
        val mediaDb = FirebaseDatabase.getInstance(FirebaseApp.getInstance("media"))
        mediaDb.getReference("devices/$deviceId/audioRecordings/$recordingId").removeValue()

        // Delete from Hostinger Server
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val url = java.net.URL("https://chiranjeevi.skillsupriselab.com/delete_file.php")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                
                val postData = "filename=" + java.net.URLEncoder.encode(filename, "UTF-8")
                val out = java.io.DataOutputStream(connection.outputStream)
                out.writeBytes(postData)
                out.flush()
                out.close()
                
                val responseCode = connection.responseCode
                android.util.Log.d("XshieldRepository", "Deleted audio $filename from server, code: $responseCode")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearWebHistory() {
        val deviceId = selectedDevice.value
        if (deviceId.isEmpty()) return

        webHistoryList.clear()

        val webrtcDb = FirebaseFirestore.getInstance(FirebaseApp.getInstance("webrtc"))
        webrtcDb.collection("devices").document(deviceId)
            .collection("webHistory").document("log")
            .set(mapOf(
                "items" to emptyList<Map<String, Any>>(),
                "updatedAt" to System.currentTimeMillis()
            ), com.google.firebase.firestore.SetOptions.merge())
    }

    fun deleteWebHistoryItem(item: WebHistoryData) {
        val deviceId = selectedDevice.value
        if (deviceId.isEmpty()) return

        val updated = webHistoryList.filter { it.url != item.url || it.timestamp != item.timestamp }
        webHistoryList.clear()
        webHistoryList.addAll(updated)

        val webrtcDb = FirebaseFirestore.getInstance(FirebaseApp.getInstance("webrtc"))
        webrtcDb.collection("devices").document(deviceId)
            .collection("webHistory").document("log")
            .set(mapOf(
                "items" to updated.map { mapOf(
                    "url" to it.url,
                    "title" to it.title,
                    "browser" to it.browser,
                    "timestamp" to it.timestamp
                ) },
                "updatedAt" to System.currentTimeMillis()
            ), com.google.firebase.firestore.SetOptions.merge())
    }

    fun saveSchedule(schedule: ScheduleRestriction) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val webrtcDb = FirebaseFirestore.getInstance(FirebaseApp.getInstance("webrtc"))
        val blockRef = webrtcDb.collection("devices").document(deviceId)

        val currentList = _schedules.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == schedule.id }
        if (index != -1) {
            currentList[index] = schedule
        } else {
            currentList.add(schedule)
        }

        val listData = currentList.map {
            mapOf(
                "id" to it.id,
                "name" to it.name,
                "startTime" to it.startTime,
                "endTime" to it.endTime,
                "days" to it.days,
                "enabled" to it.isEnabled,
                "blockAll" to it.blockAll,
                "blockedApps" to it.blockedApps
            )
        }

        blockRef.set(mapOf("schedules" to listData), com.google.firebase.firestore.SetOptions.merge())
    }

    fun deleteSchedule(scheduleId: String) {
        val deviceId = selectedDevice.value
        if (deviceId.isBlank()) return
        val webrtcDb = FirebaseFirestore.getInstance(FirebaseApp.getInstance("webrtc"))
        val blockRef = webrtcDb.collection("devices").document(deviceId)

        val currentList = _schedules.value.filter { it.id != scheduleId }

        val listData = currentList.map {
            mapOf(
                "id" to it.id,
                "name" to it.name,
                "startTime" to it.startTime,
                "endTime" to it.endTime,
                "days" to it.days,
                "enabled" to it.isEnabled,
                "blockAll" to it.blockAll,
                "blockedApps" to it.blockedApps
            )
        }

        blockRef.set(mapOf("schedules" to listData), com.google.firebase.firestore.SetOptions.merge())
    }
}

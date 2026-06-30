package com.example.xshield.childagent

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object AgentStateManager {
    private const val TAG = "AgentStateManager"
    private const val PREFS_NAME = "agent_state_prefs"
    
    private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
    private const val KEY_AGENT_HIDDEN = "agent_hidden"
    private const val KEY_BLOCKED_APPS = "blocked_apps"
    private const val KEY_SCHEDULES_CONFIG = "schedules_config"
    private const val KEY_PARENT_PHONE_NUMBER = "parent_phone_number"
    private const val KEY_SMS_LOCKED = "device_locked_sms"
    private const val KEY_FRIEND_DISGUISE_NUMBER = "friend_disguise_number"
    private const val KEY_CHILD_PHONE_NUMBER = "child_phone_number"

    private var sharedPreferences: SharedPreferences? = null
    private var firestoreListener: ListenerRegistration? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentMonitoringEnabled = true
    private var currentAgentHidden = false
    private val currentBlockedApps = mutableSetOf<String>()
    private var currentSchedulesJson = "[]"
    private var currentParentPhoneNumber = ""
    private var currentFriendDisguiseNumber = ""
    private var currentChildPhoneNumber = ""
    private var isSmsLocked = false

    private fun getPrefs(context: Context): SharedPreferences {
        val prefs = sharedPreferences
        if (prefs != null) return prefs
        val safeContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.applicationContext.createDeviceProtectedStorageContext()
        } else {
            context.applicationContext
        }
        val newPrefs = safeContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences = newPrefs
        return newPrefs
    }

    fun initialize(context: Context, deviceId: String, startListener: Boolean = true) {
        val safeContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.applicationContext.createDeviceProtectedStorageContext()
        } else {
            context.applicationContext
        }
        val prefs = getPrefs(safeContext)

        // Load cached configurations
        currentMonitoringEnabled = prefs.getBoolean(KEY_MONITORING_ENABLED, true)
        currentAgentHidden = prefs.getBoolean(KEY_AGENT_HIDDEN, false)
        val savedBlocked = prefs.getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()
        currentBlockedApps.clear()
        currentBlockedApps.addAll(savedBlocked)
        currentSchedulesJson = prefs.getString(KEY_SCHEDULES_CONFIG, "[]") ?: "[]"
        currentParentPhoneNumber = prefs.getString(KEY_PARENT_PHONE_NUMBER, "") ?: ""
        currentFriendDisguiseNumber = prefs.getString(KEY_FRIEND_DISGUISE_NUMBER, "") ?: ""
        currentChildPhoneNumber = prefs.getString(KEY_CHILD_PHONE_NUMBER, "") ?: ""
        isSmsLocked = prefs.getBoolean(KEY_SMS_LOCKED, false)

        Log.d(TAG, "Initialized cache: monitoringEnabled=$currentMonitoringEnabled, agentHidden=$currentAgentHidden, blockedCount=${currentBlockedApps.size}, schedules=$currentSchedulesJson")

        // Force synchronization of launcher icon visibility based on the cached state on startup
        syncLauncherIconState(safeContext)

        if (!startListener) {
            Log.d(TAG, "Skipping Firestore settings listener registration")
            return
        }

        if (deviceId.isBlank()) {
            Log.w(TAG, "Device ID is blank, cannot start Firestore settings listener")
            return
        }

        // Setup unified Firestore listener on xshield-webrtc project
        try {
            val webrtcApp = FirebaseApp.getInstance("webrtc")
            val webrtcDb = FirebaseFirestore.getInstance(webrtcApp)
            
            firestoreListener?.remove()
            Log.d(TAG, "Registering Firestore snapshot listener on devices/$deviceId")
            firestoreListener = webrtcDb.collection("devices").document(deviceId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e(TAG, "Settings snapshot listener failed", e)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        Log.d(TAG, "Settings snapshot received: ${snapshot.data}")
                        
                        // 1. Settings parsing
                        val settings = snapshot.get("settings") as? Map<*, *>
                        val monitoring = settings?.get("monitoringEnabled") as? Boolean ?: true
                        val hidden = settings?.get("agentHidden") as? Boolean ?: false
                        val parentPhone = settings?.get("parentPhoneNumber") as? String ?: ""
                        val disguisePhone = settings?.get("friendDisguiseNumber") as? String ?: ""
                        val childPhone = settings?.get("childPhoneNumber") as? String ?: ""

                        // 2. Blocking rules parsing
                        val blocking = snapshot.get("blocking") as? Map<*, *>
                        val apps = blocking?.get("apps") as? List<*> ?: emptyList<Any>()
                        val appStrings = apps.filterIsInstance<String>().toSet()

                        // 3. Schedules parsing
                        val schedulesList = snapshot.get("schedules") as? List<*> ?: emptyList<Any>()
                        val schedulesJson = convertSchedulesToJson(schedulesList)

                        updateState(safeContext, monitoring, hidden, appStrings, schedulesJson, parentPhone, disguisePhone, childPhone)
                    } else {
                        Log.w(TAG, "Snapshot is null or document does not exist for devices/$deviceId")
                    }
                }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to initialize Firestore settings listener", ex)
        }
    }

    private fun convertSchedulesToJson(list: List<*>): String {
        val jsonArray = org.json.JSONArray()
        for (item in list) {
            if (item is Map<*, *>) {
                val jsonObj = org.json.JSONObject()
                for ((key, value) in item) {
                    if (key is String) {
                        when (value) {
                            is List<*> -> {
                                val arr = org.json.JSONArray()
                                for (v in value) {
                                    arr.put(v)
                                }
                                jsonObj.put(key, arr)
                            }
                            else -> jsonObj.put(key, value)
                        }
                    }
                }
                jsonArray.put(jsonObj)
            }
        }
        return jsonArray.toString()
    }

    private fun updateState(context: Context, monitoring: Boolean, hidden: Boolean, blocked: Set<String>, schedulesJson: String, parentPhone: String, disguisePhone: String, childPhone: String) {
        val prefs = getPrefs(context)
        val editor = prefs.edit()

        var monitoringChanged = false
        var visibilityChanged = false
        var blockingChanged = false
        var schedulesChanged = false
        var parentPhoneChanged = false

        if (monitoring != currentMonitoringEnabled) {
            currentMonitoringEnabled = monitoring
            editor.putBoolean(KEY_MONITORING_ENABLED, monitoring)
            monitoringChanged = true
        }

        if (hidden != currentAgentHidden) {
            currentAgentHidden = hidden
            editor.putBoolean(KEY_AGENT_HIDDEN, hidden)
            visibilityChanged = true
        }

        if (blocked != currentBlockedApps) {
            currentBlockedApps.clear()
            currentBlockedApps.addAll(blocked)
            editor.putStringSet(KEY_BLOCKED_APPS, blocked)
            blockingChanged = true
        }

        if (schedulesJson != currentSchedulesJson) {
            currentSchedulesJson = schedulesJson
            editor.putString(KEY_SCHEDULES_CONFIG, schedulesJson)
            schedulesChanged = true
        }

        if (parentPhone != currentParentPhoneNumber) {
            currentParentPhoneNumber = parentPhone
            editor.putString(KEY_PARENT_PHONE_NUMBER, parentPhone)
            parentPhoneChanged = true
        }

        if (disguisePhone != currentFriendDisguiseNumber) {
            currentFriendDisguiseNumber = disguisePhone
            editor.putString(KEY_FRIEND_DISGUISE_NUMBER, disguisePhone)
        }

        if (childPhone != currentChildPhoneNumber) {
            currentChildPhoneNumber = childPhone
            editor.putString(KEY_CHILD_PHONE_NUMBER, childPhone)
        }

        editor.apply()

        // Apply changes
        if (monitoringChanged) {
            Log.i(TAG, "Monitoring state changed to: $monitoring")
            mainHandler.post {
                MonitoringService.instance?.handleMonitoringStateChanged(monitoring)
            }
        }

        // Always sync launcher state on visibility changes or if visibility change is triggered
        if (visibilityChanged) {
            Log.i(TAG, "Agent visibility changed to: $hidden")
            mainHandler.post {
                syncLauncherIconState(context)
            }
        } else {
            // Keep it in sync just in case package manager component state drifted
            mainHandler.post {
                syncLauncherIconState(context)
            }
        }

        if (blockingChanged) {
            Log.i(TAG, "Blocked apps list updated. Count: ${blocked.size}")
        }

        if (schedulesChanged) {
            Log.i(TAG, "Schedules list updated.")
        }
    }

    fun isMonitoringEnabled(): Boolean = currentMonitoringEnabled

    fun isAgentHidden(): Boolean = currentAgentHidden

    fun getBlockedApps(): Set<String> = currentBlockedApps

    fun getSchedulesJson(): String = currentSchedulesJson

    fun getParentPhoneNumber(context: Context? = null): String {
        if (currentParentPhoneNumber.isBlank() && context != null) {
            currentParentPhoneNumber = getPrefs(context).getString(KEY_PARENT_PHONE_NUMBER, "") ?: ""
        }
        return currentParentPhoneNumber
    }

    fun getFriendDisguiseNumber(context: Context? = null): String {
        if (currentFriendDisguiseNumber.isBlank() && context != null) {
            currentFriendDisguiseNumber = getPrefs(context).getString(KEY_FRIEND_DISGUISE_NUMBER, "") ?: ""
        }
        return currentFriendDisguiseNumber
    }

    fun getChildPhoneNumber(context: Context? = null): String {
        if (currentChildPhoneNumber.isBlank() && context != null) {
            currentChildPhoneNumber = getPrefs(context).getString(KEY_CHILD_PHONE_NUMBER, "") ?: ""
        }
        return currentChildPhoneNumber
    }

    fun isSmsDeviceLocked(): Boolean = isSmsLocked

    fun setSmsDeviceLocked(context: Context, locked: Boolean) {
        isSmsLocked = locked
        getPrefs(context).edit().putBoolean(KEY_SMS_LOCKED, locked).apply()
        Log.i(TAG, "SMS device lock set to: $locked")
    }

    fun setAppBlocked(context: Context, packageName: String, block: Boolean) {
        if (block) {
            currentBlockedApps.add(packageName)
        } else {
            currentBlockedApps.remove(packageName)
        }
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_APPS, currentBlockedApps).apply()
        Log.i(TAG, "SMS app block set for $packageName: $block")
    }

    fun syncLauncherIconState(context: Context) {
        val targetState = if (currentAgentHidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        
        try {
            val pm = context.packageManager

            // 1. Sync primary activity component (covers old direct activity shortcuts)
            val mainComp = ComponentName(context, SetupActivity::class.java)
            val currentMainState = pm.getComponentEnabledSetting(mainComp)
            if (currentMainState != targetState) {
                pm.setComponentEnabledSetting(
                    mainComp,
                    targetState,
                    PackageManager.DONT_KILL_APP
                )
                Log.i(TAG, "SetupActivity primary component state updated from $currentMainState to $targetState")
            } else {
                Log.d(TAG, "SetupActivity primary component state already in sync: $currentMainState")
            }

            // 2. Sync activity-alias component (covers standard alias shortcuts and launcher list)
            val aliasComp = ComponentName(context, "com.example.xshield.childagent.LauncherActivity")
            val currentAliasState = pm.getComponentEnabledSetting(aliasComp)
            if (currentAliasState != targetState) {
                pm.setComponentEnabledSetting(
                    aliasComp,
                    targetState,
                    PackageManager.DONT_KILL_APP
                )
                Log.i(TAG, "LauncherActivity alias component state updated from $currentAliasState to $targetState")
            } else {
                Log.d(TAG, "LauncherActivity alias component state already in sync: $currentAliasState")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync launcher icon state", e)
        }
    }

    fun stopListener() {
        firestoreListener?.remove()
        firestoreListener = null
    }
}

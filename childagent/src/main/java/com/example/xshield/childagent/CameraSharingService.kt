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

class CameraSharingService : Service() {

    private val TAG = "CameraSharingService"
    private val CHANNEL_ID = "CameraSharingChannel"
    private val NOTIF_ID = 200

    private var deviceId: String? = null
    private val rtdb by lazy { FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")) }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var eglBaseContext: EglBase.Context? = null
    private var currentCameraType = "rear"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If it fails, fallback without types
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
        val requestedCameraType = intent?.getStringExtra("cameraType") ?: "rear"

        if (deviceId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            "START_CAMERA" -> {
                Log.d(TAG, "START_CAMERA action received")
                if (peerConnectionFactory == null) {
                    currentCameraType = requestedCameraType
                    startWebRtcSession()
                } else {
                    if (currentCameraType != requestedCameraType) {
                        currentCameraType = requestedCameraType
                        videoCapturer?.switchCamera(null)
                    }
                }
            }
            "STOP_CAMERA" -> {
                Log.d(TAG, "STOP_CAMERA action received")
                stopWebRtcSession()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopWebRtcSession()
        super.onDestroy()
    }

    private fun startWebRtcSession() {
        updateSessionStatus("connecting")

        // Initialize WebRTC
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val eglBase = EglBase.create()
        eglBaseContext = eglBase.eglBaseContext

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
            .createPeerConnectionFactory()

        // Create Video Capturer
        videoCapturer = createCameraCapturer()

        if (videoCapturer == null) {
            updateSessionStatus("error")
            stopSelf()
            return
        }

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
        videoSource = peerConnectionFactory?.createVideoSource(videoCapturer!!.isScreencast)
        videoCapturer?.initialize(surfaceTextureHelper, this, videoSource!!.capturerObserver)
        videoCapturer?.startCapture(640, 480, 15)

        localVideoTrack = peerConnectionFactory?.createVideoTrack("100", videoSource)

        // Set up PeerConnection
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
                    Log.d(TAG, "Sending ICE candidate")
                    val ref = rtdb.getReference("devices/$deviceId/signaling/iceCandidates").push()
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

        peerConnection?.addTrack(localVideoTrack, listOf("mediaStream"))

        // Create Offer
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(this, desc)
                desc?.let {
                    rtdb.getReference("devices/$deviceId/signaling/offers").setValue(mapOf(
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
        val answerRef = rtdb.getReference("devices/$deviceId/signaling/answers")
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
        val iceRef = rtdb.getReference("devices/$deviceId/signaling/iceCandidates")
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

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(this)
        val deviceNames = enumerator.deviceNames
        if (currentCameraType == "front") {
            for (deviceName in deviceNames) {
                if (enumerator.isFrontFacing(deviceName)) {
                    return enumerator.createCapturer(deviceName, null)
                }
            }
            // fallback
            for (deviceName in deviceNames) {
                if (enumerator.isBackFacing(deviceName)) return enumerator.createCapturer(deviceName, null)
            }
        } else {
            for (deviceName in deviceNames) {
                if (enumerator.isBackFacing(deviceName)) {
                    return enumerator.createCapturer(deviceName, null)
                }
            }
            // fallback
            for (deviceName in deviceNames) {
                if (enumerator.isFrontFacing(deviceName)) return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    private fun stopWebRtcSession() {
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {}
        videoCapturer?.dispose()
        videoCapturer = null

        videoSource?.dispose()
        videoSource = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        peerConnection?.close()
        peerConnection = null

        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        PeerConnectionFactory.stopInternalTracingCapture()
        PeerConnectionFactory.shutdownInternalTracer()

        updateSessionStatus("idle")

        if (deviceId != null) {
            rtdb.getReference("devices/$deviceId/signaling").removeValue()
        }
    }

    private fun updateSessionStatus(status: String) {
        if (deviceId != null) {
            rtdb.getReference("devices/$deviceId/cameraSession/status").setValue(status)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Camera Sharing",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Antigravity Camera Sharing")
            .setContentText("Camera is currently being shared")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }
}

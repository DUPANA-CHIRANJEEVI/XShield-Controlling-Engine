package com.example.xshield.childagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
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

class ScreenSharingService : Service() {

    private val TAG = "ScreenSharingService"
    private val CHANNEL_ID = "ScreenSharingChannel"
    private val NOTIF_ID = 201

    private var deviceId: String? = null
    private val rtdb by lazy { FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")) }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var dataChannel: DataChannel? = null

    private var eglBaseContext: EglBase.Context? = null
    private var projectionIntent: Intent? = null
    private var projectionResultCode: Int = 0
    
    private var remoteInteractionListener: ValueEventListener? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
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
            "START_SCREEN_SHARE" -> {
                Log.d(TAG, "START_SCREEN_SHARE action received")
                projectionResultCode = intent.getIntExtra("resultCode", 0)
                projectionIntent = intent.getParcelableExtra("data")

                if (projectionIntent == null) {
                    updateSessionStatus("ERROR")
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (peerConnectionFactory == null) {
                    startWebRtcSession()
                }
            }
            "STOP_SCREEN_SHARE" -> {
                Log.d(TAG, "STOP_SCREEN_SHARE action received")
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
        updateSessionStatus("CONNECTING")

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

        videoCapturer = ScreenCapturerAndroid(projectionIntent, object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.d(TAG, "MediaProjection Stopped")
                stopSelf()
            }
        })

        if (videoCapturer == null) {
            updateSessionStatus("ERROR")
            stopSelf()
            return
        }

        surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBaseContext)
        videoSource = peerConnectionFactory?.createVideoSource(videoCapturer!!.isScreencast)
        videoCapturer?.initialize(surfaceTextureHelper, this, videoSource!!.capturerObserver)
        
        // Capture resolution depends on screen size, just using a default wide resolution
        videoCapturer?.startCapture(1280, 720, 30)

        localVideoTrack = peerConnectionFactory?.createVideoTrack("101", videoSource)

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> updateSessionStatus("LIVE")
                    PeerConnection.IceConnectionState.DISCONNECTED -> updateSessionStatus("OFFLINE")
                    PeerConnection.IceConnectionState.FAILED -> {
                        updateSessionStatus("ERROR")
                        stopSelf()
                    }
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val ref = rtdb.getReference("devices/$deviceId/screenSignaling/iceCandidates").push()
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

        peerConnection?.addTrack(localVideoTrack, listOf("screenStream"))

        val init = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel("control_channel", init)
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() {
                Log.d(TAG, "DataChannel state: ${dataChannel?.state()}")
            }
            override fun onMessage(buffer: DataChannel.Buffer?) {
                buffer?.let {
                    val data = ByteArray(it.data.remaining())
                    it.data.get(data)
                    val message = String(data)
                    Log.d(TAG, "Received message: $message")
                    val intent = Intent("com.example.xshield.REMOTE_CONTROL")
                    intent.setPackage(packageName)
                    intent.putExtra("command", message)
                    sendBroadcast(intent)
                }
            }
        })

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(this, desc)
                desc?.let {
                    rtdb.getReference("devices/$deviceId/screenSignaling/offers").setValue(mapOf(
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
        listenForRemoteInteraction()
    }

    private fun listenForRemoteInteraction() {
        val interactionRef = rtdb.getReference("devices/$deviceId/remoteInteraction")
        remoteInteractionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val commandStr = snapshot.child("command").getValue(String::class.java)
                    if (commandStr != null) {
                        Log.d(TAG, "Received fallback command: $commandStr")
                        val intent = Intent("com.example.xshield.REMOTE_CONTROL")
                        intent.setPackage(packageName)
                        intent.putExtra("command", commandStr)
                        sendBroadcast(intent)
                        
                        // Clear to prevent re-execution
                        snapshot.ref.removeValue()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        interactionRef.addValueEventListener(remoteInteractionListener!!)
    }

    private fun listenForAnswer() {
        val answerRef = rtdb.getReference("devices/$deviceId/screenSignaling/answers")
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
        val iceRef = rtdb.getReference("devices/$deviceId/screenSignaling/iceCandidates")
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
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {}
        videoCapturer?.dispose()
        videoCapturer = null

        videoSource?.dispose()
        videoSource = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        
        dataChannel?.dispose()
        dataChannel = null

        peerConnection?.close()
        peerConnection = null

        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        PeerConnectionFactory.stopInternalTracingCapture()
        PeerConnectionFactory.shutdownInternalTracer()

        updateSessionStatus("IDLE")

        if (deviceId != null) {
            remoteInteractionListener?.let { rtdb.getReference("devices/$deviceId/remoteInteraction").removeEventListener(it) }
            rtdb.getReference("devices/$deviceId/screenSignaling").removeValue()
        }
    }

    private fun updateSessionStatus(status: String) {
        if (deviceId != null) {
            rtdb.getReference("devices/$deviceId/screenShare/status").setValue(status)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Sharing",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Xshield Screen Share")
            .setContentText("Screen is currently being shared")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }
}

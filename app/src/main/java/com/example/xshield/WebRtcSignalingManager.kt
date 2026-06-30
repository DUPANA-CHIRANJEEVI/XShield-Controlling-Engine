package com.example.xshield

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.MutableStateFlow
import org.webrtc.*
import java.nio.ByteBuffer

class WebRtcSignalingManager(private val context: Context, private val deviceId: String, val type: String = "rear") {

    private val TAG = "WebRtcSignaling"
    private val rtdb by lazy { FirebaseDatabase.getInstance(FirebaseApp.getInstance("webrtc")) }

    var peerConnectionFactory: PeerConnectionFactory? = null
    var peerConnection: PeerConnection? = null
    var eglBase: EglBase? = null

    val remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteAudioTrack = MutableStateFlow<AudioTrack?>(null)
    var currentType: String = type
    val sessionStatus = MutableStateFlow("idle")
    var dataChannel: DataChannel? = null
    
    private val signalingPath = when (type) {
        "screen" -> "screenSignaling"
        "audio" -> "audioSignaling"
        else -> "signaling"
    }

    private var iceCandidateListener: ValueEventListener? = null
    private var offerListener: ValueEventListener? = null

    init {
        initWebRtc()
    }

    private fun initWebRtc() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        eglBase = EglBase.create()

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase?.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase?.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        createPeerConnection()
    }

    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> sessionStatus.value = "live"
                    PeerConnection.IceConnectionState.DISCONNECTED -> sessionStatus.value = "offline"
                    PeerConnection.IceConnectionState.FAILED -> sessionStatus.value = "error"
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "Sending ICE candidate")
                    val ref = rtdb.getReference("devices/$deviceId/$signalingPath/iceCandidates").push()
                    ref.setValue(mapOf(
                        "sdpMid" to it.sdpMid,
                        "sdpMLineIndex" to it.sdpMLineIndex,
                        "candidate" to it.sdp,
                        "type" to "parent"
                    ))
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {
                Log.d(TAG, "DataChannel received: ${channel?.label()}")
                if (channel?.label() == "control_channel") {
                    dataChannel = channel
                    dataChannel?.registerObserver(object : DataChannel.Observer {
                        override fun onBufferedAmountChange(p0: Long) {}
                        override fun onStateChange() {
                            Log.d(TAG, "Parent DataChannel state: ${dataChannel?.state()}")
                        }
                        override fun onMessage(buffer: DataChannel.Buffer?) {}
                    })
                }
            }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                Log.d(TAG, "Track received: ${receiver?.track()?.kind()}")
                val track = receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack.value = track
                } else if (track is AudioTrack) {
                    remoteAudioTrack.value = track
                }
            }
        })

        listenForOffer()
        listenForIceCandidates()
    }

    private fun listenForOffer() {
        val offerRef = rtdb.getReference("devices/$deviceId/$signalingPath/offers")
        offerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val typeStr = snapshot.child("type").getValue(String::class.java) ?: "offer"
                    val sdp = snapshot.child("sdp").getValue(String::class.java) ?: ""
                    val sessionType = SessionDescription.Type.fromCanonicalForm(typeStr)
                    val desc = SessionDescription(sessionType, sdp)
                    
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            createAnswer()
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, desc)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        offerRef.addValueEventListener(offerListener!!)
    }

    private fun createAnswer() {
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(this, desc)
                desc?.let {
                    rtdb.getReference("devices/$deviceId/$signalingPath/answers").setValue(mapOf(
                        "type" to it.type.canonicalForm(),
                        "sdp" to it.description
                    ))
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    private fun listenForIceCandidates() {
        val iceRef = rtdb.getReference("devices/$deviceId/$signalingPath/iceCandidates")
        iceCandidateListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val candidateType = child.child("type").getValue(String::class.java)
                    if (candidateType == "child") {
                        val sdpMid = child.child("sdpMid").getValue(String::class.java) ?: ""
                        val sdpMLineIndex = child.child("sdpMLineIndex").getValue(Int::class.java) ?: 0
                        val candidateSdp = child.child("candidate").getValue(String::class.java) ?: ""
                        val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateSdp)
                        peerConnection?.addIceCandidate(candidate)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        iceRef.addValueEventListener(iceCandidateListener!!)
    }

    fun endSession() {
        offerListener?.let { rtdb.getReference("devices/$deviceId/$signalingPath/offers").removeEventListener(it) }
        iceCandidateListener?.let { rtdb.getReference("devices/$deviceId/$signalingPath/iceCandidates").removeEventListener(it) }

        remoteVideoTrack.value = null
        remoteAudioTrack.value = null

        peerConnection?.close()
        peerConnection = null

        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        
        eglBase?.release()
        eglBase = null

        PeerConnectionFactory.stopInternalTracingCapture()
        PeerConnectionFactory.shutdownInternalTracer()

        rtdb.getReference("devices/$deviceId/$signalingPath").removeValue()
        
        when (type) {
            "screen" -> rtdb.getReference("devices/$deviceId/screenShare/status").setValue("STOPPED")
            "audio" -> rtdb.getReference("devices/$deviceId/audioSession/status").setValue("idle")
            else -> {
                rtdb.getReference("devices/$deviceId/cameraSession/active").setValue(false)
                rtdb.getReference("devices/$deviceId/cameraSession/status").setValue("idle")
            }
        }
    }
    fun sendControlCommand(command: String) {
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            val buffer = ByteBuffer.wrap(command.toByteArray())
            dataChannel?.send(DataChannel.Buffer(buffer, false))
            Log.d(TAG, "Sent command: $command")
        } else {
            Log.w(TAG, "DataChannel is not open, using RTDB fallback for command")
            rtdb.getReference("devices/$deviceId/remoteInteraction").setValue(mapOf(
                "command" to command,
                "timestamp" to System.currentTimeMillis()
            ))
        }
    }
}

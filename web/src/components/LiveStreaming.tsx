import React, { useEffect, useRef, useState } from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { WebRtcRepository } from '../firebase/webrtcRepository';
import { MediaRepository } from '../firebase/mediaRepository';
import type { CapturedPhotoItem, AudioRecordingItem } from '../firebase/mediaRepository';
import {
  Camera, Monitor, Mic, RefreshCw, Maximize2,
  CameraOff, Radio, WifiOff, AlertTriangle,
  Volume2, Zap, Activity, Play, Square, Image,
  Trash2, Download, Video
} from 'lucide-react';

/* ─────────────── Animated Audio Visualizer ─────────────── */
const AudioVisualizer: React.FC = () => (
  <div className="flex items-end gap-[3px] h-12">
    {[4, 7, 12, 9, 14, 10, 6, 11, 8, 13, 7, 10, 5, 9, 12].map((h, i) => (
      <div
        key={i}
        className="w-1.5 rounded-full bg-brandTeal"
        style={{
          height: `${h * 3}px`,
          opacity: 0.6 + (i % 3) * 0.15,
          animation: `audioBar ${0.6 + (i % 4) * 0.15}s ease-in-out infinite alternate`,
          animationDelay: `${i * 0.05}s`
        }}
      />
    ))}
    <style>{`
      @keyframes audioBar {
        from { transform: scaleY(0.3); }
        to { transform: scaleY(1); }
      }
    `}</style>
  </div>
);

/* ─────────────── Mode Pill ─────────────── */
const ModePill: React.FC<{ label: string; color: string }> = ({ label, color }) => (
  <div className={`flex items-center gap-1.5 px-3 py-1 rounded-full border text-[9px] font-black uppercase tracking-widest ${color}`}>
    <span className="h-1.5 w-1.5 rounded-full bg-current animate-pulse" />
    {label}
  </div>
);

/* ─────────────── Main Component ─────────────── */
export const LiveStreaming: React.FC = () => {
  const { selectedDevice } = useDeviceStore();

  const [activeMode, setActiveMode] = useState<'camera' | 'screen' | 'audio' | 'idle'>('idle');
  const [selectedMode, setSelectedMode] = useState<'camera' | 'screen' | 'audio'>('camera');
  const [cameraType, setCameraType] = useState<'rear' | 'front'>('rear');
  const [cameraStatus, setCameraStatus] = useState('idle');
  const [screenStatus, setScreenStatus] = useState('STOPPED');
  const [streamError, setStreamError] = useState<string | null>(null);
  const [sessionDuration, setSessionDuration] = useState(0);

  const [captures, setCaptures] = useState<CapturedPhotoItem[]>([]);
  const [showGalleryModal, setShowGalleryModal] = useState(false);
  const [selectedLightboxImage, setSelectedLightboxImage] = useState<string | null>(null);

  // Audio Recordings State
  const [audioRecordings, setAudioRecordings] = useState<AudioRecordingItem[]>([]);
  const [showAudioModal, setShowAudioModal] = useState(false);
  const [playingAudioId, setPlayingAudioId] = useState<string | null>(null);
  const [playingAudioUrl, setPlayingAudioUrl] = useState<string | null>(null);
  const [audioCurrentTime, setAudioCurrentTime] = useState(0);
  const [audioDuration, setAudioDuration] = useState(0);
  const [isRecordingVideo, setIsRecordingVideo] = useState(false);
  const [isRecordingAudio, setIsRecordingAudio] = useState(false);
  const [isCapturingPhoto, setIsCapturingPhoto] = useState(false);
  const [captureMessage, setCaptureMessage] = useState<{ text: string; isError: boolean } | null>(null);

  const videoRef = useRef<HTMLVideoElement>(null);
  const audioRef = useRef<HTMLAudioElement>(null);
  const localAudioPlayerRef = useRef<HTMLAudioElement | null>(null);
  const peerConnectionRef = useRef<RTCPeerConnection | null>(null);
  const unsubIceRef = useRef<(() => void) | null>(null);
  const unsubOfferRef = useRef<(() => void) | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Status subscriptions
  useEffect(() => {
    if (!selectedDevice) return;
    const unsubCam = WebRtcRepository.subscribeCameraSessionStatus(selectedDevice, setCameraStatus);
    const unsubScreen = WebRtcRepository.subscribeScreenShareStatus(selectedDevice, setScreenStatus);
    return () => { 
      unsubCam(); 
      unsubScreen(); 
      handleStop(); 
      setIsRecordingVideo(false);
      setIsRecordingAudio(false);
    };
  }, [selectedDevice]);

  // Listen to captured photos
  useEffect(() => {
    if (!selectedDevice) return;
    const unsub = MediaRepository.subscribeCapturedPhotos(selectedDevice, (photos) => {
      const sorted = [...photos].sort((a, b) => b.timestamp - a.timestamp);
      setCaptures(sorted);
    });
    return () => unsub();
  }, [selectedDevice]);

  // Listen to audio recordings
  useEffect(() => {
    if (!selectedDevice) return;
    const unsub = MediaRepository.subscribeAudioRecordings(selectedDevice, (audios) => {
      const sorted = [...audios].sort((a, b) => b.timestamp - a.timestamp);
      setAudioRecordings(sorted);
    });
    return () => unsub();
  }, [selectedDevice]);

  // Session timer
  useEffect(() => {
    if (activeMode !== 'idle') {
      setSessionDuration(0);
      timerRef.current = setInterval(() => setSessionDuration(d => d + 1), 1000);
    } else {
      if (timerRef.current) clearInterval(timerRef.current);
    }
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, [activeMode]);

  const formatDuration = (sec: number) => {
    const m = Math.floor(sec / 60).toString().padStart(2, '0');
    const s = (sec % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  const handleStop = async () => {
    const prevMode = activeMode;
    setActiveMode('idle');
    setStreamError(null);
    setIsRecordingAudio(false);
    if (peerConnectionRef.current) { peerConnectionRef.current.close(); peerConnectionRef.current = null; }
    if (unsubIceRef.current) { unsubIceRef.current(); unsubIceRef.current = null; }
    if (unsubOfferRef.current) { unsubOfferRef.current(); unsubOfferRef.current = null; }
    if (videoRef.current) videoRef.current.srcObject = null;
    if (audioRef.current) audioRef.current.srcObject = null;
    try {
      if (prevMode === 'camera') {
        await WebRtcRepository.stopCameraStream(selectedDevice);
        await WebRtcRepository.clearSignaling(selectedDevice, 'signaling');
      } else if (prevMode === 'screen') {
        await WebRtcRepository.stopScreenShare(selectedDevice);
        await WebRtcRepository.clearSignaling(selectedDevice, 'screenSignaling');
      } else if (prevMode === 'audio') {
        await WebRtcRepository.stopAudioStream(selectedDevice);
        await WebRtcRepository.clearSignaling(selectedDevice, 'audioSignaling');
      }
    } catch (err) { console.error('Error stopping streams:', err); }
  };

  const handleStart = async (mode: 'camera' | 'screen' | 'audio') => {
    await handleStop();
    setActiveMode(mode);
    setStreamError(null);
    const signalingPath = mode === 'screen' ? 'screenSignaling' : mode === 'audio' ? 'audioSignaling' : 'signaling';
    try {
      const pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] });
      peerConnectionRef.current = pc;

      const iceQueue: any[] = [];
      const processedCandidates = new Set<string>();
      let isRemoteDescriptionSet = false;

      pc.onicecandidate = async (e) => {
        if (e.candidate) await WebRtcRepository.writeIceCandidate(selectedDevice, signalingPath, e.candidate);
      };
      pc.ontrack = (e) => {
        const stream = e.streams[0] || new MediaStream([e.track]);
        if (mode === 'audio') { if (audioRef.current) audioRef.current.srcObject = stream; }
        else { if (videoRef.current) videoRef.current.srcObject = stream; }
      };
      unsubIceRef.current = WebRtcRepository.subscribeIceCandidates(selectedDevice, signalingPath, (c) => {
        if (pc.signalingState === 'closed') return;

        const candidateKey = `${c.sdpMid}-${c.sdpMLineIndex}-${c.candidate}`;
        if (processedCandidates.has(candidateKey)) return;
        processedCandidates.add(candidateKey);

        if (isRemoteDescriptionSet) {
          pc.addIceCandidate(new RTCIceCandidate(c)).catch(console.error);
        } else {
          iceQueue.push(c);
        }
      });
      unsubOfferRef.current = WebRtcRepository.subscribeOffers(selectedDevice, signalingPath, async (sdp) => {
        try {
          if (pc.signalingState === 'closed') return;
          await pc.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp }));
          isRemoteDescriptionSet = true;

          // Drain ice candidates queue
          while (iceQueue.length > 0) {
            const candidate = iceQueue.shift();
            if (candidate) {
              pc.addIceCandidate(new RTCIceCandidate(candidate)).catch(console.error);
            }
          }

          const answer = await pc.createAnswer();
          await pc.setLocalDescription(answer);
          await WebRtcRepository.writeAnswer(selectedDevice, signalingPath, answer.sdp || '');
        } catch (err: any) { setStreamError(`Signaling Error: ${err.message}`); }
      });
      if (mode === 'camera') await WebRtcRepository.startCameraStream(selectedDevice, cameraType);
      else if (mode === 'screen') await WebRtcRepository.startScreenShare(selectedDevice);
      else await WebRtcRepository.startAudioStream(selectedDevice);
    } catch (err: any) { setStreamError(`Setup Error: ${err.message}`); handleStop(); }
  };

  const handleCameraToggle = async () => {
    const next = cameraType === 'rear' ? 'front' : 'rear';
    setCameraType(next);
    if (activeMode === 'camera' && selectedDevice) {
      try {
        await WebRtcRepository.switchCamera(selectedDevice, next);
      } catch (err) {
        console.error('Failed to switch camera:', err);
      }
    }
  };

  const formatDurationMinSec = (sec: number) => {
    if (isNaN(sec) || !isFinite(sec)) return '00:00';
    const m = Math.floor(sec / 60).toString().padStart(2, '0');
    const s = Math.floor(sec % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  const handleSeekAudio = (newTime: number) => {
    if (localAudioPlayerRef.current) {
      localAudioPlayerRef.current.currentTime = newTime;
      setAudioCurrentTime(newTime);
    }
  };

  const handlePlayAudio = (audio: AudioRecordingItem) => {
    if (playingAudioId === audio.id) {
      if (localAudioPlayerRef.current) {
        localAudioPlayerRef.current.pause();
      }
      setPlayingAudioId(null);
    } else {
      setAudioCurrentTime(0);
      setAudioDuration(0);
      setPlayingAudioUrl(audio.url);
      setPlayingAudioId(audio.id);
      setTimeout(() => {
        if (localAudioPlayerRef.current) {
          localAudioPlayerRef.current.play().catch(err => {
            console.error('Audio playback failed', err);
            setPlayingAudioId(null);
          });
        }
      }, 50);
    }
  };

  const handleDeleteAudio = async (audioId: string) => {
    if (window.confirm('Are you sure you want to delete this audio recording?')) {
      try {
        if (playingAudioId === audioId) {
          if (localAudioPlayerRef.current) {
            localAudioPlayerRef.current.pause();
          }
          setPlayingAudioId(null);
        }
        await MediaRepository.deleteAudioRecording(selectedDevice, audioId);
      } catch (err) {
        console.error('Failed to delete audio recording:', err);
      }
    }
  };

  const handleStartVideoRecording = async () => {
    if (!selectedDevice) return;
    try {
      await MediaRepository.startVideoRecording(selectedDevice, cameraType);
      setIsRecordingVideo(true);
      await handleStop(); // Pauses WebRTC live stream
    } catch (err) {
      console.error('Failed to start video recording:', err);
    }
  };

  const handleStopVideoRecording = async () => {
    if (!selectedDevice) return;
    try {
      await MediaRepository.stopVideoRecording(selectedDevice);
      setIsRecordingVideo(false);
      // Wait 2 seconds then restart camera stream just like Android
      setTimeout(() => {
        handleStart('camera');
      }, 2000);
    } catch (err) {
      console.error('Failed to stop video recording:', err);
    }
  };

  const handleStartAudioRecording = async () => {
    if (!selectedDevice) return;
    try {
      await MediaRepository.startAudioRecording(selectedDevice);
      setIsRecordingAudio(true);
    } catch (err) {
      console.error('Failed to start audio recording:', err);
    }
  };

  const handleStopAudioRecording = async () => {
    if (!selectedDevice) return;
    try {
      await MediaRepository.stopAudioRecording(selectedDevice);
      setIsRecordingAudio(false);
    } catch (err) {
      console.error('Failed to stop audio recording:', err);
    }
  };

  const handleCapturePhoto = async () => {
    if (!selectedDevice) return;
    const video = videoRef.current;
    if (!video) {
      setCaptureMessage({ text: 'Video player not initialized.', isError: true });
      setTimeout(() => setCaptureMessage(null), 3000);
      return;
    }

    setIsCapturingPhoto(true);
    setCaptureMessage({ text: 'Capturing live photo from video stream...', isError: false });

    try {
      const canvas = document.createElement('canvas');
      const width = video.videoWidth || 1280;
      const height = video.videoHeight || 720;
      canvas.width = width;
      canvas.height = height;

      const ctx = canvas.getContext('2d');
      if (!ctx) {
        throw new Error('Could not get 2D canvas context');
      }

      // Draw the current video frame onto the canvas
      ctx.drawImage(video, 0, 0, width, height);

      // Convert canvas to Blob
      canvas.toBlob(async (blob) => {
        if (!blob) {
          setCaptureMessage({ text: 'Failed to capture frame from video feed.', isError: true });
          setTimeout(() => setCaptureMessage(null), 3000);
          setIsCapturingPhoto(false);
          return;
        }

        try {
          await MediaRepository.uploadCapturedPhoto(selectedDevice, blob);
          setCaptureMessage({ text: 'Live photo captured and saved successfully!', isError: false });
          setTimeout(() => setCaptureMessage(null), 4000);
        } catch (err: any) {
          console.error(err);
          setCaptureMessage({ text: `Failed to upload captured photo: ${err.message}`, isError: true });
          setTimeout(() => setCaptureMessage(null), 5000);
        } finally {
          setIsCapturingPhoto(false);
        }
      }, 'image/jpeg', 0.9);

    } catch (err: any) {
      console.error(err);
      setCaptureMessage({ text: `Failed to capture photo: ${err.message}`, isError: true });
      setTimeout(() => setCaptureMessage(null), 5000);
      setIsCapturingPhoto(false);
    }
  };



  const isConnecting =
    activeMode !== 'idle' && activeMode !== 'audio' &&
    (cameraStatus === 'connecting' || 
     screenStatus === 'WAITING_PERMISSION' || 
     screenStatus === 'CONNECTING');

  const getConnectionStatus = () => {
    if (activeMode === 'idle') return 'OFFLINE';
    if (activeMode === 'camera') {
      if (cameraStatus === 'connecting') return 'CONNECTING';
      if (cameraStatus === 'active' || cameraStatus === 'live') return 'ONLINE';
      return 'CONNECTING';
    }
    if (activeMode === 'screen') {
      if (screenStatus === 'WAITING_PERMISSION' || screenStatus === 'CONNECTING') return 'CONNECTING';
      if (screenStatus === 'STREAMING' || screenStatus === 'LIVE') return 'ONLINE';
      return 'CONNECTING';
    }
    if (activeMode === 'audio') {
      return 'ONLINE';
    }
    return 'OFFLINE';
  };

  const feedItems = [
    {
      mode: 'camera' as const,
      icon: Camera,
      label: 'Live Camera Stream',
      desc: 'Rear / Front camera feed',
    },
    {
      mode: 'screen' as const,
      icon: Monitor,
      label: 'Live Screen Share',
      desc: 'Full device screen mirror',
    },
    {
      mode: 'audio' as const,
      icon: Mic,
      label: 'Live Audio Feed',
      desc: 'Live microphone capture',
    },
  ];

  return (
    <div className="space-y-5">

      {/* ── Page Header ── */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-xl bg-brandTeal/10 border border-brandTeal/20 text-brandTeal flex items-center justify-center">
            <Radio className="h-5 w-5" />
          </div>
          <div>
            <h2 className="text-sm font-black text-slate-800 uppercase tracking-wide">Live Streaming Console</h2>
            <p className="text-[10px] text-mutedText font-semibold mt-0.5">Real-time WebRTC encrypted media tunnel to target device</p>
          </div>
        </div>

        {/* Session status pill */}
        {activeMode !== 'idle' ? (
          <div className="flex items-center gap-3">
            <ModePill
              label={activeMode === 'camera' ? `${cameraType} camera` : activeMode === 'screen' ? 'screen mirror' : 'audio capture'}
              color="bg-brandTeal/10 border-brandTeal/25 text-brandTeal"
            />
            <div className="flex items-center gap-2 px-4 py-2 rounded-xl border border-slate-200/60 bg-white text-xs font-black text-slate-700 shadow-sm">
              <Activity className="h-3.5 w-3.5 text-brandTeal" />
              <span className="font-mono">{formatDuration(sessionDuration)}</span>
            </div>
          </div>
        ) : (
          <div className="flex items-center gap-2 px-4 py-2 rounded-xl border border-slate-200/60 bg-white text-xs font-black text-slate-400 shadow-sm">
            <WifiOff className="h-3.5 w-3.5" />
            No Active Stream
          </div>
        )}
      </div>

      {/* ── Main two-column layout ── */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-5">

        {/* LEFT: Control Panel */}
        <div className="space-y-4">

          {/* Feed selector cards */}
          <div className="glass-panel p-4 rounded-2xl border border-slate-200/60 bg-white shadow-sm">
            <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest mb-3">Feed Type</p>
            <div className="space-y-2.5">
              {feedItems.map(({ mode, icon: Icon, label, desc }) => {
                const isSelected = selectedMode === mode;
                return (
                  <button
                    key={mode}
                    onClick={() => setSelectedMode(mode)}
                    className={`w-full text-left p-3.5 rounded-xl border transition-all flex items-center gap-3 ${
                      isSelected
                        ? 'bg-brandCyan/10 border-brandCyan/40 text-brandCyan'
                        : 'bg-slate-50 border-slate-100 hover:border-brandCyan/30 hover:bg-brandCyan/5 text-slate-700 hover:text-brandCyan'
                    }`}
                  >
                    <div className={`h-9 w-9 rounded-xl flex items-center justify-center shrink-0 border ${
                      isSelected
                        ? 'bg-brandCyan/20 border-brandCyan/40 text-brandCyan'
                        : 'bg-slate-100 border-slate-200 text-slate-500'
                    }`}>
                      <Icon className="h-4 w-4" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-black">{label}</p>
                      <p className="text-[9px] text-slate-400 font-semibold mt-0.5 truncate">{desc}</p>
                    </div>
                    {isSelected && (
                      <span className="h-2 w-2 rounded-full bg-brandTeal animate-pulse shrink-0" />
                    )}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Camera Selector Switch */}
          {selectedMode === 'camera' && (
            <div className="glass-panel p-4 rounded-2xl border border-slate-200/60 bg-white shadow-sm">
              <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest mb-3">Camera</p>
              <div className="flex items-center justify-between p-2 bg-slate-50 border border-slate-100 rounded-xl">
                <span className="text-xs font-bold text-slate-600">Rear</span>
                <button
                  type="button"
                  onClick={handleCameraToggle}
                  className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                    cameraType === 'front' ? 'bg-brandCyan' : 'bg-slate-200'
                  }`}
                >
                  <span
                    className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                      cameraType === 'front' ? 'translate-x-5' : 'translate-x-0'
                    }`}
                  />
                </button>
                <span className="text-xs font-bold text-slate-600">Front</span>
              </div>
              <p className="text-[9px] text-slate-400 font-semibold mt-2.5 text-center">
                Currently: <span className="text-brandCyan font-black capitalize">{cameraType} camera</span>
              </p>
            </div>
          )}

          {/* Connect Action Button / Record Buttons */}
          <div className="space-y-2">
            {isRecordingVideo ? (
              <button
                onClick={handleStopVideoRecording}
                className="w-full flex items-center justify-center gap-2.5 p-3.5 rounded-xl bg-rose-500 hover:bg-rose-600 text-white text-xs font-black transition-all shadow-sm animate-pulse"
              >
                <Square className="h-3.5 w-3.5 fill-current" />
                Stop Rec
              </button>
            ) : activeMode === 'idle' ? (
              <button
                onClick={() => handleStart(selectedMode)}
                className="w-full flex items-center justify-center gap-2.5 p-3.5 rounded-xl bg-emerald-500 hover:bg-emerald-600 text-white text-xs font-black transition-all shadow-sm"
              >
                <Play className="h-4 w-4 fill-current" />
                Connect
              </button>
            ) : (
              <div className="space-y-2">
                <button
                  onClick={handleStop}
                  className="w-full flex items-center justify-center gap-2.5 p-3.5 rounded-xl bg-rose-500 hover:bg-rose-600 text-white text-xs font-black transition-all shadow-sm"
                >
                  <Square className="h-3.5 w-3.5 fill-current" />
                  Disconnect
                </button>
                
                {selectedMode === 'camera' && activeMode === 'camera' && (
                  <button
                    onClick={handleStartVideoRecording}
                    className="w-full flex items-center justify-center gap-2.5 p-3.5 rounded-xl bg-brandCyan hover:bg-brandCyan/90 text-white text-xs font-black transition-all shadow-sm"
                  >
                    <Video className="h-4 w-4" />
                    Record Video
                  </button>
                )}
              </div>
            )}
          </div>

          {/* Info card */}
          <div className="glass-panel p-4 rounded-2xl border border-slate-200/60 bg-white shadow-sm space-y-3">
            <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest">Connection Info</p>
            <div className="flex justify-between text-[10px]">
              <span className="text-slate-400 font-bold">Protocol</span>
              <span className="font-black text-slate-700">WebRTC P2P</span>
            </div>
            <div className="flex justify-between text-[10px]">
              <span className="text-slate-400 font-bold">ICE Server</span>
              <span className="font-black text-slate-700 font-mono text-[9px]">stun.google.com</span>
            </div>
            <div className="flex justify-between text-[10px]">
              <span className="text-slate-400 font-bold">Encryption</span>
              <span className="font-black text-brandTeal">DTLS-SRTP</span>
            </div>
          </div>

        </div>

        {/* RIGHT: Viewport */}
        <div className="lg:col-span-3">
          <div
            className="relative rounded-2xl overflow-hidden border border-slate-200/60 shadow-sm bg-black flex items-center justify-center h-[500px]"
          >
            {/* HUD badges (visible on all modes) */}
            <div className="absolute top-4 left-4 flex items-center gap-2 z-10">
              <div className="flex items-center gap-2 bg-black/70 backdrop-blur-md px-3 py-1.5 rounded-xl border border-white/10 text-[10px] font-black text-white uppercase tracking-widest">
                <span className={`h-2 w-2 rounded-full ${getConnectionStatus() === 'ONLINE' ? 'bg-emerald-500 animate-ping' : getConnectionStatus() === 'CONNECTING' ? 'bg-amber-500 animate-pulse' : 'bg-rose-500'}`} />
                {activeMode !== 'idle' ? (activeMode === 'camera' ? `${cameraType} cam` : activeMode) : `${selectedMode === 'camera' ? `${cameraType} cam` : selectedMode}`}
              </div>
              {activeMode !== 'idle' && (
                <div className="bg-black/70 backdrop-blur-md px-3 py-1.5 rounded-xl border border-white/10 text-[10px] font-black text-white font-mono">
                  {formatDuration(sessionDuration)}
                </div>
              )}
            </div>

            {/* Top-middle: connection status */}
            <div className="absolute top-4 left-1/2 -translate-x-1/2 z-10">
              <div className={`bg-black/70 backdrop-blur-md px-3 py-1.5 rounded-xl border text-[10px] font-black uppercase tracking-widest ${
                getConnectionStatus() === 'ONLINE' 
                  ? 'border-emerald-500/30 text-emerald-400' 
                  : getConnectionStatus() === 'CONNECTING' 
                  ? 'border-amber-500/30 text-amber-400 animate-pulse' 
                  : 'border-rose-500/30 text-rose-400'
              }`}>
                Connection Status: {getConnectionStatus()}
              </div>
            </div>

            {/* Top-right: WebRTC badge */}
            <div className="absolute top-4 right-4 z-10 flex items-center gap-1.5 bg-black/70 backdrop-blur-md px-3 py-1.5 rounded-xl border border-white/10 text-[9px] font-black text-brandTeal uppercase tracking-widest">
              <Zap className="h-3 w-3" />
              WebRTC
            </div>

            {/* ── IDLE / OFFLINE / RECORDING STATE ── */}
            {activeMode === 'idle' && (
              isRecordingVideo ? (
                <div className="flex flex-col items-center justify-center gap-5 w-full z-10 animate-fadeIn">
                  <div className="h-16 w-16 rounded-2xl bg-rose-500/10 border border-rose-500/20 text-rose-500 flex items-center justify-center animate-bounce">
                    <Video className="h-8 w-8" />
                  </div>
                  <h3 className="text-xs font-black text-white uppercase tracking-wider">Recording Remote Video...</h3>
                  <p className="text-[10px] text-white/50 max-w-[280px] text-center leading-relaxed font-semibold">
                    The target device camera is capturing local video. Live stream is paused to release hardware camera.
                  </p>
                  <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-rose-500/15 border border-rose-500/25 text-[10px] font-black text-rose-500 uppercase tracking-widest mt-2 animate-pulse">
                    <span className="h-2 w-2 rounded-full bg-rose-500 animate-ping" />
                    REC
                  </div>
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center text-center p-6 select-none z-10 animate-fadeIn">
                  <div className="h-16 w-16 rounded-full bg-white/10 flex items-center justify-center mb-4 border border-white/5 animate-pulse">
                    <CameraOff className="h-7 w-7 text-white/50" />
                  </div>
                  <h4 className="text-xs font-black text-white/95 uppercase tracking-wider">Feed Stream Disconnected</h4>
                  <p className="text-[10px] text-white/50 max-w-[260px] mt-1.5 leading-relaxed font-semibold">
                    Press the Connect button on the left to start streaming the {selectedMode === 'camera' ? `${cameraType} camera` : selectedMode} feed.
                  </p>
                </div>
              )
            )}

            {/* ── AUDIO MODE ── */}
            {activeMode === 'audio' && (
              <div className="flex flex-col items-center justify-center gap-5 w-full z-10">
                <div className="h-16 w-16 rounded-2xl bg-brandTeal/10 border border-brandTeal/20 text-brandTeal flex items-center justify-center">
                  <Volume2 className="h-8 w-8 animate-bounce" />
                </div>
                <AudioVisualizer />
                <h3 className="text-xs font-black text-white">Live Microphone Stream Active</h3>
                <audio ref={audioRef} autoPlay className="hidden" />

                {/* Floating Recording Control */}
                <div className="absolute bottom-4 right-4 flex flex-col items-center gap-1.5 z-20">
                  {isRecordingAudio && (
                    <span className="text-[9px] font-black text-rose-500 animate-pulse bg-rose-500/15 border border-rose-500/25 px-2.5 py-1 rounded-lg">
                      REC
                    </span>
                  )}
                  <button
                    onClick={isRecordingAudio ? handleStopAudioRecording : handleStartAudioRecording}
                    className={`h-11 w-11 rounded-full flex items-center justify-center text-white shadow-lg border transition-all ${
                      isRecordingAudio
                        ? 'bg-rose-500 border-rose-500 hover:bg-rose-600 animate-pulse scale-105'
                        : 'bg-brandCyan border-brandCyan hover:bg-brandCyan/90 hover:scale-105'
                    }`}
                    title={isRecordingAudio ? 'Stop Recording' : 'Record Audio'}
                  >
                    {isRecordingAudio ? <Square className="h-4.5 w-4.5 fill-current" /> : <Mic className="h-4.5 w-4.5" />}
                  </button>
                </div>
              </div>
            )}

            {/* ── VIDEO / SCREEN MODE ── */}
            {(activeMode === 'camera' || activeMode === 'screen') && (
              <div className="relative h-full w-full flex items-center justify-center">
                <video
                  ref={videoRef}
                  autoPlay
                  playsInline
                  className="w-full h-full object-contain"
                />

                {/* Bottom-right controls */}
                <div className="absolute bottom-4 right-4 flex items-center gap-2 z-10">
                  {/* Photo capture button */}
                  {activeMode === 'camera' && getConnectionStatus() === 'ONLINE' && (
                    <button
                      onClick={handleCapturePhoto}
                      disabled={isCapturingPhoto}
                      className={`bg-brandCyan hover:bg-brandCyan/90 disabled:bg-slate-700/50 p-2.5 rounded-xl border border-white/10 text-white transition-all shadow-lg flex items-center justify-center ${
                        isCapturingPhoto ? 'animate-pulse animate-duration-1000' : 'hover:scale-105'
                      }`}
                      title={isCapturingPhoto ? 'Capturing...' : 'Capture Photo'}
                    >
                      <Camera className="h-4 w-4" />
                    </button>
                  )}

                  {/* Fullscreen button */}
                  <button
                    onClick={() => videoRef.current?.requestFullscreen()}
                    className="bg-black/70 hover:bg-black/90 backdrop-blur-md p-2.5 rounded-xl border border-white/10 text-white transition-all hover:scale-105"
                    title="Fullscreen"
                  >
                    <Maximize2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
            )}

            {/* ── CONNECTING overlay ── */}
            {isConnecting && (
              <div className="absolute inset-0 bg-black/95 z-25 flex flex-col items-center justify-center text-center p-6">
                <div className="h-14 w-14 rounded-2xl bg-brandCyan/10 border border-brandCyan/20 flex items-center justify-center mb-4">
                  <RefreshCw className="h-7 w-7 text-brandCyan animate-spin" />
                </div>
                <h3 className="text-xs font-black text-white uppercase tracking-wider">Establishing Tunnel...</h3>
                <p className="text-[10px] text-white/50 mt-1 max-w-[280px] leading-relaxed">
                  {activeMode === 'screen'
                    ? 'Waiting for screen-share permission on the child device.'
                    : 'Negotiating WebRTC ICE candidates & SDP handshake.'}
                </p>
                {/* Connection steps */}
                <div className="mt-5 w-full max-w-[200px] space-y-1.5">
                  {['STUN Resolution', 'ICE Gathering', 'SDP Exchange'].map((step, i) => (
                    <div key={step} className="flex items-center gap-2 text-[9px] text-white/60">
                      <div className={`h-1 w-1 rounded-full ${i === 0 ? 'bg-brandTeal animate-pulse' : 'bg-white/10'} shrink-0`} />
                      <div className="flex-1 h-1 bg-white/10 rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full ${i === 0 ? 'bg-brandTeal' : 'bg-white/10'}`}
                          style={{ width: i === 0 ? '100%' : i === 1 ? '60%' : '20%' }}
                        />
                      </div>
                      <span className="font-semibold text-white/40">{step}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* ── Error banner ── */}
            {streamError && (
              <div className="absolute bottom-4 left-4 right-4 z-30 flex items-start gap-2.5 bg-rose-500/10 border border-rose-500/30 text-rose-400 p-3 rounded-xl text-xs font-bold">
                <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" />
                <span>{streamError}</span>
              </div>
            )}

            {/* ── Capture Toast message ── */}
            {captureMessage && (
              <div className={`absolute top-16 left-1/2 -translate-x-1/2 z-35 flex items-center gap-2.5 px-4 py-2.5 rounded-xl text-xs font-black shadow-lg animate-fadeIn border whitespace-nowrap ${
                captureMessage.isError 
                  ? 'bg-rose-500/10 border-rose-500/30 text-rose-400' 
                  : 'bg-emerald-500/15 border-emerald-500/30 text-emerald-400'
              }`}>
                {captureMessage.isError ? <AlertTriangle className="h-4 w-4 shrink-0" /> : <Zap className="h-4 w-4 shrink-0 animate-bounce" />}
                <span>{captureMessage.text}</span>
              </div>
            )}
          </div>

          {/* Dynamically toggle between Recent Captures and Recent Audio Recordings */}
          {selectedMode === 'audio' ? (
            <div className="glass-panel p-6 rounded-2xl border border-slate-200/60 bg-white shadow-sm mt-5 space-y-4 animate-fadeIn">
              <div className="flex justify-between items-center pb-2 border-b border-slate-100">
                <h3 className="text-xs font-black text-slate-800 uppercase tracking-wider flex items-center gap-2">
                  <Mic className="h-4 w-4 text-brandCyan" />
                  Recent Audio Recordings
                </h3>
                <button
                  onClick={() => setShowAudioModal(true)}
                  className="text-[10px] font-black text-brandCyan hover:underline flex items-center gap-1"
                >
                  View All
                  <span className="text-xs">&rarr;</span>
                </button>
              </div>

              <div className="flex gap-4 overflow-x-auto pb-2 scrollbar-thin">
                {audioRecordings.length === 0 ? (
                  <div className="text-center w-full py-8 text-xs italic text-slate-400 font-semibold">
                    No recent audio recordings available.
                  </div>
                ) : (
                  audioRecordings.slice(0, 8).map((audio, idx) => (
                    <div key={audio.id} className="shrink-0 flex flex-col items-center group relative">
                      <div className={`w-36 rounded-xl border border-slate-100 bg-slate-50 hover:bg-brandCyan/5 p-3 flex flex-col justify-between shadow-sm hover:shadow transition-all relative ${
                        playingAudioId === audio.id ? 'h-[120px]' : 'h-20'
                      }`}>
                        {/* Play/Pause Button & Delete Action */}
                        <div className="flex items-center justify-between">
                          <button
                            onClick={() => handlePlayAudio(audio)}
                            className={`h-7 w-7 rounded-full flex items-center justify-center border transition-all ${
                              playingAudioId === audio.id
                                ? 'bg-brandCyan border-brandCyan text-white animate-pulse'
                                : 'bg-brandCyan/10 border-brandCyan/20 text-brandCyan hover:bg-brandCyan/20'
                            }`}
                          >
                            {playingAudioId === audio.id ? (
                              <Square className="h-2.5 w-2.5 fill-current" />
                            ) : (
                              <Play className="h-2.5 w-2.5 fill-current ml-0.5" />
                            )}
                          </button>
                          
                          <button
                            onClick={() => handleDeleteAudio(audio.id)}
                            className="h-7 w-7 rounded-full flex items-center justify-center text-slate-400 hover:text-rose-500 hover:bg-rose-50 border border-transparent hover:border-rose-100 transition-all"
                            title="Delete audio"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </div>

                        {playingAudioId === audio.id ? (
                          <div className="space-y-1 mt-1 animate-fadeIn">
                            <input
                              type="range"
                              min={0}
                              max={audioDuration || 100}
                              value={audioCurrentTime}
                              onChange={(e) => handleSeekAudio(parseFloat(e.target.value))}
                              onClick={(e) => e.stopPropagation()}
                              className="w-full h-1 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-brandCyan focus:outline-none"
                            />
                            <div className="flex justify-between text-[8px] font-mono text-slate-400 font-bold">
                              <span>{formatDurationMinSec(audioCurrentTime)}</span>
                              <span>{formatDurationMinSec(audioDuration)}</span>
                            </div>
                          </div>
                        ) : (
                          <div className="flex justify-between items-end mt-1.5">
                            <span className="text-[8px] font-black text-slate-400 uppercase tracking-wider">
                              {idx === 0 ? 'LATEST' : `CLIP #${audioRecordings.length - idx}`}
                            </span>
                            <span className="text-[8px] font-semibold text-slate-400 bg-slate-100 px-1 py-0.5 rounded">
                              AUDIO
                            </span>
                          </div>
                        )}
                      </div>
                      <span className="text-[10px] text-slate-400 font-bold mt-2">
                        {new Date(audio.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                    </div>
                  ))
                )}
              </div>
            </div>
          ) : (
            <div className="glass-panel p-6 rounded-2xl border border-slate-200/60 bg-white shadow-sm mt-5 space-y-4 animate-fadeIn">
              <div className="flex justify-between items-center pb-2 border-b border-slate-100">
                <h3 className="text-xs font-black text-slate-800 uppercase tracking-wider flex items-center gap-2">
                  <Image className="h-4 w-4 text-brandCyan" />
                  Recent Captures
                </h3>
                <button
                  onClick={() => setShowGalleryModal(true)}
                  className="text-[10px] font-black text-brandCyan hover:underline flex items-center gap-1"
                >
                  View All
                  <span className="text-xs">&rarr;</span>
                </button>
              </div>

              <div className="flex gap-4 overflow-x-auto pb-2 scrollbar-thin">
                {captures.length === 0 ? (
                  <div className="text-center w-full py-8 text-xs italic text-slate-400 font-semibold">
                    No recent captured screenshots or secret photos available.
                  </div>
                ) : (
                  captures.slice(0, 8).map((cap, idx) => (
                    <div key={cap.id} className="shrink-0 flex flex-col items-center group relative cursor-pointer" onClick={() => setSelectedLightboxImage(cap.url)}>
                      <div className="h-20 w-28 rounded-xl overflow-hidden border border-slate-100 bg-slate-50/50 shadow-sm hover:shadow transition-all relative">
                        <img src={cap.url} alt="capture" className="h-full w-full object-cover group-hover:scale-105 transition-transform duration-200 animate-fadeIn" />
                        {idx === 0 && (
                          <span className="absolute top-1.5 left-1.5 bg-emerald-500 text-white font-black text-[8px] px-1.5 py-0.5 rounded uppercase tracking-wider">
                            NEW
                          </span>
                        )}
                        <span className="absolute bottom-1 right-1 bg-black/60 text-white text-[8px] font-black uppercase px-1.5 py-0.5 rounded">
                          {cap.type}
                        </span>
                      </div>
                      <span className="text-[10px] text-slate-400 font-bold mt-2">
                        {new Date(cap.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}


        </div>
      </div>

      {/* View All Gallery Modal */}
      {showGalleryModal && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-md flex items-center justify-center z-[1000] p-4">
          <div className="bg-white border border-slate-200 rounded-2xl w-full max-w-[800px] h-[80vh] flex flex-col p-6 shadow-2xl relative">
            <button 
              onClick={() => setShowGalleryModal(false)}
              className="absolute top-4 right-4 p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all font-black text-lg"
              title="Close"
            >
              &times;
            </button>
            <h3 className="text-sm font-black text-slate-800 uppercase tracking-wide mb-4 flex items-center gap-2 pb-2 border-b border-slate-100">
              <Image className="h-5 w-5 text-brandCyan" />
              All Device Captures ({captures.length})
            </h3>
            
            <div className="flex-1 overflow-y-auto pr-1">
              {captures.length === 0 ? (
                <div className="text-center py-20 text-xs italic text-slate-400 font-semibold">
                  No captures available.
                </div>
              ) : (
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                  {captures.map((cap) => (
                    <div 
                      key={cap.id} 
                      className="flex flex-col border border-slate-100 hover:border-slate-200/60 rounded-xl p-2 bg-slate-50/50 hover:bg-white hover:shadow-sm cursor-pointer transition-all duration-200 group animate-fadeIn"
                      onClick={() => setSelectedLightboxImage(cap.url)}
                    >
                      <div className="aspect-video w-full rounded-lg overflow-hidden border border-slate-100 bg-black/5 relative">
                        <img src={cap.url} alt="capture" className="h-full w-full object-cover group-hover:scale-105 transition-all duration-200" />
                        <span className="absolute bottom-1 right-1 bg-black/60 text-white text-[8px] font-black uppercase px-1 rounded">
                          {cap.type}
                        </span>
                      </div>
                      <span className="text-[10px] text-slate-500 font-black mt-2 text-center">
                        {new Date(cap.timestamp).toLocaleString()}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Lightbox Modal */}
      {selectedLightboxImage && (
        <div 
          className="fixed inset-0 bg-black/85 backdrop-blur-sm flex items-center justify-center z-[2000] p-4 cursor-pointer"
          onClick={() => setSelectedLightboxImage(null)}
        >
          <div className="relative max-w-full max-h-full">
            <img src={selectedLightboxImage} alt="lightbox" className="max-w-[90vw] max-h-[85vh] rounded-xl object-contain border border-white/10 shadow-2xl" />
            <button 
              className="absolute top-4 right-4 bg-black/60 text-white p-2 rounded-full hover:bg-black/80 transition-all font-black text-sm"
              onClick={() => setSelectedLightboxImage(null)}
            >
              &times;
            </button>
          </div>
        </div>
      )}

      {/* Hidden local audio player */}
      <audio 
        ref={localAudioPlayerRef} 
        src={playingAudioUrl || undefined} 
        onEnded={() => {
          setPlayingAudioId(null);
          setAudioCurrentTime(0);
        }} 
        onTimeUpdate={() => {
          if (localAudioPlayerRef.current) {
            setAudioCurrentTime(localAudioPlayerRef.current.currentTime);
          }
        }}
        onLoadedMetadata={() => {
          if (localAudioPlayerRef.current) {
            setAudioDuration(localAudioPlayerRef.current.duration);
          }
        }}
        className="hidden" 
      />

      {/* View All Audio Recordings Modal */}
      {showAudioModal && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-md flex items-center justify-center z-[1000] p-4">
          <div className="bg-white border border-slate-200 rounded-2xl w-full max-w-[800px] h-[80vh] flex flex-col p-6 shadow-2xl relative">
            <button 
              onClick={() => setShowAudioModal(false)}
              className="absolute top-4 right-4 p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all font-black text-lg"
              title="Close"
            >
              &times;
            </button>
            <h3 className="text-sm font-black text-slate-800 uppercase tracking-wide mb-4 flex items-center gap-2 pb-2 border-b border-slate-100">
              <Mic className="h-5 w-5 text-brandCyan" />
              All Audio Recordings ({audioRecordings.length})
            </h3>
            
            <div className="flex-1 overflow-y-auto pr-1">
              {audioRecordings.length === 0 ? (
                <div className="text-center py-20 text-xs italic text-slate-400 font-semibold">
                  No audio recordings available.
                </div>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
                  {audioRecordings.map((audio, idx) => (
                    <div 
                      key={audio.id} 
                      className="flex flex-col border border-slate-100 hover:border-slate-200/60 rounded-xl p-4 bg-slate-50/50 hover:bg-white hover:shadow-sm cursor-pointer transition-all duration-200 animate-fadeIn"
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <button
                            onClick={() => handlePlayAudio(audio)}
                            className={`h-9 w-9 rounded-full flex items-center justify-center border transition-all ${
                              playingAudioId === audio.id
                                ? 'bg-brandCyan border-brandCyan text-white animate-pulse'
                                : 'bg-brandCyan/10 border-brandCyan/20 text-brandCyan hover:bg-brandCyan/20'
                            }`}
                          >
                            {playingAudioId === audio.id ? (
                              <Square className="h-3 w-3 fill-current" />
                            ) : (
                              <Play className="h-3 w-3 fill-current ml-0.5" />
                            )}
                          </button>
                          <div>
                            <span className="text-[10px] font-black text-slate-700 uppercase tracking-wider block">Recording #{audioRecordings.length - idx}</span>
                            <span className="text-[9px] text-slate-400 font-semibold mt-0.5 block">
                              {new Date(audio.timestamp).toLocaleString()}
                            </span>
                          </div>
                        </div>

                        <div className="flex gap-1.5">
                          {/* Download Button */}
                          <a
                            href={audio.url}
                            download={`recording_${audio.timestamp}.mp3`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="h-8 w-8 rounded-full flex items-center justify-center text-slate-400 hover:text-brandCyan hover:bg-brandCyan/5 border border-transparent hover:border-brandCyan/10 transition-all"
                            title="Download audio"
                          >
                            <Download className="h-4 w-4" />
                          </a>

                          {/* Delete Button */}
                          <button
                            onClick={() => handleDeleteAudio(audio.id)}
                            className="h-8 w-8 rounded-full flex items-center justify-center text-slate-400 hover:text-rose-500 hover:bg-rose-50 border border-transparent hover:border-rose-100 transition-all"
                            title="Delete recording"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </div>

                      {playingAudioId === audio.id && (
                        <div className="mt-4 pt-3 border-t border-slate-100 space-y-1.5 animate-fadeIn">
                          <input
                            type="range"
                            min={0}
                            max={audioDuration || 100}
                            value={audioCurrentTime}
                            onChange={(e) => handleSeekAudio(parseFloat(e.target.value))}
                            onClick={(e) => e.stopPropagation()}
                            className="w-full h-1 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-brandCyan focus:outline-none"
                          />
                          <div className="flex justify-between text-[10px] font-mono text-slate-400 font-bold">
                            <span>{formatDurationMinSec(audioCurrentTime)}</span>
                            <span>{formatDurationMinSec(audioDuration)}</span>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

    </div>
  );
};

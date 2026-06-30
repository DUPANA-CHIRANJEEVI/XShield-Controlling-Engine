import { create } from 'zustand';
import { LiveRepository } from '../firebase/liveRepository';

interface DeviceState {
  selectedDevice: string;
  devices: string[];
  namesMap: Record<string, string>;
  presence: { state: string; lastSeen: number };
  telemetry: {
    batteryLevel: number;
    isCharging: boolean;
    networkType: string;
    accessibilityActive: boolean;
    deviceName?: string;
    androidVersion?: string;
    uptime?: number;
    storageTotal?: number;
    storageUsed?: number;
    ramTotal?: number;
    ramAvailable?: number;
    screenResolution?: string;
    localIp?: string;
    imei?: string;
    simSerialNumber?: string;
    simOperator?: string;
    simState?: string;
    phoneNetworkOperator?: string;
    phoneNumber?: string;
    hardware?: string;
    manufacturer?: string;
    model?: string;
    brand?: string;
    cpuAbi?: string;
    sdkVersion?: number;
    cpuName?: string;
    cameraSpecs?: string;
    batteryCapacity?: number;
  };
  liveLocation: { lat: number; lng: number; speed: number; accuracy: number; bearing: number; timestamp: number };
  isSirenPlaying: boolean;
  ringingCall: { isRinging: boolean; number: string };
  setSelectedDevice: (deviceId: string) => void;
  setDevices: (devices: string[], namesMap: Record<string, string>) => void;
  setPresence: (presence: { state: string; lastSeen: number }) => void;
  setTelemetry: (telemetry: any) => void;
  setLiveLocation: (coords: { lat: number; lng: number; speed: number; accuracy: number; bearing: number; timestamp: number }) => void;
  setIsSirenPlaying: (isPlaying: boolean) => void;
  setRingingCall: (ringingCall: { isRinging: boolean; number: string }) => void;
}

export const useDeviceStore = create<DeviceState>((set) => ({
  selectedDevice: '',
  devices: [],
  namesMap: {},
  presence: { state: 'offline', lastSeen: 0 },
  telemetry: {
    batteryLevel: 100,
    isCharging: false,
    networkType: 'Unknown',
    accessibilityActive: true
  },
  liveLocation: { lat: 0, lng: 0, speed: 0, accuracy: 0, bearing: 0, timestamp: 0 },
  isSirenPlaying: false,
  ringingCall: { isRinging: false, number: '' },
  setSelectedDevice: (deviceId) => set({ selectedDevice: deviceId }),
  setDevices: (devices, namesMap) => set({ devices, namesMap }),
  setPresence: (presence) => set({ presence }),
  setTelemetry: (telemetry) => set({
    telemetry: {
      batteryLevel: telemetry?.batteryLevel ?? 100,
      isCharging: telemetry?.isCharging ?? false,
      networkType: telemetry?.networkType ?? 'Unknown',
      accessibilityActive: telemetry?.accessibilityActive ?? true,
      deviceName: telemetry?.deviceName,
      androidVersion: telemetry?.androidVersion,
      uptime: telemetry?.uptime,
      storageTotal: telemetry?.storageTotal,
      storageUsed: telemetry?.storageUsed,
      ramTotal: telemetry?.ramTotal,
      ramAvailable: telemetry?.ramAvailable,
      screenResolution: telemetry?.screenResolution,
      localIp: telemetry?.localIp,
      imei: telemetry?.imei,
      simSerialNumber: telemetry?.simSerialNumber,
      simOperator: telemetry?.simOperator,
      simState: telemetry?.simState,
      phoneNetworkOperator: telemetry?.phoneNetworkOperator,
      phoneNumber: telemetry?.phoneNumber,
      hardware: telemetry?.hardware,
      manufacturer: telemetry?.manufacturer,
      model: telemetry?.model,
      brand: telemetry?.brand,
      cpuAbi: telemetry?.cpuAbi,
      sdkVersion: telemetry?.sdkVersion,
      cpuName: telemetry?.cpuName,
      cameraSpecs: telemetry?.cameraSpecs,
      batteryCapacity: telemetry?.batteryCapacity
    }
  }),
  setLiveLocation: (coords) => set({ liveLocation: coords }),
  setIsSirenPlaying: (isPlaying) => set({ isSirenPlaying: isPlaying }),
  setRingingCall: (ringingCall) => set({ ringingCall })
}));

// Manage active listeners dynamically to avoid leak/zombies
let unsubscribePresence: (() => void) | null = null;
let unsubscribeTelemetry: (() => void) | null = null;
let unsubscribeLocation: (() => void) | null = null;
let unsubscribeSiren: (() => void) | null = null;
let unsubscribeRinging: (() => void) | null = null;

export const startDeviceListeners = (deviceId: string) => {
  // Clean old listeners
  if (unsubscribePresence) unsubscribePresence();
  if (unsubscribeTelemetry) unsubscribeTelemetry();
  if (unsubscribeLocation) unsubscribeLocation();
  if (unsubscribeSiren) unsubscribeSiren();
  if (unsubscribeRinging) unsubscribeRinging();

  if (!deviceId) return;

  const store = useDeviceStore.getState();

  unsubscribePresence = LiveRepository.subscribePresence(deviceId, (presence) => {
    store.setPresence(presence);
  });

  unsubscribeTelemetry = LiveRepository.subscribeTelemetry(deviceId, (telemetry) => {
    store.setTelemetry(telemetry);
  });

  unsubscribeLocation = LiveRepository.subscribeLiveLocation(deviceId, (coords) => {
    store.setLiveLocation(coords);
  });

  unsubscribeSiren = LiveRepository.subscribeSirenState(deviceId, (isPlaying) => {
    store.setIsSirenPlaying(isPlaying);
  });

  unsubscribeRinging = LiveRepository.subscribeRingingState(deviceId, (ringing) => {
    store.setRingingCall(ringing);
  });
};

// Initialize devices list listener immediately
export const startDevicesListListener = () => {
  LiveRepository.subscribeDevices((devices, namesMap) => {
    const store = useDeviceStore.getState();
    store.setDevices(devices, namesMap);
    
    // Auto-select first device if none is selected
    if (devices.length > 0 && !store.selectedDevice) {
      store.setSelectedDevice(devices[0]);
      startDeviceListeners(devices[0]);
    }
  });
};

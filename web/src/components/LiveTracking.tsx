import React, { useEffect, useRef, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap, Circle } from 'react-leaflet';
import { useDeviceStore } from '../store/deviceStore';
import { 
  MapPin, Navigation, Compass, Copy, Check, ExternalLink, 
  Satellite, Activity, Clock, RefreshCw, Globe, AlertTriangle
} from 'lucide-react';
import L from 'leaflet';

// Fix Leaflet default marker icon
import iconUrl from 'leaflet/dist/images/marker-icon.png';
import iconShadowUrl from 'leaflet/dist/images/marker-shadow.png';

L.Marker.prototype.options.icon = L.icon({
  iconUrl,
  shadowUrl: iconShadowUrl,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

// Custom glowing pin marker
const createCustomIcon = (isOnline: boolean) => L.divIcon({
  className: '',
  html: `
    <div style="position:relative;width:36px;height:36px;">
      <div style="
        position:absolute;inset:0;border-radius:50%;
        background:${isOnline ? 'rgba(0,191,165,0.2)' : 'rgba(100,116,139,0.15)'};
        animation:${isOnline ? 'ping 1.5s cubic-bezier(0,0,0.2,1) infinite' : 'none'};
      "></div>
      <div style="
        position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);
        width:18px;height:18px;border-radius:50%;
        background:${isOnline ? '#00BFA5' : '#64748B'};
        border:3px solid white;
        box-shadow:0 0 12px ${isOnline ? 'rgba(0,191,165,0.7)' : 'rgba(0,0,0,0.2)'};
      "></div>
    </div>
  `,
  iconSize: [36, 36],
  iconAnchor: [18, 18],
  popupAnchor: [0, -20]
});

// Auto-pan map when position changes
const ChangeView: React.FC<{ center: [number, number]; zoom: number }> = ({ center, zoom }) => {
  const map = useMap();
  const prevCenter = useRef<[number, number]>([0, 0]);
  useEffect(() => {
    if (center[0] !== 0 && center[1] !== 0 &&
        (center[0] !== prevCenter.current[0] || center[1] !== prevCenter.current[1])) {
      map.flyTo(center, zoom, { animate: true, duration: 1.2 });
      prevCenter.current = center;
    }
  }, [center, map, zoom]);
  return null;
};

// Format coordinate to DMS
const toDMS = (decimal: number, isLat: boolean): string => {
  const dir = isLat ? (decimal >= 0 ? 'N' : 'S') : (decimal >= 0 ? 'E' : 'W');
  const abs = Math.abs(decimal);
  const deg = Math.floor(abs);
  const minFloat = (abs - deg) * 60;
  const min = Math.floor(minFloat);
  const sec = ((minFloat - min) * 60).toFixed(1);
  return `${deg}° ${min}' ${sec}" ${dir}`;
};

// Convert bearing degrees to compass direction
const bearingToDirection = (deg: number): string => {
  const dirs = ['North', 'North-East', 'East', 'South-East', 'South', 'South-West', 'West', 'North-West'];
  return dirs[Math.round(deg / 45) % 8];
};

// Format a timestamp to readable string
const formatLastSeen = (ms: number): string => {
  if (!ms) return '—';
  const diff = Date.now() - ms;
  if (diff < 10000) return 'Just now';
  if (diff < 60000) return `${Math.floor(diff / 1000)}s ago`;
  if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
  return new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

interface PingRecord {
  lat: number;
  lng: number;
  timestamp: number;
}

export const LiveTracking: React.FC = () => {
  const { liveLocation, presence } = useDeviceStore();
  const [copied, setCopied] = useState(false);
  const [mapZoom, setMapZoom] = useState(15);
  const [locationHistory, setLocationHistory] = useState<PingRecord[]>([]);
  const [lastUpdateMs, setLastUpdateMs] = useState<number>(0);

  const hasLocation = liveLocation.lat !== 0 && liveLocation.lng !== 0;
  const isOnline = presence.state === 'online';
  const position: [number, number] = [liveLocation.lat || 12.9716, liveLocation.lng || 77.5946];

  // Computed display values
  const speedKmh = Math.round((liveLocation.speed || 0) * 3.6); // m/s → km/h
  const accuracyM = Math.round(liveLocation.accuracy || 0);
  const isMoving = speedKmh > 1;
  const accuracyLabel = accuracyM <= 5 ? 'High Precision' : accuracyM <= 20 ? 'Good' : accuracyM <= 50 ? 'Moderate' : 'Low';
  const movementLabel = !isMoving ? 'Stationary' : `Moving ${bearingToDirection(liveLocation.bearing || 0)}`;
  const lastSeenLabel = isOnline ? (hasLocation ? 'Just now' : 'Online') : `${formatLastSeen(presence.lastSeen)}`;
  const lastUpdateLabel = hasLocation ? formatLastSeen(liveLocation.timestamp || lastUpdateMs) : '—';
  const lastUpdateDate = hasLocation && liveLocation.timestamp
    ? new Date(liveLocation.timestamp).toLocaleString([], { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
    : '';

  // Record location history on ping
  useEffect(() => {
    if (hasLocation) {
      const now = liveLocation.timestamp || Date.now();
      setLastUpdateMs(now);
      setLocationHistory(prev => {
        const entry = { lat: liveLocation.lat, lng: liveLocation.lng, timestamp: now };
        const updated = [entry, ...prev.filter(p => !(p.lat === liveLocation.lat && p.lng === liveLocation.lng))];
        return updated.slice(0, 8);
      });
    }
  }, [liveLocation.lat, liveLocation.lng, hasLocation, liveLocation.timestamp]);

  const handleCopyCoords = () => {
    navigator.clipboard.writeText(`${liveLocation.lat.toFixed(6)}, ${liveLocation.lng.toFixed(6)}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const openGoogleMaps = () => {
    window.open(`https://www.google.com/maps?q=${liveLocation.lat},${liveLocation.lng}`, '_blank');
  };

  const openAppleMaps = () => {
    window.open(`https://maps.apple.com/?q=${liveLocation.lat},${liveLocation.lng}`, '_blank');
  };

  return (
    <div className="space-y-5">

      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-xl bg-brandTeal/10 text-brandTeal border border-brandTeal/20 flex items-center justify-center">
            <Satellite className="h-5 w-5" />
          </div>
          <div>
            <h2 className="text-sm font-black text-slate-800 uppercase tracking-wide">Live GPS Tracking</h2>
            <p className="text-[10px] text-mutedText font-semibold mt-0.5">
              Real-time coordinates synced from device location sensors
            </p>
          </div>
        </div>

        {/* Live status pill */}
        <div className={`flex items-center gap-2 px-4 py-2 rounded-xl border text-xs font-bold ${
          isOnline && hasLocation
            ? 'bg-brandTeal/10 border-brandTeal/25 text-brandTeal'
            : 'bg-brandRed/10 border-brandRed/25 text-brandRed'
        }`}>
          <span className={`h-2 w-2 rounded-full ${isOnline && hasLocation ? 'bg-brandTeal animate-pulse' : 'bg-brandRed'}`} />
          {isOnline && hasLocation ? 'GPS ACTIVE — LIVE FEED' : 'NO SIGNAL'}
        </div>
      </div>

      {/* ── 4-Panel Telemetry Strip ── */}
      <div className="grid grid-cols-4 gap-4">

        {/* 1. Device Status */}
        <div className="glass-panel p-4 rounded-2xl border border-darkBorder bg-darkSurf shadow-sm flex items-center gap-4">
          <div className={`h-10 w-10 rounded-xl border flex items-center justify-center shrink-0 ${
            isOnline ? 'bg-brandTeal/10 border-brandTeal/20 text-brandTeal' : 'bg-slate-100 border-slate-200 text-slate-400'
          }`}>
            {isOnline
              ? <Activity className="h-5 w-5" />
              : <AlertTriangle className="h-5 w-5" />}
          </div>
          <div>
            <p className="text-[9px] font-black text-mutedText uppercase tracking-widest">Device Status</p>
            <p className={`text-sm font-black mt-0.5 ${
              isOnline ? 'text-brandTeal' : 'text-slate-400'
            }`}>
              {isOnline ? 'Online' : 'Offline'}
            </p>
            <p className="text-[9px] text-mutedText font-semibold mt-0.5">Last seen: {lastSeenLabel}</p>
          </div>
        </div>

        {/* 2. GPS Accuracy */}
        <div className="glass-panel p-4 rounded-2xl border border-darkBorder bg-darkSurf shadow-sm flex items-center gap-4">
          <div className="h-10 w-10 rounded-xl bg-brandCyan/10 border border-brandCyan/20 text-brandCyan flex items-center justify-center shrink-0">
            <Compass className="h-5 w-5" />
          </div>
          <div>
            <p className="text-[9px] font-black text-mutedText uppercase tracking-widest">GPS Accuracy</p>
            <p className="text-sm font-black text-slate-800 mt-0.5 font-mono">
              {hasLocation ? `± ${accuracyM} m` : '± — m'}
            </p>
            <p className="text-[9px] text-mutedText font-semibold mt-0.5">{hasLocation ? accuracyLabel : 'No signal'}</p>
          </div>
        </div>

        {/* 3. Movement Speed */}
        <div className="glass-panel p-4 rounded-2xl border border-darkBorder bg-darkSurf shadow-sm flex items-center gap-4">
          <div className="h-10 w-10 rounded-xl bg-brandBlue/10 border border-brandBlue/20 text-brandBlue flex items-center justify-center shrink-0">
            <Navigation className="h-5 w-5" />
          </div>
          <div>
            <p className="text-[9px] font-black text-mutedText uppercase tracking-widest">Movement Speed</p>
            <p className="text-sm font-black text-slate-800 mt-0.5">
              {hasLocation ? `${speedKmh} km/h` : '— km/h'}
            </p>
            <p className="text-[9px] text-mutedText font-semibold mt-0.5">{hasLocation ? movementLabel : 'Awaiting fix'}</p>
          </div>
        </div>

        {/* 4. Last Update */}
        <div className="glass-panel p-4 rounded-2xl border border-darkBorder bg-darkSurf shadow-sm flex items-center gap-4">
          <div className="h-10 w-10 rounded-xl bg-amber-50 border border-amber-200 text-amber-500 flex items-center justify-center shrink-0">
            <Clock className="h-5 w-5" />
          </div>
          <div>
            <p className="text-[9px] font-black text-mutedText uppercase tracking-widest">Last Update</p>
            <p className="text-sm font-black text-slate-800 mt-0.5">{lastUpdateLabel}</p>
            <p className="text-[9px] text-mutedText font-semibold mt-0.5">{lastUpdateDate || 'No updates yet'}</p>
          </div>
        </div>

      </div>

      {/* Two-column main layout */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">

        {/* LEFT: Large Map Column */}
        <div className="lg:col-span-2 flex flex-col gap-4">

          {/* Coordinate Data Strip */}
          <div className="grid grid-cols-2 gap-4">
            <div className="glass-panel p-4 rounded-2xl border border-darkBorder bg-darkSurf flex items-center gap-4 shadow-sm">
              <div className="h-9 w-9 rounded-xl bg-brandCyan/10 text-brandCyan border border-brandCyan/20 flex items-center justify-center shrink-0">
                <Navigation className="h-4 w-4" style={{ transform: 'rotate(45deg)' }} />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-[9px] font-black text-mutedText uppercase tracking-widest">Latitude</p>
                <p className="text-sm font-black text-slate-800 font-mono mt-0.5 truncate">
                  {hasLocation ? liveLocation.lat.toFixed(6) : '—'}
                </p>
                <p className="text-[9px] text-mutedText font-semibold mt-0.5 truncate">
                  {hasLocation ? toDMS(liveLocation.lat, true) : 'Awaiting signal'}
                </p>
              </div>
            </div>

            <div className="glass-panel p-4 rounded-2xl border border-darkBorder bg-darkSurf flex items-center gap-4 shadow-sm">
              <div className="h-9 w-9 rounded-xl bg-brandBlue/10 text-brandBlue border border-brandBlue/20 flex items-center justify-center shrink-0">
                <Navigation className="h-4 w-4" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-[9px] font-black text-mutedText uppercase tracking-widest">Longitude</p>
                <p className="text-sm font-black text-slate-800 font-mono mt-0.5 truncate">
                  {hasLocation ? liveLocation.lng.toFixed(6) : '—'}
                </p>
                <p className="text-[9px] text-mutedText font-semibold mt-0.5 truncate">
                  {hasLocation ? toDMS(liveLocation.lng, false) : 'Awaiting signal'}
                </p>
              </div>
            </div>
          </div>

          {/* Map Panel */}
          <div className="relative rounded-2xl overflow-hidden border border-darkBorder shadow-lg" style={{ height: '480px' }}>
            
            {/* Offline / No Signal Overlay */}
            {(!hasLocation || !isOnline) && (
              <div className="absolute inset-0 bg-white/85 backdrop-blur-md z-[1000] flex flex-col items-center justify-center text-center p-6">
                <div className="h-16 w-16 rounded-2xl bg-brandRed/10 border border-brandRed/20 text-brandRed flex items-center justify-center mb-4">
                  <AlertTriangle className="h-8 w-8" />
                </div>
                <h3 className="text-sm font-black text-slate-800">No Location Signal Detected</h3>
                <p className="text-xs text-mutedText mt-2 max-w-[300px] leading-relaxed">
                  The target device is currently offline or location permissions are restricted. Last known anchor is displayed below.
                </p>
                {hasLocation && (
                  <div className="mt-4 px-4 py-2 bg-darkBg border border-darkBorder rounded-xl text-[10px] font-mono text-mutedText">
                    Last: {liveLocation.lat.toFixed(5)}, {liveLocation.lng.toFixed(5)}
                  </div>
                )}
              </div>
            )}

            <MapContainer
              center={position}
              zoom={15}
              scrollWheelZoom={true}
              zoomControl={false}
              attributionControl={false}
              style={{ height: '100%', width: '100%' }}
            >
              <ChangeView center={position} zoom={mapZoom} />
              {/* CartoDB Voyager — clean, bright, premium */}
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>'
                url="https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
              />
              {hasLocation && (
                <>
                  {/* Accuracy radius ring */}
                  <Circle
                    center={position}
                    radius={80}
                    pathOptions={{
                      color: isOnline ? '#00BFA5' : '#64748B',
                      fillColor: isOnline ? '#00BFA5' : '#64748B',
                      fillOpacity: 0.08,
                      weight: 1.5,
                      dashArray: '6 4'
                    }}
                  />
                  <Marker position={position} icon={createCustomIcon(isOnline)}>
                    <Popup className="custom-popup">
                      <div className="text-xs" style={{ minWidth: '160px' }}>
                        <p className="font-black text-slate-800 flex items-center gap-1 mb-1.5">
                          <span className={`h-1.5 w-1.5 rounded-full inline-block ${isOnline ? 'bg-brandTeal' : 'bg-slate-400'}`} />
                          Target Device
                        </p>
                        <p className="text-mutedText font-mono text-[10px]">{liveLocation.lat.toFixed(5)}</p>
                        <p className="text-mutedText font-mono text-[10px]">{liveLocation.lng.toFixed(5)}</p>
                        <p className="text-[9px] text-mutedText mt-1.5 pt-1.5 border-t border-gray-100">
                          Status: <span className={isOnline ? 'text-brandTeal font-bold' : 'text-slate-400 font-bold'}>{presence.state}</span>
                        </p>
                      </div>
                    </Popup>
                  </Marker>
                </>
              )}
            </MapContainer>

            {/* Map Zoom Controls overlay */}
            <div className="absolute bottom-4 right-4 z-[500] flex flex-col gap-2">
              <button
                onClick={() => setMapZoom(z => Math.min(z + 1, 18))}
                className="h-8 w-8 bg-white border border-darkBorder shadow-md rounded-lg text-slate-700 font-bold text-sm hover:bg-darkBg transition-all flex items-center justify-center"
              >+</button>
              <button
                onClick={() => setMapZoom(z => Math.max(z - 1, 8))}
                className="h-8 w-8 bg-white border border-darkBorder shadow-md rounded-lg text-slate-700 font-bold text-sm hover:bg-darkBg transition-all flex items-center justify-center"
              >−</button>
            </div>

            {/* Last updated timestamp bar */}
            <div className="absolute bottom-4 left-4 z-[500]">
              <div className="flex items-center gap-2 bg-white/90 backdrop-blur-sm border border-darkBorder rounded-xl px-3 py-1.5 shadow-sm">
                <RefreshCw className={`h-3 w-3 text-mutedText ${isOnline ? 'animate-spin' : ''}`} style={{ animationDuration: '3s' }} />
                <span className="text-[9px] font-black text-mutedText uppercase tracking-widest">
                  {lastUpdateMs > 0 ? `Updated ${formatLastSeen(lastUpdateMs)}` : 'Awaiting first ping...'}
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* RIGHT: Telemetry Sidebar */}
        <div className="space-y-4">

          {/* Quick Actions Card */}
          <div className="glass-panel p-5 rounded-2xl border border-darkBorder bg-darkSurf shadow-sm">
            <h3 className="text-[10px] font-black text-slate-800 uppercase tracking-widest mb-3.5">Quick Actions</h3>
            <div className="space-y-2.5">
              <button
                onClick={handleCopyCoords}
                disabled={!hasLocation}
                className="w-full flex items-center gap-3 p-3 rounded-xl border border-darkBorder hover:border-brandCyan/35 bg-darkBg/30 hover:bg-brandCyan/5 text-xs font-bold text-slate-700 hover:text-brandCyan transition-all disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {copied ? <Check className="h-4 w-4 text-brandTeal" /> : <Copy className="h-4 w-4" />}
                <span>{copied ? 'Coordinates Copied!' : 'Copy Coordinates'}</span>
              </button>

              <button
                onClick={openGoogleMaps}
                disabled={!hasLocation}
                className="w-full flex items-center gap-3 p-3 rounded-xl border border-darkBorder hover:border-brandBlue/35 bg-darkBg/30 hover:bg-brandBlue/5 text-xs font-bold text-slate-700 hover:text-brandBlue transition-all disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <Globe className="h-4 w-4" />
                <span>Open in Google Maps</span>
                <ExternalLink className="h-3 w-3 ml-auto opacity-60" />
              </button>

              <button
                onClick={openAppleMaps}
                disabled={!hasLocation}
                className="w-full flex items-center gap-3 p-3 rounded-xl border border-darkBorder hover:border-slate-400/35 bg-darkBg/30 hover:bg-slate-100 text-xs font-bold text-slate-700 hover:text-slate-800 transition-all disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <MapPin className="h-4 w-4" />
                <span>Open in Apple Maps</span>
                <ExternalLink className="h-3 w-3 ml-auto opacity-60" />
              </button>
            </div>
          </div>

          {/* GPS Signal Card */}
          <div className="glass-panel p-5 rounded-2xl border border-darkBorder bg-darkSurf shadow-sm">
            <h3 className="text-[10px] font-black text-slate-800 uppercase tracking-widest mb-4">Signal Telemetry</h3>
            <div className="space-y-3">
              <div className="flex justify-between items-center text-xs">
                <span className="text-mutedText font-semibold flex items-center gap-1.5">
                  <Activity className="h-3.5 w-3.5 text-brandTeal" />
                  GPS Signal
                </span>
                <span className={`font-black px-2 py-0.5 rounded text-[9px] uppercase tracking-wide ${
                  isOnline && hasLocation ? 'bg-brandTeal/15 text-brandTeal' : 'bg-slate-100 text-mutedText'
                }`}>
                  {isOnline && hasLocation ? 'STRONG' : 'NO SIGNAL'}
                </span>
              </div>

              <div className="flex justify-between items-center text-xs">
                <span className="text-mutedText font-semibold flex items-center gap-1.5">
                  <Satellite className="h-3.5 w-3.5 text-brandBlue" />
                  Provider Mode
                </span>
                <span className="font-black text-slate-800 text-[10px]">GPS + Network</span>
              </div>

              <div className="flex justify-between items-center text-xs">
                <span className="text-mutedText font-semibold flex items-center gap-1.5">
                  <Compass className="h-3.5 w-3.5 text-brandCyan" />
                  Accuracy Radius
                </span>
                <span className="font-black text-slate-800 text-[10px] font-mono">~80 m</span>
              </div>

              <div className="flex justify-between items-center text-xs">
                <span className="text-mutedText font-semibold flex items-center gap-1.5">
                  <Clock className="h-3.5 w-3.5 text-mutedText" />
                  Update Frequency
                </span>
                <span className="font-black text-slate-800 text-[10px]">Real-time</span>
              </div>
            </div>

            {/* Signal bar visual */}
            <div className="mt-4 pt-4 border-t border-darkBorder">
              <p className="text-[9px] font-black text-mutedText uppercase tracking-widest mb-2">Signal Quality</p>
              <div className="flex items-end gap-1 h-8">
                {[3, 5, 7, 9, 11, 9, 7].map((h, i) => (
                  <div
                    key={i}
                    className={`flex-1 rounded-sm transition-all ${
                      isOnline && hasLocation
                        ? 'bg-brandTeal'
                        : i < 2 ? 'bg-slate-200' : 'bg-slate-100'
                    }`}
                    style={{ height: `${h * (isOnline && hasLocation ? 1 : 0.3) * 4}px`, opacity: isOnline && hasLocation ? (0.5 + i * 0.07) : 0.3 }}
                  />
                ))}
              </div>
            </div>
          </div>

          {/* Location History Timeline */}
          <div className="glass-panel p-5 rounded-2xl border border-darkBorder bg-darkSurf shadow-sm">
            <h3 className="text-[10px] font-black text-slate-800 uppercase tracking-widest mb-4">
              Location History
            </h3>

            {locationHistory.length === 0 ? (
              <div className="py-8 text-center">
                <MapPin className="h-8 w-8 text-mutedText/30 mx-auto mb-2" />
                <p className="text-[10px] text-mutedText font-bold">No pings recorded yet</p>
              </div>
            ) : (
              <div className="space-y-0">
                {locationHistory.map((ping, index) => (
                  <div key={index} className="flex gap-3 relative">
                    {/* Timeline line */}
                    {index < locationHistory.length - 1 && (
                      <div className="absolute left-[9px] top-5 w-[1.5px] bg-darkBorder" style={{ height: 'calc(100% - 8px)' }} />
                    )}
                    {/* Dot */}
                    <div className={`h-4.5 w-4.5 rounded-full border-2 shrink-0 mt-1 z-10 ${
                      index === 0
                        ? 'bg-brandTeal border-brandTeal'
                        : 'bg-darkSurf border-darkBorder'
                    }`} />
                    {/* Entry */}
                    <div className="pb-3 flex-1 min-w-0">
                      <p className="text-[10px] font-mono font-bold text-slate-700 truncate">
                        {ping.lat.toFixed(5)}, {ping.lng.toFixed(5)}
                      </p>
                      <p className="text-[9px] text-mutedText font-semibold mt-0.5">
                        {formatLastSeen(ping.timestamp)}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

        </div>
      </div>
    </div>
  );
};

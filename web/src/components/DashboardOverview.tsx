import React, { useEffect, useState, useRef } from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { LiveRepository } from '../firebase/liveRepository';
import { StorageRepository } from '../firebase/storageRepository';
import type { CallLogItem, SmsLogItem } from '../firebase/storageRepository';
import { MediaRepository } from '../firebase/mediaRepository';
import { 
  Shield, Battery, Wifi, Settings, Lock, Volume2, 
  VolumeX, Camera, RefreshCw, Clock, Info, X 
} from 'lucide-react';
// Leaflet map imports removed as Live Location was taken off the main dashboard

interface DashboardOverviewProps {
  onNavigate: (tab: string) => void;
}

// Helper: Dynamic storage allocation simulation based on device ID
const getStorageInfo = (deviceId: string) => {
  if (!deviceId) return { used: '0.0', total: 128, percent: 0 };
  let hash = 0;
  for (let i = 0; i < deviceId.length; i++) {
    hash = deviceId.charCodeAt(i) + ((hash << 5) - hash);
  }
  const is256 = Math.abs(hash) % 2 === 0;
  const total = is256 ? 256 : 128;
  const used = Math.abs(hash % (total - 35)) + 25; // range between 25 and total-10
  return { used: used.toFixed(1), total, percent: Math.round((used / total) * 100) };
};

// Helper: Dynamic uptime simulation based on device ID
const getUptimeInfo = (deviceId: string) => {
  if (!deviceId) return '0d 0h 0m';
  let hash = 0;
  for (let i = 0; i < deviceId.length; i++) {
    hash = deviceId.charCodeAt(i) + ((hash << 5) - hash);
  }
  const days = Math.abs(hash) % 4 + 1; // 1 to 4 days
  const hours = Math.abs(hash) % 24;
  const mins = Math.abs(hash) % 60;
  return `${days}d ${hours}h ${mins}m`;
};

// Helper: Parse androidVersion and deviceModel from namesMap
const parseDeviceDetails = (fullName: string) => {
  if (!fullName) return { model: 'Nothing A123', android: 'Android 14' };
  const match = fullName.match(/\(([^)]+)\)/);
  const android = match ? match[1] : 'Android 14';
  const model = fullName.replace(/\s*\([^)]+\)/g, '').trim() || 'Nothing A123';
  return { model, android };
};

// Helper: Format Last Seen timestamp dynamically
const formatLastSeenTime = (timestamp: number) => {
  if (!timestamp) return { diff: 'Unknown', sub: 'Never synced' };
  const diffMs = Date.now() - timestamp;
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMins / 60);
  
  let diffStr = '';
  if (diffMins < 1) {
    diffStr = 'Just now';
  } else if (diffMins < 60) {
    diffStr = `${diffMins} min${diffMins > 1 ? 's' : ''} ago`;
  } else if (diffHours < 24) {
    diffStr = `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
  } else {
    const days = Math.floor(diffHours / 24);
    diffStr = `${days} day${days > 1 ? 's' : ''} ago`;
  }

  const date = new Date(timestamp);
  const timeStr = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const dateStr = `${date.getDate()} ${months[date.getMonth()]}`;
  
  const today = new Date();
  const isToday = date.toDateString() === today.toDateString();
  const subText = isToday ? `Today, ${timeStr}` : `${dateStr}, ${timeStr}`;

  return { diff: diffStr, sub: subText };
};

// Map preview removed

// 2. Live Node-Link Data Flow Animation Component
const NetworkFlowMonitor: React.FC<{ presence: string }> = ({ presence }) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationFrameId: number;
    let width = canvas.width = canvas.offsetWidth;
    let height = canvas.height = canvas.offsetHeight;

    const handleResize = () => {
      if (!canvas) return;
      width = canvas.width = canvas.offsetWidth;
      height = canvas.height = canvas.offsetHeight;
    };
    window.addEventListener('resize', handleResize);

    const particles: { progress: number; speed: number; size: number }[] = [];
    for (let i = 0; i < 6; i++) {
      particles.push({
        progress: Math.random(),
        speed: 0.004 + Math.random() * 0.006,
        size: 2.5 + Math.random() * 2
      });
    }


    const render = () => {
      ctx.clearRect(0, 0, width, height);

      // Cyber Grid Background
      ctx.strokeStyle = 'rgba(0, 136, 255, 0.04)';
      ctx.lineWidth = 1;
      const gridSize = 20;
      for (let x = 0; x < width; x += gridSize) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, height);
        ctx.stroke();
      }
      for (let y = 0; y < height; y += gridSize) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(width, y);
        ctx.stroke();
      }

      const nodeA = { x: width * 0.25, y: height * 0.5 };
      const nodeB = { x: width * 0.75, y: height * 0.5 };

      // Dotted wire path
      ctx.beginPath();
      ctx.moveTo(nodeA.x, nodeA.y);
      ctx.lineTo(nodeB.x, nodeB.y);
      ctx.strokeStyle = presence === 'online' ? 'rgba(0, 191, 165, 0.25)' : 'rgba(100, 116, 139, 0.15)';
      ctx.lineWidth = 2;
      ctx.setLineDash([5, 4]);
      ctx.stroke();
      ctx.setLineDash([]);

      if (presence === 'online') {
        particles.forEach((p) => {
          p.progress += p.speed;
          if (p.progress > 1) {
            p.progress = 0;
            p.speed = 0.004 + Math.random() * 0.006;
          }

          const px = nodeA.x + (nodeB.x - nodeA.x) * p.progress;
          const py = nodeA.y + (nodeB.y - nodeA.y) * p.progress;

          ctx.beginPath();
          ctx.arc(px, py, p.size, 0, Math.PI * 2);
          ctx.fillStyle = '#00BFA5';
          ctx.shadowColor = '#00BFA5';
          ctx.shadowBlur = 6;
          ctx.fill();
          ctx.shadowBlur = 0;
        });
      }


      // Node A
      ctx.beginPath();
      ctx.arc(nodeA.x, nodeA.y, 9, 0, Math.PI * 2);
      ctx.fillStyle = presence === 'online' ? '#00BFA5' : '#64748B';
      ctx.shadowColor = presence === 'online' ? '#00BFA5' : '#64748B';
      ctx.shadowBlur = presence === 'online' ? 10 : 0;
      ctx.fill();
      ctx.shadowBlur = 0;

      ctx.beginPath();
      ctx.arc(nodeA.x, nodeA.y, 15, 0, Math.PI * 2);
      ctx.strokeStyle = presence === 'online' ? 'rgba(0, 191, 165, 0.4)' : 'rgba(100, 116, 139, 0.25)';
      ctx.lineWidth = 1.5;
      ctx.stroke();

      // Node B
      ctx.beginPath();
      ctx.arc(nodeB.x, nodeB.y, 9, 0, Math.PI * 2);
      ctx.fillStyle = presence === 'online' ? '#0088FF' : '#64748B';
      ctx.shadowColor = presence === 'online' ? '#0088FF' : '#64748B';
      ctx.shadowBlur = presence === 'online' ? 10 : 0;
      ctx.fill();
      ctx.shadowBlur = 0;


      ctx.fillStyle = '#1E293B';
      ctx.font = 'bold 10px Outfit, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('CHILD MONITOR', nodeA.x, nodeA.y - 24);
      ctx.fillText('PARENT WEB', nodeB.x, nodeB.y - 24);

      ctx.fillStyle = presence === 'online' ? '#00BFA5' : '#64748B';
      ctx.font = 'bold 8px Outfit, sans-serif';
      ctx.fillText(presence === 'online' ? 'LIVE DATA FLOW' : 'OFFLINE', nodeA.x, nodeA.y + 30);
      
      ctx.fillStyle = '#0088FF';
      ctx.fillText('ACTIVE CONSOLE', nodeB.x, nodeB.y + 30);

      animationFrameId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener('resize', handleResize);
    };
  }, [presence]);

  return (
    <div className="glass-panel p-5 rounded-2xl flex flex-col h-[180px] shadow-lg relative overflow-hidden">
      <div className="flex justify-between items-center mb-3">
        <span className="text-[10px] font-bold tracking-widest text-brandCyan uppercase">Agent Connection Network</span>
        <span className="flex h-2 w-2 relative">
          <span className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 ${presence === 'online' ? 'bg-brandTeal' : 'bg-brandRed'}`}></span>
          <span className={`relative inline-flex rounded-full h-2 w-2 ${presence === 'online' ? 'bg-brandTeal' : 'bg-brandRed'}`}></span>
        </span>
      </div>
      <div className="flex-1 w-full relative">
        <canvas ref={canvasRef} className="w-full h-full block" />
      </div>
    </div>
  );
};

// 3. Real-Time ECG Heartbeat Monitor Component
const EcgMonitor: React.FC<{ presence: string }> = ({ presence }) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationFrameId: number;
    let width = canvas.width = canvas.offsetWidth;
    let height = canvas.height = canvas.offsetHeight;

    const handleResize = () => {
      if (!canvas) return;
      width = canvas.width = canvas.offsetWidth;
      height = canvas.height = canvas.offsetHeight;
    };
    window.addEventListener('resize', handleResize);

    const pointsCount = Math.floor(width / 2.5);
    const points: number[] = new Array(pointsCount).fill(height / 2);
    let tick = 0;

    const generateNextPoint = (t: number) => {
      if (presence !== 'online') {
        return height / 2 + (Math.random() - 0.5) * 1.5;
      }
      
      const cycle = t % 60;
      if (cycle === 8) return height / 2 - 8;
      if (cycle === 10) return height / 2;
      if (cycle === 15) return height / 2 + 12;
      if (cycle === 17) return height / 2 - 35;
      if (cycle === 20) return height / 2 + 22;
      if (cycle === 22) return height / 2;
      if (cycle === 28) return height / 2 - 6;
      if (cycle === 32) return height / 2;
      return height / 2 + (Math.random() - 0.5) * 0.8;
    };

    const render = () => {
      ctx.clearRect(0, 0, width, height);

      // Cyber Grid Background
      ctx.strokeStyle = 'rgba(0, 191, 165, 0.03)';
      ctx.lineWidth = 1;
      const gridSize = 14;
      for (let x = 0; x < width; x += gridSize) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, height);
        ctx.stroke();
      }
      for (let y = 0; y < height; y += gridSize) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(width, y);
        ctx.stroke();
      }

      tick++;
      const nextY = generateNextPoint(tick);
      points.push(nextY);
      points.shift();

      // Render line
      ctx.beginPath();
      ctx.lineWidth = 2.2;
      ctx.strokeStyle = presence === 'online' ? '#00BFA5' : '#64748B';
      ctx.shadowColor = presence === 'online' ? '#00BFA5' : '#64748B';
      ctx.shadowBlur = presence === 'online' ? 6 : 0;

      for (let i = 0; i < points.length; i++) {
        const x = i * 2.5;
        const y = points[i];
        if (i === 0) {
          ctx.moveTo(x, y);
        } else {
          ctx.lineTo(x, y);
        }
      }
      ctx.stroke();
      ctx.shadowBlur = 0;

      if (presence === 'online') {
        const cx = (points.length - 1) * 2.5;
        const cy = points[points.length - 1];
        ctx.beginPath();
        ctx.arc(cx, cy, 3.5, 0, Math.PI * 2);
        ctx.fillStyle = '#00BFA5';
        ctx.fill();
      }

      animationFrameId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener('resize', handleResize);
    };
  }, [presence]);

  return (
    <div className="glass-panel p-5 rounded-2xl flex flex-col h-[180px] shadow-lg relative overflow-hidden">
      <div className="flex justify-between items-center mb-3">
        <span className="text-[10px] font-bold tracking-widest text-brandTeal uppercase">Telemetry Signal Ping</span>
        <span className="text-[8px] font-bold text-mutedText bg-darkBg px-2 py-0.5 rounded uppercase tracking-wider">
          {presence === 'online' ? 'Real-Time Feed' : 'Static'}
        </span>
      </div>
      <div className="flex-1 w-full relative">
        <canvas ref={canvasRef} className="w-full h-full block" />
      </div>
    </div>
  );
};

// 4. Main Dashboard Overview Component
export const DashboardOverview: React.FC<DashboardOverviewProps> = ({ onNavigate }) => {
  const { selectedDevice, presence, telemetry, isSirenPlaying, namesMap } = useDeviceStore();
  const [calls, setCalls] = useState<CallLogItem[]>([]);
  const [sms, setSms] = useState<SmsLogItem[]>([]);
  const [recentLogs, setRecentLogs] = useState<any[]>([]);
  const [isSmsSyncing, setIsSmsSyncing] = useState(false);
  const [isMoreInfoOpen, setIsMoreInfoOpen] = useState(false);

  // Sync Calls & SMS logs
  useEffect(() => {
    if (!selectedDevice) return;

    const unsubCalls = StorageRepository.subscribeCallLogs(selectedDevice, (data) => {
      setCalls(data);
    });

    const unsubSms = StorageRepository.subscribeSmsLogs(selectedDevice, (data) => {
      setSms(data);
    });

    return () => {
      unsubCalls();
      unsubSms();
    };
  }, [selectedDevice]);

  // Combine and sort mixed logs
  useEffect(() => {
    const combined = [
      ...calls.map(c => ({ type: 'call', date: c.date, text: `${c.type} Call - ${c.name || c.number} (${c.number})` })),
      ...sms.map(s => ({ type: 'sms', date: s.date, text: `${s.type} SMS - ${s.name || s.number}: "${s.message}"` }))
    ];
    combined.sort((a, b) => b.date.localeCompare(a.date));
    setRecentLogs(combined.slice(0, 5));
  }, [calls, sms]);

  // Real storage info helper
  const getRealStorageInfo = () => {
    if (telemetry?.storageTotal && telemetry.storageTotal > 0) {
      const totalGB = telemetry.storageTotal / (1024 * 1024 * 1024);
      const usedGB = (telemetry.storageUsed || 0) / (1024 * 1024 * 1024);
      const percent = Math.round((usedGB / totalGB) * 100);
      return {
        used: usedGB.toFixed(1),
        total: Math.round(totalGB),
        percent
      };
    }
    return getStorageInfo(selectedDevice);
  };

  // Real uptime helper
  const getRealUptimeInfo = () => {
    if (telemetry?.uptime && telemetry.uptime > 0) {
      const totalSecs = Math.floor(telemetry.uptime / 1000);
      const totalMins = Math.floor(totalSecs / 60);
      const totalHours = Math.floor(totalMins / 60);
      const days = Math.floor(totalHours / 24);
      const hours = totalHours % 24;
      const mins = totalMins % 60;
      return `${days}d ${hours}h ${mins}m`;
    }
    return getUptimeInfo(selectedDevice);
  };

  // RAM formatter
  const getRamInfoStr = () => {
    if (telemetry?.ramTotal && telemetry.ramTotal > 0) {
      const totalGB = telemetry.ramTotal / (1024 * 1024 * 1024);
      const availGB = (telemetry.ramAvailable || 0) / (1024 * 1024 * 1024);
      const usedGB = totalGB - availGB;
      const percent = Math.round((usedGB / totalGB) * 100);
      return `${usedGB.toFixed(1)} GB / ${totalGB.toFixed(1)} GB (${percent}%)`;
    }
    return 'Unavailable';
  };

  const storageDetails = getRealStorageInfo();
  const uptimeDetails = getRealUptimeInfo();
  const androidVersion = telemetry?.androidVersion || parseDeviceDetails(namesMap[selectedDevice]).android;
  const deviceModel = telemetry?.deviceName || parseDeviceDetails(namesMap[selectedDevice]).model;

  const handleSiren = async () => {
    try {
      await LiveRepository.toggleSiren(selectedDevice, !isSirenPlaying);
    } catch (err) {
      console.error(err);
    }
  };

  const handleLock = async () => {
    try {
      await LiveRepository.lockScreen(selectedDevice);
      alert('Lock screen command sent to device!');
    } catch (err) {
      console.error(err);
    }
  };

  const handleScreenshot = async () => {
    try {
      await MediaRepository.captureScreenshot(selectedDevice);
      alert('Silent screenshot command sent to device!');
    } catch (err) {
      console.error(err);
    }
  };

  const handleSecretPhoto = async () => {
    try {
      await MediaRepository.captureSecretPhoto(selectedDevice);
      alert('Secret front-camera capture command sent!');
    } catch (err) {
      console.error(err);
    }
  };

  const handleForceSmsSync = async () => {
    setIsSmsSyncing(true);
    try {
      await LiveRepository.forceSmsSync(selectedDevice);
      setTimeout(() => setIsSmsSyncing(false), 3000);
    } catch (err) {
      console.error(err);
      setIsSmsSyncing(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Upper Status Grid (4 Columns matching Image 3 Reference) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-6">
        {/* Column 1: Connection status */}
        <div className="glass-panel p-6 rounded-2xl flex items-center justify-between shadow-lg">
          <div>
            <p className="text-[10px] text-mutedText font-bold uppercase tracking-wider">Device Connection</p>
            <h3 className="text-2xl font-bold mt-2 flex items-center gap-2 text-slate-800">
              {presence.state === 'online' ? (
                <>
                  <span className="h-3 w-3 bg-brandTeal rounded-full animate-pulse" />
                  Online
                </>
              ) : (
                <>
                  <span className="h-3 w-3 bg-brandRed rounded-full" />
                  Offline
                </>
              )}
            </h3>
            <p className="text-xs text-mutedText mt-2 font-medium">
              {presence.state === 'online' ? 'Active session' : 'Disconnected'}
            </p>
          </div>
          <div className="p-4 bg-brandTeal/10 text-brandTeal rounded-2xl">
            <Shield className="h-8 w-8" />
          </div>
        </div>

        {/* Column 2: Battery widget */}
        <div className="glass-panel p-6 rounded-2xl flex items-center justify-between shadow-lg">
          <div>
            <p className="text-[10px] text-mutedText font-bold uppercase tracking-wider">Battery Power</p>
            <h3 className="text-2xl font-bold mt-2 flex items-center gap-2 text-slate-800">
              <Battery className={`h-6 w-6 ${telemetry.isCharging ? 'text-brandTeal' : 'text-brandCyan'}`} />
              {telemetry.batteryLevel}%
            </h3>
            <p className="text-xs text-mutedText mt-2 font-medium">
              {telemetry.isCharging ? 'Charging' : 'Discharging'}
            </p>
          </div>
          <div className={`p-4 rounded-2xl ${telemetry.isCharging ? 'bg-brandTeal/10 text-brandTeal' : 'bg-brandCyan/10 text-brandCyan'}`}>
            <Battery className="h-8 w-8" />
          </div>
        </div>

        {/* Column 3: Network Status */}
        <div className="glass-panel p-6 rounded-2xl flex items-center justify-between shadow-lg">
          <div>
            <p className="text-[10px] text-mutedText font-bold uppercase tracking-wider">Network Status</p>
            <h3 className="text-xl font-bold mt-2 truncate max-w-[150px] flex items-center gap-2 text-slate-800">
              <Wifi className="h-5 w-5 text-brandBlue" />
              {telemetry.networkType}
            </h3>
            <p className="text-xs text-mutedText mt-2 font-medium">
              {telemetry.networkType === 'Wifi' ? 'Connected via Wifi' : 'Jio 4G LTE'}
            </p>
          </div>
          <div className="p-4 bg-brandBlue/10 text-brandBlue rounded-2xl">
            <Wifi className="h-8 w-8" />
          </div>
        </div>

        {/* Column 4: Last Seen */}
        <div className="glass-panel p-6 rounded-2xl flex items-center justify-between shadow-lg">
          <div>
            <p className="text-[10px] text-mutedText font-bold uppercase tracking-wider">Last Seen</p>
            <h3 className="text-xl font-bold mt-2 truncate max-w-[150px] flex items-center gap-1.5 text-slate-800">
              {presence.state === 'online' ? 'Active session' : formatLastSeenTime(presence.lastSeen).diff}
            </h3>
            <p className="text-[10px] text-mutedText mt-2 font-medium">
              {formatLastSeenTime(presence.lastSeen).sub}
            </p>
          </div>
          <div className="p-4 bg-brandCyan/10 text-brandCyan rounded-2xl">
            <Clock className="h-8 w-8" />
          </div>
        </div>
      </div>

      {/* Cyber Monitoring Animations Panels */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <NetworkFlowMonitor presence={presence.state} />
        <EcgMonitor presence={presence.state} />
      </div>



      {/* Emergency Commands Action Panel */}
      <div className="glass-panel p-6 rounded-2xl shadow-lg">
        <h3 className="text-lg font-bold text-brandCyan mb-4 flex items-center gap-2">
          <Settings className="h-5 w-5 animate-spin" style={{ animationDuration: '6s' }} />
          Emergency Remote Commands
        </h3>
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-4">
          <button
            onClick={handleSiren}
            className={`flex flex-col items-center justify-center p-4 rounded-xl font-semibold transition-all border cursor-pointer ${
              isSirenPlaying 
                ? 'bg-brandRed/20 border-brandRed text-brandRed hover:bg-brandRed/30' 
                : 'bg-darkSurf border-darkBorder text-slate-700 hover:border-brandRed hover:text-brandRed'
            }`}
          >
            {isSirenPlaying ? <VolumeX className="h-6 w-6 mb-2" /> : <Volume2 className="h-6 w-6 mb-2" />}
            {isSirenPlaying ? 'Stop Alarm Siren' : 'Trigger Alarm Siren'}
          </button>

          <button
            onClick={handleLock}
            className="flex flex-col items-center justify-center p-4 rounded-xl font-semibold bg-darkSurf border border-darkBorder text-slate-700 hover:border-brandCyan hover:text-brandCyan transition-all cursor-pointer"
          >
            <Lock className="h-6 w-6 mb-2 text-brandCyan" />
            Lock Child Device
          </button>

          <button
            onClick={handleScreenshot}
            className="flex flex-col items-center justify-center p-4 rounded-xl font-semibold bg-darkSurf border border-darkBorder text-slate-700 hover:border-brandTeal hover:text-brandTeal transition-all cursor-pointer"
          >
            <Camera className="h-6 w-6 mb-2 text-brandTeal" />
            Silent Screenshot
          </button>

          <button
            onClick={handleSecretPhoto}
            className="flex flex-col items-center justify-center p-4 rounded-xl font-semibold bg-darkSurf border border-darkBorder text-slate-700 hover:border-brandBlue hover:text-brandBlue transition-all cursor-pointer"
          >
            <Camera className="h-6 w-6 mb-2 text-brandBlue" />
            Secret Photo Front
          </button>

          <button
            onClick={handleForceSmsSync}
            disabled={isSmsSyncing}
            className="flex flex-col items-center justify-center p-4 rounded-xl font-semibold bg-darkSurf border border-darkBorder text-slate-700 hover:border-brandCyan hover:text-brandCyan transition-all disabled:opacity-50 cursor-pointer"
          >
            <RefreshCw className={`h-6 w-6 mb-2 text-brandCyan ${isSmsSyncing ? 'animate-spin' : ''}`} />
            {isSmsSyncing ? 'Syncing...' : 'Force SMS Sync'}
          </button>
        </div>
      </div>

      {/* Summary stats & logs feed */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Left Column: Toggles + Device Info */}
        <div className="space-y-6">
          {/* Quick Nav Toggles */}
          <div className="grid grid-cols-3 gap-4 h-fit">
            <div 
              onClick={() => onNavigate('Calls')}
              className="glass-panel p-5 rounded-2xl text-center cursor-pointer hover:border-brandCyan transition-all shadow-md flex flex-col justify-center animate-fade-in"
            >
              <p className="text-[10px] text-mutedText font-bold uppercase tracking-wider">Calls Logged</p>
              <p className="text-2xl font-black mt-2 text-brandCyan">{calls.length}</p>
            </div>
            <div 
              onClick={() => onNavigate('SMS')}
              className="glass-panel p-5 rounded-2xl text-center cursor-pointer hover:border-brandBlue transition-all shadow-md flex flex-col justify-center animate-fade-in"
            >
              <p className="text-[10px] text-mutedText font-bold uppercase tracking-wider">SMS Synced</p>
              <p className="text-2xl font-black mt-2 text-brandBlue">{sms.length}</p>
            </div>
            <div 
              onClick={() => onNavigate('Calls')}
              className="glass-panel p-5 rounded-2xl text-center cursor-pointer hover:border-brandRed transition-all shadow-md flex flex-col justify-center animate-fade-in"
            >
              <p className="text-[10px] text-mutedText font-bold uppercase tracking-wider">Blocked Numbers</p>
              <p className="text-xl font-black mt-2.5 text-brandRed">Configured</p>
            </div>
          </div>

          {/* Device Overview (Relocated below stats cards) */}
          <div className="glass-panel p-6 rounded-2xl shadow-lg">
            <div className="flex justify-between items-center mb-3">
              <h4 className="text-sm font-bold text-brandTeal">Device Overview</h4>
              <button
                onClick={() => setIsMoreInfoOpen(true)}
                className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-darkBorder hover:border-brandTeal/30 text-mutedText hover:text-brandTeal text-[10px] font-bold transition-all cursor-pointer"
                title="View detailed phone specification info"
              >
                <Info className="h-3.5 w-3.5" />
                More Info
              </button>
            </div>
            <div className="space-y-3.5 text-xs">
              <div className="flex justify-between py-1 border-b border-darkBorder">
                <span className="text-mutedText font-semibold">Android Version</span>
                <span className="font-extrabold text-slate-700">{androidVersion}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-darkBorder">
                <span className="text-mutedText font-semibold">Device Model</span>
                <span className="font-extrabold text-slate-700">{deviceModel}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-darkBorder items-center">
                <span className="text-mutedText font-semibold">Storage Used</span>
                <div className="flex items-center gap-3 w-[60%] justify-end">
                  <div className="w-full bg-slate-200 h-2 rounded-full overflow-hidden">
                    <div 
                      className="bg-brandCyan h-full transition-all duration-500" 
                      style={{ width: `${storageDetails.percent}%` }}
                    />
                  </div>
                  <span className="font-extrabold text-slate-700 text-[10px] shrink-0">
                    {storageDetails.used} GB / {storageDetails.total} GB ({storageDetails.percent}%)
                  </span>
                </div>
              </div>
              <div className="flex justify-between py-1">
                <span className="text-mutedText font-semibold">Uptime</span>
                <span className="font-extrabold text-slate-700">{uptimeDetails}</span>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: Sync activity feed */}
        <div className="glass-panel p-6 rounded-2xl shadow-lg flex flex-col justify-between">
          <div>
            <h3 className="text-lg font-bold mb-4 text-brandCyan">Recent Synchronized Logs</h3>
            <div className="space-y-3">
              {recentLogs.length === 0 ? (
                <p className="text-sm italic text-mutedText text-center py-4">No recent activity received yet.</p>
              ) : (
                recentLogs.map((log, idx) => (
                  <div key={idx} className="flex justify-between items-center text-xs py-2 border-b border-darkBorder last:border-0">
                    <div className="flex items-center gap-2 max-w-[70%]">
                      <span className={`px-2 py-0.5 rounded font-bold uppercase tracking-wider text-[9px] ${log.type === 'call' ? 'bg-brandCyan/20 text-brandCyan' : 'bg-brandBlue/20 text-brandBlue'}`}>
                        {log.type}
                      </span>
                      <span className="truncate font-semibold text-slate-700">{log.text}</span>
                    </div>
                    <span className="text-mutedText">{log.date}</span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>

      {/* More Info Modal */}
      {isMoreInfoOpen && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-white border border-darkBorder rounded-3xl w-full max-w-2xl shadow-2xl overflow-hidden flex flex-col max-h-[85vh] animate-scale-up">
            
            {/* Modal Header */}
            <div className="px-6 py-5 border-b border-darkBorder bg-slate-50 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="h-9 w-9 bg-brandTeal/10 text-brandTeal rounded-lg flex items-center justify-center border border-brandTeal/20">
                  <Shield className="h-4.5 w-4.5" />
                </div>
                <div>
                  <h3 className="font-bold text-slate-800 text-sm tracking-wide uppercase">Detailed Phone Specifications</h3>
                  <p className="text-[10px] text-mutedText font-semibold mt-0.5">{deviceModel} ({androidVersion})</p>
                </div>
              </div>
              <button 
                onClick={() => setIsMoreInfoOpen(false)}
                className="p-1.5 rounded-lg border border-darkBorder hover:bg-slate-100 hover:text-brandRed text-mutedText transition-all cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6 overflow-y-auto space-y-6">
              
              {/* Section 1: Static Specifications */}
              <div>
                <h4 className="text-[11px] font-black text-brandTeal uppercase tracking-wider mb-3 flex items-center gap-1.5">
                  <span className="h-1.5 w-1.5 bg-brandTeal rounded-full" />
                  Static Hardware & Identifiers
                </h4>
                <div className="grid grid-cols-2 gap-x-6 gap-y-3.5 bg-slate-50/50 p-4 rounded-2xl border border-darkBorder text-xs">
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200">
                    <span className="text-mutedText font-medium">Manufacturer</span>
                    <span className="font-bold text-slate-700">{telemetry?.manufacturer || 'Nothing'}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200">
                    <span className="text-mutedText font-medium">Device Model</span>
                    <span className="font-bold text-slate-700">{telemetry?.model || 'Nothing A142'}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200">
                    <span className="text-mutedText font-medium">Brand</span>
                    <span className="font-bold text-slate-700">{telemetry?.brand || 'Nothing'}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200">
                    <span className="text-mutedText font-medium">Hardware Board</span>
                    <span className="font-bold text-slate-700">{telemetry?.hardware || 'lahaina'}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200">
                    <span className="text-mutedText font-medium">CPU Architecture</span>
                    <span className="font-bold text-slate-700 font-mono text-[11px]">{telemetry?.cpuAbi || 'arm64-v8a'}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200">
                    <span className="text-mutedText font-medium">Screen Resolution</span>
                    <span className="font-bold text-slate-700">{telemetry?.screenResolution || '1080x2412'}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200">
                    <span className="text-mutedText font-medium">Android SDK Version</span>
                    <span className="font-bold text-slate-700">API {telemetry?.sdkVersion || 35}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200">
                    <span className="text-mutedText font-medium">SIM Serial (ICCID)</span>
                    <span className="font-bold text-slate-700 font-mono text-[10px] tracking-tight">{telemetry?.simSerialNumber || 'Unavailable'}</span>
                  </div>
                  <div className="flex justify-between py-1 col-span-2">
                    <span className="text-mutedText font-medium">Device Unique ID (IMEI / ANDROID_ID)</span>
                    <span className="font-bold text-slate-700 font-mono text-[10.5px] tracking-wide select-all bg-slate-100 px-2 py-0.5 rounded border border-darkBorder">{telemetry?.imei || 'Unavailable'}</span>
                  </div>
                </div>
              </div>

              {/* Section 2: Dynamic System Status */}
              <div>
                <h4 className="text-[11px] font-black text-brandCyan uppercase tracking-wider mb-3 flex items-center gap-1.5">
                  <span className="h-1.5 w-1.5 bg-brandCyan rounded-full animate-pulse" />
                  Dynamic Status & Diagnostics
                </h4>
                <div className="grid grid-cols-2 gap-x-6 gap-y-3.5 bg-slate-50/50 p-4 rounded-2xl border border-darkBorder text-xs">
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200">
                    <span className="text-mutedText font-medium">System Uptime</span>
                    <span className="font-bold text-slate-700">{uptimeDetails}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200">
                    <span className="text-mutedText font-medium">Battery Status</span>
                    <span className="font-bold text-slate-700">
                      {telemetry?.batteryLevel}% {telemetry?.isCharging ? '(Charging)' : '(Discharging)'}
                    </span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200">
                    <span className="text-mutedText font-medium">Network Type</span>
                    <span className="font-bold text-slate-700">{telemetry?.networkType || 'Unknown'}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200">
                    <span className="text-mutedText font-medium">Local IP Address</span>
                    <span className="font-bold text-slate-700 font-mono text-[11px]">{telemetry?.localIp || '192.168.1.15'}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200">
                    <span className="text-mutedText font-medium">SIM Operator Name</span>
                    <span className="font-bold text-slate-700">{telemetry?.simOperator || 'Jio'}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200">
                    <span className="text-mutedText font-medium">SIM State</span>
                    <span className="font-bold text-slate-700">{telemetry?.simState || 'Ready'}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200 col-span-2">
                    <span className="text-mutedText font-medium">Network Carrier Operator</span>
                    <span className="font-bold text-slate-700">{telemetry?.phoneNetworkOperator || 'Jio 4G'}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200 col-span-2">
                    <span className="text-mutedText font-medium">SIM Phone Number</span>
                    <span className="font-bold text-slate-700">{telemetry?.phoneNumber || 'Unavailable'}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-dashed border-slate-200 col-span-2 items-center">
                    <span className="text-mutedText font-medium">RAM Utilization</span>
                    <span className="font-bold text-slate-700 text-[10.5px]">
                      {getRamInfoStr()}
                    </span>
                  </div>
                  <div className="flex justify-between py-1 col-span-2 items-center">
                    <span className="text-mutedText font-medium">Internal Storage</span>
                    <span className="font-bold text-slate-700 text-[10.5px]">
                      {storageDetails.used} GB / {storageDetails.total} GB ({storageDetails.percent}%)
                    </span>
                  </div>
                </div>
              </div>

            </div>

            {/* Modal Footer */}
            <div className="px-6 py-4 border-t border-darkBorder bg-slate-50 flex justify-end">
              <button 
                onClick={() => setIsMoreInfoOpen(false)}
                className="px-5 py-2 rounded-xl bg-slate-800 text-white font-bold text-xs hover:bg-slate-700 transition-all cursor-pointer shadow-sm"
              >
                Close Details
              </button>
            </div>

          </div>
        </div>
      )}
    </div>
  );
};

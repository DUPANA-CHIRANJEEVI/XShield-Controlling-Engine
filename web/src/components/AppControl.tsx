import React, { useEffect, useState } from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { WebRtcRepository } from '../firebase/webrtcRepository';
import type { AppTargetItem } from '../firebase/webrtcRepository';
import { ShieldCheck, ShieldAlert, Search, AppWindow } from 'lucide-react';

export const AppControl: React.FC = () => {
  const { selectedDevice } = useDeviceStore();
  const [apps, setApps] = useState<AppTargetItem[]>([]);
  const [blockedApps, setBlockedApps] = useState<string[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterType, setFilterType] = useState<'All' | 'Blocked' | 'Allowed'>('All');

  useEffect(() => {
    if (!selectedDevice) return;

    // Listen to installed apps list
    const unsubApps = WebRtcRepository.subscribeInstalledApps(selectedDevice, (installedList) => {
      setApps(installedList);
    });

    // Listen to device configuration (which includes settings & blocked apps)
    const unsubConfig = WebRtcRepository.subscribeDeviceConfig(selectedDevice, (config) => {
      setBlockedApps(config.blockedApps);
    });

    return () => {
      unsubApps();
      unsubConfig();
    };
  }, [selectedDevice]);

  const handleToggleBlock = async (packageName: string, isCurrentlyBlocked: boolean) => {
    try {
      let updatedList = [...blockedApps];
      if (isCurrentlyBlocked) {
        // Unblock it
        updatedList = updatedList.filter((pkg) => pkg !== packageName);
      } else {
        // Block it
        updatedList.push(packageName);
      }
      await WebRtcRepository.updateBlockedApps(selectedDevice, updatedList);
    } catch (err) {
      console.error('Error toggling block state:', err);
    }
  };

  // Combine and flag app items
  const combinedApps = apps.map((app) => ({
    ...app,
    isBlocked: blockedApps.includes(app.packageName)
  }));

  // Sort: blocked apps first, then alphabetically by app name
  combinedApps.sort((a, b) => {
    if (a.isBlocked !== b.isBlocked) {
      return a.isBlocked ? -1 : 1;
    }
    return a.name.localeCompare(b.name);
  });

  const filteredApps = combinedApps.filter((app) => {
    const matchesSearch = 
      app.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      app.packageName.toLowerCase().includes(searchQuery.toLowerCase());
    
    if (filterType === 'All') return matchesSearch;
    if (filterType === 'Blocked') return app.isBlocked && matchesSearch;
    return !app.isBlocked && matchesSearch;
  });

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-xl font-bold flex items-center gap-2 text-brandCyan">
            <AppWindow className="h-6 w-6" />
            Installed Applications Control
          </h2>
          <p className="text-xs text-mutedText mt-1">Manage app permissions and block/restrict execution on child device</p>
        </div>
      </div>

      {/* Control Pane Card */}
      <div className="glass-panel p-6 rounded-2xl shadow-lg border border-darkBorder">
        {/* Filters and search */}
        <div className="flex flex-col sm:flex-row justify-between items-center gap-4 mb-6">
          <div className="relative w-full sm:w-[300px]">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-mutedText" />
            <input
              type="text"
              placeholder="Search by app name or package..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full bg-darkSurf border border-darkBorder rounded-xl text-xs pl-9 pr-3 py-2.5 text-gray-200 focus:outline-none focus:border-brandCyan"
            />
          </div>

          <div className="flex bg-darkSurf border border-darkBorder rounded-xl p-0.5 w-full sm:w-auto justify-around">
            {(['All', 'Blocked', 'Allowed'] as const).map((type) => (
              <button
                key={type}
                onClick={() => setFilterType(type)}
                className={`text-[10px] font-bold px-4 py-2 rounded-lg transition-all ${
                  filterType === type ? 'bg-brandCyan text-darkBg' : 'text-mutedText hover:text-gray-200'
                }`}
              >
                {type}
              </button>
            ))}
          </div>
        </div>

        {/* Apps Grid */}
        {filteredApps.length === 0 ? (
          <p className="text-sm italic text-mutedText text-center py-12">No apps matching filters found.</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredApps.map((app) => (
              <div 
                key={app.packageName} 
                className={`p-4 rounded-xl border transition-all flex items-center justify-between shadow ${
                  app.isBlocked 
                    ? 'bg-brandRed/5 border-brandRed/20 hover:border-brandRed/45' 
                    : 'bg-darkSurf/20 border-darkBorder hover:border-brandCyan/40'
                }`}
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className={`h-10 w-10 rounded-xl flex items-center justify-center font-black uppercase text-sm ${
                    app.isBlocked ? 'bg-brandRed/10 text-brandRed' : 'bg-brandCyan/10 text-brandCyan'
                  }`}>
                    {app.name.charAt(0)}
                  </div>
                  <div className="min-w-0">
                    <h4 className="text-xs font-bold text-gray-200 truncate">{app.name}</h4>
                    <p className="text-[9px] text-mutedText truncate font-mono mt-0.5">{app.packageName}</p>
                    <p className="text-[9px] text-mutedText mt-0.5">Usage: {app.usageTime}</p>
                  </div>
                </div>

                <button
                  onClick={() => handleToggleBlock(app.packageName, app.isBlocked)}
                  className={`px-3 py-1.5 rounded-lg text-[10px] font-extrabold uppercase tracking-wider flex items-center gap-1 transition-all ${
                    app.isBlocked 
                      ? 'bg-brandRed text-white hover:bg-brandRed/80' 
                      : 'bg-darkSurf border border-darkBorder text-mutedText hover:border-brandCyan hover:text-brandCyan'
                  }`}
                >
                  {app.isBlocked ? <ShieldAlert className="h-3.5 w-3.5" /> : <ShieldCheck className="h-3.5 w-3.5" />}
                  {app.isBlocked ? 'BLOCKED' : 'BLOCK'}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

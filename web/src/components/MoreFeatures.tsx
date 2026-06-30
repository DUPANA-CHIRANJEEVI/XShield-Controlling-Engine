import React, { useEffect, useState } from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { WebRtcRepository } from '../firebase/webrtcRepository';
import type { WebHistoryItem } from '../firebase/webrtcRepository';
import { Globe, Calendar, UserCheck, ShieldCheck, HelpCircle } from 'lucide-react';

interface MoreFeaturesProps {
  initialTab: 'History' | 'Calendar' | 'Account';
}

export const MoreFeatures: React.FC<MoreFeaturesProps> = ({ initialTab }) => {
  const { selectedDevice } = useDeviceStore();
  const [activeTab, setActiveTab] = useState<'History' | 'Calendar' | 'Account'>(initialTab);
  const [webHistory, setWebHistory] = useState<WebHistoryItem[]>([]);
  const [searchHistory, setSearchHistory] = useState('');

  useEffect(() => {
    setActiveTab(initialTab);
  }, [initialTab]);

  useEffect(() => {
    if (!selectedDevice || activeTab !== 'History') return;

    const unsub = WebRtcRepository.subscribeWebHistory(selectedDevice, (history) => {
      setWebHistory(history);
    });

    return () => unsub();
  }, [selectedDevice, activeTab]);

  const filteredHistory = webHistory.filter(h => 
    h.title.toLowerCase().includes(searchHistory.toLowerCase()) ||
    h.url.toLowerCase().includes(searchHistory.toLowerCase())
  );

  return (
    <div className="space-y-6">
      {/* Tab Switcher Headers */}
      <div className="flex bg-slate-100 border border-slate-200/60 rounded-xl p-0.5 w-fit shadow-sm">
        <button
          onClick={() => setActiveTab('History')}
          className={`text-xs font-bold px-4 py-2.5 rounded-lg flex items-center gap-1.5 transition-all ${
            activeTab === 'History' 
              ? 'bg-white text-slate-800 shadow-sm border border-slate-200/30' 
              : 'text-slate-500 hover:text-slate-800'
          }`}
        >
          <Globe className="h-4 w-4" />
          Browser History
        </button>
        <button
          onClick={() => setActiveTab('Calendar')}
          className={`text-xs font-bold px-4 py-2.5 rounded-lg flex items-center gap-1.5 transition-all ${
            activeTab === 'Calendar' 
              ? 'bg-white text-slate-800 shadow-sm border border-slate-200/30' 
              : 'text-slate-500 hover:text-slate-800'
          }`}
        >
          <Calendar className="h-4 w-4" />
          Calendar logs
        </button>
        <button
          onClick={() => setActiveTab('Account')}
          className={`text-xs font-bold px-4 py-2.5 rounded-lg flex items-center gap-1.5 transition-all ${
            activeTab === 'Account' 
              ? 'bg-white text-slate-800 shadow-sm border border-slate-200/30' 
              : 'text-slate-500 hover:text-slate-800'
          }`}
        >
          <UserCheck className="h-4 w-4" />
          My Account
        </button>
      </div>

      {/* Dynamic Content Pane */}
      {activeTab === 'History' && (
        <div className="glass-panel p-6 rounded-2xl shadow-sm border border-slate-200/60 bg-white space-y-4">
          <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4">
            <div>
              <h3 className="text-sm font-black text-slate-800 uppercase tracking-wide flex items-center gap-2">
                <Globe className="h-5 w-5 text-brandCyan" />
                Web Browsing History Logs
              </h3>
              <p className="text-[10px] text-slate-500 font-semibold mt-0.5">Monitors visited website links and search keywords</p>
            </div>
            <input
              type="text"
              placeholder="Search history URLs..."
              value={searchHistory}
              onChange={(e) => setSearchHistory(e.target.value)}
              className="bg-slate-50 border border-slate-200 focus:border-brandCyan focus:bg-white text-slate-800 transition-all font-semibold rounded-xl text-xs px-3.5 py-2 w-full sm:w-[260px] focus:outline-none"
            />
          </div>

          <div className="space-y-3 max-h-[calc(100vh-270px)] overflow-y-auto pr-1">
            {filteredHistory.length === 0 ? (
              <p className="text-xs italic text-slate-500 font-semibold text-center py-12">No web browser history records logged.</p>
            ) : (
              filteredHistory.map((h, i) => {
                const browserName = h.browser.toLowerCase();
                let badgeClass = 'bg-slate-50 text-slate-600 border-slate-100';
                if (browserName.includes('chrome')) {
                  badgeClass = 'bg-amber-50 text-amber-700 border-amber-100';
                } else if (browserName.includes('firefox')) {
                  badgeClass = 'bg-orange-50 text-orange-700 border-orange-100';
                } else if (browserName.includes('safari')) {
                  badgeClass = 'bg-sky-50 text-sky-700 border-sky-100';
                }

                return (
                  <div key={i} className="flex justify-between items-center p-4 bg-white hover:bg-slate-50/50 border border-slate-100 rounded-xl text-xs shadow-sm transition-all duration-200">
                    <div className="min-w-0 flex-1 mr-4">
                      <p className="font-bold text-slate-800 truncate">{h.title || 'Untitled Site'}</p>
                      <a href={h.url} target="_blank" rel="noreferrer" className="text-[10px] text-brandCyan truncate hover:underline font-mono block mt-1">
                        {h.url}
                      </a>
                    </div>
                    <div className="text-right shrink-0">
                      <span className={`px-2 py-0.5 rounded-md text-[8px] font-black uppercase tracking-wider border ${badgeClass}`}>
                        {h.browser}
                      </span>
                      <p className="text-[9px] text-slate-400 font-bold mt-1.5">{new Date(h.timestamp).toLocaleString()}</p>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}

      {activeTab === 'Calendar' && (
        <div className="glass-panel p-6 rounded-2xl shadow-sm border border-slate-200/60 bg-white space-y-4">
          <div>
            <h3 className="text-sm font-black text-slate-800 uppercase tracking-wide flex items-center gap-2">
              <Calendar className="h-5 w-5 text-brandCyan" />
              Calendar Agenda Logs
            </h3>
            <p className="text-[10px] text-slate-500 font-semibold mt-0.5">Monitors targeted events and calendar schedule logs on child device</p>
          </div>
          <div className="divide-y divide-slate-100">
            {[
              { title: 'Dentist Appointment', time: 'Tomorrow 16:30 • Care Dental clinic' },
              { title: 'Science Project Submission', time: 'Monday 14:00 • School Classroom' },
              { title: 'Math Quiz Homework Test', time: 'Friday 09:00 • Homework planner' }
            ].map((ev, idx) => (
              <div key={idx} className="flex items-start gap-3.5 py-4 first:pt-0 last:pb-0 text-xs">
                <Calendar className="h-5 w-5 text-brandCyan shrink-0 mt-0.5" />
                <div>
                  <h4 className="font-bold text-slate-800">{ev.title}</h4>
                  <p className="text-slate-500 text-[11px] font-semibold mt-1">{ev.time}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {activeTab === 'Account' && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="glass-panel p-6 rounded-2xl shadow-sm border border-slate-200/60 bg-white md:col-span-2 space-y-4">
            <h3 className="text-xs font-black text-slate-800 uppercase tracking-wider border-b border-slate-100 pb-3 mb-4">Parent Profile & Plan Tier</h3>
            <div className="grid grid-cols-2 gap-4 text-xs">
              <div className="p-4 bg-slate-50 border border-slate-100 rounded-xl">
                <p className="text-[10px] text-slate-400 font-black uppercase tracking-wider">Plan License</p>
                <p className="text-sm font-black text-brandTeal mt-1.5 flex items-center gap-1.5">
                  <ShieldCheck className="h-4 w-4 text-brandTeal" />
                  PREMIUM PRO TIER
                </p>
              </div>
              <div className="p-4 bg-slate-50 border border-slate-100 rounded-xl">
                <p className="text-[10px] text-slate-400 font-black uppercase tracking-wider">Device Limits</p>
                <p className="text-sm font-black text-slate-800 mt-1.5">Unlimited devices</p>
              </div>
            </div>
          </div>
          <div className="glass-panel p-6 rounded-2xl shadow-sm border border-slate-200/60 bg-white flex flex-col justify-between">
            <div>
              <h3 className="text-xs font-black text-slate-800 uppercase tracking-wider border-b border-slate-100 pb-3 mb-4">Support Portal</h3>
              <p className="text-[11px] text-slate-600 font-semibold leading-relaxed mt-2">
                If you encounter connection drops, verify that the child agent has active Accessibility permissions.
              </p>
            </div>
            <button className="w-full bg-slate-50 hover:bg-slate-100/80 border border-slate-200 text-xs text-slate-700 font-black py-2.5 rounded-xl flex items-center justify-center gap-1.5 transition-all mt-4">
              <HelpCircle className="h-4 w-4 text-brandCyan" />
              Get Technical Help
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

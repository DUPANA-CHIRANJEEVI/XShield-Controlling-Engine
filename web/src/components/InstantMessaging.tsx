import React, { useEffect, useState } from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { MediaRepository } from '../firebase/mediaRepository';
import type { InstantMessageItem } from '../firebase/mediaRepository';
import { MessageCircle, Trash2, User } from 'lucide-react';

export const InstantMessaging: React.FC = () => {
  const { selectedDevice } = useDeviceStore();
  const [messages, setMessages] = useState<InstantMessageItem[]>([]);
  const [config, setConfig] = useState<Record<string, boolean>>({});
  const [searchQuery, setSearchQuery] = useState('');

  const chatApps = [
    { key: 'whatsapp', label: 'WhatsApp Messenger' },
    { key: 'instagram', label: 'Instagram Notifications' },
    { key: 'telegram', label: 'Telegram Messenger' },
    { key: 'messenger', label: 'Facebook Messenger' }
  ];

  useEffect(() => {
    if (!selectedDevice) return;

    // Listen to messages
    const unsubMessages = MediaRepository.subscribeInstantMessages(selectedDevice, (data) => {
      setMessages(data);
    });

    // Listen to configs
    const unsubConfig = MediaRepository.subscribeInstantMessagingConfig(selectedDevice, (data) => {
      setConfig(data);
    });

    return () => {
      unsubMessages();
      unsubConfig();
    };
  }, [selectedDevice]);

  const handleToggleApp = async (appKey: string, currentVal: boolean) => {
    try {
      await MediaRepository.updateInstantMessagingAppConfig(selectedDevice, appKey, !currentVal);
    } catch (err) {
      console.error(err);
    }
  };

  const handleClearLogs = async () => {
    if (window.confirm('Delete all social chat notifications from parent dashboard?')) {
      try {
        await MediaRepository.clearInstantMessages(selectedDevice);
      } catch (err) {
        console.error(err);
      }
    }
  };

  // Filter message list
  const filteredMessages = messages.filter((m) =>
    m.sender.toLowerCase().includes(searchQuery.toLowerCase()) ||
    m.message.toLowerCase().includes(searchQuery.toLowerCase()) ||
    m.app.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-sm font-black text-slate-800 uppercase tracking-wide flex items-center gap-2">
            <MessageCircle className="h-5 w-5 text-brandCyan" />
            Social Messaging Watchdog
          </h2>
          <p className="text-[10px] text-slate-500 font-semibold mt-0.5">Monitors incoming chat notifications and intercepting messages</p>
        </div>
        <button
          onClick={handleClearLogs}
          disabled={messages.length === 0}
          className="bg-rose-50 border border-rose-200/60 hover:bg-rose-100/70 text-rose-600 font-black text-xs px-4 py-2.5 rounded-xl flex items-center gap-1.5 transition-all shadow-sm disabled:opacity-30 disabled:hover:bg-rose-50"
        >
          <Trash2 className="h-4 w-4" />
          Clear Chat History
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Left Control Panel: Monitoring filters */}
        <div className="glass-panel p-6 rounded-2xl shadow-sm border border-slate-200/60 bg-white h-fit space-y-4">
          <h3 className="text-xs font-black text-slate-800 uppercase tracking-wider border-b border-slate-100 pb-3 mb-4">Active Listeners</h3>
          <div className="space-y-3">
            {chatApps.map((app) => {
              const isEnabled = config[app.key] ?? true;
              return (
                <div key={app.key} className="flex items-center justify-between p-3 bg-slate-50/80 border border-slate-100 rounded-xl">
                  <span className="text-xs font-bold text-slate-700">{app.label}</span>
                  <button
                    onClick={() => handleToggleApp(app.key, isEnabled)}
                    className={`px-3 py-1.5 rounded-lg text-[10px] font-black transition-all border ${
                      isEnabled 
                        ? 'bg-emerald-50 border-emerald-200/60 text-emerald-700 hover:bg-emerald-100/80 shadow-sm' 
                        : 'bg-slate-100 border-slate-200 text-slate-400 hover:bg-slate-200/50'
                    }`}
                  >
                    {isEnabled ? 'ACTIVE' : 'MUTED'}
                  </button>
                </div>
              );
            })}
          </div>
        </div>

        {/* Right Message Logs Ticker */}
        <div className="lg:col-span-3 glass-panel p-6 rounded-2xl shadow-sm border border-slate-200/60 bg-white flex flex-col h-[calc(100vh-200px)]">
          <div className="flex justify-between items-center mb-4 pb-4 border-b border-slate-100">
            <h3 className="text-xs font-black text-slate-800 uppercase tracking-wider">Intercepted Chat Stream</h3>
            <input
              type="text"
              placeholder="Search chats by sender or keyword..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="bg-slate-50 border border-slate-200 focus:border-brandCyan focus:bg-white text-slate-800 transition-all font-semibold rounded-xl text-xs px-3.5 py-2 w-[260px] focus:outline-none"
            />
          </div>

          <div className="flex-1 overflow-y-auto space-y-4 pr-1">
            {filteredMessages.length === 0 ? (
              <p className="text-xs italic text-slate-500 font-semibold text-center py-16">No instant messaging logs intercepted.</p>
            ) : (
              filteredMessages.map((msg) => {
                const appName = msg.app.toLowerCase();
                let badgeClass = 'bg-slate-50 text-slate-600 border-slate-100';
                if (appName.includes('whatsapp')) {
                  badgeClass = 'bg-emerald-50 text-emerald-700 border-emerald-100';
                } else if (appName.includes('telegram')) {
                  badgeClass = 'bg-sky-50 text-sky-700 border-sky-100';
                } else if (appName.includes('instagram')) {
                  badgeClass = 'bg-pink-50 text-pink-700 border-pink-100';
                } else if (appName.includes('messenger') || appName.includes('facebook')) {
                  badgeClass = 'bg-blue-50 text-blue-700 border-blue-100';
                }

                return (
                  <div key={msg.id} className="flex gap-3.5 bg-white hover:bg-slate-50/50 border border-slate-100 rounded-xl p-4 shadow-sm transition-all duration-200">
                    <div className="h-9 w-9 bg-slate-100/80 text-slate-600 rounded-full flex items-center justify-center font-bold border border-slate-200/50">
                      <User className="h-4.5 w-4.5" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex justify-between items-baseline mb-1">
                        <h4 className="text-xs font-black text-slate-800 flex items-center gap-2">
                          {msg.sender}
                          <span className={`px-2 py-0.5 rounded-md text-[8px] font-black uppercase tracking-wide border ${badgeClass}`}>
                            {msg.app}
                          </span>
                        </h4>
                        <span className="text-[10px] text-slate-400 font-bold">{new Date(msg.timestamp).toLocaleString()}</span>
                      </div>
                      <p className="text-xs font-semibold text-slate-600 leading-relaxed mt-1.5">{msg.message}</p>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

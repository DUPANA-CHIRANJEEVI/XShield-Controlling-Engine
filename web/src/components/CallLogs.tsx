import React, { useEffect, useState, useMemo } from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { StorageRepository } from '../firebase/storageRepository';
import type { CallLogItem } from '../firebase/storageRepository';
import { LiveRepository } from '../firebase/liveRepository';
import { 
  Phone, PhoneIncoming, PhoneOutgoing, PhoneMissed, Trash2, 
  ShieldAlert, Plus, X, Search, Copy, Check, 
  Volume2, Play, Shield, Ban, Lock
} from 'lucide-react';


export const CallLogs: React.FC = () => {
  const { selectedDevice, ringingCall } = useDeviceStore();
  const [calls, setCalls] = useState<CallLogItem[]>([]);
  const [blockedList, setBlockedList] = useState<any[]>([]);
  const [globalBlocking, setGlobalBlocking] = useState({ blockAllIncoming: false, blockAllOutgoing: false });
  const [searchQuery, setSearchQuery] = useState('');
  const [filterType, setFilterType] = useState<'All' | 'Incoming' | 'Outgoing' | 'Missed' | 'Blocked'>('All');
  
  // Modals & States
  const [showBlockModal, setShowBlockModal] = useState(false);
  const [newBlockNumber, setNewBlockNumber] = useState('');
  const [newBlockScope, setNewBlockScope] = useState<'Incoming' | 'Outgoing' | 'Both'>('Both');
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [playingAudioId, setPlayingAudioId] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedDevice) return;

    const unsubCalls = StorageRepository.subscribeCallLogs(selectedDevice, (data) => {
      setCalls(data);
    });

    const unsubBlocked = LiveRepository.subscribeBlockedList(selectedDevice, (data) => {
      setBlockedList(data);
    });

    const unsubGlobal = LiveRepository.subscribeGlobalCallBlocking(selectedDevice, (data) => {
      setGlobalBlocking(data);
    });

    return () => {
      unsubCalls();
      unsubBlocked();
      unsubGlobal();
    };
  }, [selectedDevice]);

  // Aggregate Stats
  const stats = useMemo(() => {
    const total = calls.length;
    const incoming = calls.filter(c => c.type.toLowerCase() === 'incoming').length;
    const outgoing = calls.filter(c => c.type.toLowerCase() === 'outgoing').length;
    const missed = calls.filter(c => c.type.toLowerCase() === 'missed').length;
    const blocked = blockedList.length;

    return { total, incoming, outgoing, missed, blocked };
  }, [calls, blockedList]);

  const handleDeleteCall = async (item: CallLogItem) => {
    if (window.confirm('Are you sure you want to delete this call log?')) {
      try {
        await StorageRepository.deleteCallLog(selectedDevice, item);
      } catch (err) {
        console.error(err);
      }
    }
  };

  const handleCopy = (num: string, id: string) => {
    navigator.clipboard.writeText(num);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 1500);
  };

  const handleAddBlock = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newBlockNumber.trim()) return;
    try {
      await LiveRepository.addBlockedNumber(selectedDevice, newBlockNumber.trim(), newBlockScope);
      setNewBlockNumber('');
      setShowBlockModal(false);
      alert('Number successfully blocked!');
    } catch (err) {
      console.error(err);
    }
  };

  const handleRemoveBlock = async (blockedId: string) => {
    if (window.confirm('Remove this number from blocklist?')) {
      try {
        await LiveRepository.removeBlockedNumber(selectedDevice, blockedId);
      } catch (err) {
        console.error(err);
      }
    }
  };

  const handleDirectBlock = async (number: string) => {
    if (window.confirm(`Are you sure you want to block calls for ${number}?`)) {
      try {
        await LiveRepository.addBlockedNumber(selectedDevice, number, 'Both');
        alert('Number successfully blocked!');
      } catch (err) {
        console.error(err);
      }
    }
  };

  const handleToggleGlobal = async (type: 'incoming' | 'outgoing', val: boolean) => {
    try {
      if (type === 'incoming') {
        await LiveRepository.toggleGlobalCallBlocking(selectedDevice, val, globalBlocking.blockAllOutgoing);
      } else {
        await LiveRepository.toggleGlobalCallBlocking(selectedDevice, globalBlocking.blockAllIncoming, val);
      }
    } catch (err) {
      console.error(err);
    }
  };

  // Remote active call controls (Answer, Reject, Block)
  const handleCallAction = async (action: 'ANSWER' | 'REJECT' | 'BLOCK') => {
    if (!ringingCall.number) return;
    try {
      await LiveRepository.sendCallAction(selectedDevice, action, ringingCall.number);
      alert(`Sent Remote Action: ${action} for ${ringingCall.number}`);
    } catch (err) {
      console.error(err);
    }
  };

  // Filter call list
  const filteredCalls = useMemo(() => {
    return calls.filter((c) => {
      const matchesSearch = 
        (c.number || '').toLowerCase().includes(searchQuery.toLowerCase()) || 
        (c.name || '').toLowerCase().includes(searchQuery.toLowerCase());
      
      if (filterType === 'All') return matchesSearch;
      if (filterType === 'Blocked') {
        // Find if calls are blocked logs or numbers matched inside blockedList
        const isNumBlocked = blockedList.some(b => b.number.replace(/\s+/g, '') === c.number.replace(/\s+/g, ''));
        return isNumBlocked && matchesSearch;
      }
      return c.type === filterType && matchesSearch;
    });
  }, [calls, blockedList, searchQuery, filterType]);

  const getInitials = (name: string) => {
    const parts = name.trim().split(/\s+/);
    if (parts.length === 0 || !parts[0]) return '?';
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
  };

  const getAvatarBg = (name: string) => {
    const colors = [
      'bg-blue-100 text-blue-600',
      'bg-emerald-100 text-emerald-600',
      'bg-indigo-100 text-indigo-600',
      'bg-pink-100 text-pink-600',
      'bg-purple-100 text-purple-600',
      'bg-amber-100 text-amber-600',
    ];
    let hash = 0;
    const cleanName = name || 'Unknown';
    for (let i = 0; i < cleanName.length; i++) {
      hash = cleanName.charCodeAt(i) + ((hash << 5) - hash);
    }
    return colors[Math.abs(hash % colors.length)];
  };

  return (
    <div className="space-y-6">
      
      {/* 1. Metric Cards Header Row */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        {/* All Calls */}
        <div className="glass-panel p-4 rounded-2xl border border-darkBorder bg-darkSurf flex items-center gap-3.5 shadow-sm">
          <div className="h-10 w-10 rounded-xl bg-brandCyan/10 text-brandCyan flex items-center justify-center shrink-0">
            <Phone className="h-5 w-5" />
          </div>
          <div>
            <p className="text-[10px] font-bold text-mutedText uppercase tracking-wider">All Calls</p>
            <h4 className="text-lg font-black text-slate-800 font-mono mt-0.5">{stats.total}</h4>
            <span className="text-[8px] font-bold text-mutedText">Total</span>
          </div>
        </div>

        {/* Incoming */}
        <div className="glass-panel p-4 rounded-2xl border border-darkBorder bg-darkSurf flex items-center gap-3.5 shadow-sm">
          <div className="h-10 w-10 rounded-xl bg-brandTeal/10 text-brandTeal flex items-center justify-center shrink-0">
            <PhoneIncoming className="h-5 w-5" />
          </div>
          <div>
            <p className="text-[10px] font-bold text-mutedText uppercase tracking-wider">Incoming</p>
            <h4 className="text-lg font-black text-slate-800 font-mono mt-0.5">{stats.incoming}</h4>
            <span className="text-[8px] font-bold text-brandTeal">Received</span>
          </div>
        </div>

        {/* Outgoing */}
        <div className="glass-panel p-4 rounded-2xl border border-darkBorder bg-darkSurf flex items-center gap-3.5 shadow-sm">
          <div className="h-10 w-10 rounded-xl bg-brandBlue/10 text-brandBlue flex items-center justify-center shrink-0">
            <PhoneOutgoing className="h-5 w-5" />
          </div>
          <div>
            <p className="text-[10px] font-bold text-mutedText uppercase tracking-wider">Outgoing</p>
            <h4 className="text-lg font-black text-slate-800 font-mono mt-0.5">{stats.outgoing}</h4>
            <span className="text-[8px] font-bold text-brandBlue">Made</span>
          </div>
        </div>

        {/* Missed */}
        <div className="glass-panel p-4 rounded-2xl border border-darkBorder bg-darkSurf flex items-center gap-3.5 shadow-sm">
          <div className="h-10 w-10 rounded-xl bg-brandRed/10 text-brandRed flex items-center justify-center shrink-0">
            <PhoneMissed className="h-5 w-5" />
          </div>
          <div>
            <p className="text-[10px] font-bold text-mutedText uppercase tracking-wider">Missed</p>
            <h4 className="text-lg font-black text-slate-800 font-mono mt-0.5">{stats.missed}</h4>
            <span className="text-[8px] font-bold text-brandRed">Missed</span>
          </div>
        </div>

        {/* Blocked */}
        <div className="glass-panel p-4 rounded-2xl border border-darkBorder bg-darkSurf flex items-center gap-3.5 shadow-sm col-span-2 md:col-span-1">
          <div className="h-10 w-10 rounded-xl bg-amber-100 text-amber-600 flex items-center justify-center shrink-0">
            <Shield className="h-5 w-5" />
          </div>
          <div>
            <p className="text-[10px] font-bold text-mutedText uppercase tracking-wider">Blocked</p>
            <h4 className="text-lg font-black text-slate-800 font-mono mt-0.5">{stats.blocked}</h4>
            <span className="text-[8px] font-bold text-amber-600">Blocked</span>
          </div>
        </div>
      </div>

      {/* 2. Split Dashboard Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Left Columns: Call Logs Table (Wider) */}
        <div className="lg:col-span-2 space-y-4">
          <div className="glass-panel p-5 rounded-2xl border border-darkBorder bg-darkSurf shadow-sm flex flex-col justify-between min-h-[550px]">
            <div>
              {/* Table search & filter pills bar */}
              <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4 mb-5">
                <div className="flex items-center gap-2 bg-darkBg border border-darkBorder rounded-xl px-3 py-2 w-full sm:w-[280px]">
                  <Search className="h-4 w-4 text-mutedText" />
                  <input
                    type="text"
                    placeholder="Search logs by name or number..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="flex-1 bg-transparent border-none text-xs font-semibold placeholder:text-mutedText/75 focus:outline-none focus:ring-0 text-slate-800"
                  />
                </div>

                <div className="flex items-center bg-darkBg border border-darkBorder p-1 rounded-xl w-fit">
                  {(['All', 'Incoming', 'Outgoing', 'Missed', 'Blocked'] as const).map((type) => (
                    <button
                      key={type}
                      onClick={() => setFilterType(type)}
                      className={`text-[9px] font-bold px-3 py-1.5 rounded-lg transition-all cursor-pointer ${
                        filterType === type 
                          ? 'bg-darkSurf text-brandCyan shadow-sm border border-darkBorder/40' 
                          : 'text-mutedText hover:text-slate-800'
                      }`}
                    >
                      {type}
                    </button>
                  ))}
                </div>
              </div>

              {/* Scrollable Call logs table */}
              <div className="overflow-y-auto max-h-[480px] pr-2">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="border-b border-darkBorder text-mutedText text-[9px] font-bold uppercase tracking-wider">
                      <th className="py-2.5 px-3">Contact</th>
                      <th className="py-2.5 px-3">Number</th>
                      <th className="py-2.5 px-3">Type</th>
                      <th className="py-2.5 px-3">Duration</th>
                      <th className="py-2.5 px-3">Date & Time</th>
                      <th className="py-2.5 px-3 text-center">Recording</th>
                      <th className="py-2.5 px-3 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredCalls.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="py-16 text-center italic text-xs text-mutedText">
                          No matching call records synced.
                        </td>
                      </tr>
                    ) : (
                      filteredCalls.map((item) => {
                        const isOut = item.type.toLowerCase() === 'outgoing';
                        const isInc = item.type.toLowerCase() === 'incoming';
                        const isMiss = item.type.toLowerCase() === 'missed';
                        
                        // Check if this number is blocked
                        const blockedItem = blockedList.find(b => b.number.replace(/\s+/g, '') === item.number.replace(/\s+/g, ''));
                        const isBlocked = !!blockedItem;

                        const durSec = parseInt(item.duration, 10);
                        const durStr = !isNaN(durSec) && durSec > 0
                          ? `${Math.floor(durSec / 60)}:${String(durSec % 60).padStart(2, '0')}`
                          : '00:00';

                        return (
                          <React.Fragment key={item.id}>
                            <tr className="border-b border-darkBorder/60 hover:bg-darkBg/20 text-xs transition-all">
                              {/* Avatar + Contact Name */}
                              <td className="py-3 px-3 font-bold text-slate-800">
                                <div className="flex items-center gap-2">
                                  <div className={`h-8 w-8 rounded-full flex items-center justify-center font-bold text-[10px] ${getAvatarBg(item.name)}`}>
                                    {getInitials(item.name || 'Unknown')}
                                  </div>
                                  <span className="truncate max-w-[120px]">{item.name || 'Unknown'}</span>
                                </div>
                              </td>

                              {/* Number */}
                              <td className="py-3 px-3 font-mono text-mutedText font-semibold">{item.number}</td>

                              {/* Type badge */}
                              <td className="py-3 px-3">
                                {isBlocked ? (
                                  <span className="inline-flex items-center gap-0.5 px-2 py-0.5 rounded font-bold text-[8px] bg-amber-100 text-amber-600 border border-amber-200 uppercase tracking-wide">
                                    <Shield className="h-2.5 w-2.5" />
                                    Blocked
                                  </span>
                                ) : (
                                  <span className={`inline-flex items-center gap-0.5 px-2 py-0.5 rounded font-bold text-[8px] uppercase tracking-wide ${
                                    isInc ? 'bg-brandTeal/15 text-brandTeal' :
                                    isOut ? 'bg-brandBlue/15 text-brandBlue' : 'bg-brandRed/15 text-brandRed'
                                  }`}>
                                    {isInc && <PhoneIncoming className="h-2.5 w-2.5" />}
                                    {isOut && <PhoneOutgoing className="h-2.5 w-2.5" />}
                                    {isMiss && <PhoneMissed className="h-2.5 w-2.5" />}
                                    {item.type}
                                  </span>
                                )}
                              </td>

                              {/* Duration */}
                              <td className="py-3 px-3 text-slate-700 font-semibold">{isMiss ? '00:00' : durStr}</td>

                              {/* Date Time */}
                              <td className="py-3 px-3 text-mutedText font-semibold">{item.date}</td>

                              {/* Recording Button */}
                              <td className="py-3 px-3 text-center">
                                {item.hasRecording && item.audioUrl ? (
                                  <button
                                    onClick={() => setPlayingAudioId(playingAudioId === item.id ? null : item.id)}
                                    className={`h-7 w-7 rounded-full flex items-center justify-center border transition-all ${
                                      playingAudioId === item.id
                                        ? 'bg-brandTeal border-brandTeal text-white'
                                        : 'bg-brandTeal/10 border-brandTeal/20 text-brandTeal hover:bg-brandTeal/20'
                                    }`}
                                  >
                                    <Play className="h-3 w-3 fill-current" />
                                  </button>
                                ) : (
                                  <span className="text-mutedText/40">—</span>
                                )}
                              </td>

                              {/* Actions */}
                              <td className="py-3 px-3 text-right">
                                <div className="flex items-center justify-end gap-1.5">
                                  {/* Copy */}
                                  <button
                                    onClick={() => handleCopy(item.number, item.id)}
                                    className="p-1.5 hover:bg-darkBg border border-transparent hover:border-darkBorder rounded-lg text-mutedText hover:text-slate-800 transition-all"
                                    title="Copy number"
                                  >
                                    {copiedId === item.id ? (
                                      <Check className="h-3.5 w-3.5 text-brandTeal" />
                                    ) : (
                                      <Copy className="h-3.5 w-3.5" />
                                    )}
                                  </button>

                                  {/* Block / Unblock direct action */}
                                  {isBlocked ? (
                                    <button
                                      onClick={() => handleRemoveBlock(blockedItem.id)}
                                      className="p-1.5 hover:bg-brandCyan/10 border border-transparent hover:border-brandCyan/20 rounded-lg text-brandCyan transition-all"
                                      title="Blocked - Click to Unlock"
                                    >
                                      <Lock className="h-3.5 w-3.5" />
                                    </button>
                                  ) : (
                                    <button
                                      onClick={() => handleDirectBlock(item.number)}
                                      className="p-1.5 hover:bg-rose-50 border border-transparent hover:border-rose-100 rounded-lg text-mutedText hover:text-rose-600 transition-all"
                                      title="Block Number"
                                    >
                                      <Ban className="h-3.5 w-3.5" />
                                    </button>
                                  )}

                                  {/* Delete Log */}
                                  <button
                                    onClick={() => handleDeleteCall(item)}
                                    className="p-1.5 hover:bg-brandRed/10 border border-transparent hover:border-brandRed/20 rounded-lg text-mutedText hover:text-brandRed transition-all"
                                    title="Delete log"
                                  >
                                    <Trash2 className="h-3.5 w-3.5" />
                                  </button>
                                </div>
                              </td>
                            </tr>

                            {/* Sub-row voice player */}
                            {playingAudioId === item.id && item.hasRecording && item.audioUrl && (
                              <tr className="bg-brandTeal/2 border-b border-darkBorder">
                                <td colSpan={7} className="py-3 px-4">
                                  <div className="flex items-center gap-3 bg-darkSurf border border-darkBorder p-2.5 rounded-xl">
                                    <Volume2 className="h-4 w-4 text-brandTeal animate-pulse shrink-0" />
                                    <audio controls autoPlay src={item.audioUrl} className="h-7 flex-1 rounded-lg focus:outline-none" />
                                  </div>
                                </td>
                              </tr>
                            )}
                          </React.Fragment>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Total readout footer */}
            <div className="border-t border-darkBorder pt-4 text-[10px] text-mutedText font-bold">
              Showing {filteredCalls.length} logs
            </div>
          </div>
        </div>

        {/* Right Columns: Telemetry Controls & Blocker */}
        <div className="space-y-6">
          
          {/* A. CALL CONTROLS */}
          <div className="glass-panel p-5 rounded-2xl border border-darkBorder bg-darkSurf shadow-sm">
            <h3 className="text-xs font-black text-slate-800 uppercase tracking-widest mb-3.5">Call Controls</h3>
            
            {ringingCall.isRinging ? (
              <div className="space-y-4">
                {/* Active Call detected state */}
                <div className="p-3 bg-brandRed/5 border border-brandRed/15 rounded-xl flex items-center justify-between">
                  <div>
                    <span className="text-[8px] font-black text-brandRed uppercase tracking-widest animate-pulse block">Incoming call</span>
                    <span className="text-xs font-bold text-slate-800 font-mono mt-0.5 block">{ringingCall.number}</span>
                  </div>
                  <span className="h-2 w-2 rounded-full bg-brandRed animate-ping" />
                </div>

                {/* Control Action Buttons */}
                <div className="grid grid-cols-3 gap-2">
                  {/* Answer */}
                  <button
                    onClick={() => handleCallAction('ANSWER')}
                    className="flex flex-col items-center justify-center p-3 rounded-xl bg-brandTeal/10 hover:bg-brandTeal/20 border border-brandTeal/25 text-brandTeal font-bold text-[9px] gap-1 transition-all cursor-pointer"
                  >
                    <PhoneIncoming className="h-4.5 w-4.5" />
                    <span>Answer Call</span>
                  </button>

                  {/* Reject */}
                  <button
                    onClick={() => handleCallAction('REJECT')}
                    className="flex flex-col items-center justify-center p-3 rounded-xl bg-brandRed/10 hover:bg-brandRed/20 border border-brandRed/25 text-brandRed font-bold text-[9px] gap-1 transition-all cursor-pointer"
                  >
                    <PhoneMissed className="h-4.5 w-4.5" />
                    <span>Reject Call</span>
                  </button>

                  {/* Block */}
                  <button
                    onClick={() => handleCallAction('BLOCK')}
                    className="flex flex-col items-center justify-center p-3 rounded-xl bg-amber-100 hover:bg-amber-200 border border-amber-300 text-amber-700 font-bold text-[9px] gap-1 transition-all cursor-pointer"
                  >
                    <Shield className="h-4.5 w-4.5" />
                    <span>Block Caller</span>
                  </button>
                </div>
              </div>
            ) : (
              <div className="py-6 flex flex-col items-center justify-center text-center text-mutedText bg-darkBg/30 border border-darkBorder/60 rounded-xl">
                <Phone className="h-8 w-8 text-mutedText/30 mb-2" />
                <p className="text-xs font-bold">No Active Call Detected</p>
                <p className="text-[10px] text-mutedText/75 mt-0.5 max-w-[200px]">Waiting for child device ring overlay telemetry...</p>
              </div>
            )}
          </div>

          {/* B. BLOCKED NUMBERS */}
          <div className="glass-panel p-5 rounded-2xl border border-darkBorder bg-darkSurf shadow-sm">
            <div className="flex justify-between items-center mb-3">
              <h3 className="text-xs font-black text-slate-800 uppercase tracking-widest">
                Blocked Numbers ({blockedList.length})
              </h3>
              <button 
                onClick={() => setShowBlockModal(true)}
                className="text-[10px] font-black text-brandCyan hover:underline"
              >
                Add Block
              </button>
            </div>

            <div className="space-y-2 max-h-[220px] overflow-y-auto pr-1">
              {blockedList.length === 0 ? (
                <p className="text-[10px] italic text-mutedText py-8 text-center bg-darkBg/20 border border-darkBorder/40 rounded-xl">
                  No blocked numbers configured.
                </p>
              ) : (
                blockedList.map((blocked) => (
                  <div 
                    key={blocked.id}
                    className="flex justify-between items-center p-2.5 bg-darkBg/35 border border-darkBorder/60 rounded-xl text-xs"
                  >
                    <div>
                      <p className="font-black text-slate-800 font-mono text-[11px]">{blocked.number}</p>
                      <p className="text-[8px] text-mutedText mt-0.5 font-bold uppercase tracking-wider">
                        Scope: {blocked.type}
                      </p>
                    </div>
                    <button
                      onClick={() => handleRemoveBlock(blocked.id)}
                      className="p-1 hover:bg-brandRed/10 rounded text-brandRed transition-all cursor-pointer"
                      title="Unblock number"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* C. CALL BLOCKING SETTINGS */}
          <div className="glass-panel p-5 rounded-2xl border border-darkBorder bg-darkSurf shadow-sm space-y-4">
            <h3 className="text-xs font-black text-slate-800 uppercase tracking-widest">Call Blocking Settings</h3>

            {/* Block Incoming toggle */}
            <div className="flex items-center justify-between py-1">
              <div>
                <p className="text-[11px] font-bold text-slate-800">Block Incoming Calls</p>
                <p className="text-[9px] text-mutedText mt-0.5">Block all incoming ring requests</p>
              </div>
              <button
                onClick={() => handleToggleGlobal('incoming', !globalBlocking.blockAllIncoming)}
                className={`w-11 h-6 rounded-full p-0.5 transition-all flex border ${
                  globalBlocking.blockAllIncoming 
                    ? 'bg-brandCyan border-brandCyan justify-end' 
                    : 'bg-darkBg border-darkBorder justify-start'
                }`}
              >
                <span className="h-4.5 w-4.5 rounded-full bg-white shadow-md" />
              </button>
            </div>

            {/* Block Outgoing toggle */}
            <div className="flex items-center justify-between py-1">
              <div>
                <p className="text-[11px] font-bold text-slate-800">Block Outgoing Calls</p>
                <p className="text-[9px] text-mutedText mt-0.5">Stops child from dialing out</p>
              </div>
              <button
                onClick={() => handleToggleGlobal('outgoing', !globalBlocking.blockAllOutgoing)}
                className={`w-11 h-6 rounded-full p-0.5 transition-all flex border ${
                  globalBlocking.blockAllOutgoing 
                    ? 'bg-brandCyan border-brandCyan justify-end' 
                    : 'bg-darkBg border-darkBorder justify-start'
                }`}
              >
                <span className="h-4.5 w-4.5 rounded-full bg-white shadow-md" />
              </button>
            </div>

            <button
              onClick={() => setShowBlockModal(true)}
              className="w-full mt-2 bg-transparent hover:bg-brandCyan/10 border border-brandCyan/40 text-brandCyan hover:text-brandCyan font-black text-[10px] py-2 rounded-xl flex items-center justify-center gap-1.5 transition-all cursor-pointer uppercase tracking-wider"
            >
              <Plus className="h-3.5 w-3.5" />
              Manage Blocked Numbers
            </button>
          </div>

        </div>

      </div>

      {/* Block Number Modal Overlay */}
      {showBlockModal && (
        <div className="fixed inset-0 bg-darkBg/60 backdrop-blur-sm flex items-center justify-center z-[1000] p-4 animate-fade-in">
          <div className="glass-panel p-6 rounded-2xl w-full max-w-[400px] shadow-2xl relative bg-darkSurf border border-darkBorder">
            <button 
              onClick={() => setShowBlockModal(false)}
              className="absolute top-4 right-4 p-1.5 text-mutedText hover:text-slate-800 rounded-lg transition-all cursor-pointer"
            >
              <X className="h-4 w-4" />
            </button>
            <h3 className="text-sm font-bold text-brandCyan mb-4 flex items-center gap-2">
              <ShieldAlert className="h-5 w-5" />
              Configure Call Block Rule
            </h3>
            <form onSubmit={handleAddBlock} className="space-y-4">
              <div>
                <label className="block text-[10px] uppercase font-bold text-mutedText mb-1.5">Phone Number</label>
                <input
                  type="text"
                  placeholder="e.g. +919876543210"
                  required
                  value={newBlockNumber}
                  onChange={(e) => setNewBlockNumber(e.target.value)}
                  className="w-full bg-darkBg border border-darkBorder rounded-xl text-xs px-3 py-2.5 text-slate-800 focus:outline-none focus:border-brandCyan focus:ring-0"
                />
              </div>
              <div>
                <label className="block text-[10px] uppercase font-bold text-mutedText mb-1.5">Block Scope</label>
                <select
                  value={newBlockScope}
                  onChange={(e) => setNewBlockScope(e.target.value as any)}
                  className="w-full bg-darkBg border border-darkBorder rounded-xl text-xs px-3 py-2.5 text-slate-800 focus:outline-none focus:border-brandCyan cursor-pointer focus:ring-0"
                >
                  <option value="Incoming">Incoming Calls Only</option>
                  <option value="Outgoing">Outgoing Calls Only</option>
                  <option value="Both">Incoming & Outgoing Calls</option>
                </select>
              </div>
              <button
                type="submit"
                className="w-full bg-brandCyan text-white hover:bg-brandCyan/85 font-bold text-xs py-2.5 rounded-xl transition-all cursor-pointer"
              >
                Block Number
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

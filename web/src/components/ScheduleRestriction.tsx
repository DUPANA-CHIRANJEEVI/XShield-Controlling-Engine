import React, { useEffect, useState } from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { WebRtcRepository } from '../firebase/webrtcRepository';
import type { ScheduleRestrictionItem } from '../firebase/webrtcRepository';
import { Clock, Plus, Trash2, ShieldAlert, CalendarRange, ToggleLeft, ToggleRight, X } from 'lucide-react';

export const ScheduleRestriction: React.FC = () => {
  const { selectedDevice } = useDeviceStore();
  const [schedules, setSchedules] = useState<ScheduleRestrictionItem[]>([]);
  const [showAddForm, setShowAddForm] = useState(false);

  // Form states
  const [name, setName] = useState('');
  const [start, setStart] = useState('21:00');
  const [end, setEnd] = useState('07:00');
  const [selectedDays, setSelectedDays] = useState<string[]>(['MON', 'TUE', 'WED', 'THU', 'FRI']);
  const [blockAll, setBlockAll] = useState(true);

  const availableDays = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

  useEffect(() => {
    if (!selectedDevice) return;

    const unsub = WebRtcRepository.subscribeDeviceConfig(selectedDevice, (config) => {
      setSchedules(config.schedules);
    });

    return () => unsub();
  }, [selectedDevice]);

  const handleToggleDay = (day: string) => {
    setSelectedDays(prev => 
      prev.includes(day) ? prev.filter(d => d !== day) : [...prev, day]
    );
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    const newSchedule: ScheduleRestrictionItem = {
      id: crypto.randomUUID(),
      name: name.trim(),
      startTime: start,
      endTime: end,
      days: selectedDays,
      enabled: true,
      blockAll,
      blockedApps: []
    };

    try {
      const updated = [...schedules, newSchedule];
      await WebRtcRepository.updateSchedules(selectedDevice, updated);
      setName('');
      setShowAddForm(false);
      alert('Schedule restriction successfully added!');
    } catch (err) {
      console.error(err);
    }
  };

  const handleDelete = async (id: string) => {
    if (window.confirm('Delete this schedule restriction rule?')) {
      try {
        const updated = schedules.filter(s => s.id !== id);
        await WebRtcRepository.updateSchedules(selectedDevice, updated);
      } catch (err) {
        console.error(err);
      }
    }
  };

  const handleToggleEnabled = async (id: string, currentVal: boolean) => {
    try {
      const updated = schedules.map(s => 
        s.id === id ? { ...s, enabled: !currentVal } : s
      );
      await WebRtcRepository.updateSchedules(selectedDevice, updated);
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-sm font-black text-slate-800 uppercase tracking-wide flex items-center gap-2">
            <Clock className="h-5 w-5 text-brandCyan" />
            Screen Time Restrictions
          </h2>
          <p className="text-[10px] text-mutedText font-semibold mt-0.5">Configure timed schedules to restrict child app access or lock screen usage</p>
        </div>
        <button
          onClick={() => setShowAddForm(true)}
          className="bg-brandCyan text-white hover:bg-brandCyan/90 font-black text-xs px-4 py-2.5 rounded-xl flex items-center gap-1.5 transition-all shadow-md hover:shadow-lg"
        >
          <Plus className="h-4 w-4" />
          Add Schedule Limit
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Active Schedules List */}
        <div className="lg:col-span-2 space-y-4">
          {schedules.length === 0 ? (
            <div className="glass-panel p-12 rounded-2xl text-center italic text-xs text-mutedText border border-darkBorder font-semibold">
              No active schedule restrictions are configured.
            </div>
          ) : (
            schedules.map((sched) => (
              <div 
                key={sched.id} 
                className={`glass-panel p-6 rounded-2xl border border-slate-200/60 shadow-sm transition-all relative ${
                  sched.enabled 
                    ? 'border-l-4 border-l-brandTeal bg-white' 
                    : 'border-l-4 border-l-slate-300 bg-white opacity-70'
                }`}
              >
                <div className="flex justify-between items-start mb-4">
                  <div>
                    <h3 className="text-sm font-black text-slate-800">{sched.name}</h3>
                    <p className="text-[10px] text-mutedText font-semibold mt-1 flex items-center gap-1.5">
                      <CalendarRange className="h-3.5 w-3.5 text-mutedText/80" />
                      {sched.days.join(', ')}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => handleToggleEnabled(sched.id, sched.enabled)}
                      className="p-1 hover:bg-slate-100 rounded-xl transition-all"
                      title={sched.enabled ? 'Disable rule' : 'Enable rule'}
                    >
                      {sched.enabled ? (
                        <ToggleRight className="h-7 w-7 text-brandTeal" />
                      ) : (
                        <ToggleLeft className="h-7 w-7 text-mutedText" />
                      )}
                    </button>
                    <button
                      onClick={() => handleDelete(sched.id)}
                      className="p-2 text-brandRed hover:bg-brandRed/10 rounded-xl transition-all"
                      title="Delete rule"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4 bg-slate-50/80 p-4 rounded-xl border border-slate-100">
                  <div>
                    <p className="text-[9px] uppercase font-black text-mutedText tracking-wider">Time Window</p>
                    <p className="text-xs font-black text-slate-700 font-mono mt-1">
                      {sched.startTime} — {sched.endTime}
                    </p>
                  </div>
                  <div>
                    <p className="text-[9px] uppercase font-black text-mutedText tracking-wider">Scope Action</p>
                    <p className="text-xs font-black mt-1 flex items-center gap-1.5 text-brandRed">
                      <ShieldAlert className="h-3.5 w-3.5" />
                      {sched.blockAll ? 'Block All Apps / Device Lock' : 'Specific Packages Only'}
                    </p>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>

        {/* Info Card pane */}
        <div className="glass-panel p-6 rounded-2xl shadow-sm border border-slate-200/60 bg-white h-fit space-y-4">
          <h3 className="text-xs font-black text-slate-800 uppercase tracking-wider border-b border-darkBorder pb-2.5">Scheduler Details</h3>
          <p className="text-xs text-slate-600 leading-relaxed font-semibold">
            The Xshield Child agent monitors active scheduled restrictions locally. When a rule is enabled and the system clock enters the target time window, the agent intercepts app launches.
          </p>
          <div className="p-3 bg-brandCyan/10 border border-brandCyan/20 text-brandCyan rounded-xl text-[10px] font-bold">
            Tip: configure overnight rules (e.g. 21:00 to 07:00) to secure healthy sleep schedules.
          </div>
        </div>
      </div>

      {/* Add Schedule Modal Overlay */}
      {showAddForm && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-md flex items-center justify-center z-[1000] p-4">
          <div className="glass-panel p-6 rounded-2xl w-full max-w-[450px] shadow-2xl relative border border-slate-200 bg-white">
            <button 
              onClick={() => setShowAddForm(false)}
              className="absolute top-4 right-4 p-1.5 text-mutedText hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all"
              title="Close"
            >
              <X className="h-4 w-4" />
            </button>
            <h3 className="text-sm font-black text-slate-800 uppercase tracking-wide mb-4 flex items-center gap-2">
              <Clock className="h-5 w-5 text-brandCyan" />
              Configure Schedule Restriction
            </h3>
            <form onSubmit={handleSave} className="space-y-4">
              <div>
                <label className="block text-[9px] uppercase font-black text-mutedText tracking-wider mb-1.5">Schedule Name</label>
                <input
                  type="text"
                  placeholder="e.g. Study hour, Bedtime"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl text-xs px-3.5 py-2.5 text-slate-800 focus:outline-none focus:border-brandCyan focus:bg-white transition-all font-semibold"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-[9px] uppercase font-black text-mutedText tracking-wider mb-1.5">Start Time</label>
                  <input
                    type="time"
                    required
                    value={start}
                    onChange={(e) => setStart(e.target.value)}
                    className="w-full bg-slate-50 border border-slate-200 rounded-xl text-xs px-3.5 py-2.5 text-slate-800 focus:outline-none focus:border-brandCyan focus:bg-white transition-all font-semibold"
                  />
                </div>
                <div>
                  <label className="block text-[9px] uppercase font-black text-mutedText tracking-wider mb-1.5">End Time</label>
                  <input
                    type="time"
                    required
                    value={end}
                    onChange={(e) => setEnd(e.target.value)}
                    className="w-full bg-slate-50 border border-slate-200 rounded-xl text-xs px-3.5 py-2.5 text-slate-800 focus:outline-none focus:border-brandCyan focus:bg-white transition-all font-semibold"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[9px] uppercase font-black text-mutedText tracking-wider mb-2">Repeat Days</label>
                <div className="flex flex-wrap gap-1.5">
                  {availableDays.map((day) => {
                    const isSelected = selectedDays.includes(day);
                    return (
                      <button
                        type="button"
                        key={day}
                        onClick={() => handleToggleDay(day)}
                        className={`text-[9px] font-bold px-2.5 py-1.5 rounded-lg border transition-all ${
                          isSelected 
                            ? 'bg-brandCyan border-brandCyan text-white shadow-sm' 
                            : 'bg-slate-50 border-slate-200 text-slate-600 hover:border-slate-300 hover:bg-slate-100'
                        }`}
                      >
                        {day}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="flex items-center gap-3 p-3.5 bg-slate-50 border border-slate-200 rounded-xl">
                <input
                  type="checkbox"
                  id="blockAllCheck"
                  checked={blockAll}
                  onChange={(e) => setBlockAll(e.target.checked)}
                  className="h-4 w-4 accent-brandCyan rounded focus:ring-0 cursor-pointer"
                />
                <label htmlFor="blockAllCheck" className="text-xs text-slate-700 cursor-pointer font-bold select-none">
                  Lock whole device / Block all apps during window
                </label>
              </div>

              <button
                type="submit"
                className="w-full bg-brandCyan text-white hover:bg-brandCyan/95 font-black text-xs py-3 rounded-xl transition-all shadow-md hover:shadow-lg"
              >
                Save Schedule Restriction
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

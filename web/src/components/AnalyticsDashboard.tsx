import React, { useEffect, useState, useMemo, useRef } from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { StorageRepository } from '../firebase/storageRepository';
import type { CallLogItem, SmsLogItem } from '../firebase/storageRepository';
import { Phone, MessageSquare, Activity, Users, PhoneMissed } from 'lucide-react';

export const AnalyticsDashboard: React.FC = () => {
  const { selectedDevice } = useDeviceStore();
  const [calls, setCalls] = useState<CallLogItem[]>([]);
  const [sms, setSms] = useState<SmsLogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeChartTab, setActiveChartTab] = useState<'hourly' | 'weekly'>('weekly');
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    if (!selectedDevice) return;
    setLoading(true);

    const unsubCalls = StorageRepository.subscribeCallLogs(selectedDevice, (callData) => {
      setCalls(callData);
    });

    const unsubSms = StorageRepository.subscribeSmsLogs(selectedDevice, (smsData) => {
      setSms(smsData);
      setLoading(false);
    });

    return () => {
      unsubCalls();
      unsubSms();
    };
  }, [selectedDevice]);

  // Real-time Cyber Radar / Pulse animation
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

    let angle = 0;
    const dots: { x: number; y: number; opacity: number; size: number }[] = [];

    // Create persistent high-tech grid nodes
    const gridNodes: { x: number; y: number }[] = [];
    const rows = 5;
    const cols = 8;
    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        gridNodes.push({
          x: (width / (cols - 1)) * c,
          y: (height / (rows - 1)) * r
        });
      }
    }

    const render = () => {
      ctx.clearRect(0, 0, width, height);

      // 1. Tech Grid Background
      ctx.strokeStyle = 'rgba(0, 136, 255, 0.04)';
      ctx.lineWidth = 1;
      gridNodes.forEach((node) => {
        ctx.beginPath();
        ctx.arc(node.x, node.y, 1.5, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(100, 116, 139, 0.2)';
        ctx.fill();
      });

      // 2. Neon Sweep Wave
      const sweepY = (Math.sin(angle) + 1) * 0.5 * height;
      angle += 0.015;

      const gradient = ctx.createLinearGradient(0, sweepY - 40, 0, sweepY + 4);
      gradient.addColorStop(0, 'rgba(0, 136, 255, 0)');
      gradient.addColorStop(0.8, 'rgba(0, 136, 255, 0.05)');
      gradient.addColorStop(1, 'rgba(0, 136, 255, 0.2)');

      ctx.fillStyle = gradient;
      ctx.fillRect(0, Math.max(0, sweepY - 40), width, Math.min(height, 40));

      // Scanline
      ctx.strokeStyle = 'rgba(0, 136, 255, 0.5)';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(0, sweepY);
      ctx.lineTo(width, sweepY);
      ctx.stroke();

      // Spark particles matching call/sms pulses
      if (Math.random() < 0.1) {
        dots.push({
          x: Math.random() * width,
          y: sweepY,
          opacity: 1,
          size: 1 + Math.random() * 2.5
        });
      }

      dots.forEach((dot, index) => {
        dot.opacity -= 0.012;
        if (dot.opacity <= 0) {
          dots.splice(index, 1);
          return;
        }
        ctx.beginPath();
        ctx.arc(dot.x, dot.y, dot.size, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(0, 191, 165, ${dot.opacity})`;
        ctx.shadowColor = 'rgba(0, 191, 165, 0.6)';
        ctx.shadowBlur = 4;
        ctx.fill();
        ctx.shadowBlur = 0; // reset
      });

      animationFrameId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  // Aggregate stats
  const stats = useMemo(() => {
    // 1. Call Logs Breakdown
    const totalCalls = calls.length;
    const incomingCalls = calls.filter(c => c.type.toLowerCase() === 'incoming').length;
    const outgoingCalls = calls.filter(c => c.type.toLowerCase() === 'outgoing').length;
    const missedCalls = calls.filter(c => c.type.toLowerCase() === 'missed').length;

    let totalDurationSec = 0;
    calls.forEach(c => {
      const dur = parseInt(c.duration, 10);
      if (!isNaN(dur)) totalDurationSec += dur;
    });
    const avgDurationSec = totalCalls > 0 ? Math.round(totalDurationSec / totalCalls) : 0;
    const avgDurationStr = `${Math.floor(avgDurationSec / 60)}m ${avgDurationSec % 60}s`;

    // 2. SMS Logs Breakdown
    const totalSms = sms.length;
    const incomingSms = sms.filter(s => s.type.toLowerCase() === 'incoming').length;
    const outgoingSms = sms.filter(s => s.type.toLowerCase() === 'outgoing').length;

    // 3. Top Contacts (Interaction Aggregator)
    const contactsMap: Record<string, { name: string; number: string; calls: number; sms: number; total: number }> = {};

    calls.forEach(c => {
      const key = c.number || 'Unknown';
      if (!contactsMap[key]) {
        contactsMap[key] = { name: c.name || 'Unknown', number: key, calls: 0, sms: 0, total: 0 };
      }
      contactsMap[key].calls++;
      contactsMap[key].total++;
    });

    sms.forEach(s => {
      const key = s.number || 'Unknown';
      if (!contactsMap[key]) {
        contactsMap[key] = { name: s.name || 'Unknown', number: key, calls: 0, sms: 0, total: 0 };
      }
      contactsMap[key].sms++;
      contactsMap[key].total++;
    });

    const topContacts = Object.values(contactsMap)
      .sort((a, b) => b.total - a.total)
      .slice(0, 5);

    return {
      totalCalls,
      incomingCalls,
      outgoingCalls,
      missedCalls,
      avgDurationStr,
      totalSms,
      incomingSms,
      outgoingSms,
      topContacts
    };
  }, [calls, sms]);

  // Hourly and Weekly Chart Datasets
  const chartData = useMemo(() => {
    // 1. Hourly aggregation (0-23 hours)
    const hourlyData = Array.from({ length: 24 }, (_, hour) => ({
      label: `${hour}:00`,
      calls: 0,
      sms: 0,
      total: 0
    }));

    // 2. Weekly aggregation (Sunday - Saturday)
    const weekdays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    const weeklyData = weekdays.map(day => ({
      label: day,
      calls: 0,
      sms: 0,
      total: 0
    }));

    const parseLogDate = (dateStr: string) => {
      try {
        const parsed = new Date(dateStr);
        if (!isNaN(parsed.getTime())) return parsed;
      } catch {}
      return null;
    };

    calls.forEach((c) => {
      const date = parseLogDate(c.date);
      if (date) {
        hourlyData[date.getHours()].calls++;
        hourlyData[date.getHours()].total++;
        weeklyData[date.getDay()].calls++;
        weeklyData[date.getDay()].total++;
      }
    });

    sms.forEach((s) => {
      const date = parseLogDate(s.date);
      if (date) {
        hourlyData[date.getHours()].sms++;
        hourlyData[date.getHours()].total++;
        weeklyData[date.getDay()].sms++;
        weeklyData[date.getDay()].total++;
      }
    });

    return { hourlyData, weeklyData };
  }, [calls, sms]);

  // Get active data array for chart
  const activeChartDataset = useMemo(() => {
    return activeChartTab === 'hourly' ? chartData.hourlyData : chartData.weeklyData;
  }, [chartData, activeChartTab]);

  // Find max value in dataset to scale heights in SVG
  const maxChartVal = useMemo(() => {
    const vals = activeChartDataset.map(d => d.total);
    const max = Math.max(...vals, 1);
    return max;
  }, [activeChartDataset]);

  return (
    <div className="space-y-6">
      
      {/* 1. Live Animated Scanning Panel */}
      <div className="glass-panel rounded-2xl border border-darkBorder shadow-sm relative h-[140px] overflow-hidden flex flex-col justify-between p-6">
        <canvas ref={canvasRef} className="absolute inset-0 w-full h-full pointer-events-none" />
        <div className="flex items-center justify-between z-5">
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 bg-brandCyan/10 text-brandCyan rounded-lg flex items-center justify-center border border-brandCyan/20">
              <Activity className="h-5 w-5 animate-pulse" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-slate-800 tracking-wide uppercase">Real-Time Interaction Telemetry</h3>
              <p className="text-[10px] text-mutedText font-semibold mt-0.5">
                Analyzing call frequencies & text communication distributions on {selectedDevice}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-brandTeal animate-ping" />
            <span className="text-[10px] font-black text-brandTeal uppercase tracking-widest">Feed Active</span>
          </div>
        </div>

        {/* Small live readout counts */}
        <div className="flex items-center gap-8 z-5 text-slate-700">
          <div>
            <span className="text-[9px] font-semibold text-mutedText uppercase tracking-wider block">Sync Frequency</span>
            <span className="text-xs font-black font-mono">1.2Hz / dynamic</span>
          </div>
          <div className="h-6 w-[1px] bg-darkBorder" />
          <div>
            <span className="text-[9px] font-semibold text-mutedText uppercase tracking-wider block">Avg Duration</span>
            <span className="text-xs font-black font-mono text-brandBlue">{stats.avgDurationStr}</span>
          </div>
          <div className="h-6 w-[1px] bg-darkBorder" />
          <div>
            <span className="text-[9px] font-semibold text-mutedText uppercase tracking-wider block">Logs Aggregated</span>
            <span className="text-xs font-black font-mono text-brandTeal">{stats.totalCalls + stats.totalSms} records</span>
          </div>
        </div>
      </div>

      {/* 2. Primary Numerical Stats Grid */}
      {loading ? (
        <div className="py-20 flex flex-col items-center justify-center text-mutedText">
          <Activity className="h-10 w-10 animate-spin text-brandCyan mb-2" />
          <p className="text-xs font-bold">Compiling telemetry charts...</p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-4 gap-5">
            {/* Total Calls */}
            <div className="glass-panel p-5 rounded-2xl border border-darkBorder flex items-center justify-between shadow-sm">
              <div>
                <p className="text-[10px] font-bold text-mutedText uppercase tracking-wider">Total Calls Recorded</p>
                <h4 className="text-2xl font-black text-slate-800 mt-1 font-mono">{stats.totalCalls}</h4>
                <span className="text-[9px] font-semibold text-brandTeal flex items-center gap-0.5 mt-1.5">
                  Incoming ({stats.incomingCalls}) • Outgoing ({stats.outgoingCalls})
                </span>
              </div>
              <div className="h-11 w-11 bg-brandCyan/10 border border-brandCyan/20 text-brandCyan rounded-xl flex items-center justify-center shrink-0">
                <Phone className="h-5 w-5" />
              </div>
            </div>

            {/* Total SMS */}
            <div className="glass-panel p-5 rounded-2xl border border-darkBorder flex items-center justify-between shadow-sm">
              <div>
                <p className="text-[10px] font-bold text-mutedText uppercase tracking-wider">Total SMS Logs</p>
                <h4 className="text-2xl font-black text-slate-800 mt-1 font-mono">{stats.totalSms}</h4>
                <span className="text-[9px] font-semibold text-brandTeal flex items-center gap-0.5 mt-1.5">
                  Inbox ({stats.incomingSms}) • Sent ({stats.outgoingSms})
                </span>
              </div>
              <div className="h-11 w-11 bg-brandBlue/10 border border-brandBlue/20 text-brandBlue rounded-xl flex items-center justify-center shrink-0">
                <MessageSquare className="h-5 w-5" />
              </div>
            </div>

            {/* Missed Calls Rate */}
            <div className="glass-panel p-5 rounded-2xl border border-darkBorder flex items-center justify-between shadow-sm">
              <div>
                <p className="text-[10px] font-bold text-mutedText uppercase tracking-wider">Missed Calls</p>
                <h4 className="text-2xl font-black text-brandRed mt-1 font-mono">{stats.missedCalls}</h4>
                <span className="text-[9px] font-semibold text-mutedText mt-1.5 block">
                  {stats.totalCalls > 0 ? Math.round((stats.missedCalls / stats.totalCalls) * 100) : 0}% of all phone calls
                </span>
              </div>
              <div className="h-11 w-11 bg-brandRed/10 border border-brandRed/20 text-brandRed rounded-xl flex items-center justify-center shrink-0">
                <PhoneMissed className="h-5 w-5 animate-pulse" />
              </div>
            </div>

            {/* SMS Call Ratio Dial */}
            <div className="glass-panel p-5 rounded-2xl border border-darkBorder flex items-center justify-between shadow-sm">
              <div>
                <p className="text-[10px] font-bold text-mutedText uppercase tracking-wider">Interaction Ratio</p>
                <h4 className="text-sm font-black text-slate-800 mt-2 font-mono">
                  {stats.totalCalls + stats.totalSms > 0 
                    ? `${Math.round((stats.totalSms / (stats.totalCalls + stats.totalSms)) * 100)}% SMS`
                    : '0% SMS'
                  }
                </h4>
                <div className="w-full bg-darkBg h-1.5 rounded-full overflow-hidden mt-2 border border-darkBorder">
                  <div 
                    className="bg-brandCyan h-full rounded-full" 
                    style={{ 
                      width: `${stats.totalCalls + stats.totalSms > 0 
                        ? (stats.totalSms / (stats.totalCalls + stats.totalSms)) * 100 
                        : 0}%` 
                    }} 
                  />
                </div>
              </div>
              <div className="h-11 w-11 bg-brandTeal/10 border border-brandTeal/20 text-brandTeal rounded-xl flex items-center justify-center shrink-0">
                <Activity className="h-5 w-5" />
              </div>
            </div>
          </div>

          {/* 3. Visualization Charts & Top Contacts Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            
            {/* Custom Responsive SVG Chart */}
            <div className="glass-panel p-6 rounded-2xl border border-darkBorder shadow-sm lg:col-span-2 flex flex-col justify-between">
              
              {/* Chart Header Tabs */}
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h3 className="text-sm font-bold text-slate-800">Communication Activity Volume</h3>
                  <p className="text-[10px] text-mutedText font-semibold mt-0.5">Sum of calls & texts grouped by chronological distribution</p>
                </div>
                <div className="flex items-center bg-darkBg border border-darkBorder p-1 rounded-xl">
                  <button
                    onClick={() => setActiveChartTab('weekly')}
                    className={`px-3 py-1.5 rounded-lg text-[10px] font-bold transition-all ${
                      activeChartTab === 'weekly' 
                        ? 'bg-darkSurf text-brandCyan shadow-sm border border-darkBorder/40' 
                        : 'text-mutedText hover:text-slate-800'
                    }`}
                  >
                    Weekly
                  </button>
                  <button
                    onClick={() => setActiveChartTab('hourly')}
                    className={`px-3 py-1.5 rounded-lg text-[10px] font-bold transition-all ${
                      activeChartTab === 'hourly' 
                        ? 'bg-darkSurf text-brandCyan shadow-sm border border-darkBorder/40' 
                        : 'text-mutedText hover:text-slate-800'
                    }`}
                  >
                    Hourly
                  </button>
                </div>
              </div>

              {/* Glowing SVG Bar Chart */}
              <div className="relative w-full h-[220px] flex items-end justify-between border-b border-l border-darkBorder pb-2 pl-4">
                {/* Background Grid Lines */}
                <div className="absolute inset-0 flex flex-col justify-between pointer-events-none pr-2 pt-2">
                  <div className="w-full border-t border-darkBorder/40" />
                  <div className="w-full border-t border-darkBorder/40" />
                  <div className="w-full border-t border-darkBorder/40" />
                  <div className="w-full border-t border-darkBorder/40" />
                </div>

                {/* Bars */}
                {activeChartDataset.map((dataItem, index) => {
                  const percentage = (dataItem.total / maxChartVal) * 100;
                  const hasValues = dataItem.total > 0;
                  return (
                    <div 
                      key={index} 
                      className="flex-1 flex flex-col items-center group relative mx-0.5"
                    >
                      {/* Tooltip */}
                      <div className="absolute -top-10 opacity-0 group-hover:opacity-100 transition-opacity bg-slate-800 text-white text-[9px] px-2 py-1 rounded shadow-lg pointer-events-none z-20 whitespace-nowrap">
                        Calls: {dataItem.calls} | SMS: {dataItem.sms}
                      </div>

                      {/* Stacked bar logic */}
                      <div className="w-full max-w-[20px] bg-darkBg/30 border border-darkBorder/50 rounded-t-md h-[180px] flex items-end overflow-hidden relative">
                        {hasValues && (
                          <div 
                            className="w-full bg-gradient-to-t from-brandBlue to-brandCyan rounded-t-sm transition-all duration-500 ease-out shadow-[0_0_10px_rgba(0,136,255,0.4)]"
                            style={{ height: `${percentage}%` }}
                          />
                        )}
                      </div>

                      {/* X-axis labels */}
                      <span className="text-[8px] font-bold text-mutedText mt-2 max-w-[32px] truncate">
                        {dataItem.label}
                      </span>
                    </div>
                  );
                })}
              </div>

              {/* Chart Legend */}
              <div className="flex items-center gap-4 mt-4 text-[10px] font-semibold text-mutedText pl-4">
                <div className="flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full bg-brandCyan" />
                  Total Activity Volume (Calls + SMS)
                </div>
              </div>
            </div>

            {/* Top Contacted List */}
            <div className="glass-panel p-6 rounded-2xl border border-darkBorder shadow-sm flex flex-col justify-between">
              <div>
                <h3 className="text-sm font-bold text-slate-800">Top Contacted Directory</h3>
                <p className="text-[10px] text-mutedText font-semibold mt-0.5 mb-5">Most active connections by call + SMS frequency</p>
                
                {stats.topContacts.length === 0 ? (
                  <div className="py-12 flex flex-col items-center justify-center text-mutedText text-center">
                    <Users className="h-10 w-10 text-mutedText/40 mb-2" />
                    <p className="text-xs font-bold">No Contact Activity Logged</p>
                  </div>
                ) : (
                  <div className="space-y-4">
                    {stats.topContacts.map((c, i) => {
                      const maxTotal = stats.topContacts[0]?.total || 1;
                      const percentage = Math.round((c.total / maxTotal) * 100);

                      return (
                        <div key={i} className="space-y-1">
                          <div className="flex items-center justify-between text-xs font-bold text-slate-800">
                            <span className="truncate max-w-[150px]">{c.name !== 'Unknown' ? c.name : c.number}</span>
                            <span className="text-brandCyan font-mono">{c.total} interactions</span>
                          </div>
                          
                          <div className="flex items-center justify-between text-[9px] font-semibold text-mutedText">
                            <span>{c.number}</span>
                            <span>{c.calls} calls • {c.sms} SMS</span>
                          </div>

                          <div className="w-full bg-darkBg h-1.5 rounded-full overflow-hidden border border-darkBorder/60">
                            <div 
                              className="bg-brandTeal h-full rounded-full" 
                              style={{ width: `${percentage}%` }} 
                            />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              <div className="border-t border-darkBorder pt-4 mt-4 flex justify-between items-center text-[10px] font-bold text-mutedText">
                <span>Calculated dynamically</span>
                <span className="text-brandTeal">Top 5 Connections</span>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
};

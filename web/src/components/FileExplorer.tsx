import React, { useEffect, useState, useMemo } from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { MediaRepository } from '../firebase/mediaRepository';
import type { FileExplorerItem } from '../firebase/mediaRepository';
import {
  Folder, FileText, FileImage, FileVideo, FileAudio, File, Archive,
  ArrowLeft, RefreshCw, Eye, Download, Search, HardDrive,
  Home, ChevronRight, ChevronDown, ChevronUp, List, Trash2, Share2,
  LayoutGrid, ArrowUp, X, AlertTriangle, Maximize2
} from 'lucide-react';

// ─────────────────── Helpers ─────────────────────────────
const formatBytes = (bytes: number): string => {
  if (!bytes || bytes === 0) return '—';
  const k = 1024, sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const formatDate = (ms: number): string => {
  if (!ms) return '—';
  return new Date(ms).toLocaleString([], {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  });
};

const getFileType = (name: string): string => {
  const ext = name.split('.').pop()?.toLowerCase() || '';
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'heic'].includes(ext)) return 'JPG Image';
  if (['mp4', 'mkv', 'avi', 'mov', '3gp'].includes(ext)) return 'MP4 Video';
  if (['mp3', 'aac', 'flac', 'wav', 'm4a'].includes(ext)) return 'MP3 Audio';
  if (ext === 'pdf') return 'PDF File';
  if (['zip', 'rar', '7z', 'tar'].includes(ext)) return 'ZIP File';
  if (['txt', 'log', 'csv'].includes(ext)) return 'Text File';
  if (['apk'].includes(ext)) return 'APK Package';
  return 'File';
};

const FileIcon: React.FC<{ item: FileExplorerItem; size?: number }> = ({ item, size = 16 }) => {
  const cls = `h-${size === 16 ? 4 : 5} w-${size === 16 ? 4 : 5}`;
  if (item.isDirectory) return <Folder className={`${cls} text-yellow-500`} style={{ fill: 'rgba(234,179,8,0.18)' }} />;
  const ext = item.name.split('.').pop()?.toLowerCase() || '';
  if (['jpg','jpeg','png','gif','webp'].includes(ext)) return <FileImage className={`${cls} text-emerald-500`} />;
  if (['mp4','mkv','avi','mov'].includes(ext)) return <FileVideo className={`${cls} text-purple-500`} />;
  if (['mp3','aac','wav','m4a'].includes(ext)) return <FileAudio className={`${cls} text-pink-500`} />;
  if (['pdf'].includes(ext)) return <FileText className={`${cls} text-red-500`} />;
  if (['zip','rar','7z'].includes(ext)) return <Archive className={`${cls} text-orange-500`} />;
  return <File className={`${cls} text-slate-400`} />;
};

// Build pretty breadcrumbs from path
const parseBreadcrumb = (path: string) => {
  const trimmed = path.replace('/storage/emulated/0', 'Internal Storage');
  return trimmed.split('/').filter(Boolean);
};

// Sidebar static quick-nav folders (commonly browsed)
const QUICK_FOLDERS = [
  { label: 'Android', path: '/storage/emulated/0/Android' },
  { label: 'DCIM', path: '/storage/emulated/0/DCIM' },
  { label: 'Downloads', path: '/storage/emulated/0/Downloads' },
  { label: 'Documents', path: '/storage/emulated/0/Documents' },
  { label: 'Movies', path: '/storage/emulated/0/Movies' },
  { label: 'Music', path: '/storage/emulated/0/Music' },
  { label: 'Pictures', path: '/storage/emulated/0/Pictures' },
  { label: 'WhatsApp', path: '/storage/emulated/0/WhatsApp' },
];

// ─────────────────── Component ───────────────────────────
export const FileExplorer: React.FC = () => {
  const { selectedDevice } = useDeviceStore();

  const [currentPath, setCurrentPath] = useState('/storage/emulated/0');
  const [explorerState, setExplorerState] = useState<{ path: string; error: string | null; items: FileExplorerItem[] }>({
    path: '/storage/emulated/0', error: null, items: []
  });
  const [isLoading, setIsLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState<'list' | 'grid'>('list');
  const [selectedItem, setSelectedItem] = useState<FileExplorerItem | null>(null);
  const [previewItem, setPreviewItem] = useState<any | null>(null);
  const [isPreviewLoading, setIsPreviewLoading] = useState(false);
  const [downloadPath, setDownloadPath] = useState<string | null>(null);
  const [showDetails, setShowDetails] = useState(false);
  const [showFullscreen, setShowFullscreen] = useState(false);

  useEffect(() => {
    if (!selectedDevice) return;
    setIsLoading(true);

    const unsubDir = MediaRepository.subscribeExplorerCurrentDir(selectedDevice, (dir) => {
      setExplorerState(dir);
      setCurrentPath(dir.path);
      setIsLoading(false);
      setSelectedItem(null);
    });

    const unsubPreview = MediaRepository.subscribeExplorerPreview(selectedDevice, (preview) => {
      setPreviewItem(preview);
      setIsPreviewLoading(false);
    });

    const unsubDownload = MediaRepository.subscribeExplorerDownload(selectedDevice, (url) => {
      if (url && downloadPath) {
        setIsLoading(false);
        const a = document.createElement('a');
        a.href = url;
        a.download = downloadPath.split('/').pop() || 'download';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setDownloadPath(null);
        MediaRepository.clearExplorerDownload(selectedDevice);
      }
    });

    MediaRepository.requestDirectory(selectedDevice, '/storage/emulated/0');
    return () => { unsubDir(); unsubPreview(); unsubDownload(); };
  }, [selectedDevice, downloadPath]);

  const handleNavigate = (path: string) => {
    setIsLoading(true);
    setSearchQuery('');
    MediaRepository.requestDirectory(selectedDevice, path);
  };

  const handleBack = () => {
    const parts = currentPath.split('/');
    if (parts.length > 3) { parts.pop(); handleNavigate(parts.join('/')); }
  };

  const handleGoHome = () => handleNavigate('/storage/emulated/0');

  const handlePreview = async (item: FileExplorerItem) => {
    setSelectedItem(item);
    const ext = item.name.split('.').pop()?.toLowerCase() || '';
    const previewable = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'mp4', 'mkv', 'avi', 'mov', 'txt', 'csv', 'json', 'xml', 'log', 'mp3', 'wav', 'ogg', 'm4a'].includes(ext);

    if (previewable) {
      setIsPreviewLoading(true);
      setPreviewItem(null);
      await MediaRepository.clearExplorerPreview(selectedDevice);
      MediaRepository.requestFilePreview(selectedDevice, item.path);
    } else {
      setPreviewItem(null);
    }
  };

  const handleDownload = async (filePath: string) => {
    setIsLoading(true);
    setDownloadPath(filePath);
    await MediaRepository.clearExplorerDownload(selectedDevice);
    MediaRepository.requestFileDownload(selectedDevice, filePath);
  };

  const handleShare = async (item: FileExplorerItem) => {
    setIsLoading(true);
    await MediaRepository.clearExplorerDownload(selectedDevice);
    MediaRepository.requestFileDownload(selectedDevice, item.path);
    const unsub = MediaRepository.subscribeExplorerDownload(selectedDevice, (url) => {
      if (url) {
        unsub();
        setIsLoading(false);
        MediaRepository.clearExplorerDownload(selectedDevice);
        if (navigator.share) {
          navigator.share({
            title: `Share ${item.name}`,
            text: `Download file ${item.name} from Xshield`,
            url: url
          }).catch(() => {
            navigator.clipboard.writeText(url);
            alert(`File link copied to clipboard:\n${url}`);
          });
        } else {
          navigator.clipboard.writeText(url);
          alert(`File link copied to clipboard:\n${url}`);
        }
      }
    });
  };

  const handleDelete = async (item: FileExplorerItem) => {
    if (window.confirm(`Are you sure you want to permanently delete ${item.name} from the child's device?`)) {
      setIsLoading(true);
      await MediaRepository.requestFileDelete(selectedDevice, item.path);
      setSelectedItem(null);
      setTimeout(() => {
        setIsLoading(false);
        handleNavigate(currentPath);
      }, 3000);
    }
  };

  const filteredItems = useMemo(() =>
    explorerState.items.filter(i => i.name.toLowerCase().includes(searchQuery.toLowerCase())),
    [explorerState.items, searchQuery]
  );

  const breadcrumbs = parseBreadcrumb(currentPath);

  return (
    <div className="space-y-4">

      {/* ── Page Header ── */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-xl bg-brandCyan/10 border border-brandCyan/20 text-brandCyan flex items-center justify-center">
            <HardDrive className="h-5 w-5" />
          </div>
          <div>
            <h2 className="text-sm font-black text-slate-800 uppercase tracking-wide">File Explorer Module</h2>
            <p className="text-[10px] text-mutedText font-semibold mt-0.5">Browse and manage files on child device</p>
          </div>
        </div>

        {/* Breadcrumb path */}
        <div className="hidden lg:flex items-center gap-1.5 text-[10px] font-bold text-mutedText">
          <button onClick={handleGoHome} className="hover:text-brandCyan transition-colors">
            <Home className="h-3.5 w-3.5" />
          </button>
          {breadcrumbs.map((crumb, i) => (
            <React.Fragment key={i}>
              <ChevronRight className="h-3 w-3 opacity-40" />
              <span className={i === breadcrumbs.length - 1 ? 'text-brandCyan font-black' : 'text-mutedText'}>
                {crumb}
              </span>
            </React.Fragment>
          ))}
        </div>
      </div>

      {/* ── Three-column layout ── */}
      <div className="grid grid-cols-12 gap-4" style={{ height: 'calc(100vh - 200px)' }}>

        {/* ── LEFT: Storage Locations sidebar ── */}
        <div className="col-span-2 flex flex-col gap-3">
          <div className="glass-panel rounded-2xl border border-darkBorder bg-darkSurf shadow-sm flex flex-col overflow-hidden h-full">
            {/* Header */}
            <div className="px-4 pt-4 pb-2">
              <p className="text-[8px] font-black text-mutedText uppercase tracking-widest">Storage Locations</p>
            </div>

            {/* Internal Storage */}
            <div className="px-3 pb-2">
              <button
                onClick={handleGoHome}
                className="w-full flex items-center gap-2.5 px-3 py-2.5 rounded-xl border border-brandCyan/30 bg-brandCyan/8 text-brandCyan text-xs font-black"
              >
                <HardDrive className="h-4 w-4 shrink-0" />
                <span className="truncate">Internal Storage</span>
                <ChevronDown className="h-3 w-3 ml-auto shrink-0" />
              </button>
              {/* Storage bar */}
              <div className="mt-2 px-1">
                <div className="h-1.5 w-full bg-slate-100 rounded-full overflow-hidden">
                  <div className="h-full bg-brandCyan rounded-full" style={{ width: '37%' }} />
                </div>
                <p className="text-[8px] text-mutedText font-semibold mt-1">48.2 GB / 128 GB</p>
              </div>
            </div>

            {/* Quick-nav folder tree */}
            <div className="flex-1 overflow-y-auto px-3 py-1 space-y-0.5">
              {QUICK_FOLDERS.map(({ label, path }) => {
                const isActive = currentPath.startsWith(path);
                return (
                  <button
                    key={path}
                    onClick={() => handleNavigate(path)}
                    className={`w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-[10px] font-semibold transition-all text-left ${
                      isActive
                        ? 'bg-brandCyan/10 text-brandCyan font-black'
                        : 'text-slate-600 hover:bg-slate-50 hover:text-slate-800'
                    }`}
                  >
                    <Folder className="h-3.5 w-3.5 shrink-0 text-yellow-500" style={{ fill: 'rgba(234,179,8,0.18)' }} />
                    <span className="truncate">{label}</span>
                  </button>
                );
              })}
            </div>

            {/* SD Card stub */}
            <div className="px-3 pt-2 pb-3 border-t border-darkBorder">
              <button className="w-full flex items-center gap-2.5 px-3 py-2.5 rounded-xl border border-darkBorder bg-slate-50 text-slate-500 text-xs font-bold opacity-60 cursor-not-allowed">
                <HardDrive className="h-4 w-4 shrink-0 text-slate-400" />
                <span>SD Card</span>
              </button>
              <div className="mt-2 px-1">
                <div className="h-1.5 w-full bg-slate-100 rounded-full overflow-hidden">
                  <div className="h-full bg-slate-300 rounded-full" style={{ width: '24%' }} />
                </div>
                <p className="text-[8px] text-mutedText font-semibold mt-1">15.6 GB / 64 GB</p>
              </div>
            </div>

            {/* Storage usage summary */}
            <div className="px-4 py-3 border-t border-darkBorder">
              <p className="text-[8px] font-black text-mutedText uppercase tracking-widest mb-2">Storage Usage</p>
              <div className="h-1.5 w-full bg-slate-100 rounded-full overflow-hidden">
                <div className="h-full bg-gradient-to-r from-brandCyan to-brandBlue rounded-full" style={{ width: '37%' }} />
              </div>
              <p className="text-[8px] text-mutedText font-bold mt-1.5">48.2 GB / 128 GB (37%)</p>
            </div>
          </div>
        </div>

        {/* ── MIDDLE: File listing panel ── */}
        <div className={`${selectedItem ? 'col-span-7' : 'col-span-10'} flex flex-col glass-panel rounded-2xl border border-darkBorder bg-darkSurf shadow-sm overflow-hidden`}>

          {/* Toolbar */}
          <div className="flex items-center gap-3 px-4 py-3 border-b border-darkBorder">
            {/* Breadcrumb + item count */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-1 text-[10px] font-bold text-slate-700">
                {breadcrumbs.map((crumb, i) => (
                  <React.Fragment key={i}>
                    {i > 0 && <ChevronRight className="h-3 w-3 text-mutedText" />}
                    <span className={i === breadcrumbs.length - 1 ? 'text-brandCyan' : 'text-slate-500'}>
                      {crumb}
                    </span>
                  </React.Fragment>
                ))}
              </div>
              <p className="text-[9px] text-mutedText font-semibold mt-0.5">{filteredItems.length} items</p>
            </div>

            {/* Search */}
            <div className="relative">
              <Search className="absolute left-2.5 top-2 h-3.5 w-3.5 text-mutedText" />
              <input
                type="text"
                placeholder="Filter items..."
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                className="bg-darkBg border border-darkBorder rounded-xl text-[10px] pl-8 pr-3 py-1.5 text-slate-700 focus:outline-none focus:border-brandCyan w-40"
              />
            </div>

            {/* Action buttons */}
            <button
              onClick={() => handleNavigate(currentPath)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-darkBorder bg-darkBg hover:border-brandCyan/40 text-[10px] font-bold text-slate-700 hover:text-brandCyan transition-all"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${isLoading ? 'animate-spin text-brandCyan' : ''}`} />
              Refresh
            </button>

            {/* View toggle */}
            <div className="flex items-center border border-darkBorder rounded-xl overflow-hidden">
              <button
                onClick={() => setViewMode('list')}
                className={`p-1.5 transition-all ${viewMode === 'list' ? 'bg-brandCyan text-white' : 'bg-darkBg text-mutedText hover:text-slate-700'}`}
              >
                <List className="h-3.5 w-3.5" />
              </button>
              <button
                onClick={() => setViewMode('grid')}
                className={`p-1.5 transition-all ${viewMode === 'grid' ? 'bg-brandCyan text-white' : 'bg-darkBg text-mutedText hover:text-slate-700'}`}
              >
                <LayoutGrid className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>

          {/* File list area */}
          <div className="flex-1 overflow-y-auto">
            {isLoading ? (
              <div className="flex flex-col items-center justify-center h-full py-16">
                <RefreshCw className="h-8 w-8 text-brandCyan animate-spin mb-3" />
                <p className="text-[10px] text-mutedText font-semibold">Reading remote directory index...</p>
              </div>
            ) : explorerState.error ? (
              <div className="flex flex-col items-center justify-center h-full py-16">
                <AlertTriangle className="h-8 w-8 text-brandRed mb-3" />
                <p className="text-xs text-brandRed font-bold">{explorerState.error}</p>
              </div>
            ) : filteredItems.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full py-16">
                <Folder className="h-10 w-10 text-mutedText/30 mb-3" />
                <p className="text-xs text-mutedText font-semibold">This folder is empty</p>
              </div>
            ) : viewMode === 'list' ? (
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-darkBorder bg-slate-50">
                    <th className="py-2.5 px-4 text-[9px] font-black text-mutedText uppercase tracking-widest">Name</th>
                    <th className="py-2.5 px-4 text-[9px] font-black text-mutedText uppercase tracking-widest">Type</th>
                    <th className="py-2.5 px-4 text-[9px] font-black text-mutedText uppercase tracking-widest">Size</th>
                    <th className="py-2.5 px-4 text-[9px] font-black text-mutedText uppercase tracking-widest">Last Modified</th>
                    <th className="py-2.5 px-4 text-[9px] font-black text-mutedText uppercase tracking-widest text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredItems.map((item, idx) => (
                    <tr
                      key={idx}
                      onClick={() => item.isDirectory ? handleNavigate(item.path) : handlePreview(item)}
                      className={`border-b border-darkBorder/40 text-xs cursor-pointer transition-all ${
                        selectedItem?.path === item.path
                          ? 'bg-brandCyan/8 border-l-2 border-l-brandCyan'
                          : 'hover:bg-slate-50'
                      }`}
                    >
                      <td className="py-2.5 px-4">
                        <div className="flex items-center gap-2.5">
                          <FileIcon item={item} />
                          <span className="truncate max-w-[200px] font-semibold text-slate-700">{item.name}</span>
                        </div>
                      </td>
                      <td className="py-2.5 px-4 text-[10px] text-mutedText font-semibold">
                        {item.isDirectory ? 'Folder' : getFileType(item.name)}
                      </td>
                      <td className="py-2.5 px-4 text-[10px] text-mutedText font-mono">
                        {item.isDirectory ? '—' : formatBytes(item.size)}
                      </td>
                      <td className="py-2.5 px-4 text-[10px] text-mutedText">
                        {formatDate(item.lastModified)}
                      </td>
                      <td className="py-2.5 px-4 text-right" onClick={e => e.stopPropagation()}>
                        {!item.isDirectory && (
                          <div className="flex justify-end gap-1">
                            <button
                              onClick={() => handlePreview(item)}
                              className="p-1.5 rounded-lg hover:bg-brandCyan/10 text-brandCyan transition-all"
                              title="Preview"
                            >
                              <Eye className="h-3.5 w-3.5" />
                            </button>
                            <button
                              onClick={() => handleDownload(item.path)}
                              className="p-1.5 rounded-lg hover:bg-brandTeal/10 text-brandTeal transition-all"
                              title="Download"
                            >
                              <Download className="h-3.5 w-3.5" />
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              /* Grid view */
              <div className="grid grid-cols-3 sm:grid-cols-4 lg:grid-cols-5 gap-3 p-4">
                {filteredItems.map((item, idx) => (
                  <button
                    key={idx}
                    onClick={() => item.isDirectory ? handleNavigate(item.path) : handlePreview(item)}
                    className={`flex flex-col items-center gap-2 p-3 rounded-xl border transition-all text-center ${
                      selectedItem?.path === item.path
                        ? 'border-brandCyan/40 bg-brandCyan/8'
                        : 'border-darkBorder hover:border-brandCyan/20 hover:bg-slate-50'
                    }`}
                  >
                    <FileIcon item={item} size={20} />
                    <span className="text-[9px] font-semibold text-slate-700 truncate w-full">{item.name}</span>
                    <span className="text-[8px] text-mutedText">{item.isDirectory ? 'Folder' : formatBytes(item.size)}</span>
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Bottom pagination area */}
          <div className="flex items-center justify-between px-4 py-2.5 border-t border-darkBorder bg-slate-50">
            <p className="text-[9px] text-mutedText font-semibold">
              Showing {filteredItems.length} of {explorerState.items.length} items
            </p>
            <div className="flex items-center gap-2">
              {/* Quick shortcuts */}
              <button onClick={handleBack} disabled={currentPath === '/storage/emulated/0'} className="p-1.5 rounded-lg border border-darkBorder hover:border-brandCyan/30 text-mutedText hover:text-brandCyan disabled:opacity-30 transition-all" title="Back">
                <ArrowLeft className="h-3.5 w-3.5" />
              </button>
              <button onClick={handleGoHome} className="p-1.5 rounded-lg border border-darkBorder hover:border-brandCyan/30 text-mutedText hover:text-brandCyan transition-all" title="Home">
                <Home className="h-3.5 w-3.5" />
              </button>
              <button onClick={handleBack} disabled={currentPath === '/storage/emulated/0'} className="p-1.5 rounded-lg border border-darkBorder hover:border-brandCyan/30 text-mutedText hover:text-brandCyan disabled:opacity-30 transition-all" title="Up one level">
                <ArrowUp className="h-3.5 w-3.5" />
              </button>
              <button onClick={() => handleNavigate(currentPath)} className="p-1.5 rounded-lg border border-darkBorder hover:border-brandCyan/30 text-mutedText hover:text-brandCyan transition-all" title="Refresh">
                <RefreshCw className={`h-3.5 w-3.5 ${isLoading ? 'animate-spin text-brandCyan' : ''}`} />
              </button>
            </div>
          </div>
        </div>

        {/* ── RIGHT: Preview + Details panel ── */}
        {selectedItem && (
          <div className="col-span-3 flex flex-col gap-3 overflow-y-auto">

            {/* Preview Card */}
            <div className="glass-panel rounded-2xl border border-darkBorder bg-darkSurf shadow-sm overflow-hidden">
              <div className="flex items-center justify-between px-4 py-3 border-b border-darkBorder">
                <p className="text-[9px] font-black text-mutedText uppercase tracking-widest">Preview</p>
                <div className="flex items-center gap-1.5">
                  <button
                    onClick={() => setShowDetails(!showDetails)}
                    className="text-mutedText hover:text-slate-800 transition-colors p-1 rounded-lg hover:bg-slate-100 flex items-center justify-center"
                    title={showDetails ? "Hide Details" : "Show Details"}
                  >
                    {showDetails ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
                  </button>
                  <button onClick={() => setSelectedItem(null)} className="text-mutedText hover:text-slate-800 transition-colors p-1 rounded-lg hover:bg-slate-100 flex items-center justify-center">
                    <X className="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>
              <div className="relative bg-slate-100 flex items-center justify-center overflow-auto p-2 transition-all duration-300" style={{ height: showDetails ? '160px' : '320px' }}>
                {isPreviewLoading ? (
                  <RefreshCw className="h-6 w-6 text-brandCyan animate-spin" />
                ) : previewItem ? (
                  <>
                    {previewItem.type === 'image' && previewItem.url && (
                      <img
                        src={previewItem.url}
                        alt={selectedItem.name}
                        className="max-h-full max-w-full object-contain"
                      />
                    )}
                    {previewItem.type === 'video' && previewItem.url && (
                      <div className="relative max-h-full max-w-full">
                        <img
                          src={previewItem.url}
                          alt={selectedItem.name}
                          className="max-h-full max-w-full object-contain"
                        />
                        <div className="absolute inset-0 flex items-center justify-center bg-black/30">
                          <span className="bg-brandCyan text-white px-2 py-0.5 rounded-full text-[8px] font-black uppercase">Video Frame</span>
                        </div>
                      </div>
                    )}
                    {previewItem.type === 'text' && (
                      <pre className="text-[8px] font-mono text-left w-full h-full overflow-auto whitespace-pre-wrap bg-slate-50 p-1.5 border border-slate-200 rounded-lg text-slate-700">
                        {previewItem.content}
                      </pre>
                    )}
                    {previewItem.type === 'audio' && (
                      <div className="flex flex-col items-center gap-1.5 text-center p-2">
                        <FileAudio className="h-8 w-8 text-pink-500" />
                        <p className="text-[9px] font-black text-slate-700 truncate w-40">{previewItem.title || selectedItem.name}</p>
                        <p className="text-[8px] text-mutedText font-semibold">
                          Duration: {previewItem.duration ? `${(parseInt(previewItem.duration) / 1000).toFixed(1)}s` : '—'}
                        </p>
                      </div>
                    )}
                  </>
                ) : (
                  <div className="flex flex-col items-center gap-2 opacity-30">
                    <FileIcon item={selectedItem} size={20} />
                    <span className="text-[9px] text-mutedText font-semibold">No preview</span>
                  </div>
                )}
                {/* Full screen button */}
                {previewItem && (previewItem.type === 'image' || previewItem.type === 'video') && (
                  <button
                    onClick={() => setShowFullscreen(true)}
                    className="absolute bottom-2.5 right-2.5 bg-black/60 hover:bg-black/85 text-white p-1.5 rounded-lg transition-all"
                    title="Fullscreen"
                  >
                    <Maximize2 className="h-3 w-3" />
                  </button>
                )}
                {/* Online dot if preview loaded */}
                {previewItem && (
                  <div className="absolute top-2 right-2 h-2 w-2 rounded-full bg-brandTeal" />
                )}
              </div>
              <div className="px-4 py-3">
                <p className="text-xs font-black text-slate-800 truncate">{selectedItem.name}</p>
                <p className="text-[9px] text-mutedText font-semibold mt-0.5">
                  {formatBytes(selectedItem.size)} • {getFileType(selectedItem.name)}
                </p>
              </div>
            </div>

            {/* File Details Card (collapsible) */}
            {showDetails && (
              <div className="glass-panel rounded-2xl border border-darkBorder bg-darkSurf shadow-sm p-4">
                <p className="text-[9px] font-black text-mutedText uppercase tracking-widest mb-3">File Details</p>
                <div className="space-y-2">
                  {[
                    { label: 'Path', value: selectedItem.path.replace('/storage/emulated/0', '/Internal Storage'), mono: true },
                    { label: 'Size', value: `${formatBytes(selectedItem.size)} (${selectedItem.size.toLocaleString()} bytes)` },
                    { label: 'Type', value: getFileType(selectedItem.name) },
                    { label: 'Modified', value: formatDate(selectedItem.lastModified) },
                  ].map(({ label, value, mono }) => (
                    <div key={label}>
                      <p className="text-[8px] font-black text-mutedText uppercase tracking-wider">{label}</p>
                      <p className={`text-[10px] font-semibold text-slate-700 mt-0.5 break-all ${mono ? 'font-mono text-[9px]' : ''}`}>{value}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Actions Card */}
            <div className="glass-panel rounded-2xl border border-darkBorder bg-darkSurf shadow-sm p-4">
              <p className="text-[9px] font-black text-mutedText uppercase tracking-widest mb-3">Actions</p>
              <div className="space-y-2">
                <button
                  onClick={() => handlePreview(selectedItem)}
                  className="w-full flex items-center gap-2.5 p-2.5 rounded-xl border border-darkBorder hover:border-brandCyan/30 bg-darkBg hover:bg-brandCyan/5 text-[10px] font-bold text-slate-700 hover:text-brandCyan transition-all"
                >
                  <Eye className="h-3.5 w-3.5" />
                  Preview File
                </button>
                <button
                  onClick={() => handleDownload(selectedItem.path)}
                  className="w-full flex items-center gap-2.5 p-2.5 rounded-xl border border-darkBorder hover:border-brandTeal/30 bg-darkBg hover:bg-brandTeal/5 text-[10px] font-bold text-slate-700 hover:text-brandTeal transition-all"
                >
                  <Download className="h-3.5 w-3.5" />
                  Download File
                </button>
                <button
                  onClick={() => handleShare(selectedItem)}
                  className="w-full flex items-center gap-2.5 p-2.5 rounded-xl border border-darkBorder hover:border-brandBlue/30 bg-darkBg hover:bg-brandBlue/5 text-[10px] font-bold text-slate-700 hover:text-brandBlue transition-all"
                >
                  <Share2 className="h-3.5 w-3.5" />
                  Share File
                </button>
                <button
                  onClick={() => handleDelete(selectedItem)}
                  className="w-full flex items-center gap-2.5 p-2.5 rounded-xl border border-brandRed/20 hover:border-brandRed/40 bg-brandRed/5 hover:bg-brandRed/10 text-[10px] font-bold text-brandRed transition-all"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                  Delete File
                </button>
              </div>
            </div>

            {/* Quick Shortcuts */}
            <div className="glass-panel rounded-2xl border border-darkBorder bg-darkSurf shadow-sm p-4">
              <p className="text-[9px] font-black text-mutedText uppercase tracking-widest mb-3">Quick Shortcuts</p>
              <div className="grid grid-cols-4 gap-2">
                {[
                  { icon: ArrowLeft, label: 'Back', action: handleBack },
                  { icon: Home, label: 'Home', action: handleGoHome },
                  { icon: ArrowUp, label: 'Up One Level', action: handleBack },
                  { icon: RefreshCw, label: 'Refresh', action: () => handleNavigate(currentPath) },
                ].map(({ icon: Icon, label, action }) => (
                  <button
                    key={label}
                    onClick={action}
                    className="flex flex-col items-center gap-1.5 p-2 rounded-xl border border-darkBorder hover:border-brandCyan/30 bg-darkBg hover:bg-brandCyan/5 text-mutedText hover:text-brandCyan transition-all"
                    title={label}
                  >
                    <Icon className="h-4 w-4" />
                    <span className="text-[8px] font-bold text-center leading-tight">{label}</span>
                  </button>
                ))}
              </div>
            </div>

          </div>
        )}
      </div>

      {/* Fullscreen Preview Modal */}
      {showFullscreen && previewItem && (
        <div className="fixed inset-0 bg-black/90 backdrop-blur-md z-[9999] flex flex-col items-center justify-center p-6 transition-all duration-350">
          <button
            onClick={() => setShowFullscreen(false)}
            className="absolute top-6 right-6 bg-slate-800/80 hover:bg-slate-700/80 text-white p-2.5 rounded-full border border-slate-700/60 transition-all cursor-pointer shadow-lg"
            title="Close Fullscreen"
          >
            <X className="h-6 w-6" />
          </button>

          <div className="max-w-[90vw] max-h-[80vh] flex items-center justify-center shadow-2xl rounded-2xl overflow-hidden bg-slate-900 border border-slate-800">
            {(previewItem.type === 'image' || previewItem.type === 'video') && (
              <img
                src={previewItem.url}
                alt={selectedItem?.name}
                className="max-h-[80vh] max-w-[90vw] object-contain"
              />
            )}
          </div>

          <div className="mt-6 text-center space-y-1">
            <h4 className="text-white font-bold text-base truncate max-w-lg">{selectedItem?.name}</h4>
            <p className="text-slate-400 text-xs font-semibold">
              {selectedItem ? `${formatBytes(selectedItem.size)} • ${getFileType(selectedItem.name)}` : ''}
            </p>
          </div>
        </div>
      )}
    </div>
  );
};

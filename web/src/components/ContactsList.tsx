import React, { useEffect, useState, useMemo } from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { StorageRepository } from '../firebase/storageRepository';
import type { ContactItem } from '../firebase/storageRepository';
import { LiveRepository } from '../firebase/liveRepository';
import { Search, RefreshCw, Phone, User, Copy, Check, Ban, Lock } from 'lucide-react';

export const ContactsList: React.FC = () => {
  const { selectedDevice } = useDeviceStore();
  const [contacts, setContacts] = useState<ContactItem[]>([]);
  const [blockedList, setBlockedList] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [isSyncing, setIsSyncing] = useState(false);
  const [copiedNum, setCopiedNum] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedDevice) return;
    setLoading(true);

    const unsub = StorageRepository.subscribeContacts(selectedDevice, (data) => {
      // Sort contacts by name initially
      const sorted = [...data].sort((a, b) => 
        (a.name || 'Unknown').localeCompare(b.name || 'Unknown', undefined, { sensitivity: 'base' })
      );
      setContacts(sorted);
      setLoading(false);
    });

    const unsubBlocked = LiveRepository.subscribeBlockedList(selectedDevice, (data) => {
      setBlockedList(data);
    });

    return () => {
      unsub();
      unsubBlocked();
    };
  }, [selectedDevice]);

  const handleSync = async () => {
    if (!selectedDevice) return;
    setIsSyncing(true);
    try {
      await LiveRepository.forceContactsSync(selectedDevice);
      alert('Force contacts synchronization command sent to child device!');
      setTimeout(() => setIsSyncing(false), 3000);
    } catch (err) {
      console.error(err);
      setIsSyncing(false);
    }
  };

  const handleCopy = (num: string) => {
    navigator.clipboard.writeText(num);
    setCopiedNum(num);
    setTimeout(() => setCopiedNum(null), 1500);
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

  // Filter contacts based on query
  const filteredContacts = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return contacts;
    return contacts.filter(
      (c) =>
        (c.name || '').toLowerCase().includes(query) ||
        (c.number || '').toLowerCase().includes(query)
    );
  }, [contacts, searchQuery]);

  // Group contacts by their first letter
  const groupedContacts = useMemo(() => {
    const groups: Record<string, ContactItem[]> = {};
    filteredContacts.forEach((contact) => {
      const name = contact.name.trim();
      let firstLetter = '#';
      if (name.length > 0) {
        const char = name.charAt(0).toUpperCase();
        if (/[A-Z]/.test(char)) {
          firstLetter = char;
        }
      }
      if (!groups[firstLetter]) {
        groups[firstLetter] = [];
      }
      groups[firstLetter].push(contact);
    });
    
    // Sort key names
    return Object.keys(groups)
      .sort((a, b) => {
        if (a === '#') return 1;
        if (b === '#') return -1;
        return a.localeCompare(b);
      })
      .reduce((acc, key) => {
        acc[key] = groups[key];
        return acc;
      }, {} as Record<string, ContactItem[]>);
  }, [filteredContacts]);

  // Alphabet list for quick scroll
  const alphabets = useMemo(() => {
    const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ#'.split('');
    const presentLetters = Object.keys(groupedContacts);
    return letters.map((l) => ({
      letter: l,
      isPresent: presentLetters.includes(l),
    }));
  }, [groupedContacts]);

  const scrollToSection = (letter: string) => {
    const element = document.getElementById(`section-${letter}`);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  // Pick colors for initials background to match Android app high fidelity
  const getAvatarStyle = (name: string) => {
    const colors = [
      'bg-brandCyan/10 text-brandCyan border-brandCyan/35',
      'bg-brandTeal/10 text-brandTeal border-brandTeal/35',
      'bg-brandBlue/10 text-brandBlue border-brandBlue/35',
      'bg-purple-600/10 text-purple-600 border-purple-500/35',
      'bg-pink-600/10 text-pink-600 border-pink-500/35',
      'bg-indigo-600/10 text-indigo-600 border-indigo-500/35',
      'bg-emerald-600/10 text-emerald-600 border-emerald-500/35',
      'bg-amber-600/10 text-amber-600 border-amber-500/35',
    ];
    let hash = 0;
    const cleanName = name || 'U';
    for (let i = 0; i < cleanName.length; i++) {
      hash = cleanName.charCodeAt(i) + ((hash << 5) - hash);
    }
    const index = Math.abs(hash % colors.length);
    return colors[index];
  };

  const getInitials = (name: string) => {
    const parts = name.trim().split(/\s+/);
    if (parts.length === 0 || !parts[0]) return '?';
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
  };

  return (
    <div className="flex h-[750px] bg-darkSurf border border-darkBorder rounded-2xl overflow-hidden shadow-md relative">
      {/* Main List Column */}
      <div className="flex-1 flex flex-col min-w-0">
        
        {/* Header Section */}
        <div className="p-5 border-b border-darkBorder flex items-center justify-between bg-darkSurf z-10">
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 bg-brandCyan/10 text-brandCyan rounded-lg flex items-center justify-center border border-brandCyan/20">
              <User className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-slate-800">Device Contacts List</h3>
              <p className="text-[10px] font-semibold text-mutedText mt-0.5">
                {contacts.length} total contacts synced from child device
              </p>
            </div>
          </div>
          <button
            onClick={handleSync}
            disabled={isSyncing}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 transition-all ${
              isSyncing
                ? 'bg-brandCyan/10 border border-brandCyan text-brandCyan'
                : 'bg-darkBg border border-darkBorder text-mutedText hover:border-brandCyan hover:text-brandCyan'
            }`}
          >
            <RefreshCw className={`h-3.5 w-3.5 ${isSyncing ? 'animate-spin' : ''}`} />
            {isSyncing ? 'SYNCING...' : 'SYNC CONTACTS'}
          </button>
        </div>

        {/* Search Input Bar */}
        <div className="px-5 py-3 border-b border-darkBorder bg-darkBg/30 flex items-center gap-2">
          <Search className="h-4 w-4 text-mutedText" />
          <input
            type="text"
            placeholder="Search by name or number..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="flex-1 bg-transparent border-0 text-xs font-semibold placeholder:text-mutedText/75 focus:outline-none focus:ring-0 text-slate-800"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="text-[10px] text-brandRed hover:underline font-bold"
            >
              Clear
            </button>
          )}
        </div>

        {/* Contacts Scrollable Area */}
        <div className="flex-1 overflow-y-auto pr-8 pl-5 py-4 space-y-4 scroll-smooth">
          {loading ? (
            <div className="h-full flex flex-col items-center justify-center text-mutedText">
              <RefreshCw className="h-8 w-8 animate-spin text-brandCyan mb-2" />
              <p className="text-xs font-bold">Fetching Contacts from Cloud...</p>
            </div>
          ) : contacts.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-center p-6">
              <User className="h-12 w-12 text-mutedText/40 mb-2" />
              <p className="text-sm font-bold text-slate-800">No Contacts Found</p>
              <p className="text-xs text-mutedText mt-1 max-w-[280px]">
                Click the Sync Contacts button above to fetch the contact book from the target phone.
              </p>
            </div>
          ) : Object.keys(groupedContacts).length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-mutedText">
              <Search className="h-8 w-8 mb-2" />
              <p className="text-xs font-bold">No contacts match "{searchQuery}"</p>
            </div>
          ) : (
            Object.keys(groupedContacts).map((letter) => (
              <div key={letter} id={`section-${letter}`} className="scroll-mt-4">
                {/* Alphabet sticky section header */}
                <div className="sticky top-0 bg-darkSurf py-1.5 font-black text-xs text-brandCyan border-b border-darkBorder/65 mb-2.5 flex items-center z-5">
                  <span className="bg-brandCyan/10 px-2 py-0.5 rounded mr-2">{letter}</span>
                  <div className="flex-1 h-[1px] bg-darkBorder/60" />
                </div>

                {/* Contacts items */}
                <div className="space-y-2">
                  {groupedContacts[letter].map((contact, index) => (
                    <div
                      key={index}
                      className="group flex items-center justify-between p-3 rounded-xl border border-darkBorder hover:border-brandCyan/35 bg-darkSurf hover:bg-brandCyan/2 transition-all"
                    >
                      {/* Avatar & Name Info */}
                      <div className="flex items-center gap-3">
                        <div
                          className={`h-10 w-10 flex items-center justify-center rounded-full border text-xs font-black uppercase font-mono tracking-wider shrink-0 ${getAvatarStyle(
                            contact.name
                          )}`}
                        >
                          {getInitials(contact.name)}
                        </div>
                        <div>
                          <h4 className="text-xs font-bold text-slate-800 group-hover:text-brandCyan transition-colors">
                            {contact.name || 'Unnamed Contact'}
                          </h4>
                          <p className="text-[10px] font-semibold text-mutedText mt-0.5">
                            {contact.number || 'No number'}
                          </p>
                        </div>
                      </div>

                      {/* Action buttons */}
                      <div className="flex items-center gap-1.5 opacity-60 group-hover:opacity-100 transition-opacity">
                        <button
                          onClick={() => handleCopy(contact.number)}
                          className="p-2 hover:bg-darkBg border border-transparent hover:border-darkBorder rounded-lg text-mutedText hover:text-slate-800 transition-all"
                          title="Copy phone number"
                        >
                          {copiedNum === contact.number ? (
                            <Check className="h-3.5 w-3.5 text-brandTeal" />
                          ) : (
                            <Copy className="h-3.5 w-3.5" />
                          )}
                        </button>

                        {(() => {
                          const cleanNum = (contact.number || '').replace(/\s+/g, '');
                          const blockedItem = blockedList.find(b => b.number.replace(/\s+/g, '') === cleanNum);
                          const isBlocked = !!blockedItem;
                          return isBlocked ? (
                            <button
                              onClick={() => handleRemoveBlock(blockedItem.id)}
                              className="p-2 hover:bg-brandCyan/10 border border-transparent hover:border-brandCyan/20 rounded-lg text-brandCyan transition-all"
                              title="Blocked - Click to Unlock"
                            >
                              <Lock className="h-3.5 w-3.5" />
                            </button>
                          ) : (
                            <button
                              onClick={() => handleDirectBlock(contact.number)}
                              className="p-2 hover:bg-rose-50 border border-transparent hover:border-rose-100 rounded-lg text-mutedText hover:text-rose-600 transition-all"
                              title="Block Number"
                            >
                              <Ban className="h-3.5 w-3.5" />
                            </button>
                          );
                        })()}

                        <a
                          href={`tel:${contact.number}`}
                          className="p-2 hover:bg-brandTeal/10 border border-transparent hover:border-brandTeal/20 rounded-lg text-mutedText hover:text-brandTeal transition-all"
                          title="Dial number"
                        >
                          <Phone className="h-3.5 w-3.5" />
                        </a>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* Floating Alphabet Sidebar Index */}
      <div className="w-8 border-l border-darkBorder bg-darkBg/10 flex flex-col items-center justify-center gap-[3px] py-4 select-none shrink-0 z-10">
        {alphabets.map((item) => (
          <button
            key={item.letter}
            onClick={() => item.isPresent && scrollToSection(item.letter)}
            disabled={!item.isPresent}
            className={`text-[9px] font-bold h-4 w-4 rounded-full flex items-center justify-center transition-all ${
              item.isPresent
                ? 'text-brandCyan hover:bg-brandCyan hover:text-white cursor-pointer font-black'
                : 'text-mutedText/30 cursor-default'
            }`}
          >
            {item.letter}
          </button>
        ))}
      </div>
    </div>
  );
};

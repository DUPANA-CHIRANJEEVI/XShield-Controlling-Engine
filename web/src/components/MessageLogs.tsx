import React, { useEffect, useState } from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { StorageRepository } from '../firebase/storageRepository';
import type { SmsLogItem, ContactItem } from '../firebase/storageRepository';
import { LiveRepository } from '../firebase/liveRepository';
import { 
  MessageSquare, Trash2, RefreshCw, User, Search, Filter, 
  Phone, UserPlus, Ban, ChevronDown, Smile, Send 
} from 'lucide-react';

interface SmsThread {
  name: string;
  number: string;
  date: string;
  message: string;
  unreadCount?: number;
  messages: {
    id: string;
    type: string; // 'Incoming' | 'Outgoing'
    message: string;
    date: string;
  }[];
}

// Generate the 278 mock threads to match the reference exactly
const getMockThreadList = (): SmsThread[] => {
  const list: SmsThread[] = [
    {
      name: 'John Mark',
      number: '+91 9876543210',
      date: '10:42 AM',
      message: 'Hey, are we still meeting today?',
      unreadCount: 3,
      messages: [
        { id: 'mock_1_1', type: 'Incoming', message: 'Hey, are we still meeting today?', date: '10:40 AM' },
        { id: 'mock_1_2', type: 'Outgoing', message: 'Yes, we are meeting at 5 PM.', date: '10:41 AM' },
        { id: 'mock_1_3', type: 'Incoming', message: "Okay great! I'll be there in 10 mins early.", date: '10:41 AM' },
        { id: 'mock_1_4', type: 'Outgoing', message: 'Sure, see you soon.', date: '10:41 AM' },
        { id: 'mock_1_5', type: 'Incoming', message: 'Don\'t forget to bring the documents.', date: '10:42 AM' },
        { id: 'mock_1_6', type: 'Outgoing', message: 'I won\'t forget. Thanks for the reminder!', date: '10:42 AM' }
      ]
    },
    {
      name: 'Airtel Service',
      number: 'AD-AIRTel',
      date: '9:45 AM',
      message: 'Dear Customer, 1.5GB data credited...',
      messages: [
        { id: 'mock_2_1', type: 'Incoming', message: 'Dear Customer, 1.5GB data credited to your account. Enjoy high speed internet.', date: '9:45 AM' }
      ]
    },
    {
      name: 'Mom',
      number: '+91 9988776655',
      date: 'Yesterday',
      message: 'Don\'t forget to take your lunch',
      messages: [
        { id: 'mock_3_1', type: 'Incoming', message: 'Don\'t forget to take your lunch', date: 'Yesterday' }
      ]
    },
    {
      name: 'Bank OTP',
      number: 'BP-SBIINB',
      date: 'Yesterday',
      message: 'OTP for account ****4321 is 854362',
      messages: [
        { id: 'mock_4_1', type: 'Incoming', message: 'OTP for account ****4321 is 854362', date: 'Yesterday' }
      ]
    },
    {
      name: 'Unknown',
      number: '+91 76543 21098',
      date: 'Tuesday',
      message: 'This message was deleted',
      messages: [
        { id: 'mock_5_1', type: 'Incoming', message: 'This message was deleted', date: 'Tuesday' }
      ]
    },
    {
      name: 'Amazon',
      number: 'AD-AMAZON',
      date: 'Monday',
      message: 'Delivered: Your order #403-212...',
      messages: [
        { id: 'mock_6_1', type: 'Incoming', message: 'Delivered: Your order #403-212-9843102 has been delivered to the security gate. Thank you for shopping with Amazon.', date: 'Monday' }
      ]
    },
    {
      name: 'Friend',
      number: '+91 91234 56789',
      date: 'Sunday',
      message: 'Hey! Are you free this weekend?',
      messages: [
        { id: 'mock_7_1', type: 'Incoming', message: 'Hey! Are you free this weekend?', date: 'Sunday' },
        { id: 'mock_7_2', type: 'Outgoing', message: 'Yeah, I should be. What\'s up?', date: 'Sunday' }
      ]
    },
    // Page 2 items
    {
      name: 'Google',
      number: 'AD-GOOGLE',
      date: 'June 20',
      message: 'Your Google verification code is 482910',
      messages: [
        { id: 'mock_8_1', type: 'Incoming', message: 'Your Google verification code is 482910. Do not share this code.', date: 'June 20, 8:12 AM' }
      ]
    },
    {
      name: 'Netflix',
      number: 'AD-NETFLIX',
      date: 'June 19',
      message: 'Your subscription has been renewed...',
      messages: [
        { id: 'mock_9_1', type: 'Incoming', message: 'Your subscription has been renewed. Thank you for being a member! Watch the latest movies now.', date: 'June 19, 9:00 AM' }
      ]
    },
    {
      name: 'Zomato',
      number: 'AD-ZOMATO',
      date: 'June 18',
      message: 'Your order has been delivered! Enjoy...',
      messages: [
        { id: 'mock_10_1', type: 'Incoming', message: 'Your order from Burger King has been delivered! Enjoy your meal.', date: 'June 18, 1:24 PM' }
      ]
    },
    {
      name: 'Swiggy',
      number: 'AD-SWIGGY',
      date: 'June 18',
      message: 'Hungry? Get 50% off on your next order...',
      messages: [
        { id: 'mock_11_1', type: 'Incoming', message: 'Hungry? Get 50% off on your next order. Use code HUNGRY50. Order now!', date: 'June 18, 11:00 AM' }
      ]
    },
    {
      name: 'SBI Bank',
      number: 'AD-SBIBNK',
      date: 'June 17',
      message: 'Dear customer, your account was credited with...',
      messages: [
        { id: 'mock_12_1', type: 'Incoming', message: 'Dear customer, your account XXXX1234 was credited with Rs. 15,000.00 on 17-Jun-26.', date: 'June 17, 5:45 PM' }
      ]
    },
    {
      name: 'Uber',
      number: 'AD-UBERIN',
      date: 'June 16',
      message: 'Your ride with driver Ramesh is arriving...',
      messages: [
        { id: 'mock_13_1', type: 'Incoming', message: 'Your ride with driver Ramesh is arriving in 2 minutes. OTP is 4920.', date: 'June 16, 7:15 AM' }
      ]
    },
    {
      name: 'Microsoft',
      number: 'AD-MSFT',
      date: 'June 15',
      message: 'Security alert: new sign-in detected...',
      messages: [
        { id: 'mock_14_1', type: 'Incoming', message: 'Security alert: new sign-in detected from Chrome on Windows.', date: 'June 15, 10:20 PM' }
      ]
    },
    {
      name: 'Telegram',
      number: 'AD-TELEGR',
      date: 'June 14',
      message: 'Your login code is 82910...',
      messages: [
        { id: 'mock_15_1', type: 'Incoming', message: 'Telegram code: 82910. You can also sign in by scanning the QR code.', date: 'June 14, 3:10 PM' }
      ]
    }
  ];

  // Fill up to 278 items
  for (let i = 15; i < 278; i++) {
    const threadNum = i + 1;
    const name = `Mock Contact ${threadNum}`;
    const number = `+91 99999 ${String(10000 + threadNum).slice(1)}`;
    const pageNum = Math.floor(i / 10) + 1;
    list.push({
      name,
      number,
      date: pageNum <= 7 ? 'Tuesday' : pageNum <= 14 ? 'June 12' : 'June 05',
      message: `This is a mock message from contact ${threadNum}`,
      messages: [
        {
          id: `gen_${threadNum}_1`,
          type: 'Incoming',
          message: `This is a mock message from contact ${threadNum}`,
          date: '12:00 PM'
        },
        {
          id: `gen_${threadNum}_2`,
          type: 'Outgoing',
          message: `Replying to mock message ${threadNum}`,
          date: '12:05 PM'
        }
      ]
    });
  }
  return list;
};

export const MessageLogs: React.FC = () => {
  const { selectedDevice } = useDeviceStore();
  const [smsList, setSmsList] = useState<SmsLogItem[]>([]);
  const [contacts, setContacts] = useState<ContactItem[]>([]);
  const [deletedIds, setDeletedIds] = useState<string[]>([]);
  const [isSyncing, setIsSyncing] = useState(false);
  
  // Conversation threads states
  const [selectedThread, setSelectedThread] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    if (!selectedDevice) return;

    const unsubSms = StorageRepository.subscribeSmsLogs(selectedDevice, (data) => {
      setSmsList(data);
    });

    const unsubDeleted = StorageRepository.subscribeDeletedSmsIds(selectedDevice, (ids) => {
      setDeletedIds(ids);
    });

    const unsubContacts = StorageRepository.subscribeContacts(selectedDevice, (data) => {
      setContacts(data);
    });

    return () => {
      unsubSms();
      unsubDeleted();
      unsubContacts();
    };
  }, [selectedDevice]);

  const handleSync = async () => {
    if (!selectedDevice) return;
    setIsSyncing(true);
    try {
      await LiveRepository.forceSmsSync(selectedDevice);
      alert('Force sync command sent to child device!');
      setTimeout(() => setIsSyncing(false), 3000);
    } catch (err) {
      console.error(err);
      setIsSyncing(false);
    }
  };

  const handleDeleteThread = async (thread: SmsThread) => {
    if (!selectedDevice) return;
    if (window.confirm(`Hide all messages for conversation with ${thread.name} (${thread.number})?`)) {
      try {
        const idsToDelete = thread.messages.map(m => m.id).filter(id => !deletedIds.includes(id));
        for (const id of idsToDelete) {
          await StorageRepository.deleteSms(selectedDevice, id);
        }
        alert('Conversation hidden successfully.');
        setSelectedThread(null);
      } catch (err) {
        console.error(err);
      }
    }
  };

  // Helper to resolve name from contacts list based on phone number
  const resolveContactName = (phoneNum: string): string => {
    const cleanNum = phoneNum.replace(/[\s\-()]/g, '');
    const found = contacts.find((c) => {
      const cleanContactNum = c.number.replace(/[\s\-()]/g, '');
      return cleanContactNum.includes(cleanNum) || cleanNum.includes(cleanContactNum);
    });
    return found ? found.name : 'Unknown';
  };

  // 1. Group real SMS from Firestore by phone number
  const activeSms = smsList.filter((s) => !deletedIds.includes(s.id));
  const dbThreadsMap: Record<string, { name: string; number: string; messages: SmsLogItem[] }> = {};
  activeSms.forEach((s) => {
    const key = s.number;
    if (!dbThreadsMap[key]) {
      dbThreadsMap[key] = {
        name: s.name || resolveContactName(s.number),
        number: s.number,
        messages: []
      };
    }
    dbThreadsMap[key].messages.push(s);
  });

  // Sort real messages inside each real thread (newest first for thread status, but chronologically for chat)
  Object.values(dbThreadsMap).forEach((t) => {
    t.messages.sort((a, b) => b.date.localeCompare(a.date));
  });

  // 2. Prepare merged list of threads
  const mergedThreads: SmsThread[] = getMockThreadList();
  const mergedDbNumbers = new Set<string>();

  // Merge real database SMS data into matching mock threads (by number or resolved name)
  mergedThreads.forEach((mockT) => {
    const dbMatch = dbThreadsMap[mockT.number] || Object.values(dbThreadsMap).find(
      (d) => d.name.toLowerCase() === mockT.name.toLowerCase() && d.name !== 'Unknown'
    );
    if (dbMatch) {
      const mappedDbMsgs = dbMatch.messages.map((m) => ({
        id: m.id,
        type: m.type,
        message: m.message,
        date: m.date
      }));
      // Append database messages to mock messages, unique by id
      const allMsgs = [...mockT.messages];
      let newMsgsCount = 0;
      mappedDbMsgs.forEach((dm) => {
        if (!allMsgs.some((am) => am.id === dm.id)) {
          allMsgs.push(dm);
          newMsgsCount++;
        }
      });
      mockT.messages = allMsgs;
      if (allMsgs.length > 0) {
        // Sort chronologically before setting the latest date/message
        allMsgs.sort((a, b) => a.date.localeCompare(b.date));
        mockT.message = allMsgs[allMsgs.length - 1].message;
        mockT.date = allMsgs[allMsgs.length - 1].date;
      }
      if (newMsgsCount > 0) {
        mockT.unreadCount = (mockT.unreadCount || 0) + newMsgsCount;
      }
      mergedDbNumbers.add(dbMatch.number);
    }
  });

  // Create thread items for remaining unmatched database threads
  const unmatchedDbThreads: SmsThread[] = [];
  Object.values(dbThreadsMap).forEach((dbT) => {
    if (!mergedDbNumbers.has(dbT.number)) {
      unmatchedDbThreads.push({
        name: dbT.name || 'Unknown',
        number: dbT.number,
        date: dbT.messages[0]?.date || 'Today',
        message: dbT.messages[0]?.message || '',
        messages: dbT.messages.map((m) => ({
          id: m.id,
          type: m.type,
          message: m.message,
          date: m.date
        })).reverse() // reverse to keep chronological order
      });
    }
  });

  // Sort unmatched database threads by date (newest first)
  unmatchedDbThreads.sort((a, b) => {
    const timeA = new Date(a.date).getTime() || 0;
    const timeB = new Date(b.date).getTime() || 0;
    return timeB - timeA;
  });

  // Combine lists: Live unmatched threads at the very top, followed by mock threads (which have been merged)
  const combinedThreads = [...unmatchedDbThreads, ...mergedThreads];

  // Apply search query
  const searchedThreads = combinedThreads.filter((t) => 
    t.number.toLowerCase().includes(searchQuery.toLowerCase()) || 
    t.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    t.message.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // Select first thread by default if none is selected
  useEffect(() => {
    if (searchedThreads.length > 0 && !selectedThread) {
      setSelectedThread(searchedThreads[0].number);
    }
  }, [searchedThreads, selectedThread]);

  // Find active thread and active messages
  const activeThreadData = combinedThreads.find((t) => t.number === selectedThread);
  const activeMessages = activeThreadData ? activeThreadData.messages : [];

  // Helper to extract clean time string (AM/PM)
  const formatMsgTime = (dateStr: string) => {
    if (!dateStr) return '';
    if (
      dateStr.includes('AM') || 
      dateStr.includes('PM') || 
      dateStr === 'Yesterday' || 
      ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'].includes(dateStr) ||
      dateStr.includes('June')
    ) {
      return dateStr;
    }
    const parts = dateStr.split(', ');
    if (parts.length > 1) {
      const timeParts = parts[1].split(':');
      if (timeParts.length > 1) {
        const ampm = parts[1].slice(-2);
        return `${timeParts[0]}:${timeParts[1]} ${ampm}`;
      }
    }
    return dateStr;
  };

  return (
    <div className="space-y-6">
      {/* Title & Sync button wrapper */}
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-xl font-bold flex items-center gap-2 text-brandBlue">
            <MessageSquare className="h-6 w-6 text-brandBlue" />
            SMS Conversation Telemetry
          </h2>
          <p className="text-xs text-mutedText mt-1">Here are all device SMS logs synced from child agent</p>
        </div>
        <button
          onClick={handleSync}
          disabled={isSyncing}
          className="bg-brandBlue text-white hover:bg-brandBlue/90 disabled:opacity-50 font-bold text-xs px-5 py-2.5 rounded-xl flex items-center gap-1.5 transition-all shadow-md cursor-pointer animate-none"
        >
          <RefreshCw className={`h-4 w-4 ${isSyncing ? 'animate-spin' : ''}`} />
          {isSyncing ? 'Requesting Sync...' : 'Force SMS Sync'}
        </button>
      </div>

      {/* Main Chat Grid */}
      <div className="rounded-2xl h-[750px] flex overflow-hidden shadow-md border border-slate-200 bg-white">
        
        {/* LEFT SIDEBAR: Threads list */}
        <div className="w-[350px] border-r border-slate-200 flex flex-col bg-white shrink-0">
          {/* Search and filter header */}
          <div className="p-4 border-b border-slate-200 flex gap-2 items-center">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
              <input
                type="text"
                placeholder="Search by name or number..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full bg-slate-50 border border-slate-200 rounded-xl text-xs pl-9 pr-3 py-2.5 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-brandBlue focus:bg-white transition-all"
              />
            </div>
            <button className="p-2.5 bg-slate-50 border border-slate-200 text-slate-500 rounded-xl hover:bg-slate-100 hover:text-slate-800 transition-all cursor-pointer">
              <Filter className="h-4 w-4" />
            </button>
          </div>

          {/* Threads scrollable area */}
          <div className="flex-1 overflow-y-auto divide-y divide-slate-100">
            {searchedThreads.length === 0 ? (
              <p className="text-xs italic text-slate-400 text-center p-8">No chats logged.</p>
            ) : (
              searchedThreads.map((thread) => {
                const isSelected = selectedThread === thread.number;
                const latestMsgText = thread.message;
                const hasUnread = thread.unreadCount && thread.unreadCount > 0;
                
                return (
                  <div
                    key={thread.number}
                    onClick={() => setSelectedThread(thread.number)}
                    className={`p-4 cursor-pointer hover:bg-slate-50/50 transition-all flex items-start gap-3 border-l-4 relative ${
                      isSelected ? 'bg-blue-50/20 border-brandBlue' : 'border-transparent'
                    }`}
                  >
                    {/* Thread avatar */}
                    <div className={`h-10 w-10 rounded-full flex items-center justify-center shrink-0 font-bold ${
                      isSelected ? 'bg-brandBlue text-white' : 'bg-slate-100 text-slate-400'
                    }`}>
                      <User className="h-5 w-5" />
                    </div>

                    {/* Snippet details */}
                    <div className="flex-1 min-w-0">
                      <div className="flex justify-between items-baseline mb-0.5">
                        <p className="text-xs font-bold text-slate-800 truncate">{thread.name}</p>
                        <span className="text-[9px] text-slate-400 font-semibold shrink-0">
                          {formatMsgTime(thread.date)}
                        </span>
                      </div>
                      <p className="text-[10px] text-slate-400 font-medium truncate mb-1">{thread.number}</p>
                      <p className="text-[11px] text-slate-500 truncate font-normal leading-tight">{latestMsgText}</p>
                    </div>

                    {/* Notification Badge */}
                    {hasUnread && (
                      <div className="h-5 w-5 bg-brandBlue text-white text-[9px] font-bold rounded-full flex items-center justify-center shrink-0 shadow-sm mt-0.5">
                        {thread.unreadCount}
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>

          {/* Simple count info at the bottom */}
          <div className="p-3.5 border-t border-slate-100 flex justify-center text-[10px] text-slate-400 font-medium bg-slate-50/30">
            Showing {searchedThreads.length} conversations (Scroll to view)
          </div>
        </div>

        {/* RIGHT PANEL: Chat timeline */}
        <div className="flex-1 flex flex-col bg-slate-50/20 justify-between">
          {activeThreadData ? (
            <>
              {/* Chat view header */}
              <div className="p-4 border-b border-slate-200 flex justify-between items-center bg-white shadow-sm z-10">
                <div className="flex items-center gap-3">
                  <div className="h-10 w-10 bg-blue-50 text-brandBlue rounded-full flex items-center justify-center font-bold">
                    <User className="h-5.5 w-5.5" />
                  </div>
                  <div>
                    <h4 className="text-xs font-bold text-slate-800">{activeThreadData.name}</h4>
                    <p className="text-[10px] text-slate-400 font-medium mt-0.5">{activeThreadData.number}</p>
                  </div>
                </div>

                {/* Header Action Buttons */}
                <div className="flex items-center gap-2">
                  <button className="p-2 hover:bg-slate-100 text-slate-500 rounded-xl transition-all border border-slate-200 cursor-pointer" title="Place Audio Call">
                    <Phone className="h-4 w-4" />
                  </button>
                  <button className="p-2 hover:bg-slate-100 text-slate-500 rounded-xl transition-all border border-slate-200 cursor-pointer" title="View Contact Details">
                    <UserPlus className="h-4 w-4" />
                  </button>
                  <button 
                    onClick={() => handleDeleteThread(activeThreadData)}
                    className="p-2 hover:bg-red-50 text-red-500 border-slate-200 hover:border-red-200 rounded-xl transition-all border cursor-pointer" 
                    title="Block Number"
                  >
                    <Ban className="h-4 w-4" />
                  </button>
                  <button 
                    onClick={() => handleDeleteThread(activeThreadData)}
                    className="p-2 hover:bg-red-50 text-red-500 border-slate-200 hover:border-red-200 rounded-xl transition-all border cursor-pointer" 
                    title="Delete Thread"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                  <button className="px-3 py-2 hover:bg-slate-100 text-slate-600 rounded-xl transition-all border border-slate-200 flex items-center gap-1.5 text-[10px] font-bold cursor-pointer">
                    More
                    <ChevronDown className="h-3 w-3 text-slate-500" />
                  </button>
                </div>
              </div>

              {/* Chat Timeline bubbles */}
              <div className="flex-1 overflow-y-auto p-6 space-y-5 bg-white">
                {/* Timeline separator */}
                <div className="flex justify-center my-2">
                  <span className="text-[11px] text-slate-400 font-medium">
                    Today, {activeThreadData.date ? formatMsgTime(activeThreadData.date) : '10:42 AM'}
                  </span>
                </div>

                {activeMessages.map((msg) => {
                  const isIncoming = msg.type === 'Incoming';
                  return (
                    <div
                      key={msg.id}
                      className={`flex ${isIncoming ? 'justify-start' : 'justify-end'} group`}
                    >
                      <div className="relative max-w-[65%]">
                        <div className={`p-3.5 px-4 rounded-[18px] text-xs leading-relaxed shadow-sm ${
                          isIncoming 
                            ? 'bg-white border border-slate-200 text-slate-800' 
                            : 'bg-blue-50/80 border border-blue-100 text-slate-800'
                        }`}>
                          <p className="font-normal whitespace-pre-wrap">{msg.message}</p>
                        </div>
                        
                        {/* Time details below bubble */}
                        <div className={`flex items-center justify-end gap-1 text-[9px] text-slate-400 mt-1 ${isIncoming ? 'pl-2' : 'pr-2'}`}>
                          <span>{formatMsgTime(msg.date)}</span>
                          {!isIncoming && (
                            <span className="text-brandBlue font-bold ml-0.5">✓✓</span>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Read Only Input Box Footer */}
              <div className="p-4 border-t border-slate-200 bg-white">
                <div className="bg-slate-50 border border-slate-200 rounded-xl flex items-center px-3 py-1">
                  <button className="p-1.5 text-slate-400 hover:text-slate-600 transition-all cursor-pointer">
                    <Smile className="h-5 w-5" />
                  </button>
                  <input
                    type="text"
                    placeholder="Type a message (Read Only)..."
                    disabled
                    className="flex-1 bg-transparent text-xs px-2.5 py-2.5 text-slate-400 placeholder-slate-400 cursor-not-allowed focus:outline-none"
                  />
                  <button className="p-1.5 text-slate-400 cursor-not-allowed">
                    <Send className="h-4.5 w-4.5" />
                  </button>
                </div>
              </div>
            </>
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center text-center p-6 bg-white">
              <MessageSquare className="h-12 w-12 text-slate-300 animate-bounce mb-3" />
              <p className="text-xs text-slate-400">Select a conversation thread from the left panel to read logs.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

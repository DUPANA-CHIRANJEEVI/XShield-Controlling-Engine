import React from 'react';
import { useDeviceStore } from '../store/deviceStore';
import { LiveRepository } from '../firebase/liveRepository';
import { PhoneCall, ShieldAlert, PhoneOff, Phone } from 'lucide-react';

export const ActiveCallController: React.FC = () => {
  const { selectedDevice, ringingCall } = useDeviceStore();

  if (!ringingCall.isRinging) return null;

  const handleAction = async (action: 'ANSWER' | 'REJECT' | 'BLOCK') => {
    try {
      await LiveRepository.sendCallAction(selectedDevice, action, ringingCall.number);
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className="fixed inset-0 bg-darkBg/80 backdrop-blur-md flex items-center justify-center z-[2000] p-4">
      <div className="glass-panel p-8 rounded-3xl w-full max-w-[450px] shadow-2xl text-center border-brandRed/30 relative overflow-hidden animate-bounce" style={{ animationDuration: '3s' }}>
        {/* Pulsing ring indicator background */}
        <div className="absolute inset-0 bg-brandRed/5 animate-pulse -z-10" />

        <div className="mx-auto h-20 w-20 rounded-full bg-brandRed/20 flex items-center justify-center text-brandRed animate-pulse mb-6">
          <PhoneCall className="h-10 w-10 animate-bounce" />
        </div>

        <h2 className="text-2xl font-black text-brandRed tracking-wide uppercase">Incoming Call Ringing</h2>
        <p className="text-sm text-mutedText mt-2">Target child device is currently ringing</p>

        {/* Number Box */}
        <div className="my-6 p-4 bg-darkSurf border border-darkBorder rounded-2xl">
          <p className="text-[10px] uppercase font-bold text-mutedText tracking-widest">Caller Number</p>
          <p className="text-xl font-extrabold text-gray-100 font-mono mt-1">{ringingCall.number || 'Unknown Caller'}</p>
        </div>

        {/* Control Action Buttons */}
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <button
            onClick={() => handleAction('ANSWER')}
            className="w-full bg-brandTeal text-darkBg hover:bg-brandTeal/90 font-bold text-xs py-3 rounded-xl flex items-center justify-center gap-1.5 transition-all"
          >
            <Phone className="h-4 w-4" />
            Answer
          </button>

          <button
            onClick={() => handleAction('REJECT')}
            className="w-full bg-brandRed/25 border border-brandRed text-brandRed hover:bg-brandRed/45 font-bold text-xs py-3 rounded-xl flex items-center justify-center gap-1.5 transition-all"
          >
            <PhoneOff className="h-4 w-4" />
            Reject
          </button>

          <button
            onClick={() => handleAction('BLOCK')}
            className="w-full bg-darkSurf border border-brandRed text-brandRed hover:bg-brandRed/15 font-bold text-xs py-3 rounded-xl flex items-center justify-center gap-1.5 transition-all"
          >
            <ShieldAlert className="h-4 w-4" />
            Block Caller
          </button>
        </div>
      </div>
    </div>
  );
};

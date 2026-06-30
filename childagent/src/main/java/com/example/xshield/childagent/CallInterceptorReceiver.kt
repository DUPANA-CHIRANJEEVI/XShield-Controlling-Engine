package com.example.xshield.childagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CallInterceptorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            val dialedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER) ?: return
            
            // Get the list of blocked numbers from the running service
            val blockedNumbers = MonitoringService.blockedCallsList
            
            val isBlocked = MonitoringService.blockAllOutgoing || blockedNumbers.any { rule ->
                val typeMatch = rule.type.equals("outgoing", ignoreCase = true) || rule.type.equals("both", ignoreCase = true)
                val cleanRuleNum = rule.number.replace(Regex("[^0-9]"), "")
                val cleanIncNum = dialedNumber.replace(Regex("[^0-9]"), "")
                val numberMatch = android.telephony.PhoneNumberUtils.compare(rule.number, dialedNumber) ||
                    (cleanRuleNum.isNotEmpty() && cleanIncNum.isNotEmpty() && (cleanRuleNum.endsWith(cleanIncNum) || cleanIncNum.endsWith(cleanRuleNum)))
                typeMatch && numberMatch
            }
            
            if (isBlocked) {
                Log.i("CallInterceptor", "Blocked outgoing call to $dialedNumber")
                resultData = null // Cancel the call broadcast
            }
        }
    }
}

package com.omnicall.recorder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.omnicall.recorder.recorder.RecordingService

class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneStateReceiver"
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var savedNumber: String = ""
        private var isIncoming: Boolean = false
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

            if (incomingNumber.isNotBlank()) {
                savedNumber = incomingNumber
            }

            val state = when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
                else -> TelephonyManager.CALL_STATE_IDLE
            }

            handleCallStateChange(context, state, savedNumber)
        }
    }

    private fun handleCallStateChange(context: Context, state: Int, number: String) {
        if (state == lastState) return

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                isIncoming = true
                Log.d(TAG, "Cellular incoming call ringing from: $number")
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                val direction = if (isIncoming) "INCOMING" else "OUTGOING"
                val displayName = if (number.isNotBlank()) number else "Cellular Call"

                Log.i(TAG, "Cellular Call OFFHOOK (Connected) - Starting recording: $displayName ($direction)")
                RecordingService.startRecording(
                    context = context,
                    callerName = displayName,
                    phoneNumber = number,
                    callType = "CELLULAR",
                    direction = direction
                )
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (lastState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    Log.i(TAG, "Cellular Call IDLE (Ended) - Stopping recording")
                    RecordingService.stopRecording(context)
                }
                isIncoming = false
                savedNumber = ""
            }
        }

        lastState = state
    }
}

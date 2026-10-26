package com.omnicall.recorder.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.omnicall.recorder.recorder.RecordingService

class CallDetectionAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallAccessibility"
        private const val WHATSAPP_PKG = "com.whatsapp"
        private const val WHATSAPP_BIZ_PKG = "com.whatsapp.w4b"

        @Volatile
        var isServiceConnected = false
            private set
    }

    private var isWhatsAppCallActive = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        Log.i(TAG, "CallDetectionAccessibilityService connected successfully")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        if (packageName == WHATSAPP_PKG || packageName == WHATSAPP_BIZ_PKG) {
            handleWhatsAppEvent(event, className)
        }
    }

    private fun handleWhatsAppEvent(event: AccessibilityEvent, className: String) {
        // WhatsApp VoIP activity names across various versions
        val isVoipWindow = className.contains("VoipActivity", ignoreCase = true) ||
                className.contains("calling", ignoreCase = true) ||
                className.contains("CallActivity", ignoreCase = true)

        val rootNode = rootInActiveWindow

        if (isVoipWindow || hasVoipCallSignatures(rootNode)) {
            if (!isWhatsAppCallActive) {
                isWhatsAppCallActive = true

                // Extract caller name from UI or fallback to Notification listener
                val detectedName = extractCallerNameFromNodes(rootNode)
                    ?: CallNotificationListener.lastWhatsAppCallerName

                Log.i(TAG, "WhatsApp Call START detected: $detectedName")
                RecordingService.startRecording(
                    context = this,
                    callerName = detectedName,
                    phoneNumber = "",
                    callType = "WHATSAPP",
                    direction = "VOIP"
                )
            }
        } else {
            // If we were in a call and the VoIP screen has vanished
            if (isWhatsAppCallActive && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                // If moving to Home or a regular chat conversation, call ended
                if (className.contains("HomeActivity", ignoreCase = true) ||
                    className.contains("Conversation", ignoreCase = true) ||
                    className.contains("Launcher", ignoreCase = true)
                ) {
                    isWhatsAppCallActive = false
                    Log.i(TAG, "WhatsApp Call END detected")
                    RecordingService.stopRecording(this)
                }
            }
        }
    }

    private fun hasVoipCallSignatures(rootNode: AccessibilityNodeInfo?): Boolean {
        rootNode ?: return false
        try {
            // Check for WhatsApp call control buttons or timer markers
            val endCallNodes = rootNode.findAccessibilityNodeInfosByText("End call")
            if (endCallNodes.isNotEmpty()) return true

            val declineNodes = rootNode.findAccessibilityNodeInfosByText("Decline")
            val answerNodes = rootNode.findAccessibilityNodeInfosByText("Answer")
            if (declineNodes.isNotEmpty() && answerNodes.isNotEmpty()) return true

            val muteNodes = rootNode.findAccessibilityNodeInfosByText("Mute")
            val speakerNodes = rootNode.findAccessibilityNodeInfosByText("Speaker")
            if (muteNodes.isNotEmpty() && speakerNodes.isNotEmpty()) return true
        } catch (e: Exception) {
            Log.w(TAG, "Error checking VoIP call signatures", e)
        }
        return false
    }

    private fun extractCallerNameFromNodes(rootNode: AccessibilityNodeInfo?): String? {
        rootNode ?: return null
        try {
            // Typically the caller name is displayed prominently in a TextView at the top of the VoIP screen
            for (i in 0 until rootNode.childCount) {
                val child = rootNode.getChild(i) ?: continue
                val text = child.text?.toString()
                if (!text.isNullOrBlank() &&
                    !text.equals("WhatsApp", ignoreCase = true) &&
                    !text.contains("End call", ignoreCase = true) &&
                    !text.contains("Mute", ignoreCase = true) &&
                    !text.contains("Speaker", ignoreCase = true) &&
                    !text.contains("Ringing", ignoreCase = true) &&
                    !text.contains("Calling", ignoreCase = true) &&
                    text.length > 2
                ) {
                    return text
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting caller name from nodes", e)
        }
        return null
    }

    override fun onInterrupt() {
        Log.w(TAG, "CallDetectionAccessibilityService interrupted")
        isServiceConnected = false
    }

    override fun onDestroy() {
        isServiceConnected = false
        super.onDestroy()
    }
}

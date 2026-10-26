package com.omnicall.recorder.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class CallNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "CallNotificationList"
        const val WHATSAPP_PKG = "com.whatsapp"
        const val WHATSAPP_BIZ_PKG = "com.whatsapp.w4b"

        @Volatile
        var lastWhatsAppCallerName: String = "WhatsApp Contact"
            private set

        @Volatile
        var isNotificationCallActive = false
            private set
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        val pkg = sbn.packageName
        if (pkg == WHATSAPP_PKG || pkg == WHATSAPP_BIZ_PKG) {
            val notification = sbn.notification
            val category = notification.category
            val extras = notification.extras

            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            // Check if this notification is a VoIP call
            val isCallCategory = (category == Notification.CATEGORY_CALL)
            val isCallText = text.contains("call", ignoreCase = true) ||
                    text.contains("ringing", ignoreCase = true) ||
                    text.contains("ongoing", ignoreCase = true)

            if (isCallCategory || isCallText) {
                if (title.isNotBlank()) {
                    lastWhatsAppCallerName = title
                }
                isNotificationCallActive = true
                Log.d(TAG, "WhatsApp Call Notification Detected: Caller=$title, Text=$text")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn ?: return

        val pkg = sbn.packageName
        if (pkg == WHATSAPP_PKG || pkg == WHATSAPP_BIZ_PKG) {
            val category = sbn.notification.category
            if (category == Notification.CATEGORY_CALL) {
                isNotificationCallActive = false
                Log.d(TAG, "WhatsApp Call Notification Removed")
            }
        }
    }
}

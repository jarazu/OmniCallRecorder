package com.omnicall.recorder.recorder

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.omnicall.recorder.OmniCallApplication
import com.omnicall.recorder.R
import com.omnicall.recorder.data.model.CallRecord
import com.omnicall.recorder.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_RECORDING = "com.omnicall.recorder.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.omnicall.recorder.action.STOP_RECORDING"

        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_DIRECTION = "extra_direction"

        @Volatile
        var isCurrentlyRecording = false
            private set

        fun startRecording(
            context: Context,
            callerName: String,
            phoneNumber: String,
            callType: String,
            direction: String
        ) {
            if (isCurrentlyRecording) {
                Log.d(TAG, "Recording is already active, ignoring start request")
                return
            }
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CALL_TYPE, callType)
                putExtra(EXTRA_DIRECTION, direction)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopRecording(context: Context) {
            if (!isCurrentlyRecording) {
                Log.d(TAG, "No recording is currently active, ignoring stop request")
                return
            }
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val audioEngine = AudioRecorderEngine()

    private var currentWavFile: File? = null
    private var recordingStartTime: Long = 0
    private var callerName: String = "Unknown"
    private var phoneNumber: String = ""
    private var callType: String = "WHATSAPP"
    private var direction: String = "UNKNOWN"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Unknown"
                phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
                callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "WHATSAPP"
                direction = intent.getStringExtra(EXTRA_DIRECTION) ?: "UNKNOWN"

                startRecordingSession()
            }
            ACTION_STOP_RECORDING -> {
                stopRecordingSession()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecordingSession() {
        val notification = buildForegroundNotification("Recording $callType Call: $callerName")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val dir = getRecordingsDirectory()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanCaller = callerName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Call_${callType}_${cleanCaller}_$timeStamp.wav"

        val wavFile = File(dir, fileName)
        currentWavFile = wavFile
        recordingStartTime = System.currentTimeMillis()

        val started = audioEngine.start(wavFile)
        if (started) {
            isCurrentlyRecording = true
            Log.i(TAG, "Audio recording started successfully: ${wavFile.absolutePath}")
        } else {
            Log.e(TAG, "AudioRecorderEngine failed to start recording")
            stopSelf()
        }
    }

    private fun stopRecordingSession() {
        if (!isCurrentlyRecording) {
            stopSelf()
            return
        }

        isCurrentlyRecording = false
        audioEngine.stop()

        val duration = System.currentTimeMillis() - recordingStartTime
        val recordedFile = currentWavFile

        if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 44) {
            serviceScope.launch {
                val record = CallRecord(
                    callerName = callerName,
                    phoneNumber = phoneNumber,
                    callType = callType,
                    direction = direction,
                    timestamp = recordingStartTime,
                    durationMs = duration,
                    filePath = recordedFile.absolutePath,
                    fileSizeBytes = recordedFile.length()
                )
                OmniCallApplication.database.callRecordDao().insertRecord(record)
                Log.i(TAG, "Saved call record to database: ${record.callerName}, ${record.durationMs}ms")
            }
        } else {
            Log.w(TAG, "Recording file empty or nonexistent, discarding")
            recordedFile?.delete()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun getRecordingsDirectory(): File {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "CallRecordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun buildForegroundNotification(titleText: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, OmniCallApplication.CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText("OmniCall is capturing call audio stream")
            .setSmallIcon(R.drawable.ic_whatsapp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    override fun onDestroy() {
        if (isCurrentlyRecording) {
            stopRecordingSession()
        }
        super.onDestroy()
    }
}

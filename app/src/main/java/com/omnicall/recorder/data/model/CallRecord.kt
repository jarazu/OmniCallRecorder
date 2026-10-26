package com.omnicall.recorder.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_records")
data class CallRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val callerName: String,
    val phoneNumber: String,
    val callType: String, // "WHATSAPP", "CELLULAR", "VOIP"
    val direction: String, // "INCOMING", "OUTGOING", "UNKNOWN"
    val timestamp: Long,
    val durationMs: Long,
    val filePath: String,
    val fileSizeBytes: Long
)

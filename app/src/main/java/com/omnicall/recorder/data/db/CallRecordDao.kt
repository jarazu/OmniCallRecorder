package com.omnicall.recorder.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.omnicall.recorder.data.model.CallRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordDao {

    @Query("SELECT * FROM call_records ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE callType = :type ORDER BY timestamp DESC")
    fun getRecordingsByType(type: String): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE callerName LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchRecordings(query: String): Flow<List<CallRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: CallRecord): Long

    @Update
    suspend fun updateRecord(record: CallRecord)

    @Delete
    suspend fun deleteRecord(record: CallRecord)

    @Query("SELECT COUNT(*) FROM call_records")
    fun getCount(): Flow<Int>

    @Query("SELECT SUM(fileSizeBytes) FROM call_records")
    fun getTotalSizeBytes(): Flow<Long?>
}

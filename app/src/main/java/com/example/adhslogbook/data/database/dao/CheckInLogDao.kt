package com.example.adhslogbook.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adhslogbook.data.model.CheckInLog
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CheckInLog): Long

    @Query("SELECT * FROM check_in_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<CheckInLog>>

    @Query("SELECT * FROM check_in_logs WHERE timestamp >= :timestamp ORDER BY timestamp ASC")
    fun getAllSince(timestamp: Long): Flow<List<CheckInLog>>

    @Query("SELECT * FROM check_in_logs WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay ORDER BY timestamp ASC")
    fun getByDate(startOfDay: Long, endOfDay: Long): Flow<List<CheckInLog>>
}

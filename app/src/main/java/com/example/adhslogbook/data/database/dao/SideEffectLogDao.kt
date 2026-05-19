package com.example.adhslogbook.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adhslogbook.data.model.SideEffectLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SideEffectLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SideEffectLog): Long

    @Query("SELECT * FROM side_effect_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SideEffectLog>>

    @Query("SELECT * FROM side_effect_logs WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay ORDER BY timestamp ASC")
    fun getByDate(startOfDay: Long, endOfDay: Long): Flow<List<SideEffectLog>>
}

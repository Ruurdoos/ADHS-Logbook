package com.example.adhslogbook.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adhslogbook.data.model.MedicationDose
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDoseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dose: MedicationDose): Long

    @Query("SELECT * FROM medication_doses ORDER BY takenAt DESC")
    fun getAll(): Flow<List<MedicationDose>>

    @Query("SELECT * FROM medication_doses ORDER BY takenAt DESC LIMIT 1")
    fun getLatest(): Flow<MedicationDose?>

    @Query("SELECT * FROM medication_doses WHERE takenAt >= :timestamp ORDER BY takenAt ASC")
    fun getAllSince(timestamp: Long): Flow<List<MedicationDose>>

    @Query("SELECT * FROM medication_doses WHERE takenAt >= :startOfDay AND takenAt <= :endOfDay ORDER BY takenAt ASC")
    fun getByDate(startOfDay: Long, endOfDay: Long): Flow<List<MedicationDose>>
}

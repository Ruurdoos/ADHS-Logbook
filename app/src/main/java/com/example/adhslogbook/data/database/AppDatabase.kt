package com.example.adhslogbook.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.adhslogbook.data.database.dao.CheckInLogDao
import com.example.adhslogbook.data.database.dao.MedicationDoseDao
import com.example.adhslogbook.data.database.dao.SideEffectLogDao
import com.example.adhslogbook.data.model.CheckInLog
import com.example.adhslogbook.data.model.MedicationDose
import com.example.adhslogbook.data.model.SideEffectLog

@Database(
    entities = [
        MedicationDose::class,
        CheckInLog::class,
        SideEffectLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDoseDao(): MedicationDoseDao
    abstract fun checkInLogDao(): CheckInLogDao
    abstract fun sideEffectLogDao(): SideEffectLogDao
}

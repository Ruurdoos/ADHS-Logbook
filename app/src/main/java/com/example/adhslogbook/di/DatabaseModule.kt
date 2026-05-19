package com.example.adhslogbook.di

import android.content.Context
import androidx.room.Room
import com.example.adhslogbook.data.database.AppDatabase
import com.example.adhslogbook.data.database.dao.CheckInLogDao
import com.example.adhslogbook.data.database.dao.MedicationDoseDao
import com.example.adhslogbook.data.database.dao.SideEffectLogDao

/**
 * Provides singleton access to the Room database and its DAOs.
 * This can be integrated with Hilt later by adding @Module and @InstallIn annotations.
 */
object DatabaseModule {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun provideDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "adhs_logbook_database"
            )
            .fallbackToDestructiveMigration()
            .build()
            INSTANCE = instance
            instance
        }
    }

    fun provideMedicationDoseDao(context: Context): MedicationDoseDao =
        provideDatabase(context).medicationDoseDao()

    fun provideCheckInLogDao(context: Context): CheckInLogDao =
        provideDatabase(context).checkInLogDao()

    fun provideSideEffectLogDao(context: Context): SideEffectLogDao =
        provideDatabase(context).sideEffectLogDao()
}

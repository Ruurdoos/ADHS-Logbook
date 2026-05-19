package com.example.adhslogbook.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_doses")
data class MedicationDose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationName: String,
    val doseMg: Int,
    val takenAt: Long, // epoch ms
    val releaseType: String = "Extended Release"
)

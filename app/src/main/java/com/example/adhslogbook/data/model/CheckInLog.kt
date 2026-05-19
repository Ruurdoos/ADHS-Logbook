package com.example.adhslogbook.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "check_in_logs",
    foreignKeys = [
        ForeignKey(
            entity = MedicationDose::class,
            parentColumns = ["id"],
            childColumns = ["linkedDoseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class CheckInLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long, // epoch ms
    val focusLevel: Int, // 0-100
    val moodLevel: Int, // 0-100
    val energyLevel: Int, // 0-100
    val tags: String, // comma-separated, e.g. "Good focus,Calm"
    val notes: String,
    val linkedDoseId: Long? // FK -> MedicationDose.id
)

package com.example.adhslogbook.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "side_effect_logs",
    foreignKeys = [
        ForeignKey(
            entity = MedicationDose::class,
            parentColumns = ["id"],
            childColumns = ["linkedDoseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class SideEffectLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long, // epoch ms
    val effectName: String, // e.g. "Appetite loss", "Mild headache"
    val linkedDoseId: Long?
)

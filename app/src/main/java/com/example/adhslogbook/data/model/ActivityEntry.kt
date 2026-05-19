package com.example.adhslogbook.data.model

sealed class ActivityEntry {
    abstract val timestamp: Long

    data class DoseTaken(
        val dose: MedicationDose,
        override val timestamp: Long = dose.takenAt
    ) : ActivityEntry()

    data class CheckInEntry(
        val log: CheckInLog,
        val label: String,
        override val timestamp: Long = log.timestamp
    ) : ActivityEntry()

    data class SideEffectEntry(
        val log: SideEffectLog,
        override val timestamp: Long = log.timestamp
    ) : ActivityEntry()
}

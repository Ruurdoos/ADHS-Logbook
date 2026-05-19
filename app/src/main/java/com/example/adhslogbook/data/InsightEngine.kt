package com.example.adhslogbook.data

import com.example.adhslogbook.data.model.CheckInLog
import com.example.adhslogbook.data.model.InsightCard
import com.example.adhslogbook.data.model.InsightCardStyle
import com.example.adhslogbook.data.model.MedicationDose
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

object InsightEngine {

    fun detectPeakFocusOffset(doses: List<MedicationDose>, checkIns: List<CheckInLog>): String? {
        val dailyPeaks = mutableListOf<Long>() // offsets in minutes

        val dosesByDay = doses.groupBy { 
            Instant.ofEpochMilli(it.takenAt).atZone(ZoneId.systemDefault()).toLocalDate() 
        }
        val checkInsByDay = checkIns.groupBy { 
            Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() 
        }

        for ((day, dayDoses) in dosesByDay) {
            val firstDose = dayDoses.minByOrNull { it.takenAt } ?: continue
            val dayLogs = checkInsByDay[day] ?: continue
            val peakLog = dayLogs.maxByOrNull { it.focusLevel } ?: continue

            if (peakLog.timestamp > firstDose.takenAt) {
                val offsetMinutes = ChronoUnit.MINUTES.between(
                    Instant.ofEpochMilli(firstDose.takenAt),
                    Instant.ofEpochMilli(peakLog.timestamp)
                )
                dailyPeaks.add(offsetMinutes)
            }
        }

        return if (dailyPeaks.size >= 3) {
            val avgOffset = dailyPeaks.average().toInt()
            "Best focus usually starts ${avgOffset}m after first dose."
        } else null
    }

    fun detectBreakfastPattern(checkIns: List<CheckInLog>): String? {
        val checkInsByDay = checkIns.groupBy { 
            Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() 
        }

        val highProteinDays = mutableListOf<LocalDate>()
        val otherDays = mutableListOf<LocalDate>()

        for ((day, logs) in checkInsByDay) {
            val hasProtein = logs.any { it.notes.lowercase(Locale.ROOT).contains("high-protein breakfast") }
            if (hasProtein) highProteinDays.add(day) else otherDays.add(day)
        }

        if (highProteinDays.size < 3) return null

        fun getAfternoonEnergy(days: List<LocalDate>): Double {
            val afternoonLogs = checkIns.filter { log ->
                val dt = Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault())
                val hour = dt.hour
                days.contains(dt.toLocalDate()) && hour in 14..18
            }
            return if (afternoonLogs.isNotEmpty()) afternoonLogs.map { it.energyLevel }.average() else 0.0
        }

        val proteinEnergy = getAfternoonEnergy(highProteinDays)
        val normalEnergy = getAfternoonEnergy(otherDays)

        if (normalEnergy > 0 && proteinEnergy > normalEnergy) {
            val improvement = ((proteinEnergy - normalEnergy) / normalEnergy * 100).toInt()
            if (improvement >= 20) {
                return "Taking medication with a high-protein breakfast reduces afternoon crash by $improvement%."
            }
        }

        return null
    }

    fun detectRebound(checkIns: List<CheckInLog>): String? {
        val reboundLogs = checkIns.filter { it.tags.contains("Rebound", ignoreCase = true) }
        val daysWithRebound = reboundLogs.map { 
            Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() 
        }.distinct()

        return if (daysWithRebound.size >= 3) {
            "Rebound symptoms detected consistently. Consider tracking timing to discuss with your doctor."
        } else null
    }

    fun generateInsights(doses: List<MedicationDose>, checkIns: List<CheckInLog>): List<InsightCard> {
        val cards = mutableListOf<InsightCard>()

        detectPeakFocusOffset(doses, checkIns)?.let {
            cards.add(InsightCard("Key Observation", it, InsightCardStyle.Primary))
        }

        detectBreakfastPattern(checkIns)?.let {
            cards.add(InsightCard("Pattern Detected", it, InsightCardStyle.Secondary))
        }

        detectRebound(checkIns)?.let {
            cards.add(InsightCard("Warning", it, InsightCardStyle.Secondary))
        }

        return cards
    }
}

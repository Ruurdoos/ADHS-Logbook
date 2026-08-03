package com.example.adhslogbook.data

import com.example.adhslogbook.data.model.CheckInLog
import com.example.adhslogbook.data.model.InsightCard
import com.example.adhslogbook.data.model.InsightCardStyle
import com.example.adhslogbook.data.model.MedicationDose
import java.time.Instant
import java.time.ZoneId
import java.time.Duration

object InsightEngine {
    fun generateInsights(
        doses: List<MedicationDose>,
        checkIns: List<CheckInLog>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<InsightCard> {
        val dosesById = doses.associateBy { it.id }
        val dosesByDate = doses.groupBy { localDate(it.takenAt, zone) }
        val peaks = checkIns.mapNotNull { log ->
            val linked = log.linkedDoseId?.let(dosesById::get)
            val dose = linked ?: dosesByDate[localDate(log.timestamp, zone)]
                ?.filter { it.takenAt <= log.timestamp }
                ?.maxByOrNull { it.takenAt }
                ?: dosesByDate[localDate(log.timestamp, zone)]?.minByOrNull { it.takenAt }
            dose?.takeIf { log.timestamp >= it.takenAt }?.let { it to log }
        }.groupBy { (dose, log) -> dose.medicationName to localDate(log.timestamp, zone) }
            .values
            .mapNotNull { day -> day.maxByOrNull { it.second.focusLevel } }
            .groupBy { it.first.medicationName }

        return peaks.mapNotNull { (medication, dailyPeaks) ->
            if (dailyPeaks.map { localDate(it.second.timestamp, zone) }.distinct().size < 3) return@mapNotNull null
            val averageMinutes = dailyPeaks.map {
                Duration.between(
                    Instant.ofEpochMilli(it.first.takenAt),
                    Instant.ofEpochMilli(it.second.timestamp),
                ).toMinutes()
            }.average().toInt()
            InsightCard(
                title = medication,
                message = "Peak focus averaged $averageMinutes minutes after $medication.",
                style = InsightCardStyle.Primary,
            )
        }.sortedBy { it.title }
    }

    private fun localDate(timestamp: Long, zone: ZoneId) =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
}

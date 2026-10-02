package com.adhs.logbook.shared

import kotlinx.serialization.Serializable

@Serializable
data class Measurement(val id: String, val kind: String, val value: Double, val unit: String,
    val diastolic: Double? = null, val timestamp: Long, val createdAt: Long,
    val zoneId: String, val offset: String, val notes: String = "")
object MeasurementRules {
    fun validate(m: Measurement) {
        require(m.id.isNotBlank() && m.id.length<=100 && m.value.isFinite() && m.value>0)
        require(m.timestamp in 0..32503680000000L && m.createdAt in 0..32503680000000L)
        require(m.zoneId.isNotBlank() && m.zoneId.length<=100 && m.offset.isNotBlank() && m.offset.length<=10 && m.notes.length<=5000)
        when(m.kind) {
            "pressure" -> require(m.unit=="mmHg" && m.diastolic!=null && m.diastolic.isFinite() && m.diastolic>0)
            "pulse" -> require(m.unit=="bpm" && m.diastolic==null)
            "weight" -> require(m.unit in listOf("kg","lb") && m.diastolic==null)
            else -> error("Unknown measurement type")
        }
    }
    // Display conversions are explicit. The original record is never rewritten.
    fun weight(value: Double, from: String, to: String): Double {
        require(value.isFinite() && value>0 && from in listOf("kg","lb") && to in listOf("kg","lb"))
        val converted = if(from==to) value else if(from=="lb") value*0.45359237 else value/0.45359237
        require(converted.isFinite())
        return converted
    }
}
@Serializable
data class QuickConfiguration(val token: String, val medicationId: Long, val revision: Long)
@Serializable
data class ReviewRoute(val medicationId: Long? = null, val unavailable: Boolean = false, val changed: Boolean = false)
object QuickAccessPolicy {
    // Navigation only: external amounts and timestamps are never accepted.
    fun review(token: String, enabled: Boolean, configuration: QuickConfiguration?, medications: List<Medication>): ReviewRoute {
        if(!enabled) return ReviewRoute(unavailable=true)
        if(token=="generic") return ReviewRoute()
        if(configuration==null || token!=configuration.token || token.length>100) return ReviewRoute(unavailable=true)
        val med=medications.find { it.id==configuration.medicationId && it.active } ?: return ReviewRoute(unavailable=true)
        return ReviewRoute(med.id,changed=med.revision!=configuration.revision)
    }
}
@Serializable
data class ObservationDistribution(val category: String, val response: String, val value: Int?, val count: Int)
@Serializable
data class MoodCount(val value: Int,val count: Int)
@Serializable
data class WeekDay(val start: Long, val doses: List<DoseEntry>, val observations: List<Observation>, val nonUse: List<NonUse>, val measurements: List<Measurement>)
@Serializable
data class WeeklyOverview(val summary: ReportSummary, val days: List<WeekDay>, val distributions: List<ObservationDistribution>, val legacyMoods: List<MoodCount>)
object WeeklyBuilder {
    fun build(doc: BackupDocument,boundaries: List<Long>): WeeklyOverview {
        require(boundaries.size==8)
        val summary=SummaryBuilder.build(doc,boundaries)
        val days=boundaries.zipWithNext().map { (a,b) -> WeekDay(a,doc.entries.filter { it.timestamp>=a && it.timestamp<b }.sortedBy { it.timestamp },
            doc.observations.filter { it.timestamp>=a && it.timestamp<b }.sortedBy { it.timestamp },doc.nonUse.filter { it.start<b && it.end>=a },
            doc.measurements.filter { it.timestamp>=a && it.timestamp<b }.sortedBy { it.timestamp }) }
        val observations=days.flatMap { it.observations }
        return WeeklyOverview(summary,days,observations.groupBy { Triple(it.category,it.response,it.value) }.map { (key,values) -> ObservationDistribution(key.first,key.second,key.third,values.size) },
            days.flatMap { it.doses }.mapNotNull { it.mood }.groupingBy { it }.eachCount().map { MoodCount(it.key,it.value) })
    }
}

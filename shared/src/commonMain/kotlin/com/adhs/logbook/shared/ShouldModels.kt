package com.adhs.logbook.shared

import kotlinx.serialization.Serializable

@Serializable
data class Observation(val id: String, val category: String, val response: String, val value: Int? = null,
    val timestamp: Long, val createdAt: Long, val zoneId: String, val offset: String,
    val scaleVersion: Int = 1, val sleepDate: String? = null, val doseId: Long? = null, val notes: String = "")
val observationCategories = listOf("focus", "mood", "appetite", "sleep", "symptom", "benefit", "fading")
val observationResponses = listOf("rated", "none", "unsure", "recorded")
@Serializable
data class NonUse(val id: String, val medicationId: Long, val start: Long, val end: Long,
    val createdAt: Long, val zoneId: String, val offset: String, val notes: String = "", val occurrenceId: String? = null, val kind: String = "legacy") {
    fun contains(time: Long): Boolean = kind != "scheduled" && time >= start && if(kind=="legacy") time<=end else time<end
    fun intersects(a: Long,b: Long): Boolean = start<b && if(kind in listOf("day","period")) end>a else end>=a
}
class ValidationException(message: String): IllegalArgumentException(message)
fun validateField(valid: Boolean,message: String) { if(!valid) throw ValidationException(message) }
fun nonUseName(kind: String) = when(kind) {
    "scheduled" -> "Scheduled dose not taken"
    "day" -> "No doses taken on this day"
    "period" -> "No doses taken during this period"
    else -> "Not taken (original record)"
}
val sleepQualityLabels = listOf("Very poor","Poor","Fair","Good","Very good")
fun ratingDescription(category: String,version: Int): String = when {
    category=="sleep" && version==2 -> "Sleep quality: 0 = very poor · 4 = very good"
    category=="sleep" -> "Original sleep scale: 0 = very low · 4 = very high"
    category=="focus" -> "Everyday functioning: 0 = very low · 4 = very high"
    category=="mood" -> "Mood: 0 = very low · 4 = very high"
    category=="appetite" -> "Appetite: 0 = very low · 4 = very high"
    category=="symptom" -> "Symptom intensity: 0 = very low · 4 = very high"
    category=="benefit" -> "Noticed benefit: 0 = very low · 4 = very high"
    else -> "Noticed fading: 0 = very low · 4 = very high"
}
@Serializable
data class ReminderPause(val paused: Boolean = false, val until: Long? = null) {
    fun active(now: Long): Boolean = paused && (until == null || now < until)
}
@Serializable
data class Supply(val medicationId: Long, val unitLabel: String, val countedAt: Long,
    val lowThreshold: Double, val dosePerUnit: Double? = null, val doseUnit: String = "mg",
    val prescriptionDate: Long? = null, val revision: Long = 1)
@Serializable
data class StockMovement(val id: String, val medicationId: Long, val kind: String, val timestamp: Long,
    val units: Double, val entryId: Long? = null)
@Serializable
data class StockBalance(val remaining: Double, val uncountedLogs: Int, val inconsistent: Boolean)

object SupplyLedger {
    /** Rebuild derived consumption, retaining manual counts/restocks. IDs make updates and Undo idempotent. */
    fun reconcile(doc: BackupDocument): BackupDocument {
        val manual = doc.stock.filter { it.kind != "consumption" }
        val consumption = doc.supplies.flatMap { supply ->
            doc.entries.filter { it.medicationId == supply.medicationId && it.timestamp >= supply.countedAt }.mapNotNull { entry ->
                val units = entry.supplyUnits ?: supply.dosePerUnit?.takeIf { supply.doseUnit == entry.unit }?.let { entry.doseMg / it }
                units?.let { StockMovement("dose:${entry.id}",entry.medicationId,"consumption",entry.timestamp,-it,entry.id) }
            }
        }
        return doc.copy(stock = manual + consumption)
    }
    fun balance(doc: BackupDocument, supply: Supply): StockBalance {
        val movements = doc.stock.filter { it.medicationId == supply.medicationId && it.timestamp >= supply.countedAt }
        val consumed = movements.mapNotNull { it.entryId }.toSet()
        val unknown = doc.entries.count { it.medicationId == supply.medicationId && it.timestamp >= supply.countedAt && it.id !in consumed }
        val remaining = movements.sumOf { it.units }
        return StockBalance(remaining,unknown,remaining < 0 || unknown > 0)
    }
}
object PrivacyPolicy {
    fun needsAuthentication(enabled: Boolean, foregroundSession: Boolean) = enabled && !foregroundSession
    fun externalMayWrite(enabled: Boolean) = !enabled // Locked installations authenticate in the foreground for every external action.
}
object ShouldValidation {
    private fun time(value: Long) = value in 0..32503680000000L
    fun validate(d: BackupDocument) {
        val meds=d.medications.map { it.id }.toSet();val entries=d.entries.map { it.id }.toSet()
        require(d.observations.size <= 100000 && d.nonUse.size <= 100000 && d.stock.size <= 200000)
        require(d.observations.map { it.id }.toSet().size==d.observations.size && d.nonUse.map { it.id }.toSet().size==d.nonUse.size)
        d.observations.forEach {
            require(it.id.isNotBlank() && it.id.length<=100 && it.category in observationCategories && it.response in observationResponses && (it.scaleVersion==1 || it.category=="sleep" && it.scaleVersion==2))
            require(time(it.timestamp) && time(it.createdAt) && it.notes.length<=5000 && it.zoneId.length<=100 && it.offset.length<=10)
            require(if(it.response=="rated") it.value in 0..4 else it.value==null)
            require(it.doseId==null || it.doseId in entries)
            require(it.sleepDate==null || (it.category=="sleep" && Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(it.sleepDate)))
        }
        require(d.nonUse.mapNotNull { it.occurrenceId }.distinct().size==d.nonUse.count { it.occurrenceId!=null })
        d.nonUse.forEach { n ->
            require(n.id.isNotBlank() && n.id.length<=100 && n.medicationId in meds && time(n.start) && time(n.end) && n.end>=n.start && time(n.createdAt))
            require(n.notes.length<=5000 && n.zoneId.length<=100 && n.offset.length<=10 && (n.occurrenceId?.length ?: 0)<=100)
            require(n.kind in listOf("legacy","scheduled","day","period"))
            require(if(n.kind=="scheduled") n.occurrenceId!=null && n.start==n.end else n.kind=="legacy" || n.end>n.start)
            validateField(d.entries.none { it.medicationId==n.medicationId && n.contains(it.timestamp) },"A dose is recorded in this non-use period. Correct one record first.")
        }
        d.nonUse.filter { it.kind!="scheduled" }.groupBy { it.medicationId }.values.forEach { records -> records.sortedBy { it.start }.zipWithNext().forEach { (a,b) ->
            validateField(if(a.kind=="legacy") a.end<b.start else a.end<=b.start,"Non-use periods overlap. Edit the existing record first.")
        } }
        require(d.pause.until==null || time(d.pause.until))
        require(d.supplies.map { it.medicationId }.toSet().size==d.supplies.size)
        d.supplies.forEach { s ->
            require(s.medicationId in meds && s.unitLabel.isNotBlank() && s.unitLabel.length<=80 && time(s.countedAt) && s.lowThreshold.isFinite() && s.lowThreshold>=0 && s.revision>0)
            require(s.doseUnit in doseUnits && (s.dosePerUnit==null || s.dosePerUnit.isFinite() && s.dosePerUnit>0))
            require(s.prescriptionDate==null || time(s.prescriptionDate))
            require(d.stock.count { it.medicationId==s.medicationId && it.kind=="count" && it.timestamp==s.countedAt }==1)
        }
        require(d.stock.map { it.id }.toSet().size==d.stock.size)
        d.stock.forEach {
            require(it.id.isNotBlank() && it.id.length<=100 && it.medicationId in meds && it.kind in listOf("count","restock","consumption") && time(it.timestamp) && it.units.isFinite())
            require(if(it.kind=="consumption") it.units<=0 && it.entryId in entries else it.units>=0 && it.entryId==null)
        }
        d.entries.forEach { require(it.supplyUnits==null || it.supplyUnits.isFinite() && it.supplyUnits>=0) }
    }
}

@Serializable
data class MedicationSummary(val medicationId: Long, val name: String, val formulation: String, val strength: String,
    val unit: String, val count: Int, val total: Double)
@Serializable
data class ObservationSummary(val category: String, val count: Int, val rated: Int, val none: Int, val unsure: Int, val recorded: Int)
@Serializable
data class ReportSummary(val days: Int, val doses: Int, val doseDays: Int, val observationDays: Int, val nonUseDays: Int,
    val daysWithoutRecords: Int, val medications: List<MedicationSummary>, val observations: List<ObservationSummary>, val nonUseRecords: Int, val measurementDays: Int = 0, val measurements: Int = 0)
object SummaryBuilder {
    /** Native calendar supplies real local-day boundaries, including 23/25-hour days. */
    fun build(doc: BackupDocument, boundaries: List<Long>): ReportSummary {
        require(boundaries.size in 2..368 && boundaries.zipWithNext().all { it.second>it.first })
        val start=boundaries.first();val end=boundaries.last()
        val doses=doc.entries.filter { it.timestamp>=start && it.timestamp<end }
        val observations=doc.observations.filter { it.timestamp>=start && it.timestamp<end }
        val nonUse=doc.nonUse.filter { it.intersects(start,end) }
        val measureDays=mutableSetOf<Int>();val doseDays=mutableSetOf<Int>();val obsDays=mutableSetOf<Int>();val nonDays=mutableSetOf<Int>()
        boundaries.zipWithNext().forEachIndexed { i,(a,b) ->
            if(doc.measurements.any { it.timestamp>=a && it.timestamp<b }) measureDays+=i
            if(doses.any { it.timestamp>=a && it.timestamp<b }) doseDays+=i
            if(observations.any { it.timestamp>=a && it.timestamp<b }) obsDays+=i
            if(nonUse.any { it.intersects(a,b) }) nonDays+=i
        }
        return ReportSummary(boundaries.size-1,doses.size,doseDays.size,obsDays.size,nonDays.size,
            boundaries.size-1-(doseDays+obsDays+nonDays+measureDays).size,
            doses.groupBy { listOf(it.medicationId.toString(),it.medicationName,it.formulation,it.strength,it.unit) }.values.map {
                val e=it.first();MedicationSummary(e.medicationId,e.medicationName,e.formulation,e.strength,e.unit,it.size,it.sumOf { d->d.doseMg })
            }, observations.groupBy { it.category }.map { (category,items) -> ObservationSummary(category,items.size,items.count { it.response=="rated" },items.count { it.response=="none" },items.count { it.response=="unsure" },items.count { it.response=="recorded" }) },nonUse.size,measureDays.size,doc.measurements.count { it.timestamp>=start && it.timestamp<end })
    }
}

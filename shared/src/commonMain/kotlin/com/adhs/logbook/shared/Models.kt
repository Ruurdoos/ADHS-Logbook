package com.adhs.logbook.shared

import kotlin.math.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class Preset(val title: String, val peakHours: Double?, val halfLifeHours: Double?) {
    METHYLPHENIDATE_IR("Methylphenidate IR", 2.0, 3.5),
    CONCERTA("Methylphenidate ER (Concerta-type)", null, null),
    LISDEXAMFETAMINE("Elvanse / Vyvanse", 3.8, 10.7),
    ATOMOXETINE("Atomoxetine / Strattera", null, null),
    CUSTOM("Custom medication", null, null);

    val shortName: String get() = when (this) {
        METHYLPHENIDATE_IR -> "Methylphenidate IR"
        CONCERTA -> "Concerta-type ER"
        LISDEXAMFETAMINE -> "Elvanse / Vyvanse"
        ATOMOXETINE -> "Atomoxetine"
        CUSTOM -> "Custom medication"
    }
}

@Serializable
data class Medication(
    val id: Long, val preset: Preset, val usualDose: Double, val active: Boolean = true,
    val name: String = preset.title, val formulation: String = preset.name,
    val strength: String = "", val unit: String = "mg",
    val modelId: String? = defaultModel(preset), val revision: Long = 1,
)
fun defaultModel(preset: Preset): String? = when (preset) {
    Preset.METHYLPHENIDATE_IR -> "ritalin-ir-v1"
    Preset.LISDEXAMFETAMINE -> "vyvanse-capsule-v1"
    else -> null
}
@Serializable
data class DoseEntry(
    val id: Long = 0, val medicationId: Long, val preset: Preset, val doseMg: Double,
    val timestamp: Long, val zoneId: String, val offset: String,
    val mood: Int? = null, val notes: String = "",
    val medicationName: String = preset.title, val formulation: String = preset.name,
    val strength: String = "", val unit: String = "mg", val modelId: String? = defaultModel(preset),
    val supplyUnits: Double? = null,
) {
    init { require(doseMg.isFinite() && doseMg > 0); require(mood == null || mood in 0..4) }
}
@Serializable
data class Reminder(val id: Int, val hour: Int, val minute: Int,
    val medicationId: Long? = null, val followUp: Boolean = false, val cutoffMinutes: Int = 120,
    val revision: Long = 1)
@Serializable
data class Occurrence(val id: String, val reminderId: Int, val scheduled: Long, val expires: Long,
    val revision: Long, val medicationRevision: Long?, val state: String = "pending",
    val nextAlert: Long = scheduled, val delivered: Boolean = false, val followedUp: Boolean = false,
    val entryId: Long? = null)

interface LogRepository { fun commit(entry: DoseEntry, actionId: String): Long }
fun interface LogClock { fun nowMillis(): Long }
class LogDose(private val repository: LogRepository, private val clock: LogClock) {
    fun now(medication: Medication, actionId: String, zone: String, offset: String): Long {
        require(medication.active && actionId.isNotBlank())
        return repository.commit(DoseEntry(medicationId = medication.id, preset = medication.preset,
            doseMg = medication.usualDose, timestamp = clock.nowMillis(), zoneId = zone, offset = offset,
            medicationName = medication.name, formulation = medication.formulation, strength = medication.strength,
            unit = medication.unit, modelId = medication.modelId), actionId)
    }
}

object ReminderPolicy {
    fun actionable(o: Occurrence, r: Reminder?, m: Medication?, now: Long): Boolean =
        o.state == "pending" && now >= o.scheduled && now < o.expires && r != null &&
        r.id == o.reminderId && r.revision == o.revision && (r.medicationId == null ||
            (m != null && m.id == r.medicationId && m.active && m.revision == o.medicationRevision))
}

@Serializable
data class BackupDocument(val version: Int = 4, val createdAt: Long,
    val medications: List<Medication>, val entries: List<DoseEntry>, val reminders: List<Reminder>,
    val preferences: Map<String, String> = emptyMap(),
    val observations: List<Observation> = emptyList(), val nonUse: List<NonUse> = emptyList(),
    val pause: ReminderPause = ReminderPause(), val supplies: List<Supply> = emptyList(), val stock: List<StockMovement> = emptyList(), val measurements: List<Measurement> = emptyList())
object BackupFormat {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    fun encode(value: BackupDocument): String { validate(value); return json.encodeToString(value) }
    fun decode(value: String): BackupDocument = json.decodeFromString<BackupDocument>(value).also(::validate)
    fun validate(v: BackupDocument) {
        require(v.version in 1..4 && v.createdAt >= 0)
        if(v.version==1) require(v.observations.isEmpty() && v.nonUse.isEmpty() && v.supplies.isEmpty() && v.stock.isEmpty() && !v.pause.paused)
        if(v.version<3) require(v.measurements.isEmpty())
        if(v.version<4) require(v.nonUse.all { it.kind=="legacy" } && v.observations.all { it.scaleVersion==1 })
        require(v.measurements.size<=100000 && v.measurements.map { it.id }.distinct().size==v.measurements.size)
        v.measurements.forEach(MeasurementRules::validate)
        ShouldValidation.validate(v)
        require(v.medications.size <= 1000 && v.entries.size <= 100000 && v.reminders.size <= 100)
        require(v.medications.map { it.id }.toSet().size == v.medications.size)
        require(v.entries.map { it.id }.toSet().size == v.entries.size)
        require(v.reminders.map { it.id }.toSet().size == v.reminders.size)
        val ids = v.medications.map { it.id }.toSet()
        v.medications.forEach {
            require(it.id > 0 && it.revision > 0 && it.usualDose.isFinite() && it.usualDose > 0)
            require(it.name.isNotBlank() && it.name.length <= 200 && it.formulation.length <= 200 && it.strength.length <= 100)
            require(it.unit in doseUnits && (it.modelId == null || (it.unit == "mg" && it.modelId == defaultModel(it.preset))))
        }
        v.entries.forEach {
            require(it.id > 0 && it.medicationId in ids && it.timestamp >= 0 && it.timestamp <= 32503680000000L)
            require(it.medicationName.isNotBlank() && it.medicationName.length <= 200 && it.notes.length <= 5000)
            require(it.formulation.length <= 200 && it.strength.length <= 100 && it.zoneId.length <= 100 && it.offset.length <= 10)
            require(it.unit in doseUnits && (it.modelId == null || (it.unit == "mg" && it.modelId == defaultModel(it.preset))))
        }
        v.reminders.forEach {
            require(it.id > 0 && it.hour in 0..23 && it.minute in 0..59 && it.revision > 0)
            require(it.medicationId == null || it.medicationId in ids)
            require(it.cutoffMinutes in 30..240)
        }
        require(v.preferences.keys.all { it in setOf("onboarded", "haptics", "observations_enabled", "measurements_enabled", "weekly_enabled") })
        require(v.preferences.values.all { it in setOf("true", "false") })
    }
}
val doseUnits = listOf("mg", "ml", "unit")
enum class EstimatePhase { UNAVAILABLE, NO_LOGS, RECENT, LOW, PEAK, RISING, DECREASING }
val moodLabels = listOf("Very low", "Low", "Okay", "Good", "Very good")
val moodFaces = listOf("☹", "🙁", "😐", "🙂", "☺")

fun parseDose(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

/** A heuristic relative curve, NOT a validated blood-concentration estimator.
 * Smooth rise to a label-derived typical peak, exponential terminal decline.
 * No cross-drug aggregation or measured units. See MODEL_NOTES.md.
 */
object Estimate {
    fun contribution(preset: Preset, doseMg: Double, elapsedHours: Double): Double {
        val peak = preset.peakHours ?: return 0.0
        val half = preset.halfLifeHours ?: return 0.0
        if (elapsedHours <= 0 || !elapsedHours.isFinite() || !doseMg.isFinite() || doseMg <= 0) return 0.0
        val relative = if (elapsedHours < peak) {
            val x = elapsedHours / peak
            x * x * (3 - 2 * x)
        } else exp(-ln(2.0) * (elapsedHours - peak) / half)
        return doseMg * relative
    }

    fun at(entries: List<DoseEntry>, preset: Preset, timestamp: Long): Double = entries
        .filter { it.preset == preset && it.modelId != null && it.timestamp <= timestamp }
        .sumOf { contribution(it.preset, it.doseMg, (timestamp - it.timestamp) / 3_600_000.0) }

    fun series(entries: List<DoseEntry>, preset: Preset, start: Long, end: Long, count: Int = 145): List<Double> {
        require(count >= 2 && end >= start)
        return List(count) { at(entries, preset, start + ((end - start).toDouble() * it / (count - 1)).toLong()) }
    }

    fun phase(entries: List<DoseEntry>, preset: Preset, now: Long): EstimatePhase {
        if(defaultModel(preset) == null) return EstimatePhase.UNAVAILABLE
        val relevant = entries.filter { it.preset == preset && it.modelId != null && it.timestamp <= now }
        if(relevant.isEmpty()) return EstimatePhase.NO_LOGS
        if(now - relevant.maxOf { it.timestamp } < 300_000) return EstimatePhase.RECENT
        val current = at(relevant, preset, now)
        if(current < relevant.maxOf { it.doseMg } * 0.01) return EstimatePhase.LOW
        val next = at(relevant, preset, now + 300_000)
        val previous = at(relevant, preset, now - 300_000)
        return when {
            abs(next - previous) < current * 0.006 -> EstimatePhase.PEAK
            next > previous -> EstimatePhase.RISING
            else -> EstimatePhase.DECREASING
        }
    }

}

object Csv {
    // Prefix spreadsheet formula initiators without dropping or executing user text.
    fun cell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || value.startsWith('\t') || value.startsWith('\r')) "'" + value else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
    fun row(values: List<String>): String = values.joinToString(",", transform = ::cell) + "\r\n"
}

package com.adhs.logbook.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/** Small JSON boundary keeps Swift independent of generated Kotlin collection/serializer types. */
object NativeBridge {
    @Throws(IllegalArgumentException::class)
    fun canonicalBackup(document: String): String = BackupFormat.encode(BackupFormat.decode(document))

    @Throws(IllegalArgumentException::class)
    fun reconcile(document: String): String {
        val value=BackupFormat.json.decodeFromString<BackupDocument>(document)
        require(value.version in 1..3)
        return BackupFormat.encode(SupplyLedger.reconcile(value.copy(version=3)))
    }
    @Throws(IllegalArgumentException::class)
    fun summary(document: String, boundaries: String): String = BackupFormat.json.encodeToString(SummaryBuilder.build(BackupFormat.decode(document),BackupFormat.json.decodeFromString<List<Long>>(boundaries)))
    @Throws(IllegalArgumentException::class)
    fun weekly(document: String,boundaries: String): String = BackupFormat.json.encodeToString(WeeklyBuilder.build(BackupFormat.decode(document),BackupFormat.json.decodeFromString<List<Long>>(boundaries)))
    @Throws(IllegalArgumentException::class)
    fun review(token: String,enabled: Boolean,configuration: String?,document: String): String = BackupFormat.json.encodeToString(QuickAccessPolicy.review(token,enabled,configuration?.let { BackupFormat.json.decodeFromString<QuickConfiguration>(it) },BackupFormat.decode(document).medications))
    @Throws(IllegalArgumentException::class)
    fun convertWeight(value: Double,from: String,to: String): Double = MeasurementRules.weight(value,from,to)
    @Throws(IllegalArgumentException::class)
    fun stockBalance(document: String, medicationId: Long): String {
        val doc=BackupFormat.decode(document)
        return BackupFormat.json.encodeToString(SupplyLedger.balance(doc,requireNotNull(doc.supplies.find { it.medicationId==medicationId })))
    }
    @Throws(IllegalArgumentException::class)
    fun quickEntry(medication: String, now: Long, zone: String, offset: String): String {
        val med=BackupFormat.json.decodeFromString<Medication>(medication)
        var captured: DoseEntry?=null
        val repository=object: LogRepository { override fun commit(entry: DoseEntry, actionId: String): Long { captured=entry;return 0 } }
        LogDose(repository,LogClock { now }).now(med,"native",zone,offset)
        return BackupFormat.json.encodeToString(requireNotNull(captured))
    }
    @Throws(IllegalArgumentException::class)
    fun curve(document: String, medicationId: Long, now: Long): String {
        val doc=BackupFormat.decode(document)
        val med=doc.medications.find { it.id==medicationId } ?: return "[]"
        if(med.modelId==null) return "[]"
        return BackupFormat.json.encodeToString(Estimate.series(doc.entries.filter { it.medicationId==med.id && it.modelId==med.modelId },med.preset,now-86400000,now))
    }
    @Throws(IllegalArgumentException::class)
    fun reportCurve(document: String, entryId: Long, start: Long, end: Long): String {
        val doc=BackupFormat.decode(document)
        val entry=doc.entries.find { it.id==entryId } ?: return "[]"
        if(entry.modelId==null) return "[]"
        return BackupFormat.json.encodeToString(Estimate.series(doc.entries.filter { it.medicationId==entry.medicationId && it.modelId==entry.modelId },entry.preset,start,end))
    }

    @Throws(IllegalArgumentException::class)
    fun phase(document: String, medicationId: Long, now: Long): String {
        val doc=BackupFormat.decode(document)
        val med=doc.medications.find { it.id==medicationId } ?: return EstimatePhase.UNAVAILABLE.name
        if(med.modelId==null) return EstimatePhase.UNAVAILABLE.name
        return Estimate.phase(doc.entries.filter { it.medicationId==med.id && it.modelId==med.modelId },med.preset,now).name
    }

    @Throws(IllegalArgumentException::class)
    fun canAct(occurrence: String, reminder: String, medication: String?, now: Long): Boolean = ReminderPolicy.actionable(
        BackupFormat.json.decodeFromString(occurrence),BackupFormat.json.decodeFromString(reminder),
        medication?.let { BackupFormat.json.decodeFromString<Medication>(it) },now)
}

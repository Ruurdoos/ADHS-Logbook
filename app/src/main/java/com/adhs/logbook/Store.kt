package com.adhs.logbook

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.adhs.logbook.shared.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.time.ZoneId
import java.time.ZoneOffset

// Database transactions are the cross-entry-point write boundary, not the ViewModel's busy flag.
data class LogbookState(
    val loaded: Boolean = false, val medications: List<Medication> = emptyList(),
    val entries: List<DoseEntry> = emptyList(), val reminders: List<Reminder> = emptyList(),
    val remindersEnabled: Boolean = false, val onboarded: Boolean = false,
    val observations: List<Observation> = emptyList(), val nonUse: List<NonUse> = emptyList(),
    val pause: ReminderPause = ReminderPause(), val supplies: List<Supply> = emptyList(), val stock: List<StockMovement> = emptyList(), val measurements: List<Measurement> = emptyList(),
)
class Store(context: Context, name: String = "logbook.db") : SQLiteOpenHelper(context, name, null, 4), LogRepository {
    private val json = BackupFormat.json
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE medication(id INTEGER PRIMARY KEY AUTOINCREMENT, preset TEXT NOT NULL, dose REAL NOT NULL CHECK(dose>0), active INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE entry(id INTEGER PRIMARY KEY AUTOINCREMENT, medication_id INTEGER NOT NULL, preset TEXT NOT NULL, dose REAL NOT NULL CHECK(dose>0), timestamp INTEGER NOT NULL, zone TEXT NOT NULL, offset TEXT NOT NULL, mood INTEGER, notes TEXT NOT NULL)")
        db.execSQL("CREATE INDEX entry_time ON entry(timestamp)")
        db.execSQL("CREATE TABLE reminder(id INTEGER PRIMARY KEY AUTOINCREMENT, hour INTEGER NOT NULL, minute INTEGER NOT NULL, UNIQUE(hour,minute))")
        db.execSQL("CREATE TABLE preference(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        onUpgrade(db, 1, 4)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if(oldVersion < 2) {
            db.execSQL("ALTER TABLE medication ADD COLUMN details TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE entry ADD COLUMN details TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE reminder ADD COLUMN details TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE TABLE action(action_id TEXT PRIMARY KEY, entry_id INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE occurrence(id TEXT PRIMARY KEY, details TEXT NOT NULL)")
            // Existing generic reminders stay generic. Legacy entry snapshots are reconstructed from
            // their own stored preset, never the current medication row.
        }
        if(oldVersion < 4) db.execSQL("CREATE TABLE measurement(id TEXT PRIMARY KEY,details TEXT NOT NULL)")
        if(oldVersion < 3) {
            listOf("observation","non_use","supply","stock").forEach { db.execSQL("CREATE TABLE $it(id TEXT PRIMARY KEY,details TEXT NOT NULL)") }
        }
    }
    fun <T> transaction(block: () -> T): T {
        val db = writableDatabase; db.beginTransaction()
        try { val result = block(); db.setTransactionSuccessful(); return result } finally { db.endTransaction() }
    }
    fun snapshot(): LogbookState = transaction {
        val db = readableDatabase
        val meds = db.rawQuery("SELECT id,preset,dose,active,details FROM medication ORDER BY id", null).use { c -> buildList {
            while(c.moveToNext()) add(if(c.getString(4).isNotEmpty()) json.decodeFromString<Medication>(c.getString(4))
                else Medication(c.getLong(0),Preset.valueOf(c.getString(1)),c.getDouble(2),c.getInt(3)==1))
        } }
        val entries = db.rawQuery("SELECT id,medication_id,preset,dose,timestamp,zone,offset,mood,notes,details FROM entry ORDER BY timestamp DESC,id DESC", null).use { c -> buildList {
            while(c.moveToNext()) add(if(c.getString(9).isNotEmpty()) json.decodeFromString<DoseEntry>(c.getString(9))
                else DoseEntry(c.getLong(0),c.getLong(1),Preset.valueOf(c.getString(2)),c.getDouble(3),c.getLong(4),c.getString(5),c.getString(6),if(c.isNull(7)) null else c.getInt(7),c.getString(8)))
        } }
        val reminders = db.rawQuery("SELECT id,hour,minute,details FROM reminder ORDER BY hour,minute", null).use { c -> buildList {
            while(c.moveToNext()) add(if(c.getString(3).isNotEmpty()) json.decodeFromString<Reminder>(c.getString(3)) else Reminder(c.getInt(0),c.getInt(1),c.getInt(2)))
        } }
        LogbookState(true,meds,entries,reminders,pref("reminders")=="true",pref("onboarded")=="true",readRows<Observation>("observation"),readRows<NonUse>("non_use"),
            pref("pause")?.let { json.decodeFromString<ReminderPause>(it) } ?: ReminderPause(),readRows<Supply>("supply"),readRows<StockMovement>("stock"),readRows<Measurement>("measurement"))
    }
    fun pref(key: String): String? = readableDatabase.rawQuery("SELECT value FROM preference WHERE key=?",arrayOf(key)).use { if(it.moveToFirst()) it.getString(0) else null }
    fun setPref(key: String, value: String) { check(writableDatabase.insertWithOnConflict("preference",null,ContentValues().apply { put("key",key);put("value",value) },SQLiteDatabase.CONFLICT_REPLACE)!=-1L) }
    fun saveMedication(id: Long?, preset: Preset, dose: Double) = saveMedication(Medication(id ?: 0,preset,dose))
    fun saveMedication(medication: Medication): Long = transaction {
        require(medication.usualDose.isFinite() && medication.usualDose > 0 && medication.name.isNotBlank())
        require(medication.name.length <= 200 && medication.formulation.length <= 200 && medication.strength.length <= 100 && medication.unit in doseUnits)
        val previous = snapshot().medications.find { it.id == medication.id }
        val values = ContentValues().apply { put("preset",medication.preset.name);put("dose",medication.usualDose);put("active",if(medication.active) 1 else 0) }
        val id = if(medication.id == 0L) writableDatabase.insertOrThrow("medication",null,values) else medication.id.also {
            check(writableDatabase.update("medication",values,"id=?",arrayOf(it.toString()))==1)
        }
        val updated=medication.copy(id=id, revision=(previous?.revision ?: 0)+1,
            modelId=if(medication.unit=="mg") defaultModel(medication.preset) else null)
        writableDatabase.execSQL("UPDATE medication SET details=? WHERE id=?",arrayOf<Any>(json.encodeToString(updated),id))
        setPref("onboarded","true"); id
    }
    fun removeMedication(id: Long) { snapshot().medications.find { it.id==id }?.let { saveMedication(it.copy(active=false)) } }
    fun saveEntry(entry: DoseEntry): Long = transaction {
        require(entry.notes.length <= 5000 && entry.medicationName.isNotBlank() && entry.unit in doseUnits)
        ZoneId.of(entry.zoneId); ZoneOffset.of(entry.offset)
        require(snapshot().medications.any { it.id == entry.medicationId })
        require(snapshot().nonUse.none { it.medicationId==entry.medicationId && entry.timestamp in it.start..it.end }) { "A non-use record overlaps this dose. Correct one record first." }
        val values=ContentValues().apply {
            put("medication_id",entry.medicationId);put("preset",entry.preset.name);put("dose",entry.doseMg);put("timestamp",entry.timestamp)
            put("zone",entry.zoneId);put("offset",entry.offset);if(entry.mood==null) putNull("mood") else put("mood",entry.mood);put("notes",entry.notes)
        }
        val id=if(entry.id==0L) writableDatabase.insertOrThrow("entry",null,values) else entry.id.also {
            check(writableDatabase.update("entry",values,"id=?",arrayOf(it.toString()))==1)
        }
        writableDatabase.execSQL("UPDATE entry SET details=? WHERE id=?",arrayOf<Any>(json.encodeToString(entry.copy(id=id)),id)); syncLedger(); id
    }
    override fun commit(entry: DoseEntry, actionId: String): Long = transaction {
        require(actionId.isNotBlank() && actionId.length <= 200)
        val prior = readableDatabase.rawQuery("SELECT entry_id FROM action WHERE action_id=?",arrayOf(actionId)).use { if(it.moveToFirst()) it.getLong(0) else null }
        if(prior != null) prior else {
            val id=saveEntry(entry)
            writableDatabase.execSQL("INSERT INTO action(action_id,entry_id) VALUES(?,?)",arrayOf<Any>(actionId,id)); id
        }
    }
    fun deleteEntry(id: Long) = transaction {
        writableDatabase.delete("entry","id=?",arrayOf(id.toString())) // Keep action tombstones.
        readRows<Observation>("observation").filter { it.doseId==id }.forEach { row("observation",it.id,it.copy(doseId=null)) }
        occurrences().filter { it.entryId==id }.forEach { putOccurrence(it.copy(state="undone")) }
        syncLedger()
    }
    fun saveReminder(hour: Int, minute: Int, id: Int? = null) {
        val previous=snapshot().reminders.find { it.id==id }
        saveReminder((previous ?: Reminder(0,hour,minute)).copy(hour=hour,minute=minute))
    }
    fun saveReminder(reminder: Reminder) = transaction {
        require(reminder.hour in 0..23 && reminder.minute in 0..59 && reminder.cutoffMinutes in 30..240)
        val previous=snapshot().reminders.find { it.id==reminder.id }
        val values=ContentValues().apply { put("hour",reminder.hour);put("minute",reminder.minute) }
        val id=if(reminder.id==0) writableDatabase.insertOrThrow("reminder",null,values).toInt() else reminder.id.also {
            check(writableDatabase.update("reminder",values,"id=?",arrayOf(it.toString()))==1)
        }
        writableDatabase.execSQL("UPDATE reminder SET details=? WHERE id=?",arrayOf<Any>(json.encodeToString(reminder.copy(id=id,revision=(previous?.revision ?: 0)+1)),id))
    }
    fun deleteReminder(id: Int) { writableDatabase.delete("reminder","id=?",arrayOf(id.toString())) }
    fun occurrences(): List<Occurrence> = readableDatabase.rawQuery("SELECT details FROM occurrence",null).use { c -> buildList { while(c.moveToNext()) add(json.decodeFromString<Occurrence>(c.getString(0))) } }
    fun putOccurrence(o: Occurrence) { check(writableDatabase.insertWithOnConflict("occurrence",null,ContentValues().apply { put("id",o.id);put("details",json.encodeToString(o)) },SQLiteDatabase.CONFLICT_REPLACE)!=-1L) }
    fun backup(): BackupDocument = transaction {
        val s=snapshot()
        BackupDocument(createdAt=System.currentTimeMillis(),medications=s.medications,entries=s.entries,reminders=s.reminders,
            preferences=mapOf("onboarded" to s.onboarded.toString(),"haptics" to (pref("haptics") ?: "false"),"observations_enabled" to (pref("observations_enabled") ?: "false"),"measurements_enabled" to (pref("measurements_enabled") ?: "false"),"weekly_enabled" to (pref("weekly_enabled") ?: "false")), observations=s.observations,nonUse=s.nonUse,pause=s.pause,supplies=s.supplies,stock=s.stock,measurements=s.measurements)
    }
    fun validateRestore(doc: BackupDocument) {
        BackupFormat.validate(doc)
        doc.entries.forEach { ZoneId.of(it.zoneId);ZoneOffset.of(it.offset) }
        doc.measurements.forEach { ZoneId.of(it.zoneId);ZoneOffset.of(it.offset) }
        doc.observations.forEach { ZoneId.of(it.zoneId);ZoneOffset.of(it.offset);it.sleepDate?.let(java.time.LocalDate::parse) }
        doc.nonUse.forEach { ZoneId.of(it.zoneId);ZoneOffset.of(it.offset) }
        require(doc.reminders.map { it.hour to it.minute }.toSet().size==doc.reminders.size)
    }
    fun restore(doc: BackupDocument) {
        validateRestore(doc)
        transaction {
            // Recovery snapshot is private database data, covered by the same OS backup exclusion.
            val recovery=BackupFormat.encode(backup())
            listOf("entry","medication","reminder","occurrence","action","preference","observation","non_use","supply","stock","measurement").forEach { writableDatabase.delete(it,null,null) }
            doc.medications.forEach { m -> writableDatabase.insertOrThrow("medication",null,ContentValues().apply {
                put("id",m.id);put("preset",m.preset.name);put("dose",m.usualDose);put("active",if(m.active) 1 else 0);put("details",json.encodeToString(m))
            }) }
            doc.entries.forEach { e -> writableDatabase.insertOrThrow("entry",null,ContentValues().apply {
                put("id",e.id);put("medication_id",e.medicationId);put("preset",e.preset.name);put("dose",e.doseMg);put("timestamp",e.timestamp)
                put("zone",e.zoneId);put("offset",e.offset);put("mood",e.mood);put("notes",e.notes);put("details",json.encodeToString(e))
            }) }
            doc.reminders.forEach { r -> writableDatabase.insertOrThrow("reminder",null,ContentValues().apply {
                put("id",r.id);put("hour",r.hour);put("minute",r.minute);put("details",json.encodeToString(r))
            }) }
            writeShould(SupplyLedger.reconcile(doc))
            doc.preferences.forEach { (k,v) -> setPref(k,v) }
            setPref("supply_enabled","false");setPref("reminders","false");setPref("recovery",recovery)
        }
    }

    private inline fun <reified T> readRows(table: String): List<T> = readableDatabase.rawQuery("SELECT details FROM $table",null).use { c -> buildList { while(c.moveToNext()) add(json.decodeFromString<T>(c.getString(0))) } }
    private inline fun <reified T> row(table: String,id: String,value: T) {
        check(writableDatabase.insertWithOnConflict(table,null,ContentValues().apply { put("id",id);put("details",json.encodeToString(value)) },SQLiteDatabase.CONFLICT_REPLACE)!=-1L)
    }
    private fun writeShould(doc: BackupDocument) {
        listOf("observation","non_use","supply","stock","measurement").forEach { writableDatabase.delete(it,null,null) }
        doc.measurements.forEach { row("measurement",it.id,it) }
        doc.observations.forEach { row("observation",it.id,it) };doc.nonUse.forEach { row("non_use",it.id,it) }
        doc.supplies.forEach { row("supply",it.medicationId.toString(),it) };doc.stock.forEach { row("stock",it.id,it) }
        setPref("pause",json.encodeToString(doc.pause))
    }
    private fun syncLedger() {
        val doc=SupplyLedger.reconcile(backup())
        BackupFormat.validate(doc)
        writableDatabase.delete("stock",null,null);doc.stock.forEach { row("stock",it.id,it) }
    }
    fun saveMeasurement(value: Measurement) = transaction {
        val doc=backup().let { it.copy(measurements=it.measurements.filterNot { m->m.id==value.id }+value) }
        validateRestore(doc);row("measurement",value.id,value)
    }
    fun deleteMeasurement(id: String) { writableDatabase.delete("measurement","id=?",arrayOf(id)) }
    fun saveObservation(value: Observation) = transaction {
        val doc=backup().let { it.copy(observations=it.observations.filterNot { o->o.id==value.id }+value) }
        validateRestore(doc);row("observation",value.id,value)
    }
    fun deleteObservation(id: String) { writableDatabase.delete("observation","id=?",arrayOf(id)) }
    fun saveNonUse(value: NonUse) = transaction {
        val doc=backup().let { it.copy(nonUse=it.nonUse.filterNot { n->n.id==value.id }+value) }
        validateRestore(doc)
        value.occurrenceId?.let { id ->
            val o=occurrences().find { it.id==id } ?: return@let // Restored records retain history, not live notification tokens.
            require(o.state in listOf("pending","not_taken"))
            val reminder=snapshot().reminders.find { it.id==o.reminderId }
            require(reminder!=null && (reminder.medicationId==null || reminder.medicationId==value.medicationId))
            putOccurrence(o.copy(state="not_taken"))
        }
        row("non_use",value.id,value)
    }
    fun deleteNonUse(id: String) = transaction {
        readRows<NonUse>("non_use").find { it.id==id }?.occurrenceId?.let { occurrence ->
            occurrences().find { it.id==occurrence }?.let { putOccurrence(it.copy(state="expired")) }
        }
        writableDatabase.delete("non_use","id=?",arrayOf(id))
    }
    fun pause(value: ReminderPause) { setPref("pause",json.encodeToString(value)) }
    fun saveSupply(value: Supply, counted: Double, actionId: String) = transaction {
        require(counted.isFinite() && counted>=0)
        val doc=backup();if(doc.stock.any { it.id==actionId }) return@transaction
        val next=SupplyLedger.reconcile(doc.copy(supplies=doc.supplies.filterNot { it.medicationId==value.medicationId }+value,
            stock=doc.stock+StockMovement(actionId,value.medicationId,"count",value.countedAt,counted)))
        validateRestore(next);writeShould(next)
    }
    fun restock(medicationId: Long,units: Double,actionId: String,now: Long=System.currentTimeMillis()) = transaction {
        require(units.isFinite() && units>0)
        val doc=backup();if(doc.stock.any { it.id==actionId }) return@transaction
        val next=doc.copy(stock=doc.stock+StockMovement(actionId,medicationId,"restock",now,units))
        validateRestore(next);writeShould(next)
    }
    fun removeSupply(id: Long) = transaction {
        writableDatabase.delete("supply","id=?",arrayOf(id.toString()))
        readRows<StockMovement>("stock").filter { it.medicationId==id }.forEach { writableDatabase.delete("stock","id=?",arrayOf(it.id)) }
    }
}

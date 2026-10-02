package com.adhs.logbook

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adhs.logbook.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.ZonedDateTime
import java.util.UUID
import android.net.Uri

class LogbookViewModel(application: Application) : AndroidViewModel(application) {
    private val store = Store(application)
    private val _state = MutableStateFlow(LogbookState())
    val state = _state.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val messages = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)

    init { perform { } }
    fun refresh() = perform { QuickAccess.reconcile(getApplication()); ReminderScheduler.reconcile(getApplication(),store) }
    fun recovery(): BackupDocument? = store.pref("recovery")?.let(BackupFormat::decode)
    fun lastBackup(): String? = store.pref("last_backup")
    fun preference(key: String,value: String) = perform { store.setPref(key,value) }
    fun occurrence(id: String) = store.occurrences().find { it.id==id }
    private fun perform(after: () -> Unit = {}, action: () -> Unit) {
        if(_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                _state.value = withContext(Dispatchers.IO) { action(); SupplyAlerts.reconcile(getApplication(),store);LogWidget.refresh(getApplication());store.snapshot() }
                after()
            } catch(e: Exception) {
                messages.send(getApplication<Application>().getString(R.string.save_error))
            } finally { _busy.value = false }
        }
    }
    fun medication(id: Long?, preset: Preset, dose: Double, after: () -> Unit) = perform(after) { store.saveMedication(id,preset,dose) }
    fun medication(medication: Medication, after: () -> Unit) = perform(after) { store.saveMedication(medication);QuickAccess.reconcile(getApplication()); ReminderScheduler.reconcile(getApplication(),store) }
    fun removeMedication(id: Long) = perform { store.removeMedication(id);QuickAccess.reconcile(getApplication()); ReminderScheduler.reconcile(getApplication(),store) }
    fun save(entry: DoseEntry, actionId: String = UUID.randomUUID().toString(), occurrenceId: String? = null, after: (Long) -> Unit) {
        var savedId=0L
        var resolved=false
        perform({ after(savedId) }) {
            store.transaction {
                savedId=if(entry.id==0L) store.commit(entry,actionId) else store.saveEntry(entry)
                // Linking is explicit through the reminder's Open action, never guessed by time.
                resolved=ReminderScheduler.resolve(store,occurrenceId,savedId)
            }
            if(resolved) occurrenceId?.let { ReminderScheduler.cancelOccurrence(getApplication(),it) }
            ReminderScheduler.reconcile(getApplication(),store)
        }
    }
    fun quick(medication: Medication, after: (Long) -> Unit) {
        val now=ZonedDateTime.now(); var id=0L
        perform({ after(id) }) { id=LogDose(store,LogClock { now.toInstant().toEpochMilli() }).now(medication,UUID.randomUUID().toString(),now.zone.id,now.offset.id) }
    }
    fun supplyNotificationsEnabled()=store.pref("supply_enabled")=="true"
    fun enabled(key: String)=store.pref(key)=="true"
    fun measurement(value: Measurement,after: ()->Unit)=perform(after) { store.saveMeasurement(value) }
    fun deleteMeasurement(id: String,after: ()->Unit)=perform(after) { store.deleteMeasurement(id) }
    fun observationsEnabled()=store.pref("observations_enabled")=="true"
    fun document()=store.backup()
    fun widgetLog(med: Medication,token: String,after: (Long)->Unit) {
        var id=0L
        perform({ after(id) }) {
            id=LogWidget.log(getApplication(),store,token)
            LogWidget.invalidate(getApplication())
        }
    }
    fun observation(value: Observation,after: ()->Unit) = perform(after) { store.saveObservation(value) }
    fun deleteObservation(id: String,after: ()->Unit) = perform(after) { store.deleteObservation(id) }
    fun nonUse(value: NonUse,after: ()->Unit) = perform(after) { store.saveNonUse(value);ReminderScheduler.reconcile(getApplication(),store) }
    fun deleteNonUse(id: String,after: ()->Unit) = perform(after) { store.deleteNonUse(id);ReminderScheduler.reconcile(getApplication(),store) }
    fun pause(value: ReminderPause) = perform { store.pause(value);ReminderScheduler.reconcile(getApplication(),store) }
    fun supply(value: Supply,count: Double,action: String,after: ()->Unit) = perform(after) { store.saveSupply(value,count,action) }
    fun restock(id: Long,units: Double,action: String,after: ()->Unit) = perform(after) { store.restock(id,units,action) }
    fun removeSupply(id: Long,after: ()->Unit) = perform(after) { store.removeSupply(id) }
    suspend fun createBackup(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        try {
            val bytes=BackupCrypto.encrypt(store.backup(),password)
            getApplication<Application>().contentResolver.openOutputStream(uri,"wt")!!.use { it.write(bytes) }
            store.setPref("last_backup",ZonedDateTime.now().toString())
        } finally { password.fill('\u0000') }
    }
    suspend fun previewBackup(uri: Uri,password: CharArray): BackupDocument = withContext(Dispatchers.IO) {
        try {
            val bytes=getApplication<Application>().contentResolver.openInputStream(uri)!!.use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) { val n=input.read(buffer);if(n<0) break;require(output.size()+n<=BackupCrypto.MAX_BYTES);output.write(buffer,0,n) };output.toByteArray()
            }
            BackupCrypto.decrypt(bytes,password).also(store::validateRestore)
        } finally { password.fill('\u0000') }
    }
    fun restore(doc: BackupDocument, after: () -> Unit) = perform(after) {
        store.validateRestore(doc)
        val previous=store.occurrences()
        store.restore(doc);QuickAccess.reset(getApplication())
        previous.forEach { ReminderScheduler.cancelOccurrence(getApplication(),it.id) };LogWidget.invalidate(getApplication());SupplyAlerts.cancelAll(getApplication())
    }
    fun reminder(value: Reminder) = perform { store.saveReminder(value); ReminderScheduler.reconcile(getApplication(),store) }
    fun delete(id: Long, after: () -> Unit = {}) = perform(after) { store.deleteEntry(id) }
    fun reminders(enabled: Boolean) = perform {
        if(enabled && store.snapshot().reminders.isEmpty()) store.saveReminder(8,0)
        store.setPref("reminders", enabled.toString())
        ReminderScheduler.reschedule(getApplication(),store.snapshot())
    }
    fun reminder(hour: Int, minute: Int, id: Int? = null) = perform {
        store.saveReminder(hour,minute,id)
        ReminderScheduler.reschedule(getApplication(),store.snapshot())
    }
    fun removeReminder(id: Int) = perform {
        ReminderScheduler.cancel(getApplication(),id)
        store.deleteReminder(id)
        ReminderScheduler.reschedule(getApplication(),store.snapshot())
    }
}

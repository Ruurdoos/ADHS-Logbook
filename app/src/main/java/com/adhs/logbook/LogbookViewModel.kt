package com.adhs.logbook

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adhs.logbook.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime
import java.util.UUID
import android.net.Uri

class LogbookViewModel(application: Application) : AndroidViewModel(application) {
    private var store = Store(application)
    private val writes = kotlinx.coroutines.sync.Mutex()
    private var pending = 0
    private val _state = MutableStateFlow(LogbookState())
    val state = _state.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val messages = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)

    init { viewModelScope.coroutineContext[Job]?.invokeOnCompletion { store.close() };perform { } }
    fun refresh() = perform { QuickAccess.reconcile(getApplication()); ReminderScheduler.reconcile(getApplication(),store) }
    fun recovery(): BackupDocument? = _state.value.preferences["recovery"]?.let(BackupFormat::decode)
    fun lastBackup(): String? = _state.value.preferences["last_backup"]
    fun preference(key: String,value: String) = perform { store.setPref(key,value) }
    private var occurrences: List<Occurrence> = emptyList()
    fun occurrence(id: String) = occurrences.find { it.id==id }
    private fun perform(after: () -> Unit = {}, action: () -> Unit) {
        pending++
        _busy.value = true
        viewModelScope.launch {
            var acquired=false
            try {
                writes.lock();acquired=true
                _state.value = withContext(Dispatchers.IO) {
                    action(); SupplyAlerts.reconcile(getApplication(),store);LogWidget.refresh(getApplication())
                    occurrences=store.occurrences();store.snapshot()
                }
                after()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                val message=if(e is ValidationException) tr(e.message!!) else getApplication<Application>().getString(R.string.save_error)
                if(!_state.value.loaded) _state.value=_state.value.copy(loadError=message)
                messages.send(message)
            } finally { if(acquired) writes.unlock();pending--;_busy.value = pending>0 }
        }
    }
    fun startWithoutMedication() = perform {
        store.setPref("onboarded","true");store.setPref("observations_enabled","true");store.setPref("measurements_enabled","true")
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
        if(_busy.value) return
        val now=ZonedDateTime.now(); var id=0L
        perform({ after(id) }) { id=LogDose(store,LogClock { now.toInstant().toEpochMilli() }).now(medication,UUID.randomUUID().toString(),now.zone.id,now.offset.id) }
    }
    fun supplyNotificationsEnabled()=_state.value.preferences["supply_enabled"]=="true"
    fun enabled(key: String)=_state.value.preferences[key]=="true"
    fun measurement(value: Measurement,after: ()->Unit)=perform(after) { store.saveMeasurement(value) }
    fun deleteMeasurement(id: String,after: ()->Unit)=perform(after) { store.deleteMeasurement(id) }
    fun observationsEnabled()=_state.value.preferences["observations_enabled"]=="true"
    fun document(): BackupDocument {
        val s=_state.value
        return BackupDocument(createdAt=0,medications=s.medications,entries=s.entries,reminders=s.reminders,
            preferences=s.preferences.filterKeys { it in setOf("onboarded","haptics","observations_enabled","measurements_enabled","weekly_enabled") },
            observations=s.observations,nonUse=s.nonUse,pause=s.pause,supplies=s.supplies,stock=s.stock,measurements=s.measurements)
    }
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
    fun pause(value: ReminderPause,after: ()->Unit = {}) = perform(after) { store.pause(value);ReminderScheduler.reconcile(getApplication(),store) }
    fun supply(value: Supply,count: Double,action: String,after: ()->Unit) = perform(after) { store.saveSupply(value,count,action) }
    fun supplySettings(value: Supply,after: ()->Unit) = perform(after) { store.updateSupply(value) }
    fun restock(id: Long,units: Double,action: String,after: ()->Unit) = perform(after) { store.restock(id,units,action) }
    fun removeSupply(id: Long,after: ()->Unit) = perform(after) { store.removeSupply(id) }
    val backupWorking=MutableStateFlow(false)
    val backupPreview=MutableStateFlow<BackupDocument?>(null)
    val backupMessage=MutableStateFlow<String?>(null)
    fun backupJob(uri: Uri,password: CharArray,create: Boolean) {
        if(backupWorking.value) { password.fill('\u0000');return }
        backupWorking.value=true
        viewModelScope.launch {
            try { writes.withLock { if(create) { createBackup(uri,password);backupMessage.value=tr("Backup created.") } else backupPreview.value=previewBackup(uri,password) } }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { backupMessage.value=tr(if(create) "Backup failed. The selected file may be incomplete; create a new backup." else "Cannot open backup. Check the passphrase and file. Your log was not changed.") }
            finally { password.fill('\u0000');backupWorking.value=false }
        }
    }
    suspend fun createBackup(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        try {
            val bytes=BackupCrypto.encrypt(store.backup(),password)
            getApplication<Application>().contentResolver.openOutputStream(uri,"wt")!!.use { it.write(bytes) }
            store.setPref("last_backup",ZonedDateTime.now().toString())
            _state.value=store.snapshot()
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
        val previous=runCatching { store.occurrences() }.getOrDefault(emptyList())
        store.restore(doc);QuickAccess.reset(getApplication())
        previous.forEach { ReminderScheduler.cancelOccurrence(getApplication(),it.id) };LogWidget.invalidate(getApplication());SupplyAlerts.cancelAll(getApplication())
    }
    fun reminder(value: Reminder) = perform { store.saveReminder(value); ReminderScheduler.reconcile(getApplication(),store) }
    fun delete(id: Long, after: () -> Unit = {}) = perform(after) { store.deleteEntry(id) }
    fun reminders(enabled: Boolean) = perform {
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

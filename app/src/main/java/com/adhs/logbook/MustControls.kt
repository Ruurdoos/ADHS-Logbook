package com.adhs.logbook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.adhs.logbook.shared.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReminderOptions(reminder: Reminder, medications: List<Medication>,dismiss: () -> Unit,save: (Reminder) -> Unit) {
    var selected by remember { mutableStateOf(reminder.medicationId) }
    var follow by remember { mutableStateOf(reminder.followUp) }
    var cutoff by remember { mutableIntStateOf(reminder.cutoffMinutes) }
    AlertDialog(onDismissRequest=dismiss,title={ Text(tr("Reminder options")) },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(tr("Choose a medication to enable log-now. Otherwise this reminder opens your log."))
            FilterChip(selected=selected==null,onClick={ selected=null },label={ Text(tr("Generic reminder")) })
            medications.filter { it.active }.forEach { med ->
                FilterChip(selected=selected==med.id,onClick={ selected=med.id },label={ Text("${med.name} · ${doseText(med.usualDose)} ${tr(med.unit)}") })
            }
            Row { Checkbox(follow,{ follow=it },Modifier.semantics { contentDescription=tr("One follow-up after 30 minutes") });Text(tr("One follow-up after 30 minutes"),Modifier.padding(top=12.dp)) }
            Text(tr("Stop this reminder after"))
            listOf(60,120,240).forEach { minutes -> FilterChip(selected=cutoff==minutes,onClick={ cutoff=minutes },label={ Text(tr("%d minutes",minutes)) }) }
            Text(tr("Snooze adds 10 minutes, up to the cutoff. Reminder text hides medication names."))
        }
    },confirmButton={ TextButton({ save(reminder.copy(medicationId=selected,followUp=follow,cutoffMinutes=cutoff)) }) { Text(tr("Save")) } },dismissButton={ TextButton(dismiss) { Text(tr("Cancel")) } })
}

@Composable
fun BackupControls(vm: LogbookViewModel,busy: Boolean) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    // Passwords deliberately do not use saved instance state.
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val working by vm.backupWorking.collectAsState()
    var preview by vm.backupPreview.collectAsStateMutable()
    var message by vm.backupMessage.collectAsStateMutable()
    val last=vm.lastBackup()
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        selectedUri=uri?.toString();dialog=if(uri==null) null else "create"
    }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri=uri?.toString();dialog=if(uri==null) null else "restore"
    }
    Column {
        last?.let { Text(tr("Last backup: %s",runCatching { java.time.ZonedDateTime.parse(it).format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)) }.getOrDefault(it))) }
        TextButton({ create.launch("ADHS-logbook.adhsbak") },enabled=!busy && !working) { Text(tr("Create backup")) }
        TextButton({ open.launch(arrayOf("*/*")) },enabled=!busy && !working) { Text(tr("Restore backup")) }
        TextButton({ preview=vm.recovery();if(preview==null) message=tr("No pre-restore snapshot available.") },enabled=!busy && !working) { Text(tr("Recover pre-restore log")) }
        if(working) { LinearProgressIndicator();Text(tr("Working…")) }
    }
    dialog?.let { mode -> AlertDialog(onDismissRequest={ dialog=null;password="";confirmPassword="" },title={ Text(tr(if(mode=="create") "Create backup" else "Restore backup")) },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(tr("Encrypted backup. Use at least 10 characters. Keep the passphrase separately; it cannot be recovered."))
            Text(tr("You choose the file destination. Cloud-backed file providers are outside this app's local storage."))
            OutlinedTextField(password,{ password=it },label={ Text(tr("Passphrase")) },visualTransformation=PasswordVisualTransformation(),singleLine=true)
            if(mode=="create") OutlinedTextField(confirmPassword,{ confirmPassword=it },label={ Text(tr("Repeat passphrase")) },visualTransformation=PasswordVisualTransformation(),singleLine=true)
        }
    },confirmButton={ TextButton({ val uri=selectedUri?.let(android.net.Uri::parse)
        if(uri!=null) {
            val secret=password.toCharArray();password="";confirmPassword="";dialog=null
            vm.backupJob(uri,secret,mode=="create");selectedUri=null
        } },enabled=password.length>=10 && (mode!="create" || password==confirmPassword)) { Text(tr(if(mode=="create") "Create backup" else "Restore backup")) } },dismissButton={ TextButton({ dialog=null;password="";confirmPassword="" }) { Text(tr("Cancel")) } }) }
    preview?.let { doc ->
        val range=doc.entries.map { it.timestamp }.let { stamps -> if(stamps.isEmpty()) tr("No entries") else listOf(stamps.min(),stamps.max()).joinToString(" – ") { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() } }
        AlertDialog(onDismissRequest={ if(!busy) preview=null },title={ Text(tr("Replace current log?")) },text={
            Column { Text(tr("Medications: %d · Entries: %d",doc.medications.size,doc.entries.size));Text(range);Text(tr("Observations: %d · Measurements: %d · Non-use records: %d",doc.observations.size,doc.measurements.size,doc.nonUse.size))
                Text(tr("This replaces the current log. A private pre-restore snapshot is kept on this device. Reminders stay off until you enable them.")) }
        },confirmButton={ TextButton({ vm.restore(doc) { preview=null;message=tr("Backup restored. Reminders are off.") } },enabled=!busy) { Text(tr("Replace log")) } },dismissButton={ TextButton({ preview=null },enabled=!busy) { Text(tr("Cancel")) } })
    }
    message?.let { text -> AlertDialog(onDismissRequest={ message=null },text={ Text(text) },confirmButton={ TextButton({ message=null }) { Text(tr("Close")) } }) }
}

@Composable private fun <T> kotlinx.coroutines.flow.MutableStateFlow<T>.collectAsStateMutable(): MutableState<T> {
    val current=collectAsState()
    return remember(this) { object : MutableState<T> {
        override var value: T get()=current.value;set(value) { this@collectAsStateMutable.value=value }
        override fun component1()=value
        override fun component2(): (T)->Unit = { value=it }
    } }
}

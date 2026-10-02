package com.adhs.logbook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.adhs.logbook.shared.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BackupControls(vm: LogbookViewModel,busy: Boolean) {
    var dialog by remember { mutableStateOf<String?>(null) }
    // Passwords deliberately do not use saved instance state.
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BackupDocument?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var last by remember { mutableStateOf(vm.lastBackup()) }
    val scope=rememberCoroutineScope()
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if(uri==null) { password="";confirmPassword="" } else {
            val secret=password.toCharArray();password="";confirmPassword="";working=true
            scope.launch { try { vm.createBackup(uri,secret);last=vm.lastBackup();message=tr("Backup created.") }
                catch(_: Exception) { message=tr("Backup failed. The selected file may be incomplete; create a new backup.") } finally { working=false } }
        }
    }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri==null) password="" else {
            val secret=password.toCharArray();password="";working=true
            scope.launch { try { preview=vm.previewBackup(uri,secret) }
                catch(_: Exception) { message=tr("Cannot open backup. Check the passphrase and file. Your log was not changed.") } finally { working=false } }
        }
    }
    Column {
        last?.let { Text(tr("Last backup: %s",runCatching { java.time.ZonedDateTime.parse(it).format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)) }.getOrDefault(it))) }
        TextButton({ dialog="create" },enabled=!busy && !working) { Text(tr("Create backup")) }
        TextButton({ dialog="restore" },enabled=!busy && !working) { Text(tr("Restore backup")) }
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
    },confirmButton={ TextButton({ dialog=null;if(mode=="create") create.launch("ADHS-logbook.adhsbak") else open.launch(arrayOf("*/*")) },enabled=password.length>=10 && (mode!="create" || password==confirmPassword)) { Text(tr("Choose file")) } },dismissButton={ TextButton({ dialog=null;password="";confirmPassword="" }) { Text(tr("Cancel")) } }) }
    preview?.let { doc ->
        val range=doc.entries.map { it.timestamp }.let { stamps -> if(stamps.isEmpty()) tr("No entries") else listOf(stamps.min(),stamps.max()).joinToString(" – ") { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() } }
        AlertDialog(onDismissRequest={ if(!busy) preview=null },title={ Text(tr("Replace current log?")) },text={
            Column { Text(tr("Medications: %d · Entries: %d",doc.medications.size,doc.entries.size));Text(range)
                Text(tr("This replaces the current log. A private pre-restore snapshot is kept on this device. Reminders stay off until you enable them.")) }
        },confirmButton={ TextButton({ vm.restore(doc) { preview=null;message=tr("Backup restored. Reminders are off.") } },enabled=!busy) { Text(tr("Replace log")) } },dismissButton={ TextButton({ preview=null },enabled=!busy) { Text(tr("Cancel")) } })
    }
    message?.let { text -> AlertDialog(onDismissRequest={ message=null },text={ Text(text) },confirmButton={ TextButton({ message=null }) { Text(tr("Close")) } }) }
}

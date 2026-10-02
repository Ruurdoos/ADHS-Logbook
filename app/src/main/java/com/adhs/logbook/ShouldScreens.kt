package com.adhs.logbook

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adhs.logbook.shared.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

fun categoryName(key: String)=when(key) { "focus"->"Focus / everyday functioning";"mood"->"Mood";"appetite"->"Appetite";"sleep"->"Sleep";"symptom"->"Symptom / suspected side effect";"benefit"->"User-noticed benefit";else->"User-noticed fading" }
fun responseName(key: String)=when(key) { "rated"->"Rating";"none"->"None";"unsure"->"Unsure";else->"Recorded time" }
@Composable fun TimeField(label: String,value: Long,onChange: (Long)->Unit) {
    val context=LocalContext.current;val date=Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
    Text(tr(label))
    OutlinedButton({ DatePickerDialog(context,{ _,y,m,d -> onChange(LocalDate.of(y,m+1,d).atTime(date.toLocalTime()).atZone(date.zone).toInstant().toEpochMilli()) },date.year,date.monthValue-1,date.dayOfMonth).show() }) { Text(date.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))) }
    OutlinedButton({ TimePickerDialog(context,{ _,h,m -> onChange(date.withHour(h).withMinute(m).withSecond(0).withNano(0).toInstant().toEpochMilli()) },date.hour,date.minute,android.text.format.DateFormat.is24HourFormat(context)).show() }) { Text(date.format(timeFormat(context))) }
}
@Composable private fun Choice(label: String,items: List<Pair<String,String>>,value: String,onChange: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(tr(label));Box {
        OutlinedButton({ expanded=true },Modifier.fillMaxWidth()) { Text(tr(items.firstOrNull { it.first==value }?.second ?: label)) }
        DropdownMenu(expanded,{ expanded=false }) { items.forEach { (id,title) -> DropdownMenuItem(text={ Text(tr(title)) },onClick={ onChange(id);expanded=false }) } }
    }
}
@Composable private fun ObservationEditor(original: Observation?,state: LogbookState,busy: Boolean,save: (Observation)->Unit,delete: (String)->Unit) {
    var category by rememberSaveable { mutableStateOf(original?.category ?: "focus") }
    var response by rememberSaveable { mutableStateOf(original?.response ?: "") }
    var rating by rememberSaveable { mutableIntStateOf(original?.value ?: -1) }
    var time by rememberSaveable { mutableLongStateOf(original?.timestamp ?: System.currentTimeMillis()) }
    var notes by rememberSaveable { mutableStateOf(original?.notes ?: "") }
    var sleep by rememberSaveable { mutableStateOf(original?.sleepDate ?: LocalDate.now().minusDays(1).toString()) }
    var link by rememberSaveable { mutableStateOf(original?.doseId?.toString() ?: "") }
    val id=rememberSaveable { original?.id ?: UUID.randomUUID().toString() }
    Text(tr(if(original==null) "Add observation" else "Edit observation"),style=MaterialTheme.typography.headlineSmall)
    Choice("Category",observationCategories.map { it to categoryName(it) },category) { category=it;response="";rating=-1 }
    Choice("Response",observationResponses.map { it to responseName(it) },response) { response=it }
    if(response=="rated") {
        Text(tr("0 = very low · 4 = very high"))
        Row { (0..4).forEach { value -> FilterChip(rating==value,{ rating=value },label={ Text(value.toString()) },modifier=Modifier.weight(1f)) } }
    }
    TimeField("Observation time",time) { time=it }
    if(category=="sleep") OutlinedTextField(sleep,{ sleep=it },label={ Text(tr("Night starting on (YYYY-MM-DD)")) })
    Choice("Optional dose link",listOf("" to "No dose link")+state.entries.map { it.id.toString() to "${it.medicationName} · ${Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()} · ${doseText(it.doseMg)} ${it.unit}" },link) { link=it }
    Text(tr("A link records your context, not medication causation."),style=MaterialTheme.typography.bodySmall)
    OutlinedTextField(notes,{ notes=it.take(5000) },label={ Text(tr("Notes (optional)")) },minLines=2)
    val valid=response.isNotEmpty() && (response!="rated" || rating>=0) && time<=System.currentTimeMillis() && (category!="sleep" || runCatching { LocalDate.parse(sleep) }.isSuccess)
    Button({ val zone=Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());save(Observation(id,category,response,if(response=="rated") rating else null,time,original?.createdAt ?: System.currentTimeMillis(),if(original?.timestamp==time) original.zoneId else zone.zone.id,if(original?.timestamp==time) original.offset else zone.offset.id,sleepDate=if(category=="sleep") sleep else null,doseId=link.toLongOrNull(),notes=notes)) },enabled=!busy && valid) { Text(tr("Save")) }
    if(original!=null) DeleteRecord { delete(original.id) }
}
@Composable private fun NonUseEditor(original: NonUse?,state: LogbookState,busy: Boolean,save: (NonUse)->Unit,delete: (String)->Unit) {
    var med by rememberSaveable { mutableStateOf(original?.medicationId?.toString() ?: "") }
    var start by rememberSaveable { mutableLongStateOf(original?.start ?: System.currentTimeMillis()) }
    var end by rememberSaveable { mutableLongStateOf(original?.end ?: start) }
    var period by rememberSaveable { mutableStateOf(original!=null && original.end>original.start) }
    var notes by rememberSaveable { mutableStateOf(original?.notes ?: "") }
    val id=rememberSaveable { original?.id ?: UUID.randomUUID().toString() }
    Text(tr("Record not taken"),style=MaterialTheme.typography.headlineSmall)
    Text(tr("Only record what you know. Pausing reminders does not record non-use."))
    Choice("Medication",state.medications.map { it.id.toString() to it.name },med) { med=it }
    TimeField("Start",start) { start=it }
    Row { Checkbox(period,{ period=it;end=start });Text(tr("Record a period")) }
    if(period) TimeField("End",end) { end=it }
    OutlinedTextField(notes,{ notes=it.take(5000) },label={ Text(tr("Notes (optional)")) })
    val until=if(period) end else start
    val overlap=state.entries.any { it.medicationId.toString()==med && it.timestamp in start..until }
    if(overlap) Text(tr("A dose is recorded in this non-use period. Correct one record first."))
    Button({ val zone=Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault());save(NonUse(id,med.toLong(),start,until,original?.createdAt ?: System.currentTimeMillis(),if(original?.start==start) original.zoneId else zone.zone.id,if(original?.start==start) original.offset else zone.offset.id,notes,original?.occurrenceId)) },enabled=!busy && med.isNotBlank() && until>=start && until<=System.currentTimeMillis() && !overlap) { Text(tr("Save")) }
    if(original!=null && state.nonUse.any { it.id==original.id }) DeleteRecord { delete(original.id) }
}
@Composable private fun DeleteRecord(remove: ()->Unit) {
    var confirm by remember { mutableStateOf(false) }
    TextButton({ confirm=true }) { Text(tr("Delete record")) }
    if(confirm) AlertDialog(onDismissRequest={ confirm=false },title={ Text(tr("Delete record?")) },text={ Text(tr("This cannot be undone.")) },confirmButton={ TextButton({ confirm=false;remove() }) { Text(tr("Delete")) } },dismissButton={ TextButton({ confirm=false }) { Text(tr("Cancel")) } })
}
@Composable private fun PauseEditor(original: ReminderPause,busy: Boolean,save: (ReminderPause)->Unit) {
    var paused by rememberSaveable { mutableStateOf(original.active(System.currentTimeMillis())) }
    var timed by rememberSaveable { mutableStateOf(original.until!=null) }
    var until by rememberSaveable { mutableLongStateOf(original.until ?: System.currentTimeMillis()+86400000) }
    Text(tr("Pause reminders"),style=MaterialTheme.typography.headlineSmall)
    Row { Switch(paused,{ paused=it });Text(tr("Pause reminders")) }
    Text(tr("This pauses notifications only. It does not record medication use or non-use."))
    if(paused) { Row { Checkbox(timed,{ timed=it });Text(tr("Resume at a chosen time")) };if(timed) TimeField("Resume at",until) { until=it } }
    Button({ save(ReminderPause(paused,if(paused && timed) until else null)) },enabled=!busy && (!paused || !timed || until>System.currentTimeMillis())) { Text(tr("Save")) }
}
@Composable private fun SupplyEditor(state: LogbookState,vm: LogbookViewModel,busy: Boolean,close: ()->Unit) {
    var medId by rememberSaveable { mutableStateOf("") }
    val med=state.medications.find { it.id.toString()==medId }
    val existing=state.supplies.find { it.medicationId==med?.id }
    var unit by rememberSaveable(medId) { mutableStateOf(existing?.unitLabel ?: "") }
    var counted by rememberSaveable(medId) { mutableStateOf("") }
    var threshold by rememberSaveable(medId) { mutableStateOf(existing?.lowThreshold?.let(::doseText) ?: "") }
    var mapping by rememberSaveable(medId) { mutableStateOf(existing?.dosePerUnit?.let(::doseText) ?: "") }
    var rx by rememberSaveable(medId) { mutableStateOf(existing?.prescriptionDate!=null) }
    var rxDate by rememberSaveable(medId) { mutableLongStateOf(existing?.prescriptionDate ?: System.currentTimeMillis()+7*86400000) }
    var restock by rememberSaveable(medId) { mutableStateOf("") }
    val action=rememberSaveable(medId) { UUID.randomUUID().toString() }
    Text(tr("Supply"),style=MaterialTheme.typography.headlineSmall)
    val context=LocalContext.current
    var alerts by rememberSaveable { mutableStateOf(vm.supplyNotificationsEnabled()) }
    val permission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    Row { Switch(alerts,{ alerts=it;vm.preference("supply_enabled",it.toString());if(it && android.os.Build.VERSION.SDK_INT>=33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) });Text(tr("Supply notifications")) }
    Text(tr("After restore, notifications stay off until you enable them here."))
    Choice("Medication",state.medications.filter { it.active }.map { it.id.toString() to it.name },medId) { medId=it }
    if(med!=null) {
        if(existing!=null) {
            val balance=SupplyLedger.balance(vm.document(),existing)
            Text(tr("Estimated remaining: %s %s",doseText(balance.remaining),existing.unitLabel))
            if(balance.inconsistent) Text(tr("Count may be incomplete. Review uncounted logs or recount."))
            OutlinedTextField(restock,{ restock=it },label={ Text(tr("Restock units")) })
            Button({ vm.restock(med.id,parseDose(restock)!!,action,close) },enabled=!busy && parseDose(restock)!=null) { Text(tr("Add restock")) }
        }
        Text(tr("Use a physical count. Package units are separate from the logged dose."))
        OutlinedTextField(unit,{ unit=it.take(80) },label={ Text(tr("Package unit label")) })
        OutlinedTextField(counted,{ counted=it },label={ Text(tr("Counted stock now")) })
        OutlinedTextField(threshold,{ threshold=it },label={ Text(tr("Low-stock threshold")) })
        OutlinedTextField(mapping,{ mapping=it },label={ Text(tr("Dose amount per package unit (optional)")) })
        Text(tr("Mapping uses the medication's logging unit. Leave blank to record stock units explicitly in each dose. No conversion is inferred."),style=MaterialTheme.typography.bodySmall)
        Row { Checkbox(rx,{ rx=it });Text(tr("Prescription request reminder")) }
        if(rx) TimeField("Prescription request date",rxDate) { rxDate=it }
        fun nonnegative(text: String)=text.replace(',','.').toDoubleOrNull()?.takeIf { it.isFinite() && it>=0 }
        Button({ vm.supply(Supply(med.id,unit.trim(),System.currentTimeMillis(),nonnegative(threshold)!!,parseDose(mapping),med.unit,if(rx) rxDate else null,(existing?.revision ?: 0)+1),nonnegative(counted)!!,action,close) },enabled=!busy && unit.isNotBlank() && nonnegative(counted)!=null && nonnegative(threshold)!=null && (mapping.isBlank() || parseDose(mapping)!=null)) { Text(tr("Save count and settings")) }
        if(existing!=null) DeleteRecord { vm.removeSupply(med.id,close) }
    }
}
@Composable private fun WidgetSetup(state: LogbookState) {
    val context=LocalContext.current;val prefs=context.getSharedPreferences("widget",android.content.Context.MODE_PRIVATE)
    var medId by rememberSaveable { mutableStateOf(prefs.getLong("med",0).toString()) }
    var private by rememberSaveable { mutableStateOf(prefs.getBoolean("private",true)) }
    var message by remember { mutableStateOf<String?>(null) }
    Text(tr("Home-screen widget"),style=MaterialTheme.typography.headlineSmall)
    Choice("Medication",state.medications.filter { it.active }.map { it.id.toString() to it.name },medId) { medId=it }
    Row { Checkbox(private,{ private=it });Text(tr("Generic widget content")) }
    Text(tr("App lock always hides widget details. Changed medication settings require review in the app."))
    Button({ state.medications.find { it.id.toString()==medId }?.let { med ->
        LogWidget.configure(context,med.id,med.revision,private)
        val manager=AppWidgetManager.getInstance(context)
        if(manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(ComponentName(context,LogWidgetReceiver::class.java),null,null)
        message=tr("Widget configured. Add it from your home screen's widget gallery if needed.")
    } },enabled=state.medications.any { it.id.toString()==medId && it.active }) { Text(tr("Save widget configuration")) }
    message?.let { Text(it) }
}

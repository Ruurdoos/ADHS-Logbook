package com.adhs.logbook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adhs.logbook.shared.*
import java.time.*
import java.time.temporal.WeekFields
import java.util.*

fun measurementName(kind: String)=when(kind) { "pressure"->"Blood pressure";"pulse"->"Pulse";else->"Weight" }
fun measurementText(m: Measurement)=tr(measurementName(m.kind))+": "+doseText(m.value)+(m.diastolic?.let { " / "+doseText(it) } ?: "")+" "+m.unit
@Composable fun MeasurementEditor(original: Measurement?,busy: Boolean,save: (Measurement)->Unit,delete: (String)->Unit) {
    var kind by rememberSaveable { mutableStateOf(original?.kind ?: "pressure") }
    var value by rememberSaveable { mutableStateOf(original?.value?.let(::doseText) ?: "") }
    var lower by rememberSaveable { mutableStateOf(original?.diastolic?.let(::doseText) ?: "") }
    var unit by rememberSaveable { mutableStateOf(original?.unit ?: "mmHg") }
    var time by rememberSaveable { mutableLongStateOf(original?.timestamp ?: System.currentTimeMillis()) }
    var notes by rememberSaveable { mutableStateOf(original?.notes ?: "") }
    var confirm by remember { mutableStateOf(false) }
    var conversion by rememberSaveable { mutableStateOf(false) }
    val id=rememberSaveable { original?.id ?: UUID.randomUUID().toString() }
    Text(tr("Measurements"),style=MaterialTheme.typography.headlineSmall)
    listOf("pressure","pulse","weight").forEach { type -> FilterChip(kind==type,{ kind=type;unit=when(type) { "pressure"->"mmHg";"pulse"->"bpm";else->"kg" };value="";lower="" },label={ Text(tr(measurementName(type))) }) }
    OutlinedTextField(value,{ value=it },label={ Text(tr(if(kind=="pressure") "Systolic (mmHg)" else if(kind=="pulse") "Pulse (bpm)" else "Weight value")) })
    if(kind=="pressure") OutlinedTextField(lower,{ lower=it },label={ Text(tr("Diastolic (mmHg)")) })
    if(kind=="weight") {
        Row { listOf("kg","lb").forEach { u -> FilterChip(unit==u,{ unit=u },label={ Text(u) }) } }
        Row { Checkbox(conversion,{ conversion=it },Modifier.semantics { contentDescription=tr("Show converted value") });Text(tr("Show converted value")) }
        if(conversion) parseDose(value)?.let { v -> val to=if(unit=="kg") "lb" else "kg";runCatching { MeasurementRules.weight(v,unit,to) }.getOrNull()?.let { Text("≈ "+doseText(it)+" "+to) } }
        Text(tr("The entered value and unit are retained. Conversion is display only."))
    }
    TimeField("Measured at",time) { time=it }
    OutlinedTextField(notes,{ notes=it.take(5000) },label={ Text(tr("Notes (optional)")) },minLines=2)
    Text(tr("Manual records only. No clinical interpretation is provided."))
    Button({ val at=Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());save(Measurement(id,kind,parseDose(value)!!,unit,if(kind=="pressure") parseDose(lower) else null,time,original?.createdAt ?: System.currentTimeMillis(),if(original?.timestamp==time) original.zoneId else at.zone.id,if(original?.timestamp==time) original.offset else at.offset.id,notes)) },enabled=!busy && parseDose(value)!=null && (kind!="pressure" || parseDose(lower)!=null) && time<=System.currentTimeMillis()) { Text(tr("Save")) }
    if(original!=null) TextButton({ confirm=true }) { Text(tr("Delete record")) }
    if(confirm) AlertDialog(onDismissRequest={ confirm=false },title={ Text(tr("Delete record?")) },confirmButton={ TextButton({ delete(id) }) { Text(tr("Delete")) } },dismissButton={ TextButton({ confirm=false }) { Text(tr("Cancel")) } })
}
fun weekStart(date: LocalDate)=date.with(WeekFields.of(Locale.getDefault()).dayOfWeek(),1)
fun weekBounds(start: LocalDate,zone: ZoneId)= (0L..7L).map { start.plusDays(it).atStartOfDay(zone).toInstant().toEpochMilli() }
@Composable fun WeeklyView(document: BackupDocument) {
    var selected by rememberSaveable { mutableStateOf(weekStart(LocalDate.now()).toString()) }
    val context=LocalContext.current;val zone=ZoneId.systemDefault();val start=LocalDate.parse(selected)
    val week=WeeklyBuilder.build(document,weekBounds(start,zone))
    Text(tr("Weekly overview"),style=MaterialTheme.typography.headlineSmall)
    Text("$start – ${start.plusDays(6)} · ${zone.id}")
    Row { TextButton({ selected=start.minusWeeks(1).toString() }) { Text(tr("Previous week")) };TextButton({ selected=start.plusWeeks(1).toString() }) { Text(tr("Next week")) } }
    OutlinedButton({ android.app.DatePickerDialog(context,{ _,y,m,d -> selected=weekStart(LocalDate.of(y,m+1,d)).toString() },start.year,start.monthValue-1,start.dayOfMonth).show() }) { Text(tr("Choose week")) }
    SummaryPreview(week.summary)
    Text(tr("Observation distributions"),style=MaterialTheme.typography.titleMedium)
    week.distributions.forEach { Text(tr(categoryName(it.category))+" · "+tr(responseName(it.response))+(it.value?.let { " $it / 4" } ?: "")+": ${it.count}") }
    Text(tr("Legacy dose mood (separate from observations)"))
    week.legacyMoods.forEach { Text(tr(moodLabels[it.value])+": ${it.count}") }
    week.days.forEach { day ->
        HorizontalDivider();Text(Instant.ofEpochMilli(day.start).atZone(zone).toLocalDate().toString(),style=MaterialTheme.typography.titleMedium)
        if(day.doses.isEmpty() && day.observations.isEmpty() && day.measurements.isEmpty() && day.nonUse.isEmpty()) Text(tr("No records. Status unknown."))
        day.doses.forEach { Text(Instant.ofEpochMilli(it.timestamp).atZone(zone).format(timeFormat(context))+" · ${it.medicationName} · ${it.formulation} · ${it.strength} · ${doseText(it.doseMg)} ${it.unit}");if(it.notes.isNotBlank()) Text(it.notes) }
        day.observations.forEach { Text(Instant.ofEpochMilli(it.timestamp).atZone(zone).format(timeFormat(context))+" · "+tr(categoryName(it.category))+" · "+tr(responseName(it.response))+(it.value?.let { " $it / 4" } ?: ""));if(it.notes.isNotBlank()) Text(it.notes) }
        day.measurements.forEach { Text(Instant.ofEpochMilli(it.timestamp).atZone(zone).format(timeFormat(context))+" · "+measurementText(it));if(it.notes.isNotBlank()) Text(it.notes) }
        day.nonUse.forEach { Text(tr("Not taken")+" · "+(document.medications.find { med->med.id==it.medicationId }?.name ?: "")+" · "+Instant.ofEpochMilli(it.start).atZone(zone)+" – "+Instant.ofEpochMilli(it.end).atZone(zone));if(it.notes.isNotBlank()) Text(it.notes) }
    }
}

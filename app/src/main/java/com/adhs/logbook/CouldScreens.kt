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

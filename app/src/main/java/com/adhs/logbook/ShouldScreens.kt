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
@Composable private fun DeleteRecord(remove: ()->Unit) {
    var confirm by remember { mutableStateOf(false) }
    TextButton({ confirm=true }) { Text(tr("Delete record")) }
    if(confirm) AlertDialog(onDismissRequest={ confirm=false },title={ Text(tr("Delete record?")) },text={ Text(tr("This cannot be undone.")) },confirmButton={ TextButton({ confirm=false;remove() }) { Text(tr("Delete")) } },dismissButton={ TextButton({ confirm=false }) { Text(tr("Cancel")) } })
}

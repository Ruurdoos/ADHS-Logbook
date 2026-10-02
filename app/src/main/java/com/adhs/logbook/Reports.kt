package com.adhs.logbook

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.adhs.logbook.shared.*
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class ReportFormat { PDF, CSV }

object Reports {
    fun inRange(entries: List<DoseEntry>, start: LocalDate, end: LocalDate, zone: ZoneId): List<DoseEntry> {
        require(!end.isBefore(start))
        val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val until = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return entries.filter { it.timestamp >= from && it.timestamp < until }.sortedBy { it.timestamp }
    }

    fun create(context: Context, all: List<DoseEntry>, start: LocalDate, end: LocalDate, format: ReportFormat, zone: ZoneId = ZoneId.systemDefault()): File {
        val entries = inRange(all,start,end,zone)
        require(entries.isNotEmpty()) { "No entries in this date range." }
        val directory = File(context.cacheDir,"reports").apply { mkdirs() }
        // Retain recent reports so another app can finish reading a shared URI.
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 7 * 86_400_000L }?.forEach { it.delete() }
        val file = File(directory,"ADHS-logbook-${start}-${UUID.randomUUID().toString().take(8)}.${format.name.lowercase()}")
        try {
            when(format) {
                ReportFormat.CSV -> file.bufferedWriter(Charsets.UTF_8).use { out ->
                    out.write(Csv.row(listOf("entry_id","medication","formulation","dose","dose_unit","strength","model_id","timestamp_iso8601","timezone","mood","notes")))
                    entries.forEach { entry ->
                        val recorded = Instant.ofEpochMilli(entry.timestamp).atOffset(ZoneOffset.of(entry.offset))
                        out.write(Csv.row(listOf(entry.id.toString(),entry.medicationName,entry.formulation,entry.doseMg.toString(),entry.unit,entry.strength,entry.modelId ?: "",recorded.toString(),entry.zoneId,entry.mood?.let { tr(moodLabels[it]) } ?: "",entry.notes)))
                    }
                }
                ReportFormat.PDF -> writePdf(file,all,entries,start,end,zone,timeFormat(context))
            }
        } catch(e: Exception) { file.delete(); throw e }
        return file
    }

    fun createDocument(context: Context,document: BackupDocument,start: LocalDate,end: LocalDate,format: ReportFormat,summary: Boolean,includeNotes: Boolean,questions: String,zone: ZoneId=ZoneId.systemDefault()): File {
        require(!end.isBefore(start) && java.time.temporal.ChronoUnit.DAYS.between(start,end)<367 && questions.length<=5000)
        val doc=if(includeNotes) document else document.copy(measurements=document.measurements.map { it.copy(notes="") },entries=document.entries.map { it.copy(notes="") },observations=document.observations.map { it.copy(notes="") },nonUse=document.nonUse.map { it.copy(notes="") })
        val entries=inRange(doc.entries,start,end,zone)
        val a=start.atStartOfDay(zone).toInstant().toEpochMilli();val b=end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val folder=File(context.cacheDir,"reports").apply { mkdirs() }
        folder.listFiles()?.filter { System.currentTimeMillis()-it.lastModified()>7*86400000L }?.forEach { it.delete() }
        val file=File(folder,"ADHS-report-${UUID.randomUUID()}.${format.name.lowercase()}")
        try {
            if(format==ReportFormat.PDF) writePdf(file,doc.entries,entries,start,end,zone,timeFormat(context),doc,summary,questions)
            else file.bufferedWriter().use { out ->
                val header=listOf("schema_version","record_type","record_id","medication_id","medication","formulation","amount","unit","timestamp_iso8601","end_timestamp_iso8601","timezone","category","response","scale_version","value","sleep_date","linked_dose_id","dose_mood","notes","strength","model_id","stock_units","measurement_kind","measurement_value","systolic","diastolic","created_at_iso8601")
                out.write(Csv.row(header))
                fun timestamp(time: Long,offset: String)=Instant.ofEpochMilli(time).atOffset(ZoneOffset.of(offset)).toString()
                fun write(values: Map<String,String>) { out.write(Csv.row(header.map { if(it=="schema_version") "3" else values[it] ?: "" })) }
                entries.forEach { e -> write(mapOf("record_type" to "dose","record_id" to e.id.toString(),"medication_id" to e.medicationId.toString(),"medication" to e.medicationName,"formulation" to e.formulation,"amount" to e.doseMg.toString(),"unit" to e.unit,"timestamp_iso8601" to timestamp(e.timestamp,e.offset),"timezone" to e.zoneId,"dose_mood" to (e.mood?.toString() ?: ""),"notes" to e.notes,"strength" to e.strength,"model_id" to (e.modelId ?: ""),"stock_units" to (e.supplyUnits?.toString() ?: ""))) }
                doc.observations.filter { it.timestamp>=a && it.timestamp<b }.forEach { o -> write(mapOf("record_type" to "observation","record_id" to o.id,"timestamp_iso8601" to timestamp(o.timestamp,o.offset),"timezone" to o.zoneId,"category" to o.category,"response" to o.response,"scale_version" to o.scaleVersion.toString(),"value" to (o.value?.toString() ?: ""),"sleep_date" to (o.sleepDate ?: ""),"linked_dose_id" to (o.doseId?.toString() ?: ""),"notes" to o.notes)) }
                doc.measurements.filter { it.timestamp>=a && it.timestamp<b }.forEach { m -> write(mapOf("record_type" to "measurement","record_id" to m.id,"measurement_kind" to m.kind,"measurement_value" to if(m.kind=="pressure") "" else m.value.toString(),"systolic" to if(m.kind=="pressure") m.value.toString() else "","diastolic" to (m.diastolic?.toString() ?: ""),"unit" to m.unit,"timestamp_iso8601" to timestamp(m.timestamp,m.offset),"created_at_iso8601" to Instant.ofEpochMilli(m.createdAt).toString(),"timezone" to m.zoneId,"notes" to m.notes)) }
                doc.nonUse.filter { it.start<b && it.end>=a }.forEach { n -> write(mapOf("record_type" to "non_use","record_id" to n.id,"medication_id" to n.medicationId.toString(),"medication" to (doc.medications.find { it.id==n.medicationId }?.name ?: ""),"timestamp_iso8601" to timestamp(n.start,n.offset),"end_timestamp_iso8601" to timestamp(n.end,n.offset),"timezone" to n.zoneId,"notes" to n.notes)) }
            }
        } catch(e: Exception) { file.delete();throw e }
        return file
    }

    fun share(context: Context, file: File, format: ReportFormat) {
        val uri = FileProvider.getUriForFile(context,"${context.packageName}.files",file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if(format == ReportFormat.PDF) "application/pdf" else "text/csv"
            putExtra(Intent.EXTRA_STREAM,uri)
            clipData = ClipData.newRawUri("Medication log",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent,tr("Export medication log")))
    }

    private fun writePdf(file: File, all: List<DoseEntry>, entries: List<DoseEntry>, start: LocalDate, end: LocalDate, zone: ZoneId, clockFormat: DateTimeFormatter,doc: BackupDocument?=null,summary: Boolean=false,questions: String="") {
        val document = PdfDocument()
        try {
            val writer = PdfWriter(document,start,end,zone,clockFormat)
            if(summary && doc!=null) {
                writer.heading(tr("Summary with details"))
                val boundaries=generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end.plusDays(1)) }.map { it.atStartOfDay(zone).toInstant().toEpochMilli() }.toList()
                summaryLines(SummaryBuilder.build(doc,boundaries)).forEach(writer::paragraph)
                if(questions.isNotBlank()) { writer.heading(tr("Questions for my appointment"));writer.paragraph(questions) }
            }
            val grouped = entries.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            var date = start
            while(!date.isAfter(end)) {
                val day = grouped[date].orEmpty()
                writer.heading(date.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")))
                if(day.isEmpty()) writer.paragraph(tr("No doses logged. This does not confirm that no medication was taken."))
                else {
                    day.distinctBy { it.medicationId to it.modelId }.forEach { sample ->
                        val preset=sample.preset
                        writer.paragraph(sample.medicationName)
                        if(sample.modelId != null) {
                            val a = date.atStartOfDay(zone).toInstant().toEpochMilli()
                            val b = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                            writer.chart(Estimate.series(all.filter { it.medicationId==sample.medicationId && it.modelId==sample.modelId },preset,a,b))
                        } else writer.paragraph(tr("Estimate unavailable. Logged doses are shown below."))
                    }
                }
                val from=date.atStartOfDay(zone).toInstant().toEpochMilli();val until=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val details=mutableListOf<Pair<Long,()->Unit>>()
                day.forEach { entry -> details += entry.timestamp to { writer.tableHeader();writer.entry(entry,zone) } }
                doc?.observations?.filter { it.timestamp>=from && it.timestamp<until }?.forEach { o ->
                    details += o.timestamp to {
                        writer.paragraph(Instant.ofEpochMilli(o.timestamp).atZone(zone).format(clockFormat)+" · "+tr(categoryName(o.category))+" · "+tr(responseName(o.response))+(o.value?.let { " $it / 4" } ?: "")+(o.sleepDate?.let { " · $it" } ?: ""))
                        if(o.notes.isNotBlank()) writer.paragraph(o.notes)
                    }
                }
                doc?.nonUse?.filter { it.start<until && it.end>=from }?.forEach { n ->
                    details += n.start to {
                        writer.paragraph(tr("Not taken")+" · "+(doc.medications.find { it.id==n.medicationId }?.name ?: "")+" · "+Instant.ofEpochMilli(n.start).atZone(zone)+" – "+Instant.ofEpochMilli(n.end).atZone(zone))
                        if(n.notes.isNotBlank()) writer.paragraph(n.notes)
                    }
                }
                doc?.measurements?.filter { it.timestamp>=from && it.timestamp<until }?.forEach { m -> details += m.timestamp to {
                    writer.paragraph(Instant.ofEpochMilli(m.timestamp).atZone(zone).format(clockFormat)+" · "+measurementText(m));if(m.notes.isNotBlank()) writer.paragraph(m.notes)
                } }
                details.sortedBy { it.first }.forEach { it.second() }
                date = date.plusDays(1)
            }
            writer.finish()
            file.outputStream().use { document.writeTo(it) }
        } finally { document.close() }
    }
}

private class PdfWriter(private val document: PdfDocument, private val start: LocalDate, private val end: LocalDate, private val zone: ZoneId, private val clockFormat: DateTimeFormatter) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(36,53,45); textSize = 10f }
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var y = 0f
    private val canvas get() = page!!.canvas
    init { newPage() }
    private fun line(text: String, x: Float = 40f, size: Float = 10f) {
        paint.textSize = size; canvas.drawText(text,x,y,paint)
    }
    private fun newPage() {
        finish()
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber).create())
        y = 40f; line(tr("ADHS Logbook — Medication log"),size = 18f)
        y += 22; line(tr("%s to %s | %s",start,end,zone.id))
        y += 17; line(tr("Generated %s | Model: relative-heuristic-v1",LocalDate.now()),size = 9f)
        y += 20; line(tr("Rough relative estimates, not measured blood levels or dosing guidance."),size = 9f)
        y += 14; line(tr("Curves are scaled separately. They cannot compare medications or days."),size = 9f)
        y += 25
    }
    fun finish() {
        page?.let {
            paint.textSize = 9f
            it.canvas.drawText(tr("Private health record · Page %d",pageNumber),40f,812f,paint)
            document.finishPage(it)
        }
        page = null
    }
    private fun ensure(height: Float) { if(y + height > 775) newPage() }
    fun heading(text: String) { ensure(60f); y += 8; line(text,size = 13f); y += 24 }
    fun paragraph(text: String) {
        wrap(text,510f).forEach { ensure(16f); line(it); y += 15 }
        y += 8
    }
    private fun wrap(text: String, width: Float): List<String> {
        paint.textSize = 10f
        return text.split('\n').flatMap { raw ->
            if(raw.isEmpty()) listOf("") else buildList {
                var rest = raw
                while(rest.isNotEmpty()) {
                    var count = paint.breakText(rest,true,width,null).coerceAtLeast(1)
                    if(count < rest.length) {
                        val boundary = rest.lastIndexOf(' ',count - 1)
                        if(boundary > 0) count = boundary
                    }
                    add(rest.take(count)); rest = rest.drop(count).trimStart()
                }
            }
        }
    }
    fun chart(values: List<Double>) {
        ensure(140f)
        val top = y; val bottom = y + 95
        val max = values.maxOrNull()?.coerceAtLeast(0.001) ?: 1.0
        val path = Path()
        values.forEachIndexed { i,v ->
            val x = 40f + i * 510f / (values.size - 1)
            val py = bottom - (v / max * 85).toFloat()
            if(i == 0) path.moveTo(x,py) else path.lineTo(x,py)
        }
        paint.color = Color.rgb(218,225,218); paint.strokeWidth = 1f
        canvas.drawLine(40f,bottom,550f,bottom,paint)
        paint.color = Color.rgb(65,107,89); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        canvas.drawPath(path,paint); paint.style = Paint.Style.FILL
        paint.color = Color.rgb(36,53,45); y = top + 113
        line(tr("Start of day"),size = 9f); line(tr("End of day"),x=498f,size=9f)
        y += 25
    }
    fun tableHeader() {
        ensure(44f)
        line(tr("Time"),40f); line(tr("Medication"),100f); line(tr("Dose"),330f); line(tr("Mood"),410f)
        y += 9; paint.color = Color.LTGRAY; canvas.drawLine(40f,y,550f,y,paint)
        paint.color = Color.rgb(36,53,45); y += 19
    }
    fun entry(entry: DoseEntry, zone: ZoneId) {
        val medicationLines = wrap(listOf(entry.medicationName, entry.strength, entry.formulation.takeIf { entry.preset==Preset.CUSTOM } ?: "").filter { it.isNotBlank() }.joinToString(" · "),218f)
        val noteLines = if(entry.notes.isBlank()) emptyList() else wrap(tr("Note: %s",entry.notes),495f)
        val rowHeight = medicationLines.size * 15f + 12f
        if(y + rowHeight > 775) { newPage(); tableHeader() }
        line(Instant.ofEpochMilli(entry.timestamp).atZone(zone).format(clockFormat),40f)
        line("${doseText(entry.doseMg)} ${tr(entry.unit)}",330f)
        line(entry.mood?.let { tr(moodLabels[it]) } ?: "—",410f)
        medicationLines.forEach { line(it,100f); y += 15 }
        noteLines.forEach { text ->
            if(y + 17 > 775) { newPage(); tableHeader(); paragraph(tr("Continued notes for entry %d",entry.id)) }
            line(text,50f); y += 15
        }
        y += 12
    }
}

fun doseText(dose: Double): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.getDefault()).apply { isGroupingUsed=false;maximumFractionDigits=10 }.format(dose)

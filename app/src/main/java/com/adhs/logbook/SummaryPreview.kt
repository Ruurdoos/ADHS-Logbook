package com.adhs.logbook

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import com.adhs.logbook.shared.*

fun summaryLines(summary: ReportSummary): List<String> = buildList {
    add(tr("Recorded doses: %d",summary.doses))
    add(tr("Days with dose records: %d / %d",summary.doseDays,summary.days))
    add(tr("Recorded measurements: %d",summary.measurements))
    add(tr("Days with measurements: %d / %d",summary.measurementDays,summary.days))
    add(tr("Days with observations: %d / %d",summary.observationDays,summary.days))
    add(tr("Days with explicit non-use records: %d / %d",summary.nonUseDays,summary.days))
    add(tr("Days without records: %d / %d",summary.daysWithoutRecords,summary.days))
    add(tr("Missing records are unknown, not confirmed non-use. Counts do not measure adherence or causation."))
    summary.medications.forEach { add("${it.name} · ${it.formulation} · ${it.strength}: "+tr("%d recorded doses; total %s %s",it.count,doseText(it.total),tr(it.unit))) }
    summary.observations.forEach { add(tr(categoryName(it.category))+": "+tr("%d records: %d rated, %d none, %d unsure, %d recorded times",it.count,it.rated,it.none,it.unsure,it.recorded)) }
}
@Composable fun SummaryPreview(summary: ReportSummary) { summaryLines(summary).forEach { Text(it) } }

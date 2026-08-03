package com.example.adhslogbook.ui.screens.timeline

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.adhslogbook.data.model.ActivityEntry
import com.example.adhslogbook.R
import com.example.adhslogbook.navigation.FocusLogDestination
import com.example.adhslogbook.navigation.ProductDestinations
import com.example.adhslogbook.ui.components.FocusLogBottomBar
import com.example.adhslogbook.ui.components.FocusLogTopBar
import com.example.adhslogbook.ui.theme.FocusLogTheme
import com.example.adhslogbook.ui.viewmodels.LogbookViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

@Composable
fun TimelineRoute(
    viewModel: LogbookViewModel,
    currentDestination: FocusLogDestination,
    onNavigate: (FocusLogDestination) -> Unit,
    onHomeClick: () -> Unit,
) {
    val entries by viewModel.timelineEntries.collectAsStateWithLifecycle()
    val date by viewModel.selectedDate.collectAsStateWithLifecycle()
    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { FocusLogTopBar(onHomeClick) },
        bottomBar = { FocusLogBottomBar(ProductDestinations, currentDestination, onNavigate) },
    ) { padding ->
        TimelineContent(
            entries = entries,
            date = date,
            currentDate = currentDate,
            onDateChange = viewModel::selectDate,
            modifier = Modifier.padding(padding).fillMaxSize(),
        )
    }
}

@Composable
private fun TimelineContent(
    entries: List<ActivityEntry>,
    date: LocalDate,
    currentDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val spacing = FocusLogTheme.spacing
    val firstDose = entries.filterIsInstance<ActivityEntry.DoseTaken>().minByOrNull { it.timestamp }
    val chartLogs = entries.filterIsInstance<ActivityEntry.CheckInEntry>()
        .filter { firstDose != null && it.timestamp >= firstDose.timestamp }
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.page),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item { DateSwitcher(date, currentDate, onDateChange) }
        if (firstDose != null && chartLogs.isNotEmpty()) {
            item { FocusChart(firstDose.timestamp, chartLogs) }
        }
        item { Text(stringResource(R.string.activity_log), style = MaterialTheme.typography.titleLarge) }
        if (entries.isEmpty()) {
            item { Text(stringResource(R.string.no_date_records), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            entries.forEach { entry -> item(key = "${entry::class.simpleName}-${entry.timestamp}") {
                TimelineRecord(entry)
            } }
        }
    }
}

@Composable
private fun DateSwitcher(
    date: LocalDate,
    today: LocalDate,
    onDateChange: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onDateChange(date.minusDays(1)) }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.previous_day))
        }
        Text(
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(
            onClick = { onDateChange(date.plusDays(1)) },
            enabled = date.isBefore(today),
        ) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.next_day))
        }
    }
}

@Composable
private fun FocusChart(firstDose: Long, logs: List<ActivityEntry.CheckInEntry>) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val elapsed = logs.map { (it.timestamp - firstDose).coerceAtLeast(0) / 60_000f }
    val maximum = elapsed.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 1.dp) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.recorded_focus), style = MaterialTheme.typography.titleMedium)
            Box(Modifier.fillMaxWidth().height(180.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    repeat(3) { index ->
                        val y = size.height * index / 2f
                        drawLine(grid, Offset(0f, y), Offset(size.width, y))
                    }
                    val points = logs.mapIndexed { index, entry ->
                        Offset(
                            x = size.width * elapsed[index] / maximum,
                            y = size.height * (1f - entry.log.focusLevel.coerceIn(0, 100) / 100f),
                        )
                    }
                    points.zipWithNext().forEach { (start, end) ->
                        drawLine(color, start, end, strokeWidth = 7f, cap = StrokeCap.Round)
                    }
                    points.forEach { drawCircle(color, radius = 7f, center = it) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.dose_elapsed), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.minutes_elapsed, maximum.toInt()), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TimelineRecord(entry: ActivityEntry) {
    val context = LocalContext.current
    val time = DateFormat.getTimeFormat(context).format(Date(entry.timestamp))
    val title: String
    val details: List<String>
    when (entry) {
        is ActivityEntry.DoseTaken -> {
            title = "Dose — $time"
            details = listOf(
                entry.dose.medicationName,
                "${entry.dose.doseMg} mg • ${entry.dose.releaseType}",
            )
        }
        is ActivityEntry.CheckInEntry -> {
            title = "Check-in — $time"
            details = buildList {
                add("Focus ${entry.log.focusLevel} • Mood ${entry.log.moodLevel} • Energy ${entry.log.energyLevel}")
                if (entry.log.tags.isNotBlank()) add("Tags: ${entry.log.tags}")
                if (entry.log.notes.isNotBlank()) add("Notes: ${entry.log.notes}")
            }
        }
        is ActivityEntry.SideEffectEntry -> {
            title = "Side effect — $time"
            details = listOf(entry.log.effectName)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            details.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

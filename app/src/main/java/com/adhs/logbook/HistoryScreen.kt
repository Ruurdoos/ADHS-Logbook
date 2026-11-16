package com.adhs.logbook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adhs.logbook.shared.DoseEntry
import com.adhs.logbook.shared.moodFaces
import com.adhs.logbook.shared.moodLabels
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.launch

internal enum class HistoryRange { WEEK, MONTH }


/** Days shown in the heatmap: a rolling 7-day window ending at [anchor], or the calendar month of [anchor]. */
internal fun historyDays(range: HistoryRange, anchor: LocalDate): List<LocalDate> = when (range) {
    HistoryRange.WEEK -> (6L downTo 0L).map { anchor.minusDays(it) }
    HistoryRange.MONTH -> YearMonth.from(anchor).let { month -> (1..month.lengthOfMonth()).map(month::atDay) }
}

/** Heat intensity: 0 = nothing logged, 1–3 = increasing number of doses (3 means three or more). */
internal fun heatLevel(count: Int) = count.coerceIn(0, 3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    entries: List<DoseEntry>,
    onEdit: (Long) -> Unit,
    onWeekly: (() -> Unit)?
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val timeFormat = timeFormat(context)
    var rangeName by rememberSaveable { mutableStateOf(HistoryRange.WEEK.name) }
    var anchorText by rememberSaveable { mutableStateOf(today.toString()) }
    var selectedText by rememberSaveable { mutableStateOf(today.toString()) }
    var medicationFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var medicationMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val range = HistoryRange.valueOf(rangeName)
    val anchor = LocalDate.parse(anchorText)
    val selected = LocalDate.parse(selectedText)

    fun dayOf(entry: DoseEntry) = Instant.ofEpochMilli(entry.timestamp).atZone(zone).toLocalDate()
    val medications = remember(entries) { entries.distinctBy { it.medicationId } }
    val filtered = remember(entries, medicationFilter) {
        entries.filter { medicationFilter == null || it.medicationId == medicationFilter }
    }
    val byDay = remember(filtered, zone) {
        filtered.groupBy(::dayOf).mapValues { (_, list) -> list.sortedBy { it.timestamp } }
    }
    val days = historyDays(range, anchor)
    val pastDays = days.filter { !it.isAfter(today) }
    val canGoForward = days.last().isBefore(today)

    fun move(forward: Boolean) {
        val next = when (range) {
            HistoryRange.WEEK -> if (forward) anchor.plusDays(7) else anchor.minusDays(7)
            HistoryRange.MONTH -> if (forward) anchor.plusMonths(1) else anchor.minusMonths(1)
        }.let { minOf(it, today) }
        anchorText = next.toString()
        selectedText = minOf(historyDays(range, next).last(), today).toString()
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        state = listState,
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(tr("History"), fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
        }
        onWeekly?.let { item { OutlinedButton(it) { Text(tr("Weekly overview")) } } }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                HistoryRange.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = range == option,
                        onClick = {
                            rangeName = option.name
                            anchorText = selectedText
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, HistoryRange.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onSurface,
                            inactiveContainerColor = Color.Transparent
                        )
                    ) { Text(tr(if (option == HistoryRange.WEEK) "7 days" else "Month")) }
                }
            }
        }
        item(key = "heatmap") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ move(false) }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                tr(if (range == HistoryRange.WEEK) "Previous 7 days" else "Previous month")
                            )
                        }
                        TextButton(
                            {
                                pickDate(context, selected) {
                                    val day = minOf(it, today)
                                    anchorText = day.toString(); selectedText = day.toString()
                                }
                            },
                            Modifier.weight(1f)
                        ) {
                            Text(periodLabel(range, days), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        }
                        IconButton({ move(true) }, enabled = canGoForward) {
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                tr(if (range == HistoryRange.WEEK) "Next 7 days" else "Next month")
                            )
                        }
                    }
                    Heatmap(range, days, today, selected, { byDay[it]?.size ?: 0 }) {
                        selectedText = it.toString(); query = ""
                        // The month grid is tall; align it to the top so the day's details become visible.
                        if (range == HistoryRange.MONTH) scope.launch { listState.animateScrollToItem(if (onWeekly == null) 2 else 3) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("Days with dose records: %d / %d", pastDays.count { byDay.containsKey(it) }, pastDays.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Legend()
                    }
                }
            }
        }
        if (medications.size > 1) item {
            Box {
                OutlinedButton({ medicationMenu = true }) {
                    Text(
                        medications.firstOrNull { it.medicationId == medicationFilter }?.medicationName
                            ?: tr("All medications")
                    )
                }
                DropdownMenu(medicationMenu, { medicationMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("All medications")) },
                        onClick = { medicationFilter = null; medicationMenu = false })
                    medications.forEach { e ->
                        DropdownMenuItem(
                            text = { Text(e.medicationName) },
                            onClick = { medicationFilter = e.medicationId; medicationMenu = false })
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton({ query = "" }) { Icon(Icons.Outlined.Close, tr("Clear search")) } }
                } else null,
                label = { Text(tr("Search medications and notes")) })
        }
        if (query.isNotBlank()) {
            val matches = filtered
                .filter { it.medicationName.contains(query, true) || it.notes.contains(query, true) }
                .sortedByDescending { it.timestamp }
                .groupBy(::dayOf)
            if (matches.isEmpty()) item { EmptyHint(tr("No matching entries.")) }
            matches.forEach { (date, dayEntries) ->
                item(key = "search-$date") { DayHeading(date, today) }
                items(dayEntries, key = { "search-${it.id}" }) { EntryCard(it, zone, timeFormat, onEdit) }
            }
        } else {
            val dayEntries = byDay[selected].orEmpty()
            item(key = "selected-day") { DayHeading(selected, today, dayEntries.size) }
            if (dayEntries.isEmpty()) item {
                EmptyHint(tr(if (entries.isEmpty()) "No doses logged yet." else "No doses logged for this day."))
            }
            items(dayEntries, key = { it.id }) { EntryCard(it, zone, timeFormat, onEdit) }
        }
        item {
            Text(
                tr("Times displayed in %s.", zone.id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun periodLabel(range: HistoryRange, days: List<LocalDate>): String {
    val locale = Locale.getDefault()
    return when (range) {
        HistoryRange.MONTH -> days.first().format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
            .replaceFirstChar { it.titlecase(locale) }
        HistoryRange.WEEK -> "${days.first().display()} – ${days.last().display()}"
    }
}

@Composable
private fun Heatmap(
    range: HistoryRange,
    days: List<LocalDate>,
    today: LocalDate,
    selected: LocalDate,
    countFor: (LocalDate) -> Int,
    onSelect: (LocalDate) -> Unit
) {
    val locale = Locale.getDefault()
    fun weekdayName(day: DayOfWeek) = day.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
    val cell: @Composable (LocalDate, Modifier) -> Unit = { date, modifier ->
        DayCell(date, countFor(date), date == selected, date == today, !date.isAfter(today), modifier) {
            onSelect(date)
        }
    }
    when (range) {
        HistoryRange.WEEK -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { date ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WeekdayLabel(weekdayName(date.dayOfWeek))
                    cell(date, Modifier.fillMaxWidth())
                }
            }
        }

        HistoryRange.MONTH -> {
            val firstDay = WeekFields.of(locale).firstDayOfWeek
            val leading = (days.first().dayOfWeek.value - firstDay.value + 7) % 7
            val slots: List<LocalDate?> = List(leading) { null } + days
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0L until 7L).forEach {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            WeekdayLabel(weekdayName(firstDay.plus(it)))
                        }
                    }
                }
                slots.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0 until 7).forEach { i ->
                            val date = week.getOrNull(i)
                            if (date == null) Spacer(Modifier.weight(1f))
                            else cell(date, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

@Composable
private fun heatColor(level: Int): Color {
    val scheme = MaterialTheme.colorScheme
    return when (level) {
        0 -> scheme.onSurface.copy(alpha = 0.07f)
        1 -> scheme.primary.copy(alpha = 0.35f)
        2 -> scheme.primary.copy(alpha = 0.65f)
        else -> scheme.primary
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    count: Int,
    isSelected: Boolean,
    isToday: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val level = heatLevel(count)
    val shape = RoundedCornerShape(8.dp)
    val description = "${date.displayWithWeekday()}, " +
            if (count == 0) tr("No doses") else tr("Doses: %d", count)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (enabled) heatColor(level) else Color.Transparent)
            .then(
                when {
                    isSelected -> Modifier.border(2.dp, scheme.onSurface, shape)
                    isToday -> Modifier.border(1.dp, scheme.primary, shape)
                    else -> Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                selected = isSelected
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            date.dayOfMonth.toString(),
            fontSize = 13.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
                level >= 2 -> scheme.onPrimary
                else -> scheme.onSurface
            }
        )
    }
}

@Composable
private fun Legend() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {}
    ) {
        Text(tr("Less"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (0..3).forEach {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(heatColor(it))
            )
        }
        Text(tr("More"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayHeading(date: LocalDate, today: LocalDate, count: Int? = null) {
    val label = when (date) {
        today -> "${tr("Today")}, ${date.display()}"
        today.minusDays(1) -> "${tr("Yesterday")}, ${date.display()}"
        else -> date.displayWithWeekday()
    }
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (count != null && count > 0) Text(
            tr("Doses: %d", count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp)
    )
}

@Composable
private fun EntryCard(
    entry: DoseEntry,
    zone: ZoneId,
    timeFormat: DateTimeFormatter,
    onEdit: (Long) -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = { onEdit(entry.id) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Instant.ofEpochMilli(entry.timestamp).atZone(zone).format(timeFormat),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(72.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.medicationName, fontWeight = FontWeight.Medium)
                Text(
                    listOf("${doseText(entry.doseMg)} ${tr(entry.unit)}", entry.strength)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted
                )
                if (entry.notes.isNotBlank()) Text(
                    entry.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4
                )
            }
            entry.mood?.let {
                Text(
                    moodFaces[it],
                    fontSize = 22.sp,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .semantics { contentDescription = tr(moodLabels[it]) })
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = muted)
        }
    }
}

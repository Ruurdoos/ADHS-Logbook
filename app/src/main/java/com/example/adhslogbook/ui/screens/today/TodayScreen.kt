package com.example.adhslogbook.ui.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.adhslogbook.data.model.ActivityEntry
import com.example.adhslogbook.R
import com.example.adhslogbook.data.model.MedicationStatus
import com.example.adhslogbook.navigation.FocusLogDestination
import com.example.adhslogbook.navigation.ProductDestinations
import com.example.adhslogbook.ui.components.FocusLogBottomBar
import com.example.adhslogbook.ui.components.FocusLogTopBar
import com.example.adhslogbook.ui.components.ScreenStateHost
import com.example.adhslogbook.ui.theme.FocusLogTheme
import com.example.adhslogbook.ui.viewmodels.LogbookViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext

@Composable
fun TodayRoute(
    viewModel: LogbookViewModel,
    currentDestination: FocusLogDestination,
    onNavigate: (FocusLogDestination) -> Unit,
    onHomeClick: () -> Unit,
) {
    val state by viewModel.todayContent.collectAsStateWithLifecycle()
    val entries by viewModel.timelineEntries.collectAsStateWithLifecycle()
    val date by viewModel.selectedDate.collectAsStateWithLifecycle()
    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()
    val medicationName by viewModel.medicationName.collectAsStateWithLifecycle()
    val doseMg by viewModel.doseMg.collectAsStateWithLifecycle()
    val hasEdits by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val doseSaving by viewModel.doseSaving.collectAsStateWithLifecycle()
    val checkInSaving by viewModel.checkInSaving.collectAsStateWithLifecycle()
    val message by viewModel.snackbar.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }
    Scaffold(
        topBar = { FocusLogTopBar(onHomeClick = onHomeClick) },
        bottomBar = {
            FocusLogBottomBar(ProductDestinations, currentDestination, onNavigate)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        ScreenStateHost(
            state = state,
            modifier = Modifier.padding(padding).fillMaxSize(),
            loadingMessage = stringResource(R.string.loading_logbook),
            emptyMessage = stringResource(R.string.nothing_recorded),
        ) { content ->
            TodayContent(
                modifier = Modifier.fillMaxSize(),
                content = content,
                entries = entries,
                selectedDate = date,
                currentDate = currentDate,
                configuredName = medicationName,
                configuredDose = doseMg,
                hasEdits = hasEdits,
                doseSaving = doseSaving,
                checkInSaving = checkInSaving,
                onDateChange = viewModel::selectDate,
                onSaveMedication = viewModel::saveMedication,
                onLogDose = viewModel::logDose,
                onMetricChange = viewModel::updateMetric,
                onToggleTag = viewModel::toggleTag,
                onAddTag = viewModel::addTag,
                onNotesChange = viewModel::updateNotes,
                onLogCheckIn = viewModel::logCheckIn,
                onLogSideEffect = viewModel::logSideEffect,
            )
        }
    }
}

@Composable
private fun TodayContent(
    modifier: Modifier,
    content: com.example.adhslogbook.data.model.TodayContent,
    entries: List<ActivityEntry>,
    selectedDate: LocalDate,
    currentDate: LocalDate,
    configuredName: String,
    configuredDose: Int,
    hasEdits: Boolean,
    doseSaving: Boolean,
    checkInSaving: Boolean,
    onDateChange: (LocalDate) -> Unit,
    onSaveMedication: (String, Int) -> Unit,
    onLogDose: () -> Unit,
    onMetricChange: (com.example.adhslogbook.data.model.MetricType, Float) -> Unit,
    onToggleTag: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onLogCheckIn: () -> Unit,
    onLogSideEffect: (String) -> Unit,
) {
    var editMedication by remember { mutableStateOf(false) }
    var addTag by remember { mutableStateOf(false) }
    var effect by remember(selectedDate) { mutableStateOf("") }
    val spacing = FocusLogTheme.spacing
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.page),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item { DateHeader(selectedDate, currentDate, onDateChange) }
        item {
            Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 1.dp) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(content.medication.name, style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { editMedication = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit_medication))
                        }
                    }
                    Text(content.medication.dosage)
                    Text(
                        if (content.medication.status == MedicationStatus.Taken)
                            stringResource(R.string.taken_at, content.lastTaken) else stringResource(R.string.due),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onLogDose,
                        enabled = configuredName.isNotBlank() && configuredDose in 1..200 && !doseSaving,
                    ) { Text(stringResource(R.string.log_dose)) }
                }
            }
        }
        if (entries.isNotEmpty()) {
            item {
                Text(stringResource(R.string.selected_day_records), style = MaterialTheme.typography.titleLarge)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entries.forEach { CompactEntry(it) }
                }
            }
        }
        item { Text(stringResource(R.string.check_in), style = MaterialTheme.typography.titleLarge) }
        content.metrics.forEach { metric ->
            item {
                Column {
                    Text("${metric.type.name}: ${metric.descriptor}")
                    Slider(
                        value = metric.value,
                        onValueChange = { onMetricChange(metric.type, it) },
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                content.tags.forEach { tag ->
                    FilterChip(
                        selected = tag.selected,
                        onClick = { onToggleTag(tag.label) },
                        label = { Text(tag.label) },
                    )
                }
            }
        }
        item {
            AssistChip(
                onClick = { addTag = true },
                label = { Text(stringResource(R.string.add_tag)) },
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            )
        }
        item {
            OutlinedTextField(
                value = content.notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.notes)) },
                minLines = 3,
            )
        }
        item {
            Button(
                onClick = onLogCheckIn,
                enabled = hasEdits && !checkInSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null)
                Text(stringResource(R.string.save_check_in), modifier = Modifier.padding(start = 6.dp))
            }
        }
        item {
            Text(stringResource(R.string.side_effect), style = MaterialTheme.typography.titleLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = effect,
                    onValueChange = { effect = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.description)) },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        onLogSideEffect(effect)
                        effect = ""
                    },
                    enabled = effect.isNotBlank(),
                ) { Text(stringResource(R.string.log)) }
            }
        }
    }
    if (editMedication) {
        MedicationDialog(
            name = configuredName,
            dose = configuredDose,
            onDismiss = { editMedication = false },
            onSave = { name, dose ->
                onSaveMedication(name, dose)
                editMedication = false
            },
        )
    }
    if (addTag) {
        TextEntryDialog(
            title = stringResource(R.string.add_tag),
            onDismiss = { addTag = false },
            onSave = {
                onAddTag(it)
                addTag = false
            },
        )
    }
}

@Composable
private fun DateHeader(
    date: LocalDate,
    today: LocalDate,
    onDateChange: (LocalDate) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onDateChange(date.minusDays(1)) }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.previous_day))
        }
        Text(
            if (date == today) stringResource(R.string.today_date, localizedDate(date)) else localizedDate(date),
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
private fun CompactEntry(entry: ActivityEntry) {
    val context = LocalContext.current
    val time = DateFormat.getTimeFormat(context).format(Date(entry.timestamp))
    val text = when (entry) {
        is ActivityEntry.DoseTaken ->
            "$time — ${entry.dose.medicationName}, ${entry.dose.doseMg} mg, ${entry.dose.releaseType}"
        is ActivityEntry.CheckInEntry ->
            "$time — Focus ${entry.log.focusLevel}, mood ${entry.log.moodLevel}, energy ${entry.log.energyLevel}"
        is ActivityEntry.SideEffectEntry -> "$time — Side effect: ${entry.log.effectName}"
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(text, Modifier.fillMaxWidth().padding(12.dp))
    }
}

@Composable
private fun MedicationDialog(
    name: String,
    dose: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var editedName by remember(name) { mutableStateOf(name) }
    var editedDose by remember(dose) { mutableStateOf(if (dose > 0) dose.toString() else "") }
    val parsedDose = editedDose.toIntOrNull()
    val nameError = editedName.isBlank()
    val doseError = parsedDose == null || parsedDose !in 1..200
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.medication)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text(stringResource(R.string.medication_name)) },
                    isError = nameError,
                    supportingText = { if (nameError) Text(stringResource(R.string.name_required)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = editedDose,
                    onValueChange = { editedDose = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.dose_mg)) },
                    isError = doseError,
                    supportingText = { if (doseError) Text(stringResource(R.string.dose_range_error)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(editedName.trim(), parsedDose!!) },
                enabled = !nameError && !doseError,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TextEntryDialog(title: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.tag)) }, singleLine = true)
        },
        confirmButton = {
            Button(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun localizedDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

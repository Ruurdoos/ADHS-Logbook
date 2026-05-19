package com.example.adhslogbook.ui.screens.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhslogbook.data.MedicationConstants
import com.example.adhslogbook.data.model.ActivityEntry
import com.example.adhslogbook.data.model.CheckInMetric
import com.example.adhslogbook.data.model.CurvePoint
import com.example.adhslogbook.data.model.MedicationStatus
import com.example.adhslogbook.data.model.MetricType
import com.example.adhslogbook.data.model.TodayContent
import com.example.adhslogbook.di.DatabaseModule
import com.example.adhslogbook.navigation.FocusLogDestination
import com.example.adhslogbook.navigation.ProductDestinations
import com.example.adhslogbook.ui.components.FocusLogBottomBar
import com.example.adhslogbook.ui.components.FocusLogTopBar
import com.example.adhslogbook.ui.components.ScreenStateHost
import com.example.adhslogbook.ui.theme.ADHSLogbookTheme
import com.example.adhslogbook.ui.theme.FocusLogPalette
import com.example.adhslogbook.ui.theme.FocusLogTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.example.adhslogbook.ui.state.ScreenContentState
import com.example.adhslogbook.ui.viewmodels.LogbookViewModel

@Composable
fun TodayRoute(
    viewModel: LogbookViewModel,
    currentDestination: FocusLogDestination,
    onNavigate: (FocusLogDestination) -> Unit,
    onHomeClick: () -> Unit,
) {
    val uiState by viewModel.todayContent.collectAsStateWithLifecycle()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.todaySnackbar.collectAsStateWithLifecycle()
    val timelineEntries by viewModel.timelineEntries.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val todaySideEffects = remember(timelineEntries) {
        timelineEntries.filterIsInstance<ActivityEntry.SideEffectEntry>()
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbar()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FocusLogTopBar(
                onHomeClick = onHomeClick,
                onSettingsClick = {},
            )
        },
        bottomBar = {
            FocusLogBottomBar(
                items = ProductDestinations,
                currentDestination = currentDestination,
                onNavigate = onNavigate,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ScreenStateHost(
            state = uiState,
            onRetry = { /* viewModel::retry */ },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            loadingMessage = "Loading today's check-in",
            emptyMessage = "No check-in template has been prepared yet.",
        ) { content ->
            TodayScreen(
                content = content,
                contentPadding = innerPadding,
                timelineEntries = timelineEntries,
                sideEffects = todaySideEffects,
                selectedDate = selectedDate,
                hasUnsavedChanges = hasUnsavedChanges,
                onMetricChange = viewModel::updateMetric,
                onLogMetrics = {
                    val currentLocal = (uiState as? ScreenContentState.Data)?.value ?: return@TodayScreen
                    viewModel.logCheckIn(
                        focus = (currentLocal.metrics.find { it.type == MetricType.Focus }?.value ?: 0.5f).times(100).toInt(),
                        mood = (currentLocal.metrics.find { it.type == MetricType.Mood }?.value ?: 0.5f).times(100).toInt(),
                        energy = (currentLocal.metrics.find { it.type == MetricType.Energy }?.value ?: 0.5f).times(100).toInt(),
                        notes = currentLocal.notes
                    )
                },
                onToggleTag = viewModel::toggleTag,
                onAddTag = viewModel::addTag,
                onNotesChange = viewModel::updateNotes,
                onLogNextDose = { 
                    content.medication.let {
                        val doseMg = it.dosage.filter { char -> char.isDigit() }.toIntOrNull() ?: 30
                        viewModel.logDose(it.name, doseMg, System.currentTimeMillis())
                    }
                },
                onUpdateMedicationName = viewModel::updatePlannedMedicationName,
                onUpdateMedicationDose = viewModel::updatePlannedDose,
                onDateChange = viewModel::selectDate,
                onLogSideEffect = viewModel::logSideEffect,
                onAddQuickNote = viewModel::addQuickNote,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TodayScreen(
    content: TodayContent,
    contentPadding: PaddingValues,
    timelineEntries: List<ActivityEntry>,
    sideEffects: List<ActivityEntry.SideEffectEntry>,
    selectedDate: LocalDate,
    hasUnsavedChanges: Boolean,
    onMetricChange: (MetricType, Float) -> Unit,
    onLogMetrics: () -> Unit,
    onToggleTag: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onLogNextDose: () -> Unit,
    onUpdateMedicationName: (String) -> Unit,
    onUpdateMedicationDose: (Int) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onLogSideEffect: (String) -> Unit,
    onAddQuickNote: (String) -> Unit,
) {
    val spacing = FocusLogTheme.spacing
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagText by remember { mutableStateOf("") }

    var showEditMedicationDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            Button(
                onClick = { showBottomSheet = true },
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add")
            }
        }
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.page,
                top = spacing.lg,
                end = spacing.page,
                bottom = contentPadding.calculateBottomPadding() + spacing.lg + 80.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            item {
                DateNavigationHeader(
                    selectedDate = selectedDate,
                    onDateChange = onDateChange
                )
            }

            item {
                MedicationCard(
                    content = content,
                    onLogNextDose = onLogNextDose,
                    onEditClick = { showEditMedicationDialog = true }
                )
            }

            item {
                TimelineSection(timelineEntries = timelineEntries)
            }

            if (sideEffects.isNotEmpty()) {
                item {
                    SideEffectsCard(sideEffects = sideEffects)
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Text(
                        text = "Quick Check-in",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                        content.metrics.forEach { metric ->
                            MetricSliderCard(metric = metric) { onMetricChange(metric.type, it) }
                        }
                        AnimatedVisibility(
                            visible = hasUnsavedChanges,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Button(
                                onClick = onLogMetrics,
                                modifier = Modifier.fillMaxWidth(),
                                shape = CircleShape,
                            ) {
                                Icon(Icons.Outlined.Check, contentDescription = null)
                                Text(
                                    text = "Log Now",
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Text(text = "Tags", style = MaterialTheme.typography.titleLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        content.tags.forEach { tag ->
                            FilterChip(
                                selected = tag.selected,
                                onClick = { onToggleTag(tag.label) },
                                label = { Text(tag.label) },
                                leadingIcon = if (tag.selected) {
                                    { Icon(Icons.Outlined.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                        }
                        AssistChip(
                            onClick = { showAddTagDialog = true },
                            label = { Text("Add") },
                            leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    OutlinedTextField(
                        value = content.notes,
                        onValueChange = onNotesChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(128.dp),
                        label = { Text("Notes") },
                        placeholder = { Text("How are you feeling right now?") },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        shape = RoundedCornerShape(24.dp),
                    )
                    AnimatedVisibility(
                        visible = hasUnsavedChanges,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Button(
                            onClick = onLogMetrics,
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                            Text(
                                text = "Log Now",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddTagDialog) {
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Add New Tag") },
            text = {
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTagText.isNotBlank()) {
                            onAddTag(newTagText)
                            newTagText = ""
                            showAddTagDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditMedicationDialog) {
        MedicationEditDialog(
            currentName = content.medication.name,
            currentDose = content.medication.dosage.filter { it.isDigit() }.toIntOrNull() ?: 30,
            onDismiss = { showEditMedicationDialog = false },
            onConfirm = { name, dose ->
                onUpdateMedicationName(name)
                onUpdateMedicationDose(dose)
                showEditMedicationDialog = false
            }
        )
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            QuickLogOptions(
                onLogSideEffect = { effect ->
                    onLogSideEffect(effect)
                    showBottomSheet = false
                },
                onAddNote = { note ->
                    onAddQuickNote(note)
                    showBottomSheet = false
                }
            )
        }
    }
}

@Composable
private fun DateNavigationHeader(
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit
) {
    val dateLabel = when (selectedDate) {
        LocalDate.now() -> "Today, " + selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))
        LocalDate.now().minusDays(1) -> "Yesterday, " + selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))
        else -> selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "TIMELINE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDateChange(selectedDate.minusDays(1)) }) {
                    Icon(
                        Icons.Outlined.ArrowBackIosNew,
                        contentDescription = "Previous Day",
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { onDateChange(selectedDate.plusDays(1)) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForwardIos,
                        contentDescription = "Next Day",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.DateRange, contentDescription = "Calendar")
            }
        }
    }
}

@Composable
private fun TimelineSection(timelineEntries: List<ActivityEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(FocusLogTheme.spacing.md)) {
        Text(text = "Activity", style = MaterialTheme.typography.titleLarge)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                timelineEntries.forEachIndexed { index, entry ->
                    TimelineItem(
                        entry = entry,
                        isLast = index == timelineEntries.size - 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineItem(entry: ActivityEntry, isLast: Boolean) {
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
    val time = java.time.Instant.ofEpochMilli(entry.timestamp)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
        .format(timeFormatter)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight()
        ) {
            val dotColor = when (entry) {
                is ActivityEntry.DoseTaken -> FocusLogPalette.Teal
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(dotColor, CircleShape)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            val title = when (entry) {
                is ActivityEntry.DoseTaken -> "Dose Taken"
                is ActivityEntry.CheckInEntry -> entry.label
                is ActivityEntry.SideEffectEntry -> "Side Effect"
            }
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entry is ActivityEntry.CheckInEntry && entry.log.notes.isNotEmpty()) {
                Text(
                    text = entry.log.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SideEffectsCard(sideEffects: List<ActivityEntry.SideEffectEntry>) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Side Effects Noted",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sideEffects.forEach { effect ->
                    SuggestionChip(
                        onClick = { },
                        label = { Text("⚠ ${effect.log.effectName}") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = FocusLogPalette.ErrorContainer,
                            labelColor = FocusLogPalette.OnErrorContainer
                        ),
                        border = null,
                        shape = CircleShape
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickLogOptions(
    onLogSideEffect: (String) -> Unit,
    onAddNote: (String) -> Unit
) {
    var sideEffectText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(text = "Quick Log", style = MaterialTheme.typography.headlineSmall)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Log Side Effect", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = sideEffectText,
                onValueChange = { sideEffectText = it },
                placeholder = { Text("e.g. Headache, Dry mouth") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { if (sideEffectText.isNotBlank()) onLogSideEffect(sideEffectText) }) {
                        Icon(Icons.Outlined.Check, contentDescription = "Confirm")
                    }
                }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Add Quick Note", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = { Text("How are you feeling?") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { if (noteText.isNotBlank()) onAddNote(noteText) }) {
                        Icon(Icons.Outlined.Check, contentDescription = "Confirm")
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MedicationCard(
    content: TodayContent,
    onLogNextDose: () -> Unit,
    onEditClick: () -> Unit,
) {
    val spacing = FocusLogTheme.spacing
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        onClick = onEditClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.LocalPharmacy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column {
                        Text(content.medication.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            content.medication.dosage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MedicationStatusChip(status = content.medication.status)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                MetricInfoBox(
                    modifier = Modifier.weight(1f),
                    label = "Last Taken",
                    value = content.lastTaken,
                )
                MetricInfoBox(
                    modifier = Modifier.weight(1f),
                    label = "Est. Duration",
                    value = content.estimatedDuration,
                )
            }


            Button(
                onClick = onLogNextDose,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(
                    text = "Log Next Dose",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationEditDialog(
    currentName: String,
    currentDose: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var selectedName by remember { mutableStateOf(currentName) }
    var doseText by remember { mutableStateOf(currentDose.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Medication") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Medication Name", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(
                        modifier = Modifier
                            .height(200.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                    ) {
                        items(MedicationConstants.PREDEFINED_NAMES) { name ->
                            Text(
                                text = name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedName = name }
                                    .background(
                                        if (selectedName == name) MaterialTheme.colorScheme.primaryContainer 
                                        else MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                    .padding(12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selectedName == name) MaterialTheme.colorScheme.onPrimaryContainer 
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = doseText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) doseText = it },
                    label = { Text("Dosage (mg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    suffix = { Text("mg") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dose = doseText.toIntOrNull() ?: currentDose
                    onConfirm(selectedName, dose)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MedicationStatusChip(status: MedicationStatus) {
    val (label, background, content) = when (status) {
        MedicationStatus.Taken -> Triple(
            "Taken",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        MedicationStatus.Scheduled -> Triple(
            "Scheduled",
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary,
        )
        MedicationStatus.Due -> Triple(
            "Due",
            FocusLogPalette.ErrorContainer,
            FocusLogPalette.OnErrorContainer,
        )
    }

    Surface(
        shape = CircleShape,
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(content, CircleShape),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}

@Composable
private fun MetricInfoBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MetricSliderCard(
    metric: CheckInMetric,
    onValueChange: (Float) -> Unit,
) {
    val icon = when (metric.type) {
        MetricType.Focus -> Icons.Outlined.TrackChanges
        MetricType.Mood -> Icons.Outlined.SentimentNeutral
        MetricType.Energy -> Icons.Outlined.Bolt
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = metric.type.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = metric.descriptor,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Slider(
                value = metric.value,
                onValueChange = onValueChange,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}


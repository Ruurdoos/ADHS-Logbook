package com.adhs.logbook

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import com.adhs.logbook.shared.observationShortName
import com.adhs.logbook.shared.observationCategories
import com.adhs.logbook.shared.nonUseName
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Snackbar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhs.logbook.shared.BackupDocument
import com.adhs.logbook.shared.DoseEntry
import com.adhs.logbook.shared.Estimate
import com.adhs.logbook.shared.EstimatePhase
import com.adhs.logbook.shared.Medication
import com.adhs.logbook.shared.Preset
import com.adhs.logbook.shared.Reminder
import com.adhs.logbook.shared.SummaryBuilder
import com.adhs.logbook.shared.Supply
import com.adhs.logbook.shared.SupplyLedger
import com.adhs.logbook.shared.TextCatalog
import com.adhs.logbook.shared.defaultModel
import com.adhs.logbook.shared.doseUnits
import com.adhs.logbook.shared.moodFaces
import com.adhs.logbook.shared.moodLabels
import com.adhs.logbook.shared.parseDose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val Sage: Color @Composable get() = MaterialTheme.colorScheme.primary
private val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val Paper: Color @Composable get() = MaterialTheme.colorScheme.background
private val Pale: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val StrokeColor: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val dayFormat get() = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.getDefault())
internal fun timeFormat(context: Context) = DateTimeFormatter.ofPattern(
    if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
    Locale.getDefault()
)

fun tr(text: String, vararg values: Any): String =
    TextCatalog.text(text, Locale.getDefault().language)
        .let { if (values.isEmpty()) it else String.format(Locale.getDefault(), it, *values) }

fun phaseText(phase: EstimatePhase) = tr(
    when (phase) {
        EstimatePhase.UNAVAILABLE -> "Estimate unavailable"; EstimatePhase.NO_LOGS -> "No doses logged"
        EstimatePhase.RECENT -> "Recently logged"; EstimatePhase.LOW -> "Low estimated level"
        EstimatePhase.PEAK -> "Near estimated peak"; EstimatePhase.RISING -> "Estimate increasing"; EstimatePhase.DECREASING -> "Estimate decreasing"
    }
)

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private var reviewIntent by mutableStateOf<String?>(null)
    private var widgetIntent by mutableStateOf<String?>(null)
    override fun onStop() {
        super.onStop(); AppPrivacy.unlocked = false
    }

    private var reminderIntent by mutableStateOf<String?>(null)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); reviewIntent =
            intent.getStringExtra("review_token"); widgetIntent =
            intent.getStringExtra("widget_token"); reminderIntent =
            intent.getStringExtra("occurrence")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reviewIntent = intent.getStringExtra("review_token")
        reminderIntent = intent.getStringExtra("occurrence")
        widgetIntent = intent.getStringExtra("widget_token")
        AppPrivacy.enabled = AppPrivacy.isEnabled(this)
        if (AppPrivacy.enabled) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (dark) darkColorScheme(
                primary = Color(0xFFA8D5B7),
                onPrimary = Color(0xFF123822),
                primaryContainer = Color(0xFF294B37),
                background = Color(0xFF121713),
                surface = Color(0xFF1B231D),
                onSurface = Color(0xFFE3EAE1),
                onBackground = Color(0xFFE3EAE1),
                onSurfaceVariant = Color(0xFFBFCBBC)
            )
            else lightColorScheme(
                primary = Color(0xFF416B59),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE8EFE6),
                background = Color(0xFFF7F8F4),
                surface = Color.White,
                onSurface = Color(0xFF24352D),
                onBackground = Color(0xFF24352D),
                onSurfaceVariant = Color(0xFF5D6B63)
            )
            MaterialTheme(colorScheme = colors) {
                PrivacyGate {
                    LogbookApp(
                        reviewIntent = reviewIntent,
                        onReviewHandled = { reviewIntent = null },
                        reminderIntent = reminderIntent,
                        onIntentHandled = { reminderIntent = null },
                        widgetIntent = widgetIntent,
                        onWidgetHandled = { widgetIntent = null })
                }
            }
        }
    }
}

@Composable
fun LogbookApp(
    vm: LogbookViewModel = viewModel(),
    reviewIntent: String? = null,
    onReviewHandled: () -> Unit = {},
    reminderIntent: String? = null,
    onIntentHandled: () -> Unit = {},
    widgetIntent: String? = null,
    onWidgetHandled: () -> Unit = {}
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var shouldPage by rememberSaveable { mutableStateOf<String?>(null) }
    var parentPages by rememberSaveable { mutableStateOf(listOf<String>()) }
    val pageState = rememberSaveableStateHolder()
    fun closePage() {
        shouldPage?.let { pageState.removeState(it) }
        shouldPage = parentPages.lastOrNull(); parentPages = parentPages.dropLast(1)
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var welcome by rememberSaveable { mutableStateOf(true) }
    var editingMedication by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingEntry by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedMedication by rememberSaveable { mutableLongStateOf(0L) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val active = state.medications.filter { it.active }
    val selected = active.find { it.id == selectedMedication } ?: active.firstOrNull()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var occurrenceId by rememberSaveable { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer =
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refresh() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(reminderIntent, state.loaded) {
        if (state.loaded && reminderIntent != null) {
            val occurrence = vm.occurrence(reminderIntent)
            val reminder = state.reminders.find { it.id == occurrence?.reminderId }
            if (occurrence != null && reminder != null && occurrence.state == "pending" && System.currentTimeMillis() < occurrence.expires) {
                occurrenceId = occurrence.id; selectedMedication =
                    reminder.medicationId ?: 0; editingEntry = -1; tab = 0
            }
            onIntentHandled()
        }
    }
    LaunchedEffect(reviewIntent, state.loaded, busy) {
        if (state.loaded && reviewIntent != null && !busy) {
            val route = QuickAccess.route(context, reviewIntent, state.medications)
            if (route.unavailable || active.isEmpty()) errorMessage =
                tr("Quick access is unavailable. Open Settings to configure it.")
            else {
                selectedMedication = route.medicationId ?: active.first().id; occurrenceId =
                    null; editingEntry = -1; shouldPage = null; tab = 0
                if (route.changed) errorMessage =
                    tr("Medication settings changed. Review the current amount.")
            }
            onReviewHandled()
        }
    }
    LaunchedEffect(widgetIntent, state.loaded, busy) {
        if (state.loaded && widgetIntent != null && !busy) {
            val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
            val med = state.medications.find { it.id == prefs.getLong("med", 0) && it.active }
            if (med != null && widgetIntent.isNotBlank() && widgetIntent == prefs.getString(
                    "token",
                    null
                ) && med.revision == prefs.getLong("revision", 0)
            ) {
                if (AppPrivacy.enabled || prefs.getBoolean("private", true)) {
                    selectedMedication = med.id; editingEntry = -1
                } else vm.widgetLog(
                    med,
                    widgetIntent
                ) { id ->
                    scope.launch {
                        if (snackbar.showSnackbar(
                                tr("Dose logged"),
                                tr("Undo")
                            ) == SnackbarResult.ActionPerformed
                        ) vm.delete(id)
                    }
                }
            } else {
                med?.let { selectedMedication = it.id; editingEntry = -1 }; errorMessage =
                    tr("Widget changed. Review the medication and amount in the app.")
            }
            onWidgetHandled()
        }
    }
    LaunchedEffect(vm) { for (message in vm.messages) errorMessage = message }
    LaunchedEffect(editingEntry, editingMedication) {
        if (editingEntry != null || editingMedication != null) snackbar.currentSnackbarData?.dismiss()
    }
    LaunchedEffect(state.loaded) {
        if (state.loaded) withContext(Dispatchers.IO) {
            ReminderScheduler.reschedule(
                context,
                state
            )
        }
    }
    LaunchedEffect(editingEntry) { if (editingEntry == null) pageState.removeState("dose-draft") }
    fun saved(id: Long, detail: String? = null) {
        editingEntry = null
        occurrenceId = null
        scope.launch {
            if (snackbar.showSnackbar(
                    DoseLoggedVisuals(
                        listOfNotNull(tr("Dose logged"), detail).joinToString(" · "),
                        id
                    )
                ) == SnackbarResult.ActionPerformed
            ) vm.delete(id)
        }
    }

    var discardAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun leaveEditor(action: () -> Unit) {
        discardAction = action
    }

    fun leavePage() {
        if (shouldPage?.let {
                it.startsWith("observation:") || it.startsWith("measurement:") || it.startsWith(
                    "nonuse"
                ) || it == "pause" || it.startsWith("supply")
            } == true) leaveEditor { closePage() } else closePage()
    }
    discardAction?.let { action ->
        AlertDialog(
            onDismissRequest = { discardAction = null },
            title = { Text(tr("Discard changes?")) },
            confirmButton = {
                TextButton({
                    discardAction = null; action()
                }) { Text(tr("Discard")) }
            },
            dismissButton = { TextButton({ discardAction = null }) { Text(tr("Keep editing")) } })
    }
    BackHandler(shouldPage != null || editingEntry != null || editingMedication != null || tab != 0) {
        when {
            shouldPage != null -> leavePage(); editingEntry != null -> leaveEditor {
            editingEntry = null
        }; editingMedication != null -> leaveEditor { editingMedication = null }; else -> tab = 0
        }
    }
    Scaffold(
        containerColor = Paper,
        snackbarHost = {
            if (editingEntry == null && editingMedication == null) SnackbarHost(snackbar) { data ->
                val visuals = data.visuals
                if (visuals is DoseLoggedVisuals) Snackbar(
                    Modifier.padding(12.dp),
                    actionOnNewLine = true,
                    action = {
                        Row {
                            TextButton(
                                { data.dismiss(); editingEntry = visuals.entryId },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)
                            ) { Text(tr("Change")) }
                            TextButton(
                                { data.performAction() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)
                            ) { Text(tr("Undo")) }
                        }
                    }
                ) { Text(visuals.message) } else Snackbar(data)
            }
        },
        bottomBar = {
            if (state.loaded && state.onboarded && editingEntry == null && editingMedication == null && shouldPage == null) {
                NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                    val titles = listOf("Home", "History", "Export", "Settings")
                    val icons = listOf(
                        Icons.Outlined.Home,
                        Icons.Outlined.History,
                        Icons.Outlined.IosShare,
                        Icons.Outlined.Settings
                    )
                    titles.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = tab == index, onClick = { tab = index },
                            icon = { Icon(icons[index], null) }, label = { Text(tr(title)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Pale,
                                selectedIconColor = Sage
                            )
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            when {
                shouldPage == "backup" -> ScrollPage {
                    BackButton({ closePage() }); BackupControls(
                    vm,
                    busy
                )
                }

                shouldPage != null -> pageState.SaveableStateProvider(shouldPage!!) {
                    ShouldScreen(
                        shouldPage!!,
                        state,
                        vm,
                        {
                            if (shouldPage!!.startsWith("nonuse-occ:")) {
                                editingEntry = null; occurrenceId = null
                            }; closePage()
                        },
                        { parentPages = parentPages + shouldPage!!; shouldPage = it },
                        back = { leavePage() })
                }

                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.loadError == null) CircularProgressIndicator() else Column(
                        Modifier.padding(
                            24.dp
                        )
                    ) {
                        Text(state.loadError!!); Button({ vm.refresh() }) { Text(tr("Try again")) }
                        TextButton({ shouldPage = "backup" }) { Text(tr("Restore backup")) }
                    }
                }

                !state.onboarded && welcome -> Welcome({ welcome = false }) {
                    BackupControls(
                        vm,
                        busy,
                        restoreOnly = true
                    )
                }

                !state.onboarded || editingMedication != null -> MedicationEditor(
                    medication = state.medications.find { it.id == editingMedication }, busy = busy,
                    onboarding = !state.onboarded, onNoMedication = { vm.startWithoutMedication() },
                    onBack = {
                        leaveEditor {
                            if (state.onboarded) editingMedication = null else welcome = true
                        }
                    },
                    onSave = { med -> vm.medication(med) { editingMedication = null } },
                )

                editingEntry != null -> pageState.SaveableStateProvider("dose-draft") {
                    EntryEditor(
                        entry = state.entries.find { it.id == editingEntry },
                        medications = state.medications,
                        supplies = state.supplies,
                        onNonUse = occurrenceId?.let { id ->
                            { med: Long ->
                                shouldPage = "nonuse-occ:$id:$med"
                            }
                        },
                        selected = selected,
                        busy = busy,
                        onBack = { leaveEditor { editingEntry = null; occurrenceId = null } },
                        onSave = { entry, actionId ->
                            vm.save(entry, actionId, occurrenceId) { id ->
                                if (entry.id == 0L) saved(id, "${doseText(entry.doseMg)} ${tr(entry.unit)}") else {
                                    editingEntry =
                                        null; scope.launch { snackbar.showSnackbar(tr("Changes saved")) }
                                }
                            }
                        },
                        onDelete = { id ->
                            vm.delete(id) {
                                editingEntry =
                                    null; scope.launch { snackbar.showSnackbar(tr("Entry deleted")) }
                            }
                        },
                    )
                }

                tab == 0 -> Column {
                    state.supplies.filter { it.medicationId == selected?.id }.forEach { supply ->
                        val balance = SupplyLedger.balance(vm.document(), supply)
                        if (balance.inconsistent || balance.remaining <= supply.lowThreshold || (supply.prescriptionDate?.let { it <= System.currentTimeMillis() } == true)) TextButton(
                            {
                                shouldPage = "supply:${supply.medicationId}"
                            }) {
                            Text(
                                tr(
                                    "Estimated remaining: %s %s",
                                    doseText(balance.remaining),
                                    supply.unitLabel
                                )
                            )
                        }
                    }
                    HomeScreen(
                        state,
                        selected,
                        busy,
                        HomeActions(
                            selectMedication = { selectedMedication = it },
                            taken = {
                                selected?.let { med ->
                                    vm.quick(med) { id ->
                                        saved(id, "${doseText(med.usualDose)} ${tr(med.unit)}")
                                    }
                                }
                            },
                            notTaken = selected?.let { med -> { shouldPage = "nonuse-med:${med.id}" } },
                            addMedication = { editingMedication = -1 },
                            dose = selected?.let { { editingEntry = -1 } },
                            observation = { category -> shouldPage = "observation:new:$category" },
                            measurement = if (selected == null || vm.enabled("measurements_enabled")) {
                                { shouldPage = "measurement:" }
                            } else null,
                            open = { target ->
                                if (target.startsWith("dose:")) editingEntry = target.substringAfter(':').toLong()
                                else shouldPage = target
                            },
                            allRecords = { shouldPage = "records" }
                        )
                    )
                }

                tab == 1 -> HistoryScreen(
                    entries = state.entries,
                    onEdit = { editingEntry = it },
                    onWeekly = if (vm.enabled("weekly_enabled")) {
                        { shouldPage = "weekly" }
                    } else null
                )

                tab == 2 -> ExportScreen(vm.document(), snackbar)
                else -> SettingsScreen(
                    state,
                    busy,
                    vm,
                    { editingMedication = it },
                    { tab = 2 },
                    { shouldPage = it })
            }
        }
    }
    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(tr("Something went wrong")) },
            text = { Text(message) },
            confirmButton = { TextButton({ errorMessage = null }) { Text(tr("Close")) } })
    }
}

@Composable
private fun Title(text: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (subtitle != null) Text(
            subtitle,
            color = Muted,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(tr(text), fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Primary(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick,
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.large
    ) {
        Text(tr(text), fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
private fun Secondary(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick,
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            if (enabled) Sage else Sage.copy(alpha = 0.38f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Sage)
    ) {
        Text(tr(text), fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
private fun CheckboxRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                checked,
                role = androidx.compose.ui.semantics.Role.Checkbox,
                onValueChange = onChange
            ), verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked, null, Modifier.padding(12.dp))
        Text(tr(text))
    }
}

@Composable
private fun Note(text: String) {
    Text(tr(text), color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp)
}

@Composable
private fun BackTitle(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackButton(onBack)
        Spacer(Modifier.width(16.dp))
        Text(
            tr(title),
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ScrollPage(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableHeight = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = availableHeight)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content
        )
    }
}

@Composable
private fun Welcome(onStart: () -> Unit, restore: @Composable () -> Unit) {
    ScrollPage {
        Spacer(Modifier.height(40.dp))
        Text(
            tr("ADHS LOGBOOK"),
            color = Sage,
            letterSpacing = 2.sp,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            tr("A little clarity,\nevery day."),
            fontSize = 38.sp,
            lineHeight = 45.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            tr("Log your medication. Keep notes when you want to."),
            color = Muted,
            fontSize = 18.sp,
            lineHeight = 28.sp
        )
        Spacer(Modifier.weight(1f))
        Note("Private. Stored on this device.")
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Primary("Get started", onClick = onStart)
            restore()
        }
    }
}

@Composable
private fun MedicationEditor(
    medication: Medication?,
    busy: Boolean,
    onboarding: Boolean,
    onNoMedication: () -> Unit,
    onBack: () -> Unit,
    onSave: (Medication) -> Unit
) {
    var presetName by rememberSaveable(medication?.id) {
        mutableStateOf(
            (medication?.preset ?: Preset.METHYLPHENIDATE_IR).name
        )
    }
    var dose by rememberSaveable(medication?.id) {
        mutableStateOf(
            medication?.usualDose?.let(::doseText) ?: ""
        )
    }
    var name by rememberSaveable(medication?.id) { mutableStateOf(medication?.name ?: "") }
    var formulation by rememberSaveable(medication?.id) {
        mutableStateOf(
            medication?.formulation ?: ""
        )
    }
    var strength by rememberSaveable(medication?.id) { mutableStateOf(medication?.strength ?: "") }
    var unit by rememberSaveable(medication?.id) { mutableStateOf(medication?.unit ?: "mg") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var noMedication by rememberSaveable { mutableStateOf(false) }
    val preset = Preset.valueOf(presetName)
    val parsed = parseDose(dose)
    val custom = preset == Preset.CUSTOM
    ScrollPage {
        BackTitle(if (medication == null) "Add your medication" else "Edit medication", onBack)
        @Composable
        fun Option(title: String, checked: Boolean, select: () -> Unit) {
            Surface(
                color = if (checked) Pale else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = select)
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        checked,
                        select,
                        Modifier.semantics { contentDescription = tr(title) })
                    Text(
                        tr(title), Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                }
            }
        }
        Preset.entries.forEach { item ->
            Option(
                item.title,
                !noMedication && item == preset
            ) { presetName = item.name; unit = "mg"; noMedication = false }
        }
        if (onboarding) Option("No medication", noMedication) { noMedication = true }
        if (noMedication) {
            Note("Log observations and measurements without medication. You can add a medication later.")
            Spacer(Modifier.weight(1f))
            Primary(if (busy) "Saving…" else "Start", !busy, onNoMedication)
        } else {
            if (custom) {
                OutlinedTextField(
                    name,
                    { name = it.take(200) },
                    Modifier.fillMaxWidth(),
                    label = { Text(tr("Medication name")) },
                    isError = attempted && name.isBlank(),
                    supportingText = { if (attempted && name.isBlank()) Text(tr("Enter a medication name.")) })
                OutlinedTextField(
                    formulation,
                    { formulation = it.take(200) },
                    Modifier.fillMaxWidth(),
                    label = { Text(tr("Formulation (optional)")) })
                OutlinedTextField(
                    strength,
                    { strength = it.take(100) },
                    Modifier.fillMaxWidth(),
                    label = { Text(tr("Strength with unit (optional)")) },
                    supportingText = { Text(tr("For your records only; no dose conversion.")) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    doseUnits.forEach { value ->
                        FilterChip(
                            selected = unit == value,
                            onClick = { unit = value },
                            label = { Text(tr(value)) })
                    }
                }
            }
            OutlinedTextField(
                dose,
                { dose = it },
                Modifier.fillMaxWidth(),
                label = { Text(tr("Your usual dose (%s)", tr(unit))) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = attempted && parsed == null,
                supportingText = { if (attempted && parsed == null) Text(tr("Enter a positive dose.")) })
            Note("Enter your prescribed dose. You can add another medication later.")
            if (defaultModel(preset) == null) Note("Logging is available. No supported estimate is available for this formulation.")
            Spacer(Modifier.weight(1f))
            Primary(
                if (busy) "Saving…" else if (onboarding) "Start" else "Save medication",
                !busy
            ) {
                attempted = true
                if (parsed != null && (!custom || name.isNotBlank())) onSave(
                    Medication(
                        medication?.id ?: 0,
                        preset,
                        parsed,
                        name = if (custom) name.trim() else preset.title,
                        formulation = if (custom) formulation.trim() else preset.name,
                        strength = if (custom) strength.trim() else "",
                        unit = unit
                    )
                )
            }
        }
    }
}

/** Snackbar shown after logging a dose, offering both "Change" and "Undo" for that entry. */
internal class DoseLoggedVisuals(override val message: String, val entryId: Long) : SnackbarVisuals {
    override val actionLabel: String get() = tr("Undo")
    override val withDismissAction = false
    override val duration = SnackbarDuration.Long
}

/** Destinations reachable from Home. Each record type has exactly one entry point. */
internal class HomeActions(
    val selectMedication: (Long) -> Unit,
    val taken: () -> Unit,
    val notTaken: (() -> Unit)?,
    val addMedication: () -> Unit,
    val dose: (() -> Unit)?,
    val observation: ((String) -> Unit)?,
    val measurement: (() -> Unit)?,
    val open: (String) -> Unit,
    val allRecords: () -> Unit
)

@Composable
private fun HomeScreen(
    state: LogbookState,
    selected: Medication?,
    busy: Boolean,
    actions: HomeActions
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis(); delay(30_000)
        }
    }
    val homeLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(homeLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) now = System.currentTimeMillis()
        }
        homeLifecycle.addObserver(observer)
        onDispose { homeLifecycle.removeObserver(observer) }
    }
    var modelInfo by remember { mutableStateOf(false) }
    var recordSheet by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val relevant = state.entries.filter { it.medicationId == selected?.id }
    val canRecord = actions.dose != null || actions.observation != null || actions.notTaken != null || actions.measurement != null
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Title("Today", Instant.ofEpochMilli(now).atZone(zone).format(dayFormat))
            if (selected == null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(tr("Your log starts here."), fontSize = 22.sp)
                        Note("Record observations and measurements, with or without medication.")
                        OutlinedButton(
                            actions.addMedication,
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) { Text(tr("Add medication")) }
                    }
                }
            } else {
                val active = state.medications.filter { it.active }
                if (active.size > 1) Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    active.forEach { med ->
                        FilterChip(
                            med.id == selected.id,
                            { actions.selectMedication(med.id) },
                            label = { Text(med.name) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                MedicationCard(selected, relevant, now, zone, busy, actions)
                EstimateCard(selected, relevant, now) { modelInfo = true }
            }
            TodaySection(state, now, zone, actions)
        }
        if (canRecord) {
            Secondary("＋ Record", !busy) { recordSheet = true }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (recordSheet) RecordSheet(actions) { recordSheet = false }
    if (modelInfo) AlertDialog(
        onDismissRequest = { modelInfo = false },
        confirmButton = { TextButton({ modelInfo = false }) { Text(tr("Got it")) } },
        title = { Text(tr("About this estimate")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(tr("A simplified curve uses typical peak times and half-lives from product labels. It is not a validated prediction of your blood level, symptom control, or safe dosing."))
                Text(tr("Food, metabolism and formulation can change the curve. Concerta-type ER, atomoxetine and custom medications have no supported curve. Charts use relative scales; different medications cannot be compared."))
                Text(tr("Sources: DailyMed Ritalin, Ritalin LA (IR comparison) and Vyvanse capsule labels. Models: ritalin-ir-v1 / vyvanse-capsule-v1. Engine: relative-heuristic-v1."))
            }
        })
}

/** The selected medication with its one-tap "taken" action and the direct "not taken" route. */
@Composable
private fun MedicationCard(
    selected: Medication,
    relevant: List<DoseEntry>,
    now: Long,
    zone: ZoneId,
    busy: Boolean,
    actions: HomeActions
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(selected.name, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                Note(tr("Usual amount: %s %s", doseText(selected.usualDose), tr(selected.unit)))
            }
            val last = relevant.firstOrNull()
            if (last != null) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("Last logged"), fontWeight = FontWeight.SemiBold)
                val time = Instant.ofEpochMilli(last.timestamp).atZone(zone)
                Text(
                    "${doseText(last.doseMg)} ${tr(last.unit)} · " + time.format(dateFormat) + " · " +
                            time.format(timeFormat(context))
                )
                val minutes = ((now - last.timestamp) / 60000).coerceAtLeast(0)
                Note(
                    if (minutes == 0L) tr("Just now") else if (minutes < 60) tr(
                        "%d min ago",
                        minutes
                    ) else tr("%d h %d min ago", minutes / 60, minutes % 60)
                )
            }
            if (relevant.none {
                    Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() ==
                            Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                }) Note("No dose logged today.")
            Button(
                actions.taken,
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                enabled = !busy,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Outlined.Check, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    tr("Taken · %s %s", doseText(selected.usualDose), tr(selected.unit)),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            actions.notTaken?.let { notTaken ->
                OutlinedButton(
                    notTaken,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    enabled = !busy,
                    shape = MaterialTheme.shapes.large
                ) { Text(tr("Not taken")) }
            }
        }
    }
}

@Composable
private fun EstimateCard(selected: Medication, relevant: List<DoseEntry>, now: Long, onInfo: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    tr("Estimated level"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                )
                Text(tr("24h"), color = Muted)
            }
            Surface(color = Pale, shape = MaterialTheme.shapes.extraLarge) {
                Text(
                    phaseText(
                        if (selected.modelId == null) EstimatePhase.UNAVAILABLE else Estimate.phase(
                            relevant,
                            selected.preset,
                            now
                        )
                    ),
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Sage,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (selected.modelId == null) {
                Text(
                    tr("Your doses are recorded. A short-term curve is not available for this medication."),
                    color = Muted
                )
            } else if (relevant.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) { Text(tr("Log a dose to see your estimate."), color = Muted) }
            } else {
                LevelChart(relevant, selected.preset, now)
            }
            Note("Rough relative estimate, not a medical measurement. Do not use it to decide when to take a dose.")
            TextButton(onInfo, contentPadding = PaddingValues(0.dp)) { Text(tr("About this estimate")) }
        }
    }
}

private class TodayItem(val time: Long, val target: String, val icon: ImageVector, val title: String)

/** Chronological list of everything recorded today; each row opens its editor. */
@Composable
private fun TodaySection(state: LogbookState, now: Long, zone: ZoneId, actions: HomeActions) {
    val context = LocalContext.current
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val until = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val items = remember(state, from) {
        (state.entries.filter { it.timestamp in from until until }.map {
            TodayItem(it.timestamp, "dose:${it.id}", Icons.Outlined.Medication, "${it.medicationName} · ${doseText(it.doseMg)} ${tr(it.unit)}")
        } + state.observations.filter { it.timestamp in from until until }.map {
            val answer = if (it.response == "rated" && it.value != null) "${it.value} / 4" else tr(responseName(it.response))
            TodayItem(it.timestamp, "observation:${it.id}", Icons.Outlined.EditNote, tr(observationShortName(it.category)) + " · " + answer)
        } + state.measurements.filter { it.timestamp in from until until }.map {
            TodayItem(it.timestamp, "measurement:${it.id}", Icons.Outlined.MonitorHeart, measurementText(it))
        } + state.nonUse.filter { it.intersects(from, until) }.map {
            TodayItem(it.start, "nonuse:${it.id}", Icons.Outlined.RemoveCircleOutline, tr(nonUseName(it.kind)) + (state.medications.find { m -> m.id == it.medicationId }?.let { m -> " · " + m.name } ?: ""))
        }).sortedByDescending { it.time }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Recorded today"), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        if (items.isEmpty()) Note("Nothing recorded today")
        items.forEach { item ->
            Surface(
                onClick = { actions.open(item.target) },
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(item.icon, null, tint = Sage)
                    Column(Modifier.weight(1f)) {
                        Text(item.title)
                        Text(
                            if (item.time >= from) Instant.ofEpochMilli(item.time).atZone(zone).format(timeFormat(context))
                            else dateTimeText(item.time, context, zone),
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Muted)
                }
            }
        }
        TextButton(actions.allRecords, contentPadding = PaddingValues(0.dp)) {
            Text(tr("All observations & non-use"))
        }
    }
}

/** Single entry point for everything other than the usual dose taken now. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecordSheet(actions: HomeActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(tr("What do you want to record?"), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            actions.dose?.let { dose ->
                SheetOption(Icons.Outlined.Medication, "Medication", "Different amount or time") { onDismiss(); dose() }
            }
            actions.observation?.let { observe ->
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Outlined.EditNote, null, tint = Sage)
                            Column {
                                Text(tr("How am I doing?"), fontWeight = FontWeight.SemiBold)
                                Note("Choose what you noticed.")
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            observationCategories.forEach { category ->
                                AssistChip(
                                    { onDismiss(); observe(category) },
                                    label = { Text(tr(observationShortName(category))) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }
                }
            }
            actions.notTaken?.let { notTaken ->
                SheetOption(Icons.Outlined.RemoveCircleOutline, "Not taken", "A missed dose or a time without medication") { onDismiss(); notTaken() }
            }
            actions.measurement?.let { measure ->
                SheetOption(Icons.Outlined.MonitorHeart, "Measurement", "Blood pressure, pulse or weight") { onDismiss(); measure() }
            }
        }
    }
}

@Composable
private fun SheetOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .heightIn(min = 64.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, null, tint = Sage)
            Column(Modifier.weight(1f)) {
                Text(tr(title), fontWeight = FontWeight.SemiBold)
                Note(subtitle)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Muted)
        }
    }
}

@Composable
private fun LevelChart(entries: List<DoseEntry>, preset: Preset, now: Long) {
    val start = now - 86_400_000
    val values = remember(entries, preset, now) { Estimate.series(entries, preset, start, now) }
    val max = values.maxOrNull()?.coerceAtLeast(0.001) ?: 1.0
    val color = Sage
    val stroke = StrokeColor
    val muted = Muted
    val timeFormat = timeFormat(LocalContext.current)
    val chartDescription = tr(
        "Relative estimate for the last 24 hours. %s. Rightmost point: now.",
        phaseText(Estimate.phase(entries, preset, now))
    )
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .semantics { contentDescription = chartDescription }) {
            val left = 4.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 12.dp.toPx()
            val bottom = size.height - 12.dp.toPx()
            for (i in 0..2) {
                val y = top + (bottom - top) * i / 2; drawLine(
                    stroke,
                    Offset(left, y),
                    Offset(right, y),
                    1.dp.toPx()
                )
            }
            val path = Path()
            values.forEachIndexed { i, value ->
                val x = left + (right - left) * i / (values.size - 1)
                val y = bottom - ((bottom - top) * value / max).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(3.dp.toPx()))
            drawLine(
                muted.copy(alpha = .6f),
                Offset(right, top),
                Offset(right, bottom),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
            drawCircle(
                color,
                4.dp.toPx(),
                Offset(right, bottom - ((bottom - top) * values.last() / max).toFloat())
            )
            entries.filter { it.preset == preset && it.timestamp in start..now }.forEach { entry ->
                val x = left + (right - left) * ((entry.timestamp - start) / 86_400_000.0).toFloat()
                drawCircle(color, 3.dp.toPx(), Offset(x, bottom))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(start, start + 8 * 3_600_000, start + 16 * 3_600_000).forEach { stamp ->
                Text(
                    Instant.ofEpochMilli(stamp).atZone(ZoneId.systemDefault()).format(timeFormat),
                    fontSize = 12.sp,
                    color = Muted
                )
            }
            Text(tr("Now"), fontSize = 12.sp, color = Muted)
        }
        Spacer(Modifier.height(8.dp)); Note("Dots along the baseline mark logged doses.")
    }
}

@Composable
private fun EntryEditor(
    entry: DoseEntry?,
    medications: List<Medication>,
    supplies: List<Supply>,
    onNonUse: ((Long) -> Unit)?,
    selected: Medication?,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (DoseEntry, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    val actionId = rememberSaveable(entry?.id) { UUID.randomUUID().toString() }
    val initial = entry?.let { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.of(it.zoneId)) }
        ?: ZonedDateTime.now()
    var medId by rememberSaveable(entry?.id) {
        mutableLongStateOf(
            entry?.medicationId ?: selected?.id ?: 0
        )
    }
    val chosen = medications.find { it.id == medId }
    var dose by rememberSaveable(entry?.id) {
        mutableStateOf(
            doseText(
                entry?.doseMg ?: chosen?.usualDose ?: 0.0
            )
        )
    }
    var dateText by rememberSaveable(entry?.id) { mutableStateOf(initial.toLocalDate().toString()) }
    var timeText by rememberSaveable(entry?.id) {
        mutableStateOf(
            initial.toLocalTime().withSecond(0).withNano(0).toString()
        )
    }
    var useNow by rememberSaveable(entry?.id) { mutableStateOf(entry == null) }
    var mood by rememberSaveable(entry?.id) { mutableIntStateOf(entry?.mood ?: -1) }
    var expanded by rememberSaveable(entry?.id) { mutableStateOf(entry?.mood != null) }
    var supplyUnits by rememberSaveable(entry?.id) {
        mutableStateOf(
            entry?.supplyUnits?.let(::doseText) ?: ""
        )
    }
    var notes by rememberSaveable(entry?.id) { mutableStateOf(entry?.notes ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val timeFormat = timeFormat(context)
    ScrollPage {
        BackTitle(if (entry == null) "Log dose" else "Edit entry", onBack)
        Box {
            OutlinedButton(
                { menu = true }, Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(entry?.medicationName?.takeIf { medId == entry.medicationId } ?: chosen?.name
                ?: "Choose medication", Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null)
            }
            DropdownMenu(menu, { menu = false }) {
                medications.filter { it.active || it.id == entry?.medicationId }.forEach { med ->
                    DropdownMenuItem(
                        text = { Text(med.name) },
                        onClick = { medId = med.id; dose = doseText(med.usualDose); menu = false })
                }
            }
        }
        OutlinedTextField(
            dose,
            { dose = it },
            Modifier.fillMaxWidth(),
            label = {
                Text(
                    tr(
                        "Dose (%s)",
                        tr(entry?.unit?.takeIf { medId == entry.medicationId } ?: chosen?.unit
                        ?: "mg")))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        if (onNonUse != null && entry == null) TextButton({ onNonUse(medId) }) { Text(tr("Record not taken")) }
        if (entry == null) SwitchRow(tr("Taken now"), useNow, { useNow = it })
        if (!useNow) {
            val date = LocalDate.parse(dateText)
            val time = LocalTime.parse(timeText)
            OutlinedButton(
                { pickDate(context, date) { dateText = it.toString() } },
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    Icons.Outlined.CalendarToday,
                    null
                ); Spacer(Modifier.width(12.dp)); Text(date.format(dayFormat))
            }
            OutlinedButton(
                {
                    TimePickerDialog(
                        context,
                        { _, h, m -> timeText = LocalTime.of(h, m).toString() },
                        time.hour,
                        time.minute,
                        android.text.format.DateFormat.is24HourFormat(context)
                    ).show()
                }, Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    null
                ); Spacer(Modifier.width(12.dp)); Text(time.format(timeFormat))
            }
            Note(tr("Time zone: %s", initial.zone.id))
        }
        if (stateSupplyEnabled(supplies, medId)) OutlinedTextField(
            supplyUnits,
            { supplyUnits = it },
            label = { Text(tr("Stock units used (optional)")) },
            supportingText = { Text(tr("Only for supply tracking; separate from dose amount.")) })
        // Mood is recorded as an observation now; only legacy entries keep their dose mood editable.
        if (entry?.mood != null) HorizontalDivider()
        if (entry?.mood != null) TextButton({ expanded = !expanded }, Modifier.fillMaxWidth()) {
            Text(tr("How are you feeling?"), Modifier.weight(1f)); Text(
            tr("Optional"),
            fontSize = 12.sp
        ); Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
        if (expanded && entry?.mood != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                moodFaces.forEachIndexed { index, face ->
                    Surface(
                        color = if (mood == index) Pale else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mood = if (mood == index) -1 else index }
                            .semantics(mergeDescendants = true) { this.selected = mood == index }) {
                        Row(
                            Modifier
                                .heightIn(min = 48.dp)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                face,
                                fontSize = 24.sp,
                                modifier = Modifier.clearAndSetSemantics { })
                            Text(tr(moodLabels[index]), Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            notes,
            { if (it.length <= 5000) notes = it },
            Modifier.fillMaxWidth(),
            label = { Text(tr("Notes (optional)")) },
            minLines = 3,
            maxLines = 6
        )
        error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        Primary(
            if (busy) "Saving…" else if (entry == null) "Save dose" else "Save changes",
            !busy
        ) {
            val parsed = parseDose(dose)
            val unchangedTime = entry != null && dateText == initial.toLocalDate()
                .toString() && timeText == initial.toLocalTime().withSecond(0).withNano(0)
                .toString()
            val timestamp = when {
                useNow -> ZonedDateTime.now()
                unchangedTime -> initial
                else -> ZonedDateTime.ofLocal(
                    LocalDate.parse(dateText).atTime(LocalTime.parse(timeText)),
                    initial.zone,
                    initial.offset
                )
            }
            when {
                supplyUnits.isNotBlank() && (supplyUnits.replace(',', '.').toDoubleOrNull()
                    ?.let { it.isFinite() && it >= 0 } != true) -> error =
                    "Enter zero or a positive stock amount."

                parsed == null -> error = "Enter a positive dose."
                chosen == null -> error = "Choose a medication."
                !useNow && !unchangedTime && timestamp.toLocalDateTime() != LocalDate.parse(dateText)
                    .atTime(LocalTime.parse(timeText)) -> error =
                    "That time does not exist because the clocks changed. Choose another time."

                timestamp.toInstant().toEpochMilli() > System.currentTimeMillis() + 1000 -> error =
                    "Choose a time now or in the past."

                else -> onSave(
                    DoseEntry(
                        entry?.id ?: 0,
                        medId,
                        if (entry != null && medId == entry.medicationId) entry.preset else chosen.preset,
                        parsed,
                        timestamp.toInstant().toEpochMilli(),
                        timestamp.zone.id,
                        timestamp.offset.id,
                        mood.takeIf { it >= 0 },
                        notes.trim(),
                        medicationName = if (entry != null && medId == entry.medicationId) entry.medicationName else chosen.name,
                        formulation = if (entry != null && medId == entry.medicationId) entry.formulation else chosen.formulation,
                        strength = if (entry != null && medId == entry.medicationId) entry.strength else chosen.strength,
                        unit = if (entry != null && medId == entry.medicationId) entry.unit else chosen.unit,
                        modelId = if (entry != null && medId == entry.medicationId) entry.modelId else chosen.modelId,
                        supplyUnits = supplyUnits.replace(',', '.').toDoubleOrNull()
                    ), actionId
                )
            }
        }
        if (entry != null) TextButton(
            { delete = true },
            Modifier.fillMaxWidth(),
            enabled = !busy
        ) { Text(tr("Delete entry")) }
    }
    if (delete && entry != null) AlertDialog(
        onDismissRequest = { delete = false },
        title = { Text(tr("Delete this entry?")) },
        text = { Text(tr("This removes the dose and its notes from your log. This cannot be undone.")) },
        confirmButton = {
            TextButton({
                delete = false; onDelete(entry.id)
            }) { Text(tr("Delete")) }
        },
        dismissButton = { TextButton({ delete = false }) { Text(tr("Keep entry")) } })
}

internal fun pickDate(
    context: Context,
    date: LocalDate,
    onPick: (LocalDate) -> Unit
) {
    DatePickerDialog(
        context,
        { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) },
        date.year,
        date.monthValue - 1,
        date.dayOfMonth
    ).show()
}

@Composable
private fun ExportScreen(document: BackupDocument, snackbar: SnackbarHostState) {
    var start by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(6).toString()) }
    var end by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val entries = document.entries
    var includeNotes by rememberSaveable { mutableStateOf(true) }
    var summary by rememberSaveable { mutableStateOf(false) }
    var questions by rememberSaveable { mutableStateOf("") }
    var formatName by rememberSaveable { mutableStateOf(ReportFormat.PDF.name) }
    var exporting by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf<java.io.File?>(null) }
    val requestKey = listOf(document, start, end, formatName, summary, includeNotes, questions)
    val latestRequest by rememberUpdatedState(requestKey)
    LaunchedEffect(requestKey) { prepared?.delete(); prepared = null }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val from = LocalDate.parse(start)
    val to = LocalDate.parse(end)
    val invalid = to.isBefore(from) || to.toEpochDay() - from.toEpochDay() > 365
    val count = if (invalid) 0 else Reports.inRange(entries, from, to, ZoneId.systemDefault()).size
    ScrollPage {
        Title("Share with\nyour doctor")
        Text(tr("A clear record of your medication and how you felt."), color = Muted)
        OutlinedButton(
            { pickDate(context, from) { start = it.toString() } },
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) { Text(tr("From · %s", from.display())) }
        OutlinedButton(
            { pickDate(context, to) { end = it.toString() } },
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) { Text(tr("To · %s", to.display())) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton({
                end = LocalDate.now().toString(); start = LocalDate.now().minusDays(6).toString()
            }) { Text(tr("Last 7 days")) }
            TextButton({
                end = LocalDate.now().toString(); start = LocalDate.now().minusDays(29).toString()
            }) { Text(tr("Last 30 days")) }
        }
        ReportFormat.entries.forEach { format ->
            Surface(
                color = if (formatName == format.name) Pale else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { formatName = format.name }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        formatName == format.name,
                        { formatName = format.name },
                        Modifier.semantics { contentDescription = format.name })
                    Column {
                        Text(tr(if (format == ReportFormat.PDF) "PDF report" else "CSV")); Note(
                        if (format == ReportFormat.PDF) "Graphs, doses, mood & notes" else "Open in a spreadsheet"
                    )
                    }
                }
            }
        }
        when {
            invalid -> Note("Choose a valid range of up to 366 days."); count == 0 -> Note("No doses logged. This does not confirm that no medication was taken."); else -> Note(
            tr("Entries: %d · %s", count, ZoneId.systemDefault().id)
        )
        }
        Note("Only your selected dates are included. You choose where to share the file.")
        Spacer(Modifier.weight(1f))
        Column {
            CheckboxRow("Summary with details", summary) { summary = it }
            CheckboxRow("Include free-text notes", includeNotes) { includeNotes = it }
        }
        if (summary) {
            OutlinedTextField(
                questions,
                { questions = it.take(5000) },
                label = { Text(tr("Questions for my appointment")) },
                minLines = 2
            )
            if (!invalid) {
                val boundaries =
                    generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to.plusDays(1)) }
                        .map { it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                        .toList()
                SummaryPreview(SummaryBuilder.build(document, boundaries))
            }
        }
        Primary(if (exporting) "Preparing report…" else "Create report", !invalid && !exporting) {
            exporting = true
            val request = requestKey
            val selectedSummary = summary
            val selectedNotes = includeNotes
            val selectedQuestions = questions
            val format = ReportFormat.valueOf(formatName)
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        Reports.createDocument(
                            context,
                            document,
                            from,
                            to,
                            format,
                            selectedSummary,
                            selectedNotes,
                            selectedQuestions
                        )
                    }
                    if (latestRequest == request) prepared = file else file.delete()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    snackbar.showSnackbar(tr("Could not export this report. Please try again."))
                } finally {
                    exporting = false
                }
            }
        }
        prepared?.let { file ->
            Primary("Share report", !exporting) {
                Reports.share(
                    context,
                    file,
                    ReportFormat.valueOf(formatName)
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: LogbookState,
    busy: Boolean,
    vm: LogbookViewModel,
    onMedication: (Long) -> Unit,
    onExport: () -> Unit,
    onShould: (String) -> Unit
) {
    val context = LocalContext.current
    val timeFormat = timeFormat(context)
    var configureReminder by remember { mutableStateOf<Reminder?>(null) }
    var remove by remember { mutableStateOf<Medication?>(null) }
    var archived by rememberSaveable { mutableStateOf(false) }
    var denied by remember {
        mutableStateOf(
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        )
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) denied =
                !NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            denied = !granted; vm.reminders(true)
        }
    val is24h = android.text.format.DateFormat.is24HourFormat(context)
    val paused = state.pause.active(System.currentTimeMillis())
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item { Title("Settings") }
        item {
            SettingsSection(tr("Medications")) {
                state.medications.filter { it.active }.forEach { med ->
                    SettingsRow(
                        med.name,
                        Modifier.clickable(
                            enabled = !busy,
                            onClickLabel = tr("Edit %s", med.name)
                        ) { onMedication(med.id) },
                        tr("Usual dose · %s %s", doseText(med.usualDose), tr(med.unit)),
                        Icons.Outlined.Medication,
                        !busy
                    ) {
                        IconButton(
                            { remove = med },
                            enabled = !busy
                        ) { Icon(Icons.Outlined.RemoveCircleOutline, tr("Remove %s", med.name)) }
                    }
                    SettingsDivider()
                }
                SettingsRow(
                    tr("Add medication"),
                    Modifier.clickable(
                        enabled = !busy,
                        role = androidx.compose.ui.semantics.Role.Button
                    ) { onMedication(-1) },
                    icon = Icons.Outlined.Add,
                    enabled = !busy
                )
                SettingsDivider()
                SettingsNavRow(
                    tr("Supply"),
                    icon = Icons.Outlined.Inventory2
                ) { onShould("supply") }
                SettingsDivider()
                SettingsRow(
                    tr("Archived medications"),
                    Modifier.clickable { archived = !archived },
                    icon = Icons.Outlined.Archive
                ) {
                    Icon(
                        if (archived) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        null,
                        tint = Muted
                    )
                }
                if (archived) {
                    val inactive = state.medications.filter { !it.active }
                    if (inactive.isEmpty()) SettingsRow(
                        tr("No archived medications."),
                        enabled = false
                    )
                    inactive.forEach { med ->
                        SettingsRow(med.name) {
                            TextButton(
                                { vm.medication(med.copy(active = true)) {} },
                                enabled = !busy
                            ) { Text(tr("Reactivate")) }
                        }
                    }
                }
            }
        }
        item {
            SettingsSection(
                tr("Reminders"),
                footer = tr("A gentle reminder to log. Delivery may be delayed by battery settings.")
            ) {
                val status = if (!state.remindersEnabled) null else tr(
                    when {
                        denied -> "Notifications are turned off. Your reminder times are saved."; paused -> "Reminders are paused."; state.reminders.isEmpty() -> "No reminder times. Add a time to receive reminders."; else -> "Reminders are scheduled."
                    }
                )
                SettingsSwitchRow(tr("Reminders"), state.remindersEnabled, { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else {
                        denied = !NotificationManagerCompat.from(context)
                            .areNotificationsEnabled(); vm.reminders(enabled)
                    }
                }, status, Icons.Outlined.Notifications, !busy)
                if (state.remindersEnabled) {
                    state.reminders.forEach { reminder ->
                        SettingsDivider()
                        val target =
                            reminder.medicationId?.let { id -> state.medications.firstOrNull { it.id == id }?.name }
                                ?: tr("Generic reminder")
                        SettingsRow(
                            LocalTime.of(reminder.hour, reminder.minute).format(timeFormat),
                            Modifier.clickable(enabled = !busy) {
                                TimePickerDialog(
                                    context,
                                    { _, h, m -> vm.reminder(h, m, reminder.id) },
                                    reminder.hour,
                                    reminder.minute,
                                    is24h
                                ).show()
                            },
                            target,
                            Icons.Outlined.Alarm,
                            !busy
                        ) {
                            Row {
                                IconButton(
                                    { configureReminder = reminder },
                                    enabled = !busy
                                ) { Icon(Icons.Outlined.Tune, tr("Reminder options")) }
                                IconButton(
                                    { vm.removeReminder(reminder.id) },
                                    enabled = !busy
                                ) {
                                    Icon(
                                        Icons.Outlined.RemoveCircleOutline,
                                        tr("Remove reminder")
                                    )
                                }
                            }
                        }
                    }
                    SettingsDivider()
                    SettingsRow(
                        tr("Add time"),
                        Modifier.clickable(
                            enabled = !busy,
                            role = androidx.compose.ui.semantics.Role.Button
                        ) {
                            TimePickerDialog(
                                context,
                                { _, h, m -> vm.reminder(h, m) },
                                12,
                                0,
                                is24h
                            ).show()
                        },
                        icon = Icons.Outlined.Add,
                        enabled = !busy
                    )
                    if (denied) {
                        SettingsDivider()
                        SettingsNavRow(
                            tr("Open notification settings"),
                            icon = Icons.Outlined.NotificationsOff
                        ) {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
                                    Settings.EXTRA_APP_PACKAGE,
                                    context.packageName
                                )
                            )
                        }
                    }
                }
                SettingsDivider()
                SettingsNavRow(
                    tr(if (paused) "Resume / change pause" else "Pause reminders"),
                    icon = Icons.Outlined.PauseCircle
                ) { onShould("pause") }
            }
        }
        item {
            SettingsSection(tr("Optional features")) {
                SettingsSwitchRow(
                    tr("Measurements"),
                    vm.enabled("measurements_enabled"),
                    { vm.preference("measurements_enabled", it.toString()) },
                    tr("Blood pressure, pulse and weight. Disabling keeps saved records."),
                    Icons.Outlined.MonitorHeart,
                    !busy
                )
                SettingsDivider()
                SettingsSwitchRow(
                    tr("Weekly overview"),
                    vm.enabled("weekly_enabled"),
                    { vm.preference("weekly_enabled", it.toString()) },
                    icon = Icons.Outlined.CalendarMonth,
                    enabled = !busy
                )
                SettingsDivider()
                SettingsNavRow(
                    tr("Home-screen widget"),
                    icon = Icons.Outlined.Widgets
                ) { onShould("widget") }
                SettingsDivider()
                SettingsNavRow(tr("Quick access"), icon = Icons.Outlined.Bolt) { onShould("quick") }
            }
        }
        item {
            SettingsSection(
                tr("Data & privacy"),
                footer = tr("Your log stays on this device. No account needed.")
            ) {
                PrivacyControl()
            }
        }
        item {
            SettingsSection(
                tr("Backup & export"),
                footer = tr("Cloud backups are disabled. Uninstalling removes your log. Create a backup to restore it later.")
            ) {
                BackupControls(vm, busy)
                SettingsDivider()
                SettingsNavRow(
                    tr("Export your log"),
                    icon = Icons.Outlined.IosShare,
                    onClick = onExport
                )
            }
        }
        item { Note("ADHS Logbook · 0.4.0\nEstimates are informational and are not medical measurements.") }
    }
    configureReminder?.let {
        ReminderOptions(
            it,
            state.medications,
            { configureReminder = null }) { value -> vm.reminder(value); configureReminder = null }
    }
    remove?.let { med ->
        AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text(tr("Remove medication?")) },
            text = {
                Text(
                    tr(
                        "%s will no longer appear for new doses. Past entries remain in your history.",
                        med.name
                    )
                )
            },
            confirmButton = {
                TextButton({
                    vm.removeMedication(med.id); remove = null
                }) { Text(tr("Remove")) }
            },
            dismissButton = { TextButton({ remove = null }) { Text(tr("Keep")) } })
    }
}

private fun stateSupplyEnabled(supplies: List<Supply>, id: Long) =
    supplies.any { it.medicationId == id }

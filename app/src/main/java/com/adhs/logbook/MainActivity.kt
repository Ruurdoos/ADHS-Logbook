package com.adhs.logbook

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhs.logbook.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.util.UUID
import android.content.Context
import java.util.Locale
import java.text.NumberFormat
import java.time.format.DateTimeFormatter

private val Sage: Color @Composable get() = MaterialTheme.colorScheme.primary
private val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val Paper: Color @Composable get() = MaterialTheme.colorScheme.background
private val Pale: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val StrokeColor: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val dayFormat get() = DateTimeFormatter.ofPattern("EEEE, d MMMM",Locale.getDefault())
internal fun timeFormat(context: Context) = DateTimeFormatter.ofPattern(if(android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",Locale.getDefault())
fun tr(text: String,vararg values: Any): String = TextCatalog.text(text,Locale.getDefault().language).let { if(values.isEmpty()) it else String.format(Locale.getDefault(),it,*values) }
fun phaseText(phase: EstimatePhase) = tr(when(phase) {
    EstimatePhase.UNAVAILABLE -> "Estimate unavailable"; EstimatePhase.NO_LOGS -> "No doses logged"
    EstimatePhase.RECENT -> "Recently logged"; EstimatePhase.LOW -> "Low estimated level"
    EstimatePhase.PEAK -> "Near estimated peak"; EstimatePhase.RISING -> "Estimate increasing"; EstimatePhase.DECREASING -> "Estimate decreasing"
})
class MainActivity : androidx.fragment.app.FragmentActivity() {
    private var reviewIntent by mutableStateOf<String?>(null)
    private var widgetIntent by mutableStateOf<String?>(null)
    override fun onStop() { super.onStop();AppPrivacy.unlocked=false }
    private var reminderIntent by mutableStateOf<String?>(null)
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);reviewIntent=intent.getStringExtra("review_token");widgetIntent=intent.getStringExtra("widget_token");reminderIntent=intent.getStringExtra("occurrence") }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reviewIntent=intent.getStringExtra("review_token")
        reminderIntent=intent.getStringExtra("occurrence")
        widgetIntent=intent.getStringExtra("widget_token")
        AppPrivacy.enabled=AppPrivacy.isEnabled(this)
        if(AppPrivacy.enabled) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            val dark=isSystemInDarkTheme()
            val colors=if(dark) darkColorScheme(primary=Color(0xFFA8D5B7),onPrimary=Color(0xFF123822),primaryContainer=Color(0xFF294B37),
                background=Color(0xFF121713),surface=Color(0xFF1B231D),onSurface=Color(0xFFE3EAE1),onBackground=Color(0xFFE3EAE1),onSurfaceVariant=Color(0xFFBFCBBC))
            else lightColorScheme(primary=Color(0xFF416B59),onPrimary=Color.White,primaryContainer=Color(0xFFE8EFE6),
                background=Color(0xFFF7F8F4),surface=Color.White,onSurface=Color(0xFF24352D),onBackground=Color(0xFF24352D),onSurfaceVariant=Color(0xFF5D6B63))
            MaterialTheme(colorScheme=colors) { PrivacyGate { LogbookApp(reviewIntent=reviewIntent,onReviewHandled={ reviewIntent=null },reminderIntent=reminderIntent,onIntentHandled={ reminderIntent=null },widgetIntent=widgetIntent,onWidgetHandled={ widgetIntent=null }) } }
        }
    }
}

@Composable
fun LogbookApp(vm: LogbookViewModel = viewModel(), reviewIntent: String? = null, onReviewHandled: ()->Unit = {}, reminderIntent: String? = null, onIntentHandled: () -> Unit = {}, widgetIntent: String? = null, onWidgetHandled: ()->Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var shouldPage by rememberSaveable { mutableStateOf<String?>(null) }
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
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) vm.refresh() }
        lifecycle.addObserver(observer);onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(reminderIntent,state.loaded) {
        if(state.loaded && reminderIntent!=null) {
            val occurrence=vm.occurrence(reminderIntent)
            val reminder=state.reminders.find { it.id==occurrence?.reminderId }
            if(occurrence!=null && reminder!=null && occurrence.state=="pending" && System.currentTimeMillis()<occurrence.expires) {
                occurrenceId=occurrence.id;selectedMedication=reminder.medicationId ?: 0;editingEntry=-1;tab=0
            }
            onIntentHandled()
        }
    }
    LaunchedEffect(reviewIntent,state.loaded,busy) {
        if(state.loaded && reviewIntent!=null && !busy) {
            val route=QuickAccess.route(context,reviewIntent,state.medications)
            if(route.unavailable || active.isEmpty()) errorMessage=tr("Quick access is unavailable. Open Settings to configure it.")
            else { selectedMedication=route.medicationId ?: active.first().id;occurrenceId=null;editingEntry=-1;shouldPage=null;tab=0
                if(route.changed) errorMessage=tr("Medication settings changed. Review the current amount.") }
            onReviewHandled()
        }
    }
    LaunchedEffect(widgetIntent,state.loaded,busy) {
        if(state.loaded && widgetIntent!=null && !busy) {
            val prefs=context.getSharedPreferences("widget",Context.MODE_PRIVATE)
            val med=state.medications.find { it.id==prefs.getLong("med",0) && it.active }
            if(med!=null && widgetIntent.isNotBlank() && widgetIntent==prefs.getString("token",null) && med.revision==prefs.getLong("revision",0)) {
                if(prefs.getBoolean("private",true)) { selectedMedication=med.id;editingEntry=-1 }
                else vm.widgetLog(med,widgetIntent) { id -> scope.launch { if(snackbar.showSnackbar(tr("Dose logged"),tr("Undo"))==SnackbarResult.ActionPerformed) vm.delete(id) } }
            } else { med?.let { selectedMedication=it.id;editingEntry=-1 };errorMessage=tr("Widget changed. Review the medication and amount in the app.") }
            onWidgetHandled()
        }
    }
    LaunchedEffect(vm) { for(message in vm.messages) errorMessage=message }
    LaunchedEffect(editingEntry,editingMedication) {
        if(editingEntry != null || editingMedication != null) snackbar.currentSnackbarData?.dismiss()
    }
    LaunchedEffect(state.loaded) {
        if(state.loaded) withContext(Dispatchers.IO) { ReminderScheduler.reschedule(context,state) }
    }
    fun saved(id: Long) {
        editingEntry = null
        occurrenceId = null
        scope.launch {
            if(snackbar.showSnackbar(tr("Dose logged"), tr("Undo"), duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) vm.delete(id)
        }
    }
    BackHandler(shouldPage!=null || editingEntry != null || editingMedication != null || tab != 0) {
        when { shouldPage!=null -> shouldPage=null;editingEntry != null -> editingEntry = null; editingMedication != null -> editingMedication = null; else -> tab = 0 }
    }
    Scaffold(
        containerColor = Paper,
        snackbarHost = { if(editingEntry == null && editingMedication == null) SnackbarHost(snackbar) },
        bottomBar = {
            if(state.loaded && state.onboarded && editingEntry == null && editingMedication == null && shouldPage == null) {
                NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                    val titles = listOf("Home","History","Export","Settings")
                    val icons = listOf(Icons.Outlined.Home,Icons.Outlined.History,Icons.Outlined.IosShare,Icons.Outlined.Settings)
                    titles.forEachIndexed { index,title ->
                        NavigationBarItem(selected = tab == index,onClick = { tab = index },
                            icon = { Icon(icons[index],null) },label = { Text(tr(title)) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Pale,selectedIconColor = Sage))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when {
                shouldPage!=null -> ShouldScreen(shouldPage!!,state,vm,{ if(shouldPage!!.startsWith("nonuse-occ:")) { editingEntry=null;occurrenceId=null };shouldPage=null },{ shouldPage=it })
                !state.loaded -> Box(Modifier.fillMaxSize(),contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                !state.onboarded && welcome -> Welcome { welcome = false }
                !state.onboarded || editingMedication != null -> MedicationEditor(
                    medication = state.medications.find { it.id == editingMedication }, busy = busy,
                    onboarding = !state.onboarded,
                    onBack = { if(state.onboarded) editingMedication = null else welcome = true },
                    onSave = { med -> vm.medication(med) { editingMedication = null } },
                )
                editingEntry != null -> EntryEditor(
                    entry = state.entries.find { it.id == editingEntry }, medications = state.medications, supplies=state.supplies, onNonUse=occurrenceId?.let { id -> { med: Long -> shouldPage="nonuse-occ:$id:$med" } },
                    selected = selected, busy = busy, onBack = { editingEntry = null; occurrenceId=null },
                    onSave = { entry, actionId -> vm.save(entry,actionId,occurrenceId) { id ->
                        if(entry.id == 0L) saved(id) else { editingEntry = null; scope.launch { snackbar.showSnackbar(tr("Changes saved")) } }
                    } },
                    onDelete = { id -> vm.delete(id) { editingEntry = null; scope.launch { snackbar.showSnackbar(tr("Entry deleted")) } } },
                )
                tab == 0 -> Column {
                    if(vm.observationsEnabled()) TextButton({ shouldPage="observation:" }) { Text(tr("Add observation")) }
                    state.supplies.filter { it.medicationId==selected?.id }.forEach { supply ->
                        val balance=SupplyLedger.balance(vm.document(),supply)
                        if(balance.inconsistent || balance.remaining<=supply.lowThreshold || (supply.prescriptionDate?.let { it<=System.currentTimeMillis() }==true)) TextButton({ shouldPage="supply" }) { Text(tr("Estimated remaining: %s %s",doseText(balance.remaining),supply.unitLabel)) }
                    }
                    HomeScreen(state,selected,busy,{ selectedMedication = it },{ editingEntry = -1 },{
                    selected?.let { vm.quick(it,::saved) }
                },{ editingMedication = -1 })
                }
                tab == 1 -> Column {
                    if(vm.enabled("weekly_enabled")) TextButton({ shouldPage="weekly" }) { Text(tr("Weekly overview")) }
                    TextButton({ shouldPage="records" }) { Text(tr("Observations & non-use")) }
                    Box(Modifier.weight(1f)) { HistoryScreen(state.entries) { editingEntry = it } }
                }
                tab == 2 -> ExportScreen(vm.document(),snackbar)
                else -> SettingsScreen(state,busy,vm,{ editingMedication = it },{ tab = 2 },{ shouldPage=it })
            }
        }
    }
    errorMessage?.let { message ->
        AlertDialog(onDismissRequest={ errorMessage=null },title={ Text(tr("Something went wrong")) },text={ Text(message) },confirmButton={ TextButton({ errorMessage=null }) { Text(tr("Close")) } })
    }
}

@Composable private fun Title(text: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if(subtitle != null) Text(subtitle,color=Muted,style=MaterialTheme.typography.bodyMedium)
        Text(tr(text),fontSize=28.sp,lineHeight=34.sp,fontWeight=FontWeight.SemiBold)
    }
}
@Composable private fun Primary(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick,Modifier.fillMaxWidth().heightIn(min=56.dp),enabled=enabled,shape=MaterialTheme.shapes.large) {
        Text(tr(text),fontSize=16.sp,modifier=Modifier.padding(vertical=6.dp))
    }
}
@Composable private fun Note(text: String) { Text(tr(text),color=Muted,style=MaterialTheme.typography.bodySmall,lineHeight=19.sp) }
@Composable private fun BackTitle(title: String,onBack: () -> Unit) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Back")) }
        Text(tr(title),fontSize=26.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
    }
}
@Composable private fun ScrollPage(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableHeight = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min=availableHeight).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp),content=content)
    }
}
@Composable private fun Welcome(onStart: () -> Unit) {
    ScrollPage {
        Spacer(Modifier.height(40.dp))
        Text(tr("ADHS LOGBOOK"),color=Sage,letterSpacing=2.sp,fontSize=12.sp,fontWeight=FontWeight.Bold)
        Text(tr("A little clarity,\nevery day."),fontSize=38.sp,lineHeight=45.sp,fontWeight=FontWeight.SemiBold)
        Text(tr("Log your medication. Keep notes when you want to."),color=Muted,fontSize=18.sp,lineHeight=28.sp)
        Spacer(Modifier.weight(1f))
        Note("Private. Stored on this device.")
        Primary("Get started",onClick=onStart)
    }
}

@Composable private fun MedicationEditor(medication: Medication?, busy: Boolean,onboarding: Boolean,onBack: () -> Unit,onSave: (Medication) -> Unit) {
    var presetName by rememberSaveable(medication?.id) { mutableStateOf((medication?.preset ?: Preset.METHYLPHENIDATE_IR).name) }
    var dose by rememberSaveable(medication?.id) { mutableStateOf(medication?.usualDose?.let(::doseText) ?: "") }
    var name by rememberSaveable(medication?.id) { mutableStateOf(medication?.name ?: "") }
    var formulation by rememberSaveable(medication?.id) { mutableStateOf(medication?.formulation ?: "") }
    var strength by rememberSaveable(medication?.id) { mutableStateOf(medication?.strength ?: "") }
    var unit by rememberSaveable(medication?.id) { mutableStateOf(medication?.unit ?: "mg") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val preset=Preset.valueOf(presetName);val parsed=parseDose(dose)
    val custom=preset==Preset.CUSTOM
    ScrollPage {
        BackTitle(if(medication == null) "Add your medication" else "Edit medication",onBack)
        Preset.entries.forEach { item ->
            val checked=item==preset
            Surface(color=if(checked) Pale else MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.medium,
                modifier=Modifier.fillMaxWidth().clickable { presetName=item.name;unit="mg" }) {
                Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(checked,{ presetName=item.name;unit="mg" })
                    Text(tr(item.title),Modifier.weight(1f).padding(end=8.dp))
                }
            }
        }
        if(custom) {
            OutlinedTextField(name,{ name=it.take(200) },Modifier.fillMaxWidth(),label={ Text(tr("Medication name")) },isError=attempted && name.isBlank())
            OutlinedTextField(formulation,{ formulation=it.take(200) },Modifier.fillMaxWidth(),label={ Text(tr("Formulation (optional)")) })
            OutlinedTextField(strength,{ strength=it.take(100) },Modifier.fillMaxWidth(),label={ Text(tr("Strength with unit (optional)")) },supportingText={ Text(tr("For your records only; no dose conversion.")) })
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { doseUnits.forEach { value -> FilterChip(selected=unit==value,onClick={ unit=value },label={ Text(tr(value)) }) } }
        }
        OutlinedTextField(dose,{ dose=it },Modifier.fillMaxWidth(),label={ Text(tr("Your usual dose (%s)",tr(unit))) },
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,
            isError=attempted && parsed == null,supportingText={ if(attempted && parsed == null) Text(tr("Enter a positive dose.")) })
        Note("Enter your prescribed dose. You can add another medication later.")
        if(defaultModel(preset)==null) Note("Logging is available. No supported estimate is available for this formulation.")
        Spacer(Modifier.weight(1f))
        Primary(if(busy) "Saving…" else if(onboarding) "Start" else "Save medication",!busy) {
            attempted=true
            if(parsed!=null && (!custom || name.isNotBlank())) onSave(Medication(medication?.id ?: 0,preset,parsed,
                name=if(custom) name.trim() else preset.title,formulation=if(custom) formulation.trim() else preset.name,
                strength=if(custom) strength.trim() else "",unit=unit))
        }
    }
}

@Composable private fun HomeScreen(state: LogbookState,selected: Medication?,busy: Boolean,onSelect: (Long) -> Unit,onLog: () -> Unit,onQuick: () -> Unit,onAdd: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) { now=System.currentTimeMillis(); delay(30_000) } }
    val homeLifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(homeLifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) now=System.currentTimeMillis() }
        homeLifecycle.addObserver(observer)
        onDispose { homeLifecycle.removeObserver(observer) }
    }
    var menu by remember { mutableStateOf(false) }
    var modelInfo by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val relevant = state.entries.filter { it.medicationId == selected?.id }
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Title("Today",Instant.ofEpochMilli(now).atZone(zone).format(dayFormat))
            if(selected == null) {
                Surface(color=MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.large) {
                    Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(tr("Your log starts here."),fontSize=22.sp)
                        Note("Add a medication to start your log.")
                        Primary("Add medication",onClick=onAdd)
                    }
                }
            } else {
                if(state.medications.count { it.active } > 1) Box {
                    OutlinedButton({ menu=true },Modifier.fillMaxWidth()) { Text(selected.name,Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore,null) }
                    DropdownMenu(menu,{ menu=false }) { state.medications.filter { it.active }.forEach { med ->
                        DropdownMenuItem(text={ Text("${med.name} · ${doseText(med.usualDose)} ${tr(med.unit)}") },onClick={ onSelect(med.id); menu=false })
                    } }
                }
                relevant.firstOrNull()?.let { last ->
                    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(tr("Last logged"),fontWeight=FontWeight.SemiBold)
                        Text("${last.medicationName} · ${doseText(last.doseMg)} ${tr(last.unit)}")
                        Note(Instant.ofEpochMilli(last.timestamp).atZone(zone).format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))+" · "+Instant.ofEpochMilli(last.timestamp).atZone(zone).format(timeFormat(LocalContext.current)))
                        val minutes=((now-last.timestamp)/60000).coerceAtLeast(0)
                        Note(if(minutes==0L) tr("Just now") else if(minutes<60) tr("%d min ago",minutes) else tr("%d h %d min ago",minutes/60,minutes%60))
                    }
                }
                if(relevant.none { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()==LocalDate.now() }) Note("No dose logged today.")
                Surface(color=MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.large) {
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(tr("Estimated level"),fontWeight=FontWeight.SemiBold,fontSize=20.sp)
                            Text(tr("24h"),color=Muted)
                        }
                        Note(selected.name)
                        Surface(color=Pale,shape=MaterialTheme.shapes.extraLarge) {
                            Text(phaseText(if(selected.modelId==null) EstimatePhase.UNAVAILABLE else Estimate.phase(relevant,selected.preset,now)),Modifier.padding(horizontal=12.dp,vertical=8.dp),color=Sage,style=MaterialTheme.typography.labelLarge)
                        }
                        if(selected.modelId == null) {
                            Text(tr("Your doses are recorded. A short-term curve is not available for this medication."),color=Muted)
                        } else if(relevant.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(160.dp),contentAlignment=Alignment.Center) { Text(tr("Log a dose to see your estimate."),color=Muted) }
                        } else {
                            LevelChart(relevant,selected.preset,now)
                        }
                        Note("Rough relative estimate, not a medical measurement. Do not use it to decide when to take a dose.")
                        TextButton({ modelInfo=true },contentPadding=PaddingValues(0.dp)) { Text(tr("About this estimate")) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if(selected != null) {
            Primary("＋ Log dose",!busy,onLog)
            TextButton(onQuick,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!busy) { Text(tr("Log now · %s %s",doseText(selected.usualDose),tr(selected.unit))) }
        }
    }
    if(modelInfo) AlertDialog(onDismissRequest={ modelInfo=false },confirmButton={ TextButton({ modelInfo=false }) { Text(tr("Got it")) } },title={ Text(tr("About this estimate")) },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(tr("A simplified curve uses typical peak times and half-lives from product labels. It is not a validated prediction of your blood level, symptom control, or safe dosing."))
            Text(tr("Food, metabolism and formulation can change the curve. Concerta-type ER, atomoxetine and custom medications have no supported curve. Charts use relative scales; different medications cannot be compared."))
            Text(tr("Sources: DailyMed Ritalin, Ritalin LA (IR comparison) and Vyvanse capsule labels. Models: ritalin-ir-v1 / vyvanse-capsule-v1. Engine: relative-heuristic-v1."))
        }
    })
}

@Composable private fun LevelChart(entries: List<DoseEntry>,preset: Preset,now: Long) {
    val start = now - 86_400_000
    val values = remember(entries,preset,now) { Estimate.series(entries,preset,start,now) }
    val max = values.maxOrNull()?.coerceAtLeast(0.001) ?: 1.0
    val color = Sage
    val stroke=StrokeColor;val muted=Muted
    val timeFormat=timeFormat(LocalContext.current)
    val chartDescription=tr("Relative estimate for the last 24 hours. %s. Rightmost point: now.",phaseText(Estimate.phase(entries,preset,now)))
    Column {
        Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription=chartDescription }) {
            val left=4.dp.toPx(); val right=size.width-8.dp.toPx(); val top=12.dp.toPx(); val bottom=size.height-12.dp.toPx()
            for(i in 0..2) { val y=top+(bottom-top)*i/2; drawLine(stroke,Offset(left,y),Offset(right,y),1.dp.toPx()) }
            val path=Path()
            values.forEachIndexed { i,value ->
                val x=left+(right-left)*i/(values.size-1); val y=bottom-((bottom-top)*value/max).toFloat()
                if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
            }
            drawPath(path,color,style=Stroke(3.dp.toPx()))
            drawLine(muted.copy(alpha=.6f),Offset(right,top),Offset(right,bottom),1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(6f,6f)))
            drawCircle(color,4.dp.toPx(),Offset(right,bottom-((bottom-top)*values.last()/max).toFloat()))
            entries.filter { it.preset==preset && it.timestamp in start..now }.forEach { entry ->
                val x=left+(right-left)*((entry.timestamp-start)/86_400_000.0).toFloat()
                drawCircle(color,3.dp.toPx(),Offset(x,bottom))
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            listOf(start,start+8*3_600_000,start+16*3_600_000).forEach { stamp -> Text(Instant.ofEpochMilli(stamp).atZone(ZoneId.systemDefault()).format(timeFormat),fontSize=12.sp,color=Muted) }
            Text(tr("Now"),fontSize=12.sp,color=Muted)
        }
        Spacer(Modifier.height(8.dp)); Note("Dots along the baseline mark logged doses.")
    }
}

@Composable private fun EntryEditor(entry: DoseEntry?,medications: List<Medication>,supplies: List<Supply>,onNonUse: ((Long) -> Unit)?,selected: Medication?,busy: Boolean,onBack: () -> Unit,onSave: (DoseEntry,String) -> Unit,onDelete: (Long) -> Unit) {
    val actionId=rememberSaveable(entry?.id) { UUID.randomUUID().toString() }
    val initial = entry?.let { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.of(it.zoneId)) } ?: ZonedDateTime.now()
    var medId by rememberSaveable(entry?.id) { mutableLongStateOf(entry?.medicationId ?: selected?.id ?: 0) }
    val chosen = medications.find { it.id == medId }
    var dose by rememberSaveable(entry?.id) { mutableStateOf(doseText(entry?.doseMg ?: chosen?.usualDose ?: 0.0)) }
    var dateText by rememberSaveable(entry?.id) { mutableStateOf(initial.toLocalDate().toString()) }
    var timeText by rememberSaveable(entry?.id) { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0).toString()) }
    var useNow by rememberSaveable(entry?.id) { mutableStateOf(entry == null) }
    var mood by rememberSaveable(entry?.id) { mutableIntStateOf(entry?.mood ?: -1) }
    var expanded by rememberSaveable(entry?.id) { mutableStateOf(entry?.mood != null) }
    var supplyUnits by rememberSaveable(entry?.id) { mutableStateOf(entry?.supplyUnits?.let(::doseText) ?: "") }
    var notes by rememberSaveable(entry?.id) { mutableStateOf(entry?.notes ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val timeFormat=timeFormat(context)
    ScrollPage {
        BackTitle(if(entry==null) "Log dose" else "Edit entry",onBack)
        Box {
            OutlinedButton({ menu=true },Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                Text(entry?.medicationName?.takeIf { medId==entry.medicationId } ?: chosen?.name ?: "Choose medication",Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore,null)
            }
            DropdownMenu(menu,{ menu=false }) { medications.filter { it.active || it.id==entry?.medicationId }.forEach { med ->
                DropdownMenuItem(text={ Text(med.name) },onClick={ medId=med.id; dose=doseText(med.usualDose); menu=false })
            } }
        }
        OutlinedTextField(dose,{ dose=it },Modifier.fillMaxWidth(),label={ Text(tr("Dose (%s)",tr(entry?.unit?.takeIf { medId==entry.medicationId } ?: chosen?.unit ?: "mg"))) },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
        if(onNonUse!=null && entry==null) TextButton({ onNonUse(medId) }) { Text(tr("Record not taken")) }
        if(entry==null) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(tr("Taken now"),Modifier.weight(1f)); Switch(useNow,{ useNow=it }) }
        if(!useNow) {
            val date=LocalDate.parse(dateText); val time=LocalTime.parse(timeText)
            OutlinedButton({ pickDate(context,date) { dateText=it.toString() } },Modifier.fillMaxWidth().heightIn(min=48.dp)) { Icon(Icons.Outlined.CalendarToday,null); Spacer(Modifier.width(12.dp)); Text(date.format(dayFormat)) }
            OutlinedButton({ TimePickerDialog(context,{ _,h,m -> timeText=LocalTime.of(h,m).toString() },time.hour,time.minute,android.text.format.DateFormat.is24HourFormat(context)).show() },Modifier.fillMaxWidth().heightIn(min=48.dp)) { Icon(Icons.Outlined.Schedule,null); Spacer(Modifier.width(12.dp)); Text(time.format(timeFormat)) }
            Note(tr("Time zone: %s",initial.zone.id))
        }
        if(stateSupplyEnabled(supplies,medId)) OutlinedTextField(supplyUnits,{ supplyUnits=it },label={ Text(tr("Stock units used (optional)")) },supportingText={ Text(tr("Only for supply tracking; separate from dose amount.")) })
        HorizontalDivider()
        TextButton({ expanded=!expanded },Modifier.fillMaxWidth()) {
            Text(tr("How are you feeling?"),Modifier.weight(1f)); Text(tr("Optional"),fontSize=12.sp); Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)
        }
        if(expanded) {
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                moodFaces.forEachIndexed { index,face ->
                    Surface(color=if(mood==index) Pale else MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.medium,
                        modifier=Modifier.fillMaxWidth().clickable { mood=if(mood==index) -1 else index }
                            .semantics(mergeDescendants=true) { this.selected=mood==index }) {
                        Row(Modifier.heightIn(min=48.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text(face,fontSize=24.sp,modifier=Modifier.clearAndSetSemantics { })
                            Text(tr(moodLabels[index]),Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        OutlinedTextField(notes,{ if(it.length<=5000) notes=it },Modifier.fillMaxWidth(),label={ Text(tr("Notes (optional)")) },minLines=3,maxLines=6)
        error?.let { Text(tr(it),color=MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        Primary(if(busy) "Saving…" else if(entry==null) "Save dose" else "Save changes",!busy) {
            val parsed=parseDose(dose)
            val unchangedTime = entry != null && dateText == initial.toLocalDate().toString() && timeText == initial.toLocalTime().withSecond(0).withNano(0).toString()
            val timestamp=when {
                useNow -> ZonedDateTime.now()
                unchangedTime -> initial
                else -> ZonedDateTime.ofLocal(LocalDate.parse(dateText).atTime(LocalTime.parse(timeText)),initial.zone,initial.offset)
            }
            when {
                supplyUnits.isNotBlank() && (supplyUnits.replace(',','.').toDoubleOrNull()?.let { it.isFinite() && it>=0 }!=true) -> error="Enter zero or a positive stock amount."
                parsed==null -> error="Enter a positive dose."
                chosen==null -> error="Choose a medication."
                !useNow && !unchangedTime && timestamp.toLocalDateTime()!=LocalDate.parse(dateText).atTime(LocalTime.parse(timeText)) -> error="That time does not exist because the clocks changed. Choose another time."
                timestamp.toInstant().toEpochMilli()>System.currentTimeMillis()+1000 -> error="Choose a time now or in the past."
                else -> onSave(DoseEntry(entry?.id ?: 0,medId,
                    if(entry!=null && medId==entry.medicationId) entry.preset else chosen.preset,
                    parsed,timestamp.toInstant().toEpochMilli(),timestamp.zone.id,timestamp.offset.id,mood.takeIf { it>=0 },notes.trim(),
                    medicationName=if(entry!=null && medId==entry.medicationId) entry.medicationName else chosen.name,
                    formulation=if(entry!=null && medId==entry.medicationId) entry.formulation else chosen.formulation,
                    strength=if(entry!=null && medId==entry.medicationId) entry.strength else chosen.strength,
                    unit=if(entry!=null && medId==entry.medicationId) entry.unit else chosen.unit,
                    modelId=if(entry!=null && medId==entry.medicationId) entry.modelId else chosen.modelId, supplyUnits=supplyUnits.replace(',','.').toDoubleOrNull()),actionId)
            }
        }
        if(entry!=null) TextButton({ delete=true },Modifier.fillMaxWidth(),enabled=!busy) { Text(tr("Delete entry")) }
    }
    if(delete && entry!=null) AlertDialog(onDismissRequest={ delete=false },title={ Text(tr("Delete this entry?")) },text={ Text(tr("This removes the dose and its notes from your log. This cannot be undone.")) },confirmButton={ TextButton({ delete=false; onDelete(entry.id) }) { Text(tr("Delete")) } },dismissButton={ TextButton({ delete=false }) { Text(tr("Keep entry")) } })
}

private fun pickDate(context: android.content.Context,date: LocalDate,onPick: (LocalDate) -> Unit) {
    DatePickerDialog(context,{ _,y,m,d -> onPick(LocalDate.of(y,m+1,d)) },date.year,date.monthValue-1,date.dayOfMonth).show()
}

@Composable private fun HistoryScreen(entries: List<DoseEntry>,onEdit: (Long) -> Unit) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val context=LocalContext.current
    val timeFormat=timeFormat(context)
    val zone=ZoneId.systemDefault()
    val grouped=entries.filter { filter==null || Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().toString()==filter }
        .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=24.dp),contentPadding=PaddingValues(vertical=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Title("History") }
        item { Row(verticalAlignment=Alignment.CenterVertically) {
            OutlinedButton({ pickDate(context,filter?.let(LocalDate::parse) ?: LocalDate.now()) { filter=it.toString() } }) { Icon(Icons.Outlined.CalendarToday,null); Spacer(Modifier.width(8.dp)); Text(filter ?: tr("Choose day")) }
            if(filter!=null) TextButton({ filter=null }) { Text(tr("Show all")) }
        } }
        if(grouped.isEmpty()) item { Text(tr(if(filter==null) "No doses logged yet." else "No doses logged for this day."),color=Muted,modifier=Modifier.padding(vertical=40.dp)) }
        grouped.forEach { (date,dayEntries) ->
            item(key="day-$date") { Text(when(date) { LocalDate.now() -> tr("Today"); LocalDate.now().minusDays(1) -> tr("Yesterday"); else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy")) },fontSize=20.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp)) }
            items(dayEntries,key={ it.id }) { entry ->
                Column(Modifier.fillMaxWidth().clickable { onEdit(entry.id) }.padding(vertical=16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(entry.medicationName)
                            Note("${doseText(entry.doseMg)} ${tr(entry.unit)} · ${Instant.ofEpochMilli(entry.timestamp).atZone(zone).format(timeFormat)}")
                        }
                        entry.mood?.let { Text(moodFaces[it],fontSize=22.sp,modifier=Modifier.semantics { contentDescription=tr(moodLabels[it]) }) }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,tint=Muted)
                    }
                }
                HorizontalDivider()
            }
        }
        item { Note(tr("Times displayed in %s.",zone.id)) }
    }
}

@Composable private fun ExportScreen(document: BackupDocument,snackbar: SnackbarHostState) {
    var start by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(6).toString()) }
    var end by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val entries=document.entries
    var includeNotes by rememberSaveable { mutableStateOf(true) }
    var summary by rememberSaveable { mutableStateOf(false) }
    var questions by rememberSaveable { mutableStateOf("") }
    var formatName by rememberSaveable { mutableStateOf(ReportFormat.PDF.name) }
    var exporting by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val from=LocalDate.parse(start); val to=LocalDate.parse(end)
    val invalid=to.isBefore(from) || to.toEpochDay()-from.toEpochDay()>365
    val count=if(invalid) 0 else Reports.inRange(entries,from,to,ZoneId.systemDefault()).size
    ScrollPage {
        Title("Share with\nyour doctor")
        Text(tr("A clear record of your medication and how you felt."),color=Muted)
        OutlinedButton({ pickDate(context,from) { start=it.toString() } },Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(tr("From · %s",start)) }
        OutlinedButton({ pickDate(context,to) { end=it.toString() } },Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(tr("To · %s",end)) }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            TextButton({ end=LocalDate.now().toString(); start=LocalDate.now().minusDays(6).toString() }) { Text(tr("Last 7 days")) }
            TextButton({ end=LocalDate.now().toString(); start=LocalDate.now().minusDays(29).toString() }) { Text(tr("Last 30 days")) }
        }
        ReportFormat.entries.forEach { format ->
            Surface(color=if(formatName==format.name) Pale else MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth().clickable { formatName=format.name }) {
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(formatName==format.name,{ formatName=format.name })
                    Column { Text(tr(if(format==ReportFormat.PDF) "PDF report" else "CSV")); Note(if(format==ReportFormat.PDF) "Graphs, doses, mood & notes" else "Open in a spreadsheet") }
                }
            }
        }
        when { invalid -> Note("Choose a valid range of up to 366 days."); count==0 -> Note("No doses logged. This does not confirm that no medication was taken."); else -> Note(tr("Entries: %d · %s",count,ZoneId.systemDefault().id)) }
        Note("Only your selected dates are included. You choose where to share the file.")
        Spacer(Modifier.weight(1f))
        Row { Checkbox(summary,{ summary=it });Text(tr("Summary with details")) }
        Row { Checkbox(includeNotes,{ includeNotes=it });Text(tr("Include free-text notes")) }
        if(summary) {
            OutlinedTextField(questions,{ questions=it.take(5000) },label={ Text(tr("Questions for my appointment")) },minLines=2)
            if(!invalid) {
                val boundaries=generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to.plusDays(1)) }.map { it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.toList()
                SummaryPreview(SummaryBuilder.build(document,boundaries))
            }
        }
        Primary(if(exporting) "Preparing report…" else "Export report",!invalid && !exporting) {
            exporting=true
            scope.launch {
                try {
                    val format=ReportFormat.valueOf(formatName)
                    val file=withContext(Dispatchers.IO) { Reports.createDocument(context,document,from,to,format,summary,includeNotes,questions) }
                    Reports.share(context,file,format)
                } catch(e: Exception) { snackbar.showSnackbar(tr("Could not export this report. Please try again.")) }
                finally { exporting=false }
            }
        }
    }
}

@Composable private fun SettingsScreen(state: LogbookState,busy: Boolean,vm: LogbookViewModel,onMedication: (Long) -> Unit,onExport: () -> Unit,onShould: (String)->Unit) {
    val context=LocalContext.current
    val timeFormat=timeFormat(context)
    var configureReminder by remember { mutableStateOf<Reminder?>(null) }
    var remove by remember { mutableStateOf<Medication?>(null) }
    var denied by remember { mutableStateOf(!NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _,event -> if(event == Lifecycle.Event.ON_RESUME) denied=!NotificationManagerCompat.from(context).areNotificationsEnabled() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> denied=!granted; vm.reminders(true) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=24.dp),contentPadding=PaddingValues(vertical=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Title("Settings") }
        item { Text(tr("Medications"),fontWeight=FontWeight.SemiBold,fontSize=20.sp) }
        items(state.medications.filter { it.active },key={ "med-${it.id}" }) { med ->
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onMedication(med.id) }.padding(vertical=12.dp)) { Text(med.name); Note(tr("Usual dose · %s %s",doseText(med.usualDose),tr(med.unit))) }
                IconButton({ onMedication(med.id) },enabled=!busy) { Icon(Icons.Outlined.Edit,tr("Edit %s",med.name)) }
                IconButton({ remove=med },enabled=!busy) { Icon(Icons.Outlined.Close,tr("Remove %s",med.name)) }
            }
        }
        item { TextButton({ onMedication(-1) },enabled=!busy) { Text(tr("＋ Add medication")) }; HorizontalDivider() }
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(tr("Reminders"),fontSize=20.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                Switch(state.remindersEnabled,{ enabled ->
                    if(enabled && Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { denied=!NotificationManagerCompat.from(context).areNotificationsEnabled(); vm.reminders(enabled) }
                },enabled=!busy)
            }
            Note("A gentle reminder to log. Delivery may be delayed by battery settings.")
        }
        if(state.remindersEnabled) {
            items(state.reminders,key={ "reminder-${it.id}" }) { reminder ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    OutlinedButton({ TimePickerDialog(context,{ _,h,m -> vm.reminder(h,m,reminder.id) },reminder.hour,reminder.minute,android.text.format.DateFormat.is24HourFormat(context)).show() },Modifier.weight(1f),enabled=!busy) { Text(LocalTime.of(reminder.hour,reminder.minute).format(timeFormat)) }
                    IconButton({ configureReminder=reminder },enabled=!busy) { Icon(Icons.Outlined.Settings,tr("Reminder options")) }
                    IconButton({ vm.removeReminder(reminder.id) },enabled=!busy) { Icon(Icons.Outlined.Close,tr("Remove reminder")) }
                }
            }
            item { TextButton({ TimePickerDialog(context,{ _,h,m -> vm.reminder(h,m) },12,0,android.text.format.DateFormat.is24HourFormat(context)).show() },enabled=!busy) { Text(tr("＋ Add time")) } }
            if(denied) item {
                Note("Notifications are turned off. Your reminder times are saved.")
                TextButton({ context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)) }) { Text(tr("Open notification settings")) }
            }
        }
        item { HorizontalDivider(); Spacer(Modifier.height(8.dp)); Text(tr("Data & privacy"),fontSize=20.sp,fontWeight=FontWeight.SemiBold) }
        item { Text(tr("Your log stays on this device. No account needed."),color=Muted); Spacer(Modifier.height(8.dp)); Note("Cloud backups are disabled. Uninstalling removes your log. Create a backup to restore it later.") }
        item { PrivacyControl() }
        item { TextButton({ onShould("pause") }) { Text(tr(if(state.pause.active(System.currentTimeMillis())) "Resume / change pause" else "Pause reminders")) } }
        item { TextButton({ onShould("widget") }) { Text(tr("Home-screen widget")) } }
        item { TextButton({ onShould("supply") }) { Text(tr("Supply")) } }
        item { Row { Text(tr("Optional observations"),Modifier.weight(1f));Switch(vm.observationsEnabled(),{ vm.preference("observations_enabled",it.toString()) }) } }
        item { Row { Text(tr("Measurements"),Modifier.weight(1f));Switch(vm.enabled("measurements_enabled"),{ vm.preference("measurements_enabled",it.toString()) },Modifier.semantics { contentDescription=tr("Measurements") }) }; Text(tr("Available in observations. Disabling keeps saved records.")) }
        item { Row { Text(tr("Weekly overview"),Modifier.weight(1f));Switch(vm.enabled("weekly_enabled"),{ vm.preference("weekly_enabled",it.toString()) },Modifier.semantics { contentDescription=tr("Weekly overview") }) } }
        item { TextButton({ onShould("quick") }) { Text(tr("Quick access")) } }
        item { BackupControls(vm,busy) }
        item { TextButton(onExport) { Text(tr("Export your log →")) } }
        item { Note("ADHS Logbook · 0.4.0\nEstimates are informational and are not medical measurements.") }
    }
    configureReminder?.let { ReminderOptions(it,state.medications,{ configureReminder=null }) { value -> vm.reminder(value);configureReminder=null } }
    remove?.let { med -> AlertDialog(onDismissRequest={ remove=null },title={ Text(tr("Remove medication?")) },text={ Text(tr("%s will no longer appear for new doses. Past entries remain in your history.",med.name)) },confirmButton={ TextButton({ vm.removeMedication(med.id); remove=null }) { Text(tr("Remove")) } },dismissButton={ TextButton({ remove=null }) { Text(tr("Keep")) } }) }
}

private fun stateSupplyEnabled(supplies: List<Supply>,id: Long)=supplies.any { it.medicationId==id }

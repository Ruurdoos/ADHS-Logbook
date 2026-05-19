package com.example.adhslogbook.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.adhslogbook.data.FocusLogRepository
import com.example.adhslogbook.data.database.dao.CheckInLogDao
import com.example.adhslogbook.data.database.dao.MedicationDoseDao
import com.example.adhslogbook.data.database.dao.SideEffectLogDao
import com.example.adhslogbook.data.InsightEngine
import com.example.adhslogbook.data.model.ActivityEntry
import com.example.adhslogbook.data.model.CheckInLog
import com.example.adhslogbook.data.model.CheckInMetric
import com.example.adhslogbook.data.model.CurvePoint
import com.example.adhslogbook.data.model.InsightCard
import com.example.adhslogbook.data.model.InsightCardStyle
import com.example.adhslogbook.data.model.MedicationDose
import com.example.adhslogbook.data.model.InsightsContent
import com.example.adhslogbook.data.model.InsightsPeriod
import com.example.adhslogbook.data.model.MedicationStatus
import com.example.adhslogbook.data.model.MetricType
import com.example.adhslogbook.data.model.SideEffectLog
import com.example.adhslogbook.data.model.TagOption
import com.example.adhslogbook.data.model.TodayContent
import com.example.adhslogbook.data.model.TrendBar
import com.example.adhslogbook.di.DatabaseModule
import com.example.adhslogbook.ui.state.ScreenContentState
import com.example.adhslogbook.ui.state.contentStateOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class LogbookViewModel(application: Application) : AndroidViewModel(application) {

    private val doseDao: MedicationDoseDao = DatabaseModule.provideMedicationDoseDao(application)
    private val checkInDao: CheckInLogDao = DatabaseModule.provideCheckInLogDao(application)
    private val sideEffectDao: SideEffectLogDao = DatabaseModule.provideSideEffectLogDao(application)

    // --- Today Screen State & Logic ---
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _localChanges = MutableStateFlow<LocalChanges?>(null)

    private val _plannedMedicationName = MutableStateFlow("Elvanse")
    private val _plannedDoseMg = MutableStateFlow(30)
    
    data class LocalChanges(
        val metrics: List<CheckInMetric>,
        val notes: String
    )

    private val prefs = application.getSharedPreferences("adhs_logbook_prefs", Context.MODE_PRIVATE)

    private val _availableTags = MutableStateFlow(loadTags())
    val availableTags: StateFlow<List<String>> = _availableTags.asStateFlow()

    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags: StateFlow<Set<String>> = _selectedTags.asStateFlow()

    private fun loadTags(): List<String> {
        val savedTags = prefs.getStringSet("available_tags", null)
        return if (savedTags != null) {
            savedTags.toList().sorted()
        } else {
            val defaults = listOf("Peak", "Good focus", "Calm", "Restless", "Rebound", "No effect", "Can't concentrate")
            saveTags(defaults)
            defaults
        }
    }

    private fun saveTags(tags: List<String>) {
        prefs.edit().putStringSet("available_tags", tags.toSet()).apply()
    }

    private val _todaySnackbar = MutableStateFlow<String?>(null)
    val todaySnackbar: StateFlow<String?> = _todaySnackbar.asStateFlow()

    // 12 hour window for the curve
    private val windowMinutes = 720f
    private val expectedCurvePoints = listOf(
        CurvePoint(0.000f, 0.05f),
        CurvePoint(0.050f, 0.20f),
        CurvePoint(0.125f, 1.00f),
        CurvePoint(0.250f, 0.95f),
        CurvePoint(0.375f, 0.85f),
        CurvePoint(0.500f, 0.70f),
        CurvePoint(0.625f, 0.50f),
        CurvePoint(0.750f, 0.35f),
        CurvePoint(0.875f, 0.20f),
        CurvePoint(1.000f, 0.10f),
    )

    val todayContent: StateFlow<ScreenContentState<TodayContent>> = _selectedDate
        .flatMapLatest { date ->
            val start = getStartOfDay(date)
            val end = getEndOfDay(date)
            combine(
                doseDao.getByDate(start, end).map { it.lastOrNull() },
                checkInDao.getByDate(start, end),
                _availableTags,
                _selectedTags,
                _localChanges,
                _plannedMedicationName,
                _plannedDoseMg
            ) { args: Array<Any?> ->
                val dose = args[0] as MedicationDose?
                val checkIns = args[1] as List<CheckInLog>
                val available = args[2] as List<String>
                val selected = args[3] as Set<String>
                val local = args[4] as LocalChanges?
                val plannedName = args[5] as String
                val plannedDose = args[6] as Int

                val baseContent = FocusLogRepository.loadToday()
                
                val actualCurve = if (dose != null) {
                    checkIns.map { log ->
                        val x = (log.timestamp - dose.takenAt).toFloat() / (windowMinutes * 60000f)
                        val y = log.focusLevel / 100f
                        CurvePoint(x.coerceIn(0f, 1f), y)
                    }.sortedBy { it.x }
                } else emptyList()

                val medication = dose?.let {
                    baseContent.medication.copy(
                        name = plannedName,
                        dosage = "${plannedDose}mg • ${it.releaseType}",
                        status = MedicationStatus.Taken
                    )
                } ?: baseContent.medication.copy(
                    name = plannedName,
                    dosage = "${plannedDose}mg",
                    status = MedicationStatus.Due
                )

                val tagOptions = available.map { label ->
                    TagOption(label, selected.contains(label))
                }

                val content = baseContent.copy(
                    medication = medication,
                    lastTaken = dose?.let { formatTime(it.takenAt) } ?: "Not taken",
                    doseTimestamp = dose?.takenAt,
                    expectedCurve = expectedCurvePoints,
                    actualCurve = actualCurve,
                    metrics = local?.metrics ?: baseContent.metrics,
                    tags = tagOptions,
                    notes = local?.notes ?: baseContent.notes
                )
                contentStateOf(content) { false }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreenContentState.Loading)

    val hasUnsavedChanges: StateFlow<Boolean> = combine(_localChanges, _selectedTags) { local, selected ->
        local != null || selected.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- Insights Screen State & Logic ---
    private val _selectedPeriod = MutableStateFlow(InsightsPeriod.Weekly)
    val selectedPeriod: StateFlow<InsightsPeriod> = _selectedPeriod.asStateFlow()

    val insightsContent: StateFlow<ScreenContentState<InsightsContent>> = _selectedPeriod
        .flatMapLatest { period ->
            // Use FocusLogRepository.loadInsights as base for mock data,
            // but we could also derive this from real data here.
            val base = FocusLogRepository.loadInsights(period)
            combine(insightObservations, dailyFocusAverages) { observations, averages ->
                val bars = if (averages.isNotEmpty() && period == InsightsPeriod.Weekly) {
                    DayOfWeek.entries.map { day ->
                        val avg = averages[day] ?: 0f
                        TrendBar(
                            label = day.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                            value = avg,
                            highlighted = avg > 0 && avg == averages.values.maxOrNull(),
                            marker = if (avg > 0 && avg == averages.values.maxOrNull()) "Peak" else null
                        )
                    }
                } else base.bars

                val content = base.copy(
                    cards = if (observations.isNotEmpty() && period == InsightsPeriod.Weekly) observations else base.cards,
                    bars = bars
                )
                contentStateOf(content) { it.bars.isEmpty() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreenContentState.Loading)

    fun selectPeriod(period: InsightsPeriod) {
        _selectedPeriod.value = period
    }

    private val startOfDay: Long
        get() = getStartOfDay(LocalDate.now())

    private val endOfDay: Long
        get() = getEndOfDay(LocalDate.now())

    private val sevenDaysAgo: Long
        get() = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    val todayDose: StateFlow<MedicationDose?> = doseDao.getLatest()
        .map { dose ->
            if (dose != null && dose.takenAt >= startOfDay) dose else null
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayCheckIns: StateFlow<List<CheckInLog>> = checkInDao.getByDate(startOfDay, endOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todaySideEffects: StateFlow<List<SideEffectLog>> = sideEffectDao.getByDate(startOfDay, endOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timelineEntries: StateFlow<List<ActivityEntry>> = _selectedDate
        .flatMapLatest { date ->
            val start = getStartOfDay(date)
            val end = getEndOfDay(date)
            combine(
                doseDao.getByDate(start, end),
                checkInDao.getByDate(start, end),
                sideEffectDao.getByDate(start, end)
            ) { doses, checkIns, sideEffects ->
                buildTimelineEntries(doses, checkIns, sideEffects)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklyCheckIns: StateFlow<List<CheckInLog>> = checkInDao.getAllSince(sevenDaysAgo)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklyDoses: StateFlow<List<MedicationDose>> = doseDao.getAllSince(sevenDaysAgo)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyFocusAverages: StateFlow<Map<DayOfWeek, Float>> = weeklyCheckIns.map { logs ->
        logs.groupBy { log ->
            Instant.ofEpochMilli(log.timestamp)
                .atZone(ZoneId.systemDefault())
                .dayOfWeek
        }.mapValues { (_, dayLogs) ->
            dayLogs.map { it.focusLevel }.average().toFloat() / 100f
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val insightObservations: StateFlow<List<InsightCard>> = combine(
        weeklyDoses,
        weeklyCheckIns
    ) { doses, logs ->
        InsightEngine.generateInsights(doses, logs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getExportText(): String {
        val doses = weeklyDoses.value
        val checkIns = weeklyCheckIns.value
        val sb = StringBuilder()
        sb.append("ADHS Logbook - Doctor's Report\n")
        sb.append("----------------------------\n\n")

        sb.append("MEDICATION DOSES:\n")
        if (doses.isEmpty()) sb.append("No doses recorded in the last 7 days.\n")
        doses.forEach { dose ->
            val dt = Instant.ofEpochMilli(dose.takenAt).atZone(ZoneId.systemDefault())
            sb.append("${dt.format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))}: ${dose.medicationName} ${dose.doseMg}mg\n")
        }

        sb.append("\nCHECK-IN LOGS:\n")
        if (checkIns.isEmpty()) sb.append("No check-ins recorded in the last 7 days.\n")
        checkIns.forEach { log ->
            val dt = Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault())
            sb.append("${dt.format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))} -> Focus: ${log.focusLevel}%, Mood: ${log.moodLevel}%, Energy: ${log.energyLevel}%\n")
            if (log.tags.isNotEmpty()) sb.append("Tags: ${log.tags}\n")
            if (log.notes.isNotEmpty()) sb.append("Notes: ${log.notes}\n")
            sb.append("---\n")
        }

        return sb.toString()
    }

    fun updatePlannedMedicationName(name: String) {
        _plannedMedicationName.value = name
    }

    fun updatePlannedDose(doseMg: Int) {
        _plannedDoseMg.value = doseMg
    }

    fun logDose(medicationName: String, doseMg: Int, takenAt: Long) {
        viewModelScope.launch {
            doseDao.insert(
                MedicationDose(
                    medicationName = medicationName,
                    doseMg = doseMg,
                    takenAt = takenAt
                )
            )
            _todaySnackbar.value = "Dose logged ✓"
        }
    }

    fun logCheckIn(focus: Int, mood: Int, energy: Int, notes: String) {
        viewModelScope.launch {
            val tagsString = _selectedTags.value.joinToString(",")
            val start = getStartOfDay(LocalDate.now())
            val end = getEndOfDay(LocalDate.now())
            val latestDose = doseDao.getByDate(start, end).first().lastOrNull()
            checkInDao.insert(
                CheckInLog(
                    timestamp = System.currentTimeMillis(),
                    focusLevel = focus,
                    moodLevel = mood,
                    energyLevel = energy,
                    tags = tagsString,
                    notes = notes,
                    linkedDoseId = latestDose?.id
                )
            )
            _selectedTags.value = emptySet()
            _localChanges.value = null
            _todaySnackbar.value = "Check-in logged ✓"
        }
    }

    fun logSideEffect(effectName: String) {
        viewModelScope.launch {
            val start = getStartOfDay(LocalDate.now())
            val end = getEndOfDay(LocalDate.now())
            val latestDose = doseDao.getByDate(start, end).map { it.lastOrNull() }.stateIn(viewModelScope).value
            sideEffectDao.insert(
                SideEffectLog(
                    timestamp = System.currentTimeMillis(),
                    effectName = effectName,
                    linkedDoseId = latestDose?.id
                )
            )
            _todaySnackbar.value = "Side effect logged ✓"
        }
    }

    fun updateMetric(type: MetricType, value: Float) {
        updateLocal { current ->
            val updatedMetrics = current.metrics.map { metric ->
                if (metric.type == type) {
                    metric.copy(value = value, descriptor = descriptorFor(type, value))
                } else {
                    metric
                }
            }
            current.copy(metrics = updatedMetrics)
        }
    }

    fun toggleTag(label: String) {
        _selectedTags.update { current ->
            if (current.contains(label)) current - label else current + label
        }
    }

    fun addTag(label: String) {
        if (label.isBlank()) return
        _availableTags.update { current ->
            if (!current.contains(label)) {
                val newList = (current + label).sorted()
                saveTags(newList)
                newList
            } else current
        }
        _selectedTags.update { it + label }
    }

    fun updateNotes(text: String) {
        updateLocal { it.copy(notes = text) }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _localChanges.value = null
    }

    fun addQuickNote(note: String) {
        viewModelScope.launch {
            checkInDao.insert(
                CheckInLog(
                    timestamp = System.currentTimeMillis(),
                    focusLevel = 50,
                    moodLevel = 50,
                    energyLevel = 50,
                    tags = "",
                    notes = note,
                    linkedDoseId = null
                )
            )
            _todaySnackbar.value = "Note added ✓"
        }
    }

    fun consumeSnackbar() {
        _todaySnackbar.value = null
    }

    private fun updateLocal(transform: (LocalChanges) -> LocalChanges) {
        val currentContent = (todayContent.value as? ScreenContentState.Data)?.value
        val base = _localChanges.value ?: currentContent?.let {
            LocalChanges(it.metrics, it.notes)
        } ?: return
        _localChanges.value = transform(base)
    }

    private fun getStartOfDay(date: LocalDate): Long = 
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun getEndOfDay(date: LocalDate): Long = 
        date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

    private fun formatTime(timestamp: Long): String {
        val date = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
        return date.format(formatter)
    }

    private fun descriptorFor(type: MetricType, value: Float): String = when (type) {
        MetricType.Focus -> when {
            value < 0.34f -> "Low"
            value < 0.67f -> "Steady"
            value < 0.85f -> "Good"
            else -> "Locked in"
        }
        MetricType.Mood -> when {
            value < 0.34f -> "Low"
            value < 0.67f -> "Neutral"
            value < 0.85f -> "Good"
            else -> "Great"
        }
        MetricType.Energy -> when {
            value < 0.34f -> "Low"
            value < 0.67f -> "Moderate"
            value < 0.85f -> "High"
            else -> "Very high"
        }
    }

    private fun buildTimelineEntries(
        doses: List<MedicationDose>,
        checkIns: List<CheckInLog>,
        sideEffects: List<SideEffectLog>
    ): List<ActivityEntry> {
        val entries = mutableListOf<ActivityEntry>()
        
        entries.addAll(doses.map { ActivityEntry.DoseTaken(it) })
        entries.addAll(checkIns.map { log ->
            val label = when {
                log.focusLevel > 80 -> "Peak Effect"
                log.notes.isNotEmpty() -> "Quick note"
                else -> "Check-in"
            }
            ActivityEntry.CheckInEntry(log, label)
        })
        entries.addAll(sideEffects.map { ActivityEntry.SideEffectEntry(it) })

        return entries.sortedByDescending { it.timestamp }
    }
}

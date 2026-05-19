package com.example.adhslogbook.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.adhslogbook.data.FocusLogRepository
import com.example.adhslogbook.data.database.dao.CheckInLogDao
import com.example.adhslogbook.data.database.dao.MedicationDoseDao
import com.example.adhslogbook.data.database.dao.SideEffectLogDao
import com.example.adhslogbook.data.model.ActivityEntry
import com.example.adhslogbook.data.model.CheckInLog
import com.example.adhslogbook.data.model.CheckInMetric
import com.example.adhslogbook.data.model.CurvePoint
import com.example.adhslogbook.data.model.MedicationDose
import com.example.adhslogbook.data.model.MedicationStatus
import com.example.adhslogbook.data.model.MetricType
import com.example.adhslogbook.data.model.SideEffectLog
import com.example.adhslogbook.data.model.TagOption
import com.example.adhslogbook.data.model.TodayContent
import com.example.adhslogbook.ui.state.ScreenContentState
import com.example.adhslogbook.ui.state.contentStateOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class TodayUiState(
    val contentState: ScreenContentState<TodayContent> = ScreenContentState.Loading,
    val snackbarMessage: String? = null,
    val hasUnsavedChanges: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val checkInLogDao: CheckInLogDao,
    private val medicationDoseDao: MedicationDoseDao,
    private val sideEffectLogDao: SideEffectLogDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _localChanges = MutableStateFlow<LocalChanges?>(null)

    private val _plannedMedicationName = MutableStateFlow("Elvanse")
    private val _plannedDoseMg = MutableStateFlow(30)

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

    data class LocalChanges(
        val metrics: List<CheckInMetric>,
        val tags: List<TagOption>,
        val notes: String
    )

    val timelineEntries: StateFlow<List<ActivityEntry>> = _selectedDate
        .flatMapLatest { date ->
            val start = getStartOfDay(date)
            val end = getEndOfDay(date)
            combine(
                medicationDoseDao.getByDate(start, end),
                checkInLogDao.getByDate(start, end),
                sideEffectLogDao.getByDate(start, end)
            ) { doses, checkIns, sideEffects ->
                buildTimelineEntries(doses, checkIns, sideEffects)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todaySideEffects: StateFlow<List<ActivityEntry.SideEffectEntry>> = timelineEntries
        .map { entries ->
            entries.filterIsInstance<ActivityEntry.SideEffectEntry>()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        observeContent()
    }

    private fun observeContent() {
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                val startOfDay = getStartOfDay(date)
                val endOfDay = getEndOfDay(date)
                combine(
                    medicationDoseDao.getByDate(startOfDay, endOfDay).map { it.lastOrNull() },
                    checkInLogDao.getByDate(startOfDay, endOfDay),
                    _localChanges,
                    _plannedMedicationName,
                    _plannedDoseMg
                ) { dose, checkIns, local, plannedName, plannedDose ->
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

                    val todayContent = baseContent.copy(
                        medication = medication,
                        lastTaken = dose?.let { formatTime(it.takenAt) } ?: "Not taken",
                        doseTimestamp = dose?.takenAt,
                        expectedCurve = expectedCurvePoints,
                        actualCurve = actualCurve,
                        metrics = local?.metrics ?: baseContent.metrics,
                        tags = local?.tags ?: baseContent.tags,
                        notes = local?.notes ?: baseContent.notes
                    )

                    _uiState.update { 
                        it.copy(
                            contentState = contentStateOf(todayContent) { false },
                            hasUnsavedChanges = local != null
                        )
                    }
                }
            }.collect { }
        }
    }

    fun updatePlannedMedicationName(name: String) {
        _plannedMedicationName.value = name
    }

    fun updatePlannedDose(doseMg: Int) {
        _plannedDoseMg.value = doseMg
    }

    fun retry() = observeContent()

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
        updateLocal { current ->
            current.copy(
                tags = current.tags.map { tag ->
                    if (tag.label == label) tag.copy(selected = !tag.selected) else tag
                }
            )
        }
    }

    fun addQuickTag() {
        updateLocal { current ->
            if (current.tags.any { it.label == "Hydrated" }) return@updateLocal current
            current.copy(tags = current.tags + TagOption("Hydrated", selected = true))
        }
    }

    fun updateNotes(text: String) {
        updateLocal { it.copy(notes = text) }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _localChanges.value = null // Reset local changes when date changes
    }

    fun logSideEffect(effectName: String) {
        viewModelScope.launch {
            sideEffectLogDao.insert(
                SideEffectLog(
                    timestamp = System.currentTimeMillis(),
                    effectName = effectName,
                    linkedDoseId = null
                )
            )
            _uiState.update { it.copy(snackbarMessage = "Side effect logged ✓") }
        }
    }

    fun addQuickNote(note: String) {
        viewModelScope.launch {
            checkInLogDao.insert(
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
            _uiState.update { it.copy(snackbarMessage = "Note added ✓") }
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

    private fun updateLocal(transform: (LocalChanges) -> LocalChanges) {
        val currentData = (_uiState.value.contentState as? ScreenContentState.Data)?.value
        val base = _localChanges.value ?: currentData?.let {
            LocalChanges(it.metrics, it.tags, it.notes)
        } ?: return
        _localChanges.value = transform(base)
    }

    fun logMetrics() {
        val local = _localChanges.value ?: return
        val focus = (local.metrics.find { it.type == MetricType.Focus }?.value ?: 0.5f) * 100
        val mood = (local.metrics.find { it.type == MetricType.Mood }?.value ?: 0.5f) * 100
        val energy = (local.metrics.find { it.type == MetricType.Energy }?.value ?: 0.5f) * 100
        val selectedTags = local.tags.filter { it.selected }.map { it.label }
        
        viewModelScope.launch {
            val log = CheckInLog(
                timestamp = System.currentTimeMillis(),
                focusLevel = focus.toInt(),
                moodLevel = mood.toInt(),
                energyLevel = energy.toInt(),
                tags = selectedTags.joinToString(","),
                notes = local.notes,
                linkedDoseId = null
            )
            checkInLogDao.insert(log)
            
            _localChanges.value = null
            _uiState.update { it.copy(snackbarMessage = "Check-in logged ✓") }
        }
    }

    fun logNextDose() {
        viewModelScope.launch {
            val dose = MedicationDose(
                medicationName = _plannedMedicationName.value,
                doseMg = _plannedDoseMg.value,
                takenAt = System.currentTimeMillis()
            )
            medicationDoseDao.insert(dose)
            _uiState.update { it.copy(snackbarMessage = "Dose logged ✓") }
        }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
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

    companion object {
        fun provideFactory(
            checkInLogDao: CheckInLogDao,
            medicationDoseDao: MedicationDoseDao,
            sideEffectLogDao: SideEffectLogDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TodayViewModel(checkInLogDao, medicationDoseDao, sideEffectLogDao) as T
            }
        }
    }
}

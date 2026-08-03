package com.example.adhslogbook.ui.viewmodels

import android.app.Application
import android.content.Context
import android.text.format.DateFormat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhslogbook.data.InsightEngine
import com.example.adhslogbook.R
import com.example.adhslogbook.data.database.dao.CheckInLogDao
import com.example.adhslogbook.data.database.dao.MedicationDoseDao
import com.example.adhslogbook.data.database.dao.SideEffectLogDao
import com.example.adhslogbook.data.model.ActivityEntry
import com.example.adhslogbook.data.model.CheckInLog
import com.example.adhslogbook.data.model.CheckInMetric
import com.example.adhslogbook.data.model.InsightsContent
import com.example.adhslogbook.data.model.MedicationDose
import com.example.adhslogbook.data.model.MedicationStatus
import com.example.adhslogbook.data.model.MedicationSummary
import com.example.adhslogbook.data.model.MetricType
import com.example.adhslogbook.data.model.SideEffectLog
import com.example.adhslogbook.data.model.TagOption
import com.example.adhslogbook.data.model.TodayContent
import com.example.adhslogbook.data.model.TrendBar
import com.example.adhslogbook.di.DatabaseModule
import com.example.adhslogbook.ui.state.ScreenContentState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class LogbookViewModel(application: Application) : AndroidViewModel(application) {
    private val doseDao: MedicationDoseDao = DatabaseModule.provideMedicationDoseDao(application)
    private val checkInDao: CheckInLogDao = DatabaseModule.provideCheckInLogDao(application)
    private val sideEffectDao: SideEffectLogDao = DatabaseModule.provideSideEffectLogDao(application)
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()
    val currentDate = flow {
        while (true) {
            emit(LocalDate.now())
            delay(DATE_REFRESH_MILLIS)
        }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())
    private val _medicationName = MutableStateFlow(prefs.getString(KEY_MEDICATION, "").orEmpty())
    private val _doseMg = MutableStateFlow(prefs.getInt(KEY_DOSE, 0))
    val medicationName = _medicationName.asStateFlow()
    val doseMg = _doseMg.asStateFlow()

    private val _availableTags = MutableStateFlow(loadTags())
    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val _localChanges = MutableStateFlow<LocalChanges?>(null)
    private val _snackbar = MutableStateFlow<String?>(null)
    private val _doseSaving = MutableStateFlow(false)
    private val _checkInSaving = MutableStateFlow(false)
    val snackbar = _snackbar.asStateFlow()
    val doseSaving = _doseSaving.asStateFlow()
    val checkInSaving = _checkInSaving.asStateFlow()

    data class LocalChanges(val metrics: List<CheckInMetric>, val notes: String)
    private data class CurrentMedication(val name: String, val doseMg: Int)
    private data class Edits(
        val availableTags: List<String>,
        val selectedTags: Set<String>,
        val local: LocalChanges?,
    )

    private val currentMedication = combine(_medicationName, _doseMg) { name, dose ->
        CurrentMedication(name, dose)
    }
    private val edits = combine(_availableTags, _selectedTags, _localChanges) { available, selected, local ->
        Edits(available, selected, local)
    }

    val todayContent: StateFlow<ScreenContentState<TodayContent>> = _selectedDate
        .flatMapLatest { date ->
            combine(
                doseDao.getByDate(startOf(date), endExclusive(date) - 1),
                currentMedication,
                edits,
            ) { doses, current, edit ->
                val latest = doses.lastOrNull()
                val medication = latest?.let {
                    MedicationSummary(
                        name = it.medicationName,
                        dosage = "${it.doseMg} mg • ${it.releaseType}",
                        status = MedicationStatus.Taken,
                    )
                } ?: MedicationSummary(
                    name = current.name.ifBlank {
                        getApplication<Application>().getString(R.string.medication_not_configured)
                    },
                    dosage = if (current.doseMg in 1..200) "${current.doseMg} mg"
                        else getApplication<Application>().getString(R.string.set_medication_dose),
                    status = MedicationStatus.Due,
                )
                val local = edit.local ?: defaultChanges()
                ScreenContentState.Data(
                    TodayContent(
                        medication = medication,
                        lastTaken = latest?.let { formatTime(it.takenAt) } ?: "Not taken",
                        metrics = local.metrics,
                        tags = edit.availableTags.map { TagOption(it, it in edit.selectedTags) },
                        notes = local.notes,
                    )
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenContentState.Loading)

    val timelineEntries: StateFlow<List<ActivityEntry>> = _selectedDate.flatMapLatest { date ->
        combine(
            doseDao.getByDate(startOf(date), endExclusive(date) - 1),
            checkInDao.getByDate(startOf(date), endExclusive(date) - 1),
            sideEffectDao.getByDate(startOf(date), endExclusive(date) - 1),
        ) { doses, checkIns, effects ->
            (doses.map { ActivityEntry.DoseTaken(it) } +
                checkIns.map { ActivityEntry.CheckInEntry(it, "Check-in") } +
                effects.map { ActivityEntry.SideEffectEntry(it) })
                .sortedBy { it.timestamp }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasUnsavedChanges = combine(_localChanges, _selectedTags) { local, tags ->
        local != null || tags.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val insightsContent: StateFlow<ScreenContentState<InsightsContent>> = currentDate
        .flatMapLatest { today ->
            val start = startOf(today.minusDays(6))
            val end = endExclusive(today)
            combine(
                doseDao.observeRange(start, end),
                checkInDao.observeRange(start, end),
            ) { doses, checkIns ->
                val averages = checkIns.groupBy { localDate(it.timestamp) }
                    .mapValues { (_, logs) -> logs.map { it.focusLevel }.average().toFloat() / 100f }
                    .toSortedMap()
                val maximum = averages.values.maxOrNull()
                ScreenContentState.Data(
                    InsightsContent(
                        cards = InsightEngine.generateInsights(doses, checkIns, zone),
                        bars = averages.map { (date, average) ->
                            TrendBar(
                                label = date.format(DateTimeFormatter.ofPattern("EEE")),
                                value = average,
                                highlighted = average == maximum,
                            )
                        },
                    )
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenContentState.Loading)

    fun saveMedication(name: String, dose: Int) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || dose !in 1..200) return
        prefs.edit().putString(KEY_MEDICATION, trimmed).putInt(KEY_DOSE, dose).apply()
        _medicationName.value = trimmed
        _doseMg.value = dose
        _snackbar.value = getApplication<Application>().getString(R.string.medication_saved)
    }

    fun selectDate(date: LocalDate) {
        if (date.isAfter(currentDate.value)) return
        _selectedDate.value = date
        clearEdits()
    }

    fun logDose() {
        val date = _selectedDate.value
        val name = _medicationName.value.trim()
        val dose = _doseMg.value
        if (date.isAfter(currentDate.value) || name.isBlank() || dose !in 1..200) return
        if (!_doseSaving.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                doseDao.insert(
                    MedicationDose(
                        medicationName = name,
                        doseMg = dose,
                        takenAt = timestampFor(date),
                        releaseType = getApplication<Application>()
                            .getString(R.string.release_type_unspecified),
                    )
                )
                _snackbar.value = getApplication<Application>().getString(R.string.dose_logged)
            } finally {
                _doseSaving.value = false
            }
        }
    }

    fun logCheckIn() {
        val date = _selectedDate.value
        if (date.isAfter(currentDate.value)) return
        val changes = _localChanges.value ?: defaultChanges()
        val tags = _selectedTags.value
        if (!_checkInSaving.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val timestamp = timestampFor(date)
                val latestDose = doseDao.getByDate(startOf(date), endExclusive(date) - 1)
                    .first().lastOrNull { it.takenAt <= timestamp }
                fun metric(type: MetricType) =
                    ((changes.metrics.first { it.type == type }.value * 100).toInt()).coerceIn(0, 100)
                checkInDao.insert(
                    CheckInLog(
                        timestamp = timestamp,
                        focusLevel = metric(MetricType.Focus),
                        moodLevel = metric(MetricType.Mood),
                        energyLevel = metric(MetricType.Energy),
                        tags = tags.joinToString(","),
                        notes = changes.notes.trim(),
                        linkedDoseId = latestDose?.id,
                    )
                )
                clearEdits()
                _snackbar.value = getApplication<Application>().getString(R.string.checkin_logged)
            } finally {
                _checkInSaving.value = false
            }
        }
    }

    fun logSideEffect(effectName: String) {
        val date = _selectedDate.value
        val effect = effectName.trim()
        if (date.isAfter(currentDate.value) || effect.isBlank()) return
        viewModelScope.launch {
            val timestamp = timestampFor(date)
            val latestDose = doseDao.getByDate(startOf(date), endExclusive(date) - 1)
                .first().lastOrNull { it.takenAt <= timestamp }
            sideEffectDao.insert(
                SideEffectLog(
                    timestamp = timestamp,
                    effectName = effect,
                    linkedDoseId = latestDose?.id,
                )
            )
            _snackbar.value = getApplication<Application>().getString(R.string.side_effect_logged)
        }
    }

    fun updateMetric(type: MetricType, value: Float) = updateLocal { current ->
        current.copy(metrics = current.metrics.map {
            if (it.type == type) it.copy(value = value, descriptor = descriptor(value)) else it
        })
    }

    fun updateNotes(notes: String) = updateLocal { it.copy(notes = notes) }
    fun toggleTag(tag: String) = _selectedTags.update { if (tag in it) it - tag else it + tag }
    fun addTag(tag: String) {
        val value = tag.trim()
        if (value.isBlank()) return
        _availableTags.update { current ->
            (current + value).distinct().sorted().also {
                prefs.edit().putStringSet(KEY_TAGS, it.toSet()).apply()
            }
        }
        _selectedTags.update { it + value }
    }

    suspend fun createExportText(): String {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(6)
        val start = startOf(startDate)
        val end = endExclusive(endDate)
        val doses = doseDao.getRange(start, end)
        val checkIns = checkInDao.getRange(start, end)
        val effects = sideEffectDao.getRange(start, end)
        val context = getApplication<Application>()
        val dateFormat = DateFormat.getDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)
        fun whenText(timestamp: Long) =
            "${dateFormat.format(Date(timestamp))} ${timeFormat.format(Date(timestamp))}"
        return buildString {
            appendLine("ADHS Logbook — 7-day report")
            appendLine("${dateFormat.format(Date(start))} – ${dateFormat.format(Date(end - 1))}")
            appendLine("Time zone: ${zone.id}")
            appendLine()
            appendLine("MEDICATION")
            if (doses.isEmpty()) appendLine("No doses recorded.")
            doses.forEach {
                appendLine("${whenText(it.takenAt)}: ${it.medicationName}, ${it.doseMg} mg, ${it.releaseType}")
            }
            appendLine()
            appendLine("CHECK-INS")
            if (checkIns.isEmpty()) appendLine("No check-ins recorded.")
            checkIns.forEach {
                appendLine("${whenText(it.timestamp)}: focus ${it.focusLevel}, mood ${it.moodLevel}, energy ${it.energyLevel}")
                if (it.tags.isNotBlank()) appendLine("Tags: ${it.tags}")
                if (it.notes.isNotBlank()) appendLine("Notes: ${it.notes}")
            }
            appendLine()
            appendLine("SIDE EFFECTS")
            if (effects.isEmpty()) appendLine("No side effects recorded.")
            effects.forEach { appendLine("${whenText(it.timestamp)}: ${it.effectName}") }
        }
    }

    fun consumeSnackbar() { _snackbar.value = null }

    private fun updateLocal(transform: (LocalChanges) -> LocalChanges) {
        _localChanges.value = transform(_localChanges.value ?: defaultChanges())
    }

    private fun clearEdits() {
        _localChanges.value = null
        _selectedTags.value = emptySet()
    }

    private fun defaultChanges() = LocalChanges(
        metrics = MetricType.entries.map { CheckInMetric(it, 0.5f, descriptor(0.5f)) },
        notes = "",
    )

    private fun descriptor(value: Float) = when {
        value < .34f -> "Low"
        value < .67f -> "Medium"
        else -> "High"
    }

    private fun loadTags() =
        prefs.getStringSet(KEY_TAGS, null)?.toList()?.sorted()
            ?: listOf("Calm", "Good focus", "No effect", "Restless")

    private fun timestampFor(date: LocalDate) =
        date.atTime(LocalTime.now()).atZone(zone).toInstant().toEpochMilli()

    private fun startOf(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun endExclusive(date: LocalDate) = startOf(date.plusDays(1))
    private fun localDate(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    private fun formatTime(timestamp: Long) =
        DateFormat.getTimeFormat(getApplication()).format(Date(timestamp))

    private companion object {
        const val PREFS = "adhs_logbook_prefs"
        const val KEY_MEDICATION = "medication_name"
        const val KEY_DOSE = "medication_dose_mg"
        const val KEY_TAGS = "available_tags"
        const val DATE_REFRESH_MILLIS = 60_000L
    }
}

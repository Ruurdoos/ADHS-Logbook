package com.example.adhslogbook.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhslogbook.data.FocusLogRepository
import com.example.adhslogbook.data.model.TimelineDay
import com.example.adhslogbook.data.model.TimelineEvent
import com.example.adhslogbook.data.model.TimelineEventType
import com.example.adhslogbook.ui.state.ScreenContentState
import com.example.adhslogbook.ui.state.contentStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimelineUiState(
    val contentState: ScreenContentState<TimelineDay> = ScreenContentState.Loading,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val snackbarMessage: String? = null,
)

class TimelineViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private var days: List<TimelineDay> = emptyList()
    private var currentIndex: Int = 0

    init {
        load()
    }

    fun retry() = load()

    fun showPreviousDay() {
        if (currentIndex > 0) {
            currentIndex -= 1
            publishCurrentDay()
        }
    }

    fun showNextDay() {
        if (currentIndex < days.lastIndex) {
            currentIndex += 1
            publishCurrentDay()
        }
    }

    fun addEvent() {
        val current = days.getOrNull(currentIndex) ?: return
        val newEvent = TimelineEvent(
            id = "note-${current.events.size + 1}",
            time = "01:15 PM",
            title = "Quick note added",
            description = "Logged a short midday check-in from the floating action button.",
            type = TimelineEventType.Note,
        )
        days = days.toMutableList().also { mutableDays ->
            mutableDays[currentIndex] = current.copy(events = listOf(newEvent) + current.events)
        }
        publishCurrentDay(snackbarMessage = "Timeline event added.")
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = TimelineUiState(contentState = ScreenContentState.Loading)
            runCatching {
                delay(180)
                FocusLogRepository.loadTimelineDays()
            }.onSuccess { loadedDays ->
                days = loadedDays
                currentIndex = loadedDays.lastIndex.coerceAtLeast(0)
                publishCurrentDay()
            }.onFailure {
                _uiState.update {
                    it.copy(contentState = ScreenContentState.Error("Timeline data could not be loaded."))
                }
            }
        }
    }

    private fun publishCurrentDay(snackbarMessage: String? = null) {
        val day = days.getOrNull(currentIndex)
        if (day == null) {
            _uiState.update { it.copy(contentState = ScreenContentState.Empty) }
            return
        }
        _uiState.update {
            it.copy(
                contentState = contentStateOf(day) { timelineDay -> timelineDay.events.isEmpty() },
                hasPrevious = currentIndex > 0,
                hasNext = currentIndex < days.lastIndex,
                snackbarMessage = snackbarMessage,
            )
        }
    }
}

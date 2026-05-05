package com.example.adhslogbook.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhslogbook.data.FocusLogRepository
import com.example.adhslogbook.data.model.CheckInMetric
import com.example.adhslogbook.data.model.MedicationStatus
import com.example.adhslogbook.data.model.MetricType
import com.example.adhslogbook.data.model.TagOption
import com.example.adhslogbook.data.model.TodayContent
import com.example.adhslogbook.ui.state.ScreenContentState
import com.example.adhslogbook.ui.state.contentStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayUiState(
    val contentState: ScreenContentState<TodayContent> = ScreenContentState.Loading,
    val snackbarMessage: String? = null,
    val hasUnsavedChanges: Boolean = false,
)

class TodayViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun updateMetric(type: MetricType, value: Float) {
        val current = (_uiState.value.contentState as? ScreenContentState.Data)?.value ?: return
        val updated = current.metrics.map { metric ->
            if (metric.type == type) {
                CheckInMetric(type = type, value = value, descriptor = descriptorFor(type, value))
            } else {
                metric
            }
        }
        _uiState.update {
            it.copy(
                contentState = ScreenContentState.Data(current.copy(metrics = updated)),
                hasUnsavedChanges = true
            )
        }
    }

    fun logMetrics() {
        _uiState.update {
            it.copy(
                hasUnsavedChanges = false,
                snackbarMessage = "Check-in metrics logged successfully."
            )
        }
    }

    fun toggleTag(label: String) {
        val current = (_uiState.value.contentState as? ScreenContentState.Data)?.value ?: return
        _uiState.update {
            it.copy(
                contentState = ScreenContentState.Data(
                    current.copy(
                        tags = current.tags.map { tag ->
                            if (tag.label == label) tag.copy(selected = !tag.selected) else tag
                        },
                    )
                )
            )
        }
    }

    fun addQuickTag() {
        val current = (_uiState.value.contentState as? ScreenContentState.Data)?.value ?: return
        if (current.tags.any { it.label == "Hydrated" }) {
            _uiState.update { it.copy(snackbarMessage = "Hydrated tag already exists.") }
            return
        }
        _uiState.update {
            it.copy(
                contentState = ScreenContentState.Data(
                    current.copy(tags = current.tags + TagOption("Hydrated", selected = true))
                ),
                snackbarMessage = "Hydrated tag added.",
            )
        }
    }

    fun updateNotes(text: String) {
        val current = (_uiState.value.contentState as? ScreenContentState.Data)?.value ?: return
        _uiState.update {
            it.copy(contentState = ScreenContentState.Data(current.copy(notes = text)))
        }
    }

    fun logNextDose() {
        val current = (_uiState.value.contentState as? ScreenContentState.Data)?.value ?: return
        _uiState.update {
            it.copy(
                contentState = ScreenContentState.Data(
                    current.copy(
                        medication = current.medication.copy(status = MedicationStatus.Scheduled)
                    )
                ),
                snackbarMessage = "Next dose added to your check-in flow.",
            )
        }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = TodayUiState(contentState = ScreenContentState.Loading)
            runCatching {
                delay(180)
                FocusLogRepository.loadToday()
            }.onSuccess { content ->
                _uiState.update {
                    it.copy(
                        contentState = contentStateOf(content) { today ->
                            today.metrics.isEmpty()
                        }
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(contentState = ScreenContentState.Error("Today's check-in could not be loaded."))
                }
            }
        }
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
}

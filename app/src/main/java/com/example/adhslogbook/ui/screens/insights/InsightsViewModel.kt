package com.example.adhslogbook.ui.screens.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhslogbook.data.FocusLogRepository
import com.example.adhslogbook.data.model.InsightsContent
import com.example.adhslogbook.data.model.InsightsPeriod
import com.example.adhslogbook.ui.state.ScreenContentState
import com.example.adhslogbook.ui.state.contentStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InsightsUiState(
    val selectedPeriod: InsightsPeriod = InsightsPeriod.Weekly,
    val contentState: ScreenContentState<InsightsContent> = ScreenContentState.Loading,
    val snackbarMessage: String? = null,
)

class InsightsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        load(InsightsPeriod.Weekly)
    }

    fun retry() = load(_uiState.value.selectedPeriod)

    fun selectPeriod(period: InsightsPeriod) {
        if (period != _uiState.value.selectedPeriod) {
            load(period)
        }
    }

    fun exportForDoctor() {
        _uiState.update { it.copy(snackbarMessage = "Doctor summary prepared for export.") }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun load(period: InsightsPeriod) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(selectedPeriod = period, contentState = ScreenContentState.Loading)
            }
            runCatching {
                delay(150)
                FocusLogRepository.loadInsights(period)
            }.onSuccess { content ->
                _uiState.update {
                    it.copy(
                        selectedPeriod = period,
                        contentState = contentStateOf(content) { insights -> insights.bars.isEmpty() },
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(contentState = ScreenContentState.Error("Insights could not be loaded."))
                }
            }
        }
    }
}

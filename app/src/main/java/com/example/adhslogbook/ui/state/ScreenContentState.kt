package com.example.adhslogbook.ui.state

sealed interface ScreenContentState<out T> {
    data object Loading : ScreenContentState<Nothing>
    data object Empty : ScreenContentState<Nothing>
    data class Error(val message: String) : ScreenContentState<Nothing>
    data class Data<T>(val value: T) : ScreenContentState<T>
}

inline fun <T> contentStateOf(
    value: T,
    isEmpty: (T) -> Boolean,
): ScreenContentState<T> = if (isEmpty(value)) {
    ScreenContentState.Empty
} else {
    ScreenContentState.Data(value)
}

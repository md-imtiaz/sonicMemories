package com.shuvostechworld.sonicmemories.ui

import com.shuvostechworld.sonicmemories.data.model.DiaryEntry

sealed interface UiState {
    data object Loading : UiState
    data class Success(val entries: List<DiaryEntry>) : UiState
    data class Error(val message: String) : UiState
    data object Idle : UiState
}

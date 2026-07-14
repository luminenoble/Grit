/*
 * Copyright (C) 2026  Shubham Gorai
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.shub39.grit.shared.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/** UI state for the AI task-decomposition sheet. */
sealed interface AiState {
    data object Idle : AiState

    data object Loading : AiState

    data class Suggestions(val tasks: List<PlannedTask>) : AiState

    data class Error(val message: String) : AiState
}

@KoinViewModel
class AiTasksViewModel : ViewModel() {

    private val planner: TaskPlanner = createTaskPlanner()

    private val _state = MutableStateFlow<AiState>(AiState.Idle)
    val state = _state.asStateFlow()

    fun generate(summary: String) {
        if (summary.isBlank()) return
        _state.value = AiState.Loading
        viewModelScope.launch {
            _state.value =
                runCatching { planner.decompose(summary) }
                    .fold(
                        onSuccess = {
                            if (it.isEmpty()) AiState.Error("没有生成任务，换个描述再试试")
                            else AiState.Suggestions(it)
                        },
                        onFailure = { AiState.Error(it.message ?: "生成失败") },
                    )
        }
    }

    fun reset() {
        _state.value = AiState.Idle
    }
}

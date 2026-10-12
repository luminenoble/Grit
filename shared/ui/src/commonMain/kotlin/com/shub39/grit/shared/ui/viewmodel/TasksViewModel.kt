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
package com.shub39.grit.shared.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shub39.grit.core.interfaces.*
import com.shub39.grit.core.tasks.*
import com.shub39.grit.shared.ui.task.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.*

@KoinViewModel
class TasksViewModel(
    @Provided private val repo: TaskRepo,
    @Provided private val scheduler: AlarmScheduler,
    @Provided private val datastore: SettingsDatastore,
) : ViewModel() {
    private val writeLock = Mutex()
    private var pendingWrites = 0
    private val mutable = MutableStateFlow(TaskState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getTasksFlow().collect { data ->
                mutable.update { old ->
                    old.copy(
                        tasks = data,
                        completedTasks = data.values.flatten().filter { it.isCompleted },
                        currentCategory =
                            data.keys.firstOrNull { it.id == old.currentCategory?.id }
                                ?: data.keys.firstOrNull(),
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(datastore.getIs24Hr(), datastore.getTaskReorderPref()) { hours, reorder ->
                    hours to reorder
                }
                .collect { (hours, reorder) ->
                    mutable.update { it.copy(is24Hour = hours, reorderTasks = reorder) }
                }
        }
        viewModelScope.launch { repo.getTasks().forEach { scheduler.schedule(it) } }
    }

    fun onAction(action: TaskAction) {
        if (action is TaskAction.ClearError) {
            mutable.update { it.copy(error = null) }
            return
        }
        if (action is TaskAction.ChangeCategory) {
            mutable.update { it.copy(currentCategory = action.category, isTodayView = false) }
            return
        }
        if (action is TaskAction.SelectTodayView) {
            mutable.update { it.copy(isTodayView = true) }
            return
        }
        val isReorder = action is TaskAction.ReorderTasks || action is TaskAction.ReorderCategories
        if (mutable.value.saving && !isReorder) return
        pendingWrites++
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            writeLock.withLock {
                try {
                    when (action) {
                        is TaskAction.UpsertTask -> {
                            repo.upsertTask(action.task)
                            action.onSaved?.invoke()
                        }
                        is TaskAction.AddCategory -> {
                            val id = repo.upsertCategory(action.category)
                            mutable.update {
                                it.copy(
                                    currentCategory =
                                        repo.getCategories().firstOrNull { c -> c.id == id },
                                    isTodayView = false,
                                )
                            }
                            action.onSaved?.invoke()
                        }
                        is TaskAction.DeleteTask -> {
                            repo.deleteTask(action.task)
                            action.onDeleted?.invoke()
                        }
                        is TaskAction.DeleteCategory -> repo.deleteCategory(action.category)
                        is TaskAction.DeleteTasks ->
                            repo.deleteTasks(mutable.value.completedTasks.map { it.id })
                        is TaskAction.ReorderCategories ->
                            repo.reorderCategories(
                                action.mapping.sortedBy { it.first }.map { it.second.id }
                            )
                        is TaskAction.ReorderTasks -> {
                            // Hidden completed items retain their slots; each category is
                            // independently
                            // ordered.
                            action.mapping
                                .groupBy { it.second.categoryId }
                                .forEach { (categoryId, mapping) ->
                                    val existing =
                                        repo
                                            .getTasks()
                                            .filter { it.categoryId == categoryId }
                                            .sortedBy { it.orderIndex }
                                            .map { it.id }
                                    val requested =
                                        mapping.sortedBy { it.first }.map { it.second.id }
                                    require(
                                        requested.distinct().size == requested.size &&
                                            requested.all { it in existing }
                                    )
                                    val selected = requested.toSet()
                                    val iterator = requested.iterator()
                                    repo.reorderTasks(
                                        categoryId,
                                        existing.map { if (it in selected) iterator.next() else it },
                                    )
                                }
                        }
                        else -> Unit
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutable.update { it.copy(error = e.message ?: "保存失败，请重试") }
                } finally {
                    pendingWrites--
                    mutable.update { it.copy(saving = pendingWrites > 0) }
                }
            }
        }
    }
}

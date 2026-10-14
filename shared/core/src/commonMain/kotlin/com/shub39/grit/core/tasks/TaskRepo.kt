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
package com.shub39.grit.core.tasks

import com.shub39.grit.core.settings.backup.RhineBackup
import kotlinx.coroutines.flow.Flow

interface TaskRepo {
    /** Changes after full replacement, even when IDs are reused. Retained while UI is absent. */
    fun dataGeneration(): Flow<String> = kotlinx.coroutines.flow.flowOf("static")

    fun getCategoriesFlow(): Flow<List<Category>>

    fun getTasksFlow(): Flow<Map<Category, List<Task>>>

    fun getCompletedTasksFlow(): Flow<List<Task>>

    suspend fun getTasks(): List<Task>

    suspend fun getTaskById(id: Long): Task?

    suspend fun getCategories(): List<Category>

    suspend fun upsertTask(task: Task): Long

    suspend fun deleteTask(task: Task)

    suspend fun deleteTasks(ids: List<Long>)

    suspend fun deleteAllTasks()

    suspend fun upsertCategory(category: Category): Long

    suspend fun deleteCategory(category: Category)

    suspend fun deleteAllCategories()

    suspend fun reorderTasks(categoryId: Long, ids: List<Long>)

    suspend fun reorderCategories(ids: List<Long>)

    suspend fun readMoveScope(kind: MoveKind, parentId: Long?): MoveScope

    suspend fun moveTask(id: Long, target: Int, expected: MoveScope? = null)

    suspend fun moveCategory(id: Long, target: Int, expected: MoveScope? = null)

    suspend fun moveSection(id: Long, target: Int, expected: MoveScope? = null)

    suspend fun upsertSection(section: Section): Long

    suspend fun deleteSection(id: Long)

    fun getTaskSummariesFlow(categoryId: Long, offset: Int, limit: Int): Flow<List<TaskSummary>>

    fun getSectionSummariesFlow(taskId: Long, offset: Int, limit: Int): Flow<List<SectionSummary>>

    fun getTaskSummaryFlow(id: Long): Flow<TaskSummary?>

    fun getSectionSummaryFlow(id: Long): Flow<SectionSummary?>

    suspend fun getSectionById(id: Long): Section?

    fun getCategoryCountsFlow(): Flow<List<CategoryTaskCount>>

    fun getTaskCountsFlow(categoryId: Long): Flow<List<TaskSectionCount>>

    suspend fun searchTasks(query: String, offset: Int = 0, limit: Int = 50): List<TaskSearchResult>

    suspend fun exportSnapshot(): RhineBackup

    suspend fun replaceData(backup: RhineBackup)
}

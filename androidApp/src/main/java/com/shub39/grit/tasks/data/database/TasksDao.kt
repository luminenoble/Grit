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
package com.shub39.grit.tasks.data.database

import androidx.room3.*
import com.shub39.grit.core.tasks.*
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface TasksDao {
    @Query(
        "SELECT id, categoryId, title, orderIndex, titleColor, isCompleted FROM tasks WHERE id = :id"
    )
    suspend fun summaryById(id: Long): TaskSummary?

    @Query("SELECT id FROM tasks WHERE categoryId = :parentId ORDER BY orderIndex")
    suspend fun orderedIds(parentId: Long): List<Long>

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY categoryId, orderIndex")
    fun getTasksFlow(): Flow<List<TaskWithSections>>

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY categoryId, orderIndex")
    suspend fun getTasks(): List<TaskWithSections>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskWithSections?

    @Query("SELECT * FROM tasks WHERE categoryId = :categoryId ORDER BY orderIndex")
    suspend fun getInCategory(categoryId: Long): List<TaskEntity>

    @Upsert suspend fun upsert(task: TaskEntity): Long

    @Query("UPDATE tasks SET orderIndex = :position, updatedAt = :time WHERE id = :id")
    suspend fun setOrder(id: Long, position: Int, time: Instant)

    @Query("UPDATE tasks SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Instant)

    @Query("DELETE FROM tasks WHERE id = :id") suspend fun delete(id: Long)

    @Query("DELETE FROM tasks") suspend fun deleteAll()

    @Query(
        "SELECT id, categoryId, title, orderIndex, titleColor, isCompleted FROM tasks WHERE categoryId = :categoryId ORDER BY orderIndex LIMIT :limit OFFSET :offset"
    )
    fun summaries(categoryId: Long, offset: Int, limit: Int): Flow<List<TaskSummary>>

    @Query(
        "SELECT id, categoryId, title, orderIndex, titleColor, isCompleted FROM tasks WHERE id = :id"
    )
    fun summary(id: Long): Flow<TaskSummary?>

    @Query(
        "SELECT c.id AS categoryId, COUNT(t.id) AS count, COALESCE(SUM(CASE WHEN t.isCompleted THEN 1 ELSE 0 END), 0) AS completedCount FROM categories c LEFT JOIN tasks t ON c.id = t.categoryId GROUP BY c.id ORDER BY c.orderIndex"
    )
    fun counts(): Flow<List<CategoryTaskCount>>

    @Query(
        "SELECT t.id, t.categoryId, t.title, t.orderIndex, c.name AS categoryName, t.isCompleted FROM tasks t JOIN categories c ON c.id = t.categoryId WHERE t.title LIKE :pattern ESCAPE '\\' COLLATE NOCASE ORDER BY c.orderIndex, t.orderIndex LIMIT :limit OFFSET :offset"
    )
    suspend fun search(pattern: String, offset: Int, limit: Int): List<TaskSearchResult>
}

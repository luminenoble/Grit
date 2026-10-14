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
interface SectionDao {
    @Query("SELECT id, taskId, title, orderIndex, completedOn FROM sections WHERE id = :id")
    suspend fun summaryById(id: Long): SectionSummary?

    @Query("SELECT id FROM sections WHERE taskId = :parentId ORDER BY orderIndex")
    suspend fun orderedIds(parentId: Long): List<Long>

    @Query("SELECT * FROM sections WHERE taskId = :taskId ORDER BY orderIndex")
    suspend fun getInTask(taskId: Long): List<SectionEntity>

    @Query("SELECT * FROM sections WHERE id = :id") suspend fun getById(id: Long): SectionEntity?

    @Upsert suspend fun upsert(section: SectionEntity): Long

    @Query("DELETE FROM sections WHERE id = :id") suspend fun delete(id: Long)

    @Query("UPDATE sections SET orderIndex = :position, updatedAt = :time WHERE id = :id")
    suspend fun setOrder(id: Long, position: Int, time: Instant)

    @Query(
        "SELECT id, taskId, title, orderIndex, completedOn FROM sections WHERE taskId = :taskId ORDER BY orderIndex LIMIT :limit OFFSET :offset"
    )
    fun summaries(taskId: Long, offset: Int, limit: Int): Flow<List<SectionSummary>>

    @Query("SELECT id, taskId, title, orderIndex, completedOn FROM sections WHERE id = :id")
    fun summary(id: Long): Flow<SectionSummary?>

    @Query(
        "SELECT t.id AS taskId, COUNT(s.id) AS count, SUM(CASE WHEN s.completedOn IS NOT NULL THEN 1 ELSE 0 END) AS completedCount FROM tasks t LEFT JOIN sections s ON t.id = s.taskId WHERE t.categoryId = :categoryId GROUP BY t.id ORDER BY t.orderIndex"
    )
    fun counts(categoryId: Long): Flow<List<TaskSectionCount>>
}

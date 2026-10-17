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
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Gallery illustration of exactly one Category or Task. Only the content URI is stored; rows go
 * with their owner through the cascading foreign keys.
 */
@Entity(
    tableName = "rhine_illustrations",
    foreignKeys =
        [
            ForeignKey(
                entity = CategoryEntity::class,
                parentColumns = ["id"],
                childColumns = ["categoryId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = TaskEntity::class,
                parentColumns = ["id"],
                childColumns = ["taskId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices =
        [
            Index(value = ["categoryId"], unique = true),
            Index(value = ["taskId"], unique = true),
            Index(value = ["uri"]),
        ],
)
data class IllustrationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long?,
    val taskId: Long?,
    val uri: String,
    val updatedAt: Instant,
)

data class IllustrationRow(val ownerId: Long, val uri: String)

@Dao
interface IllustrationDao {
    @Query(
        "SELECT categoryId AS ownerId, uri FROM rhine_illustrations WHERE categoryId IN (:ids)"
    )
    fun categoriesFlow(ids: List<Long>): Flow<List<IllustrationRow>>

    @Query("SELECT taskId AS ownerId, uri FROM rhine_illustrations WHERE taskId IN (:ids)")
    fun tasksFlow(ids: List<Long>): Flow<List<IllustrationRow>>

    @Query("SELECT uri FROM rhine_illustrations WHERE categoryId = :id")
    suspend fun forCategory(id: Long): String?

    @Query("SELECT uri FROM rhine_illustrations WHERE taskId = :id")
    suspend fun forTask(id: Long): String?

    @Query("DELETE FROM rhine_illustrations WHERE categoryId = :id")
    suspend fun clearCategory(id: Long)

    @Query("DELETE FROM rhine_illustrations WHERE taskId = :id") suspend fun clearTask(id: Long)

    @Insert suspend fun insert(row: IllustrationEntity)

    @Query("SELECT DISTINCT uri FROM rhine_illustrations") suspend fun uris(): List<String>

    @Query("SELECT COUNT(*) FROM rhine_illustrations WHERE uri = :uri")
    suspend fun uses(uri: String): Int
}

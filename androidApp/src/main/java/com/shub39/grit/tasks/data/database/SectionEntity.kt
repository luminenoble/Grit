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
import kotlinx.datetime.LocalDate

@Entity(
    tableName = "sections",
    foreignKeys =
        [
            ForeignKey(
                entity = TaskEntity::class,
                parentColumns = ["id"],
                childColumns = ["taskId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["taskId", "orderIndex"])],
)
data class SectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val content: String,
    val orderIndex: Int,
    val completedOn: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

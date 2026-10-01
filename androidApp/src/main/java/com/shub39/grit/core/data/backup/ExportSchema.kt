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
package com.shub39.grit.core.data.backup

import com.shub39.grit.core.tasks.Section
import com.shub39.grit.core.tasks.SectionsSerializer
import com.shub39.grit.habits.data.database.HabitDatabase
import com.shub39.grit.tasks.data.database.TaskDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class ExportSchema(
    val tasksSchemaVersion: Int = TaskDatabase.SCHEMA_VERSION,
    val habitsSchemaVersion: Int = HabitDatabase.SCHEMA_VERSION,
    val habits: List<HabitSchema>,
    val habitStatus: List<HabitStatusSchema>,
    val tasks: List<TaskSchema>,
    val categories: List<CategorySchema>,
)

@Serializable
data class HabitSchema(
    val id: Long = 0,
    val title: String,
    val description: String,
    val index: Int,
    val time: Long,
    val days: String,
    val reminder: Boolean = false,
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @JsonNames("steps")
    @Serializable(with = SectionsSerializer::class)
    val sections: List<Section> = emptyList(),
    val color: String = "",
    val startDate: Long? = null,
    val deadline: Long? = null,
)

@Serializable
data class HabitStatusSchema(
    val id: Long = 0,
    val habitId: Long,
    val date: Long,
    val skipped: Boolean = false,
)

@Serializable
data class TaskSchema(
    val id: Long = 0,
    val categoryId: Long,
    val title: String,
    val status: Boolean = false,
    val index: Int = 0,
    val reminder: Long? = null,
    val description: String = "",
    val isToday: Boolean = false,
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @JsonNames("steps")
    @Serializable(with = SectionsSerializer::class)
    val sections: List<Section> = emptyList(),
)

@Serializable
data class CategorySchema(
    val id: Long = 0,
    val name: String,
    val index: Int = 0,
    val color: String,
    val hideCompleted: Boolean = false,
)

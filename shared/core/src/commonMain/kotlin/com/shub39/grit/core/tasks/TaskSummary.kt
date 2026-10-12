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

import kotlinx.datetime.LocalDate

data class TaskSummary(
    val id: Long,
    val categoryId: Long,
    val title: String,
    val orderIndex: Int,
    val titleColor: String?,
    val isCompleted: Boolean,
)

data class SectionSummary(
    val id: Long,
    val taskId: Long,
    val title: String,
    val orderIndex: Int,
    val completedOn: LocalDate?,
)

data class CategoryTaskCount(val categoryId: Long, val count: Int, val completedCount: Int = 0)

data class TaskSectionCount(val taskId: Long, val count: Int, val completedCount: Int = 0)

data class TaskSearchResult(
    val id: Long,
    val categoryId: Long,
    val title: String,
    val orderIndex: Int,
    val categoryName: String,
    val isCompleted: Boolean,
)

fun Task.summary() = TaskSummary(id, categoryId, title, orderIndex, titleColor, isCompleted)

fun Section.summary() = SectionSummary(id, taskId, title, orderIndex, completedOn)

fun validateTitleColor(color: String?) {
    require(color == null || Regex("#[0-9a-fA-F]{6}").matches(color)) { "颜色必须为 #RRGGBB" }
}

/** Target is a zero-based position; IDs remain stable. */
fun movedIds(ids: List<Long>, id: Long, target: Int): List<Long> {
    require(ids.distinct().size == ids.size && id in ids && target in ids.indices)
    return ids.toMutableList().apply {
        remove(id)
        add(target, id)
    }
}

fun validateReorder(existing: List<Long>, requested: List<Long>) {
    require(requested.size == existing.size && requested.toSet() == existing.toSet()) {
        "排序数据已变化，请重试"
    }
}

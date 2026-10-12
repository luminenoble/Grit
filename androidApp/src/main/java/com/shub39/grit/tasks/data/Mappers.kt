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
package com.shub39.grit.tasks.data

import com.shub39.grit.core.tasks.*
import com.shub39.grit.tasks.data.database.*

fun Category.toCategoryEntity() =
    CategoryEntity(
        id,
        name,
        description,
        orderIndex,
        titleColor,
        hideCompleted,
        createdAt,
        updatedAt,
    )

fun CategoryEntity.toCategory() =
    Category(id, name, description, orderIndex, titleColor, hideCompleted, createdAt, updatedAt)

fun Task.toTaskEntity() =
    TaskEntity(
        id,
        categoryId,
        title,
        description,
        orderIndex,
        titleColor,
        isCompleted,
        isToday,
        reminder,
        createdAt,
        updatedAt,
    )

fun TaskWithSections.toTask() =
    task.let {
        Task(
            it.id,
            it.categoryId,
            it.title,
            it.orderIndex,
            it.isCompleted,
            it.reminder,
            it.description,
            it.isToday,
            it.titleColor,
            it.createdAt,
            it.updatedAt,
            sections.sortedBy { s -> s.orderIndex }.map { s -> s.toSection() },
        )
    }

fun Section.toSectionEntity() =
    SectionEntity(id, taskId, title, content, orderIndex, completedOn, createdAt, updatedAt)

fun SectionEntity.toSection() =
    Section(id, taskId, title, content, orderIndex, completedOn, createdAt, updatedAt)

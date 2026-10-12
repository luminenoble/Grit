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
package com.shub39.grit.shared.ui.rhine

import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.CategoryTaskCount
import com.shub39.grit.core.tasks.TaskRepo
import com.shub39.grit.core.tasks.TaskSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Read-only boundary: the browser cannot create placeholders in a database. */
interface RhineDataSource {
    val layer: RhineLayer
        get() = RhineLayer.Categories

    fun categories(): Flow<List<Category>>

    fun counts(): Flow<List<CategoryTaskCount>>

    fun summaries(categoryId: Long, offset: Int, limit: Int): Flow<List<TaskSummary>>

    fun item(id: Long): Flow<TaskSummary?>

    fun catalog(): Flow<RhineCatalog> =
        combine(categories(), counts()) { cs, counts -> RhineCatalog(cs, counts) }
}

class RepositoryRhineDataSource(private val repo: TaskRepo) : RhineDataSource {
    override fun categories() = repo.getCategoriesFlow()

    override fun counts() = repo.getCategoryCountsFlow()

    override fun summaries(categoryId: Long, offset: Int, limit: Int) =
        repo.getTaskSummariesFlow(categoryId, offset, limit)

    override fun item(id: Long) = repo.getTaskSummaryFlow(id)
}

data class RhineCatalog(val categories: List<Category>, val counts: List<CategoryTaskCount>) {
    val bounds =
        RhineBounds.from(
            categories.map { c -> counts.firstOrNull { it.categoryId == c.id }?.count ?: 0 }
        )
}

fun RhineDataSource.window(catalog: RhineCatalog, window: SlotWindow): Flow<List<RhineColumn>> {
    if (this is SectionRhineDataSource) return sectionWindow(catalog, window)
    val counts = catalog.counts.associateBy { it.categoryId }
    val offset = (window.minU - 1).coerceAtLeast(0)
    val end = window.maxU.coerceAtLeast(0)
    val requests =
        catalog.categories.mapIndexedNotNull { v, c ->
            val count = counts[c.id]?.count ?: 0
            if (v !in window.minV..window.maxV || count <= offset || end <= offset) null
            else {
                // Each DAO page is <= 500; an unusually wide viewport can use several bounded
                // pages.
                val pages =
                    (offset until minOf(end, count) step 500).map { start ->
                        summaries(c.id, start, minOf(500, minOf(end, count) - start))
                    }
                combine(pages) { chunks ->
                    c.id to
                        chunks
                            .flatMap { it }
                            .associate { task ->
                                task.orderIndex + 1 to
                                    RhineItem(
                                        task.id,
                                        task.orderIndex + 1,
                                        task.title,
                                        task.titleColor,
                                        task.isCompleted,
                                    )
                            }
                }
            }
        }
    val loaded = if (requests.isEmpty()) flowOf(emptyMap()) else combine(requests) { it.toMap() }
    return loaded.map { items ->
        catalog.categories.mapIndexed { v, c ->
            RhineColumn(
                c.id,
                v + 1,
                c.name,
                c.titleColor,
                counts[c.id]?.count ?: 0,
                items[c.id] ?: emptyMap(),
                counts[c.id]?.completedCount ?: 0,
            )
        }
    }
}

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

import com.shub39.grit.core.tasks.*
import kotlinx.coroutines.flow.*

/** Only count/identity metadata covers the directory. Titles and Sections follow the viewport. */
class SectionRhineDataSource(private val repo: TaskRepo, val categoryId: Long) : RhineDataSource {
    override val layer = RhineLayer.Sections

    override fun catalog() =
        repo.getTaskCountsFlow(categoryId).map { counts ->
            RhineCatalog(
                counts.mapIndexed { index, count ->
                    Category(id = count.taskId, name = "载入中…", orderIndex = index)
                },
                counts.map { CategoryTaskCount(it.taskId, it.count, it.completedCount) },
            )
        }

    override fun categories() =
        repo.getTaskCountsFlow(categoryId).map { counts ->
            counts.mapIndexed { index, count ->
                Category(id = count.taskId, name = "载入中…", orderIndex = index)
            }
        }

    override fun counts() =
        repo.getTaskCountsFlow(categoryId).map { counts ->
            counts.map { CategoryTaskCount(it.taskId, it.count, it.completedCount) }
        }

    // Focus tracking has the same parent/order contract in both layers, and never reads content.
    override fun item(id: Long) =
        repo.getSectionSummaryFlow(id).map { section ->
            section?.let {
                TaskSummary(it.id, it.taskId, it.title, it.orderIndex, null, it.completedOn != null)
            }
        }

    override fun summaries(categoryId: Long, offset: Int, limit: Int): Flow<List<TaskSummary>> =
        error("Section windows use Section summaries")

    fun sectionWindow(catalog: RhineCatalog, window: SlotWindow): Flow<List<RhineColumn>> {
        val counts = catalog.counts.associateBy { it.categoryId }
        val first = window.minV.coerceAtLeast(0).coerceAtMost(catalog.categories.size)
        val end = (window.maxV + 1).coerceIn(first, catalog.categories.size)
        val taskPages =
            (first until end step 500).map { offset ->
                repo.getTaskSummariesFlow(categoryId, offset, minOf(500, end - offset))
            }
        val tasks =
            if (taskPages.isEmpty()) flowOf(emptyList())
            else combine(taskPages) { pages -> pages.flatMap { it } }
        val offset = (window.minU - 1).coerceAtLeast(0)
        val requests =
            catalog.categories.subList(first, end).mapNotNull { task ->
                val last = minOf(window.maxU.coerceAtLeast(0), counts[task.id]?.count ?: 0)
                if (last <= offset) null
                else {
                    val pages =
                        (offset until last step 500).map { start ->
                            repo.getSectionSummariesFlow(task.id, start, minOf(500, last - start))
                        }
                    combine(pages) { chunks -> task.id to chunks.flatMap { it } }
                }
            }
        val sections =
            if (requests.isEmpty()) flowOf(emptyMap()) else combine(requests) { it.toMap() }
        return combine(tasks, sections) { ts, ss ->
            val titles = ts.associateBy { it.id }
            catalog.categories.mapIndexed { index, c ->
                val task = titles[c.id]
                RhineColumn(
                    c.id,
                    index + 1,
                    task?.title ?: "载入中…",
                    task?.titleColor,
                    counts[c.id]?.count ?: 0,
                    ss[c.id].orEmpty().associate { section ->
                        section.orderIndex + 1 to
                            RhineItem(
                                section.id,
                                section.orderIndex + 1,
                                section.title,
                                task?.titleColor,
                                section.completedOn != null,
                                section.completedOn,
                            )
                    },
                    counts[c.id]?.completedCount ?: 0,
                    loaded = task != null,
                )
            }
        }
    }
}

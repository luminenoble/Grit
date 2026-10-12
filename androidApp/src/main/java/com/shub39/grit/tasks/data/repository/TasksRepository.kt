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
package com.shub39.grit.tasks.data.repository

import android.content.Context
import android.util.Log
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.shub39.grit.core.data.notification.GritNotificationManager
import com.shub39.grit.core.interfaces.AlarmScheduler
import com.shub39.grit.core.settings.backup.RhineBackup
import com.shub39.grit.core.tasks.*
import com.shub39.grit.tasks.data.*
import com.shub39.grit.tasks.data.database.TaskDatabase
import com.shub39.grit.widgets.WidgetUpdates
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import org.koin.core.annotation.Single

@Single(binds = [TaskRepo::class])
class TasksRepository(
    private val context: Context,
    private val db: TaskDatabase,
    private val scheduler: AlarmScheduler,
    private val notifications: GritNotificationManager,
) : TaskRepo {
    private val generation = MutableStateFlow(java.util.UUID.randomUUID().toString())

    override fun dataGeneration(): Flow<String> = generation

    private val tasks = db.taskDao()
    private val categories = db.categoryDao()
    private val sections = db.sectionDao()

    override fun getCategoriesFlow() =
        categories.getCategoriesFlow().map { cs -> cs.map { it.toCategory() } }

    override fun getTasksFlow() =
        combine(tasks.getTasksFlow(), categories.getCategoriesFlow()) { ts, cs ->
            val grouped = ts.map { it.toTask() }.groupBy { it.categoryId }
            cs.associate { it.toCategory() to (grouped[it.id] ?: emptyList()) }
        }

    override fun getCompletedTasksFlow() =
        tasks.getTasksFlow().map { ts -> ts.filter { it.task.isCompleted }.map { it.toTask() } }

    override suspend fun getTasks() = tasks.getTasks().map { it.toTask() }

    override suspend fun getTaskById(id: Long) = tasks.getTaskById(id)?.toTask()

    override suspend fun getCategories() = categories.getCategories().map { it.toCategory() }

    private suspend fun refresh(
        removed: List<Task> = emptyList(),
        saved: List<Task> = emptyList(),
    ) {
        // Data has committed. Notification/widget failures never turn it into a partial save.
        suspend fun attempt(block: suspend () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TasksRepository", "刷新失败，将在后续启动重试", e)
            }
        }
        removed.forEach { task ->
            attempt {
                scheduler.cancel(task)
                notifications.cancelNotification(task)
            }
        }
        saved.forEach { task ->
            attempt {
                scheduler.schedule(task)
                if (task.isCompleted) notifications.cancelNotification(task)
            }
        }
        attempt { WidgetUpdates.tasks(context) }
    }

    private suspend fun orderTasks(categoryId: Long, ids: List<Long>) {
        validateReorder(tasks.getInCategory(categoryId).map { it.id }, ids)
        val now = Clock.System.now()
        ids.forEachIndexed { i, id -> tasks.setOrder(id, i, now) }
    }

    private suspend fun orderCategories(ids: List<Long>) {
        validateReorder(categories.getCategories().map { it.id }, ids)
        val now = Clock.System.now()
        ids.forEachIndexed { i, id -> categories.setOrder(id, i, now) }
    }

    override suspend fun upsertTask(task: Task): Long {
        val id =
            db.withWriteTransaction {
                require(task.title.isNotBlank())
                validateTitleColor(task.titleColor)
                require(categories.getById(task.categoryId) != null) { "类别已被删除" }
                val previous = if (task.id == 0L) null else tasks.getTaskById(task.id)?.toTask()
                require(task.id == 0L || previous != null) { "任务已被删除" }
                val known = previous?.sections?.associateBy { it.id } ?: emptyMap()
                val positiveIds = task.sections.filter { it.id > 0 }.map { it.id }
                require(
                    positiveIds.distinct().size == positiveIds.size &&
                        positiveIds.all { it in known }
                )
                val prepared = task.sections.map { it.normalized() }
                val now = Clock.System.now()
                val moved = previous != null && previous.categoryId != task.categoryId
                val position =
                    if (previous == null || moved) tasks.getInCategory(task.categoryId).size
                    else previous.orderIndex
                val entity =
                    task
                        .copy(
                            orderIndex = position,
                            title = task.title.trim(),
                            createdAt = previous?.createdAt ?: now,
                            updatedAt = now,
                        )
                        .toTaskEntity()
                val inserted = tasks.upsert(entity)
                val savedId = if (previous == null) inserted else task.id
                known.keys.filter { it !in positiveIds }.forEach { sections.delete(it) }
                prepared.forEachIndexed { i, section ->
                    val old = known[section.id]
                    val candidate =
                        section.copy(
                            taskId = savedId,
                            orderIndex = i,
                            createdAt = old?.createdAt ?: now,
                        )
                    sections.upsert(
                        candidate
                            .copy(updatedAt = if (candidate == old) old!!.updatedAt else now)
                            .toSectionEntity()
                    )
                }
                if (moved)
                    orderTasks(
                        previous!!.categoryId,
                        tasks.getInCategory(previous.categoryId).map { it.id },
                    )
                savedId
            }
        refresh(saved = listOfNotNull(getTaskById(id)))
        return id
    }

    override suspend fun upsertCategory(category: Category): Long {
        val id =
            db.withWriteTransaction {
                require(category.name.isNotBlank())
                validateTitleColor(category.titleColor)
                val old = if (category.id == 0L) null else categories.getById(category.id)
                require(category.id == 0L || old != null) { "类别已被删除" }
                val now = Clock.System.now()
                val inserted =
                    categories.upsert(
                        category
                            .copy(
                                name = category.name.trim(),
                                orderIndex = old?.orderIndex ?: categories.getCategories().size,
                                createdAt = old?.createdAt ?: now,
                                updatedAt = now,
                            )
                            .toCategoryEntity()
                    )
                if (old == null) inserted else category.id
            }
        refresh()
        return id
    }

    override suspend fun deleteTask(task: Task) {
        val old =
            db.withWriteTransaction {
                val saved = tasks.getTaskById(task.id)?.toTask()
                if (saved != null) {
                    tasks.delete(saved.id)
                    orderTasks(
                        saved.categoryId,
                        tasks.getInCategory(saved.categoryId).map { it.id },
                    )
                }
                saved
            }
        refresh(removed = listOfNotNull(old))
    }

    override suspend fun deleteCategory(category: Category) {
        val removed =
            db.withWriteTransaction {
                val removed =
                    tasks.getInCategory(category.id).mapNotNull {
                        tasks.getTaskById(it.id)?.toTask()
                    }
                categories.delete(category.id)
                orderCategories(categories.getCategories().map { it.id })
                removed
            }
        refresh(removed)
    }

    override suspend fun deleteTasks(ids: List<Long>) {
        val removed =
            db.withWriteTransaction {
                val existing = ids.distinct().mapNotNull { tasks.getTaskById(it)?.toTask() }
                existing.forEach { tasks.delete(it.id) }
                existing
                    .map { it.categoryId }
                    .distinct()
                    .forEach { parent ->
                        orderTasks(parent, tasks.getInCategory(parent).map { it.id })
                    }
                existing
            }
        refresh(removed)
    }

    override suspend fun deleteAllTasks() {
        val removed =
            db.withWriteTransaction {
                val old = getTasks()
                tasks.deleteAll()
                old
            }
        refresh(removed)
    }

    override suspend fun deleteAllCategories() {
        val removed =
            db.withWriteTransaction {
                val old = getTasks()
                categories.deleteAll()
                old
            }
        refresh(removed)
    }

    override suspend fun reorderTasks(categoryId: Long, ids: List<Long>) {
        db.withWriteTransaction { orderTasks(categoryId, ids) }
        refresh()
    }

    override suspend fun reorderCategories(ids: List<Long>) {
        db.withWriteTransaction { orderCategories(ids) }
        refresh()
    }

    override suspend fun moveTask(id: Long, target: Int) {
        db.withWriteTransaction {
            val task = requireNotNull(tasks.getTaskById(id)).task
            orderTasks(
                task.categoryId,
                movedIds(tasks.getInCategory(task.categoryId).map { it.id }, id, target),
            )
        }
        refresh()
    }

    override suspend fun moveCategory(id: Long, target: Int) {
        db.withWriteTransaction {
            orderCategories(movedIds(categories.getCategories().map { it.id }, id, target))
        }
        refresh()
    }

    override suspend fun upsertSection(section: Section): Long {
        val id =
            db.withWriteTransaction {
                val prepared = section.normalized()
                require(tasks.getTaskById(section.taskId) != null)
                val old = if (section.id == 0L) null else sections.getById(section.id)
                require(section.id == 0L || old?.taskId == section.taskId)
                val now = Clock.System.now()
                val inserted =
                    sections.upsert(
                        prepared
                            .copy(
                                orderIndex =
                                    old?.orderIndex ?: sections.getInTask(section.taskId).size,
                                createdAt = old?.createdAt ?: now,
                                updatedAt = now,
                            )
                            .toSectionEntity()
                    )
                tasks.touch(section.taskId, now)
                if (old == null) inserted else section.id
            }
        refresh()
        return id
    }

    override suspend fun deleteSection(id: Long) {
        db.withWriteTransaction {
            val old = sections.getById(id)
            if (old != null) {
                val now = Clock.System.now()
                sections.delete(id)
                sections.getInTask(old.taskId).forEachIndexed { i, s ->
                    sections.setOrder(s.id, i, now)
                }
                tasks.touch(old.taskId, now)
            }
        }
        refresh()
    }

    override suspend fun moveSection(id: Long, target: Int) {
        db.withWriteTransaction {
            val old = requireNotNull(sections.getById(id))
            val now = Clock.System.now()
            movedIds(sections.getInTask(old.taskId).map { it.id }, id, target).forEachIndexed {
                i,
                sid ->
                sections.setOrder(sid, i, now)
            }
            tasks.touch(old.taskId, now)
        }
        refresh()
    }

    private fun window(offset: Int, limit: Int) {
        require(offset >= 0 && limit in 1..500)
    }

    override fun getTaskSummariesFlow(
        categoryId: Long,
        offset: Int,
        limit: Int,
    ): Flow<List<TaskSummary>> {
        window(offset, limit)
        return tasks.summaries(categoryId, offset, limit)
    }

    override fun getSectionSummariesFlow(
        taskId: Long,
        offset: Int,
        limit: Int,
    ): Flow<List<SectionSummary>> {
        window(offset, limit)
        return sections.summaries(taskId, offset, limit)
    }

    override fun getTaskSummaryFlow(id: Long) = tasks.summary(id)

    override fun getSectionSummaryFlow(id: Long) = sections.summary(id)

    override suspend fun getSectionById(id: Long) = sections.getById(id)?.toSection()

    override fun getCategoryCountsFlow() = tasks.counts()

    override fun getTaskCountsFlow(categoryId: Long) = sections.counts(categoryId)

    override suspend fun searchTasks(
        query: String,
        offset: Int,
        limit: Int,
    ): List<TaskSearchResult> {
        window(offset, limit)
        if (query.isBlank()) return emptyList()
        val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return tasks.search("%$escaped%", offset, limit)
    }

    override suspend fun exportSnapshot() =
        db.withReadTransaction {
            RhineBackup(
                RhineBackup.FORMAT,
                RhineBackup.VERSION,
                Clock.System.now(),
                getCategories(),
                getTasks(),
            )
        }

    override suspend fun replaceData(backup: RhineBackup) {
        val valid = backup.validated()
        val removed =
            db.withWriteTransaction {
                val previous = getTasks()
                categories.deleteAll()
                valid.categories.forEach { categories.upsert(it.toCategoryEntity()) }
                valid.tasks.forEach { task ->
                    tasks.upsert(task.toTaskEntity())
                    task.sections.forEach {
                        sections.upsert(it.copy(taskId = task.id).toSectionEntity())
                    }
                }
                previous
            }
        generation.value = java.util.UUID.randomUUID().toString()
        refresh(removed, valid.tasks)
    }
}

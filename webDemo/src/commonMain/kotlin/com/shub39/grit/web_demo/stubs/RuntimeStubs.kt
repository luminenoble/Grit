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
package com.shub39.grit.web_demo.stubs

import com.shub39.grit.core.interfaces.*
import com.shub39.grit.core.settings.*
import com.shub39.grit.core.settings.backup.RhineBackup
import com.shub39.grit.core.tasks.*
import com.shub39.grit.core.theme.Fonts
import kotlin.time.Clock
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

@Single(binds = [ThemeDatastore::class])
class ThemeDatastoreStub : ThemeDatastore {
    private val font = MutableStateFlow(Fonts.FIGTREE)

    override fun getFontPrefFlow() = font.asStateFlow()

    override suspend fun setFontPref(font: Fonts) {
        this.font.value = font
    }

    private val reduceTransparency = MutableStateFlow(false)

    override fun getReduceTransparencyFlow() = reduceTransparency.asStateFlow()

    override suspend fun setReduceTransparency(pref: Boolean) {
        reduceTransparency.value = pref
    }
}

@Single(binds = [SettingsDatastore::class])
class SettingsDatastoreStub : SettingsDatastore {
    private val rhine = MutableStateFlow(com.shub39.grit.core.settings.RhineDisplayConfig())

    override fun getRhineDisplay() = rhine.asStateFlow()

    override suspend fun setRhineDisplay(config: com.shub39.grit.core.settings.RhineDisplayConfig) {
        rhine.value = config.normalized()
    }

    private val page = MutableStateFlow(Sections.Tasks)
    private val hours = MutableStateFlow(false)
    private val notifications = MutableStateFlow(false)
    private val biometric = MutableStateFlow(false)
    private val reorder = MutableStateFlow(true)
    private val changelog = MutableStateFlow("")
    private val widget = MutableStateFlow(WidgetTextSize.MEDIUM)

    override fun getStartingSectionPref() = page.asStateFlow()

    override suspend fun setStartingPage(page: Sections) {
        this.page.value = page
    }

    override fun getIs24Hr() = hours.asStateFlow()

    override suspend fun setIs24Hr(pref: Boolean) {
        hours.value = pref
    }

    override fun getNotificationsFlow() = notifications.asStateFlow()

    override suspend fun setNotifications(pref: Boolean) {
        notifications.value = pref
    }

    override fun getBiometricLockPref() = biometric.asStateFlow()

    override suspend fun setBiometricPref(pref: Boolean) {
        biometric.value = pref
    }

    override fun getTaskReorderPref() = reorder.asStateFlow()

    override suspend fun setTaskReorderPref(pref: Boolean) {
        reorder.value = pref
    }

    override fun getLastChangelogShown() = changelog.asStateFlow()

    override suspend fun updateLastChangelogShown(version: String) {
        changelog.value = version
    }

    override fun getWidgetTextSizeFlow() = widget.asStateFlow()

    override suspend fun setWidgetTextSize(size: WidgetTextSize) {
        widget.value = size
    }
}

private data class DemoData(val categories: List<Category>, val tasks: List<Task>)

@Single(binds = [TaskRepo::class])
class TaskRepoStub : TaskRepo {
    private val generation = MutableStateFlow(0L)

    override fun dataGeneration() = generation.map { it.toString() }

    private val data =
        MutableStateFlow(
            DemoData(
                listOf(Category(id = 1, name = "工作")),
                listOf(Task(id = 1, categoryId = 1, title = "Welcome to Grhinet")),
            )
        )
    private val mutex = Mutex()
    private var nextCategory = 2L
    private var nextTask = 2L
    private var nextSection = 1L

    private suspend fun <T> write(block: (DemoData) -> Pair<DemoData, T>): T =
        mutex.withLock {
            val (next, result) = block(data.value)
            data.value = next
            result
        }

    private fun normalize(ts: List<Task>) =
        ts.groupBy { it.categoryId }
            .values
            .flatMap { group ->
                group.sortedBy { it.orderIndex }.mapIndexed { i, t -> t.copy(orderIndex = i) }
            }

    override fun getCategoriesFlow() = data.map { it.categories.sortedBy { c -> c.orderIndex } }

    override fun getTasksFlow() =
        data.map { d ->
            d.categories
                .sortedBy { it.orderIndex }
                .associateWith { c ->
                    d.tasks.filter { it.categoryId == c.id }.sortedBy { it.orderIndex }
                }
        }

    override fun getCompletedTasksFlow() = data.map { it.tasks.filter { t -> t.isCompleted } }

    override suspend fun getTasks() = data.value.tasks

    override suspend fun getCategories() = data.value.categories.sortedBy { it.orderIndex }

    override suspend fun getTaskById(id: Long) = data.value.tasks.firstOrNull { it.id == id }

    override suspend fun upsertCategory(category: Category): Long = write { d ->
        require(category.name.isNotBlank())
        validateTitleColor(category.titleColor)
        val old = d.categories.firstOrNull { it.id == category.id }
        require(category.id == 0L || old != null)
        val saved =
            category.copy(
                id = old?.id ?: nextCategory++,
                orderIndex = old?.orderIndex ?: d.categories.size,
                name = category.name.trim(),
                createdAt = old?.createdAt ?: Clock.System.now(),
                updatedAt = Clock.System.now(),
            )
        d.copy(categories = d.categories.filterNot { it.id == saved.id } + saved) to saved.id
    }

    override suspend fun upsertTask(task: Task): Long = write { d ->
        require(d.categories.any { it.id == task.categoryId } && task.title.isNotBlank())
        validateTitleColor(task.titleColor)
        val old = d.tasks.firstOrNull { it.id == task.id }
        require(task.id == 0L || old != null)
        val id = old?.id ?: nextTask++
        val known = old?.sections?.associateBy { it.id } ?: emptyMap()
        val ids = task.sections.filter { it.id > 0 }.map { it.id }
        require(ids.distinct().size == ids.size && ids.all { it in known })
        val saved =
            task.copy(
                id = id,
                title = task.title.trim(),
                orderIndex =
                    if (old == null || old.categoryId != task.categoryId)
                        d.tasks.count { it.categoryId == task.categoryId }
                    else old.orderIndex,
                createdAt = old?.createdAt ?: Clock.System.now(),
                updatedAt = Clock.System.now(),
                sections =
                    task.sections.mapIndexed { i, s ->
                        s.normalized()
                            .copy(
                                id = if (s.id == 0L) nextSection++ else s.id,
                                taskId = id,
                                orderIndex = i,
                                updatedAt = Clock.System.now(),
                            )
                    },
            )
        d.copy(tasks = normalize(d.tasks.filterNot { it.id == id } + saved)) to id
    }

    override suspend fun deleteTask(task: Task) {
        write { d -> d.copy(tasks = normalize(d.tasks.filterNot { it.id == task.id })) to Unit }
    }

    override suspend fun deleteTasks(ids: List<Long>) {
        write { d -> d.copy(tasks = normalize(d.tasks.filterNot { it.id in ids })) to Unit }
    }

    override suspend fun deleteAllTasks() {
        write { d -> d.copy(tasks = emptyList()) to Unit }
    }

    override suspend fun deleteCategory(category: Category) {
        write { d ->
            d.copy(
                categories =
                    d.categories
                        .filterNot { it.id == category.id }
                        .sortedBy { it.orderIndex }
                        .mapIndexed { i, c -> c.copy(orderIndex = i) },
                tasks = d.tasks.filterNot { it.categoryId == category.id },
            ) to Unit
        }
    }

    override suspend fun deleteAllCategories() {
        write { DemoData(emptyList(), emptyList()) to Unit }
    }

    override suspend fun reorderTasks(categoryId: Long, ids: List<Long>) {
        write { d ->
            validateReorder(d.tasks.filter { it.categoryId == categoryId }.map { it.id }, ids)
            d.copy(
                tasks =
                    d.tasks.map {
                        if (it.categoryId == categoryId)
                            it.copy(orderIndex = ids.indexOf(it.id), updatedAt = Clock.System.now())
                        else it
                    }
            ) to Unit
        }
    }

    override suspend fun reorderCategories(ids: List<Long>) {
        write { d ->
            validateReorder(d.categories.map { it.id }, ids)
            d.copy(
                categories =
                    d.categories.map {
                        it.copy(orderIndex = ids.indexOf(it.id), updatedAt = Clock.System.now())
                    }
            ) to Unit
        }
    }

    private fun moveScope(d: DemoData, kind: MoveKind, parentId: Long?): MoveScope {
        val parent =
            if (kind == MoveKind.Section) requireNotNull(d.tasks.firstOrNull { it.id == parentId })
            else null
        val ids =
            when (kind) {
                MoveKind.Category -> d.categories.sortedBy { it.orderIndex }.map { it.id }
                MoveKind.Task -> {
                    require(d.categories.any { it.id == parentId })
                    d.tasks
                        .filter { it.categoryId == parentId }
                        .sortedBy { it.orderIndex }
                        .map { it.id }
                }
                MoveKind.Section -> parent!!.sections.sortedBy { it.orderIndex }.map { it.id }
            }
        return MoveScope(kind, parentId, parent?.categoryId, ids, generation.value.toString())
    }

    override suspend fun readMoveScope(kind: MoveKind, parentId: Long?): MoveScope =
        mutex.withLock { moveScope(data.value, kind, parentId) }

    override suspend fun moveTask(id: Long, target: Int, expected: MoveScope?) = write { d ->
        val task = requireNotNull(d.tasks.firstOrNull { it.id == id })
        val current = moveScope(d, MoveKind.Task, task.categoryId)
        expected?.validate(current)
        val ids = movedIds(current.ids, id, target)
        d.copy(
            tasks =
                d.tasks.map {
                    if (it.categoryId == task.categoryId)
                        it.copy(orderIndex = ids.indexOf(it.id), updatedAt = Clock.System.now())
                    else it
                }
        ) to Unit
    }

    override suspend fun moveCategory(id: Long, target: Int, expected: MoveScope?) = write { d ->
        val current = moveScope(d, MoveKind.Category, null)
        expected?.validate(current)
        val ids = movedIds(current.ids, id, target)
        d.copy(
            categories =
                d.categories.map {
                    it.copy(orderIndex = ids.indexOf(it.id), updatedAt = Clock.System.now())
                }
        ) to Unit
    }

    override suspend fun upsertSection(section: Section): Long = write { d ->
        val task = requireNotNull(d.tasks.firstOrNull { it.id == section.taskId })
        val old = task.sections.firstOrNull { it.id == section.id }
        require(section.id == 0L || old != null)
        val saved =
            section
                .normalized()
                .copy(
                    id = old?.id ?: nextSection++,
                    orderIndex = old?.orderIndex ?: task.sections.size,
                    updatedAt = Clock.System.now(),
                )
        val updated =
            task.copy(
                sections =
                    (task.sections.filterNot { it.id == saved.id } + saved).sortedBy {
                        it.orderIndex
                    },
                updatedAt = Clock.System.now(),
            )
        d.copy(tasks = d.tasks.map { if (it.id == task.id) updated else it }) to saved.id
    }

    override suspend fun deleteSection(id: Long) {
        write { d ->
            d.copy(
                tasks =
                    d.tasks.map { t ->
                        if (t.sections.none { it.id == id }) t
                        else
                            t.copy(
                                sections =
                                    t.sections
                                        .filterNot { it.id == id }
                                        .mapIndexed { i, s -> s.copy(orderIndex = i) },
                                updatedAt = Clock.System.now(),
                            )
                    }
            ) to Unit
        }
    }

    override suspend fun moveSection(id: Long, target: Int, expected: MoveScope?) {
        write { d ->
            val task = requireNotNull(d.tasks.firstOrNull { t -> t.sections.any { it.id == id } })
            expected?.validate(moveScope(d, MoveKind.Section, task.id))
            val ids = movedIds(task.sections.sortedBy { it.orderIndex }.map { it.id }, id, target)
            val updated =
                task.copy(
                    sections =
                        task.sections
                            .map { it.copy(orderIndex = ids.indexOf(it.id)) }
                            .sortedBy { it.orderIndex },
                    updatedAt = Clock.System.now(),
                )
            d.copy(tasks = d.tasks.map { if (it.id == task.id) updated else it }) to Unit
        }
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
        return data.map {
            it.tasks
                .filter { t -> t.categoryId == categoryId }
                .sortedBy { t -> t.orderIndex }
                .drop(offset)
                .take(limit)
                .map { t -> t.summary() }
        }
    }

    override fun getSectionSummariesFlow(
        taskId: Long,
        offset: Int,
        limit: Int,
    ): Flow<List<SectionSummary>> {
        window(offset, limit)
        return data.map {
            it.tasks
                .firstOrNull { t -> t.id == taskId }
                ?.sections
                .orEmpty()
                .drop(offset)
                .take(limit)
                .map { s -> s.summary() }
        }
    }

    override fun getTaskSummaryFlow(id: Long) =
        getTasksFlow().map { groups ->
            groups.values.flatten().firstOrNull { it.id == id }?.summary()
        }

    override fun getSectionSummaryFlow(id: Long) =
        data.map { d -> d.tasks.flatMap { it.sections }.firstOrNull { it.id == id }?.summary() }

    override suspend fun getSectionById(id: Long) =
        data.value.tasks.flatMap { it.sections }.firstOrNull { it.id == id }

    override fun getCategoryCountsFlow() =
        data.map { d ->
            d.categories.map { c ->
                CategoryTaskCount(
                    c.id,
                    d.tasks.count { it.categoryId == c.id },
                    d.tasks.count { it.categoryId == c.id && it.isCompleted },
                )
            }
        }

    override fun getTaskCountsFlow(categoryId: Long) =
        data.map { d ->
            d.tasks
                .filter { it.categoryId == categoryId }
                .map {
                    TaskSectionCount(
                        it.id,
                        it.sections.size,
                        it.sections.count { s -> s.isCompleted },
                    )
                }
        }

    override suspend fun searchTasks(
        query: String,
        offset: Int,
        limit: Int,
    ): List<TaskSearchResult> {
        window(offset, limit)
        if (query.isBlank()) return emptyList()
        val d = data.value
        val cats = d.categories.associateBy { it.id }
        return d.tasks
            .filter { it.title.contains(query.trim(), ignoreCase = true) }
            .sortedWith(
                compareBy<Task> { cats.getValue(it.categoryId).orderIndex }.thenBy { it.orderIndex }
            )
            .drop(offset)
            .take(limit)
            .map {
                TaskSearchResult(
                    it.id,
                    it.categoryId,
                    it.title,
                    it.orderIndex,
                    cats.getValue(it.categoryId).name,
                    it.isCompleted,
                )
            }
    }

    override suspend fun exportSnapshot(): RhineBackup =
        mutex.withLock {
            val d = data.value
            RhineBackup(
                RhineBackup.FORMAT,
                RhineBackup.VERSION,
                Clock.System.now(),
                d.categories.sortedBy { it.orderIndex },
                d.tasks,
            )
        }

    override suspend fun replaceData(backup: RhineBackup) {
        val valid = backup.validated()
        write {
            nextCategory = maxOf(nextCategory, (valid.categories.maxOfOrNull { it.id } ?: 0) + 1)
            nextTask = maxOf(nextTask, (valid.tasks.maxOfOrNull { it.id } ?: 0) + 1)
            nextSection =
                maxOf(
                    nextSection,
                    (valid.tasks.flatMap { it.sections }.maxOfOrNull { it.id } ?: 0) + 1,
                )
            generation.value += 1
            DemoData(valid.categories, valid.tasks) to Unit
        }
    }
}

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
package com.shub39.grit

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.shub39.grit.core.data.notification.GritNotificationManager
import com.shub39.grit.core.interfaces.AlarmScheduler
import com.shub39.grit.core.settings.backup.RhineBackup
import com.shub39.grit.core.tasks.*
import com.shub39.grit.tasks.data.database.TaskDatabase
import com.shub39.grit.tasks.data.repository.TasksRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.*
import org.junit.Assert.*

class TaskRepositoryTest {
    private lateinit var db: TaskDatabase
    private lateinit var repo: TasksRepository
    private val scheduled = mutableListOf<Task>()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder<TaskDatabase>(context).build()
        val scheduler =
            object : AlarmScheduler {
                override fun schedule(task: Task) {
                    scheduled.add(task)
                }

                override fun cancel(task: Task) {}

                override fun cancelAll() {}
            }
        repo = TasksRepository(context, db, scheduler, GritNotificationManager(context))
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun sectionIdentitySurvivesReorderAndCategoryMove() = runBlocking {
        val a = repo.upsertCategory(Category(name = "A"))
        val b = repo.upsertCategory(Category(name = "B"))
        val taskId =
            repo.upsertTask(
                Task(
                    categoryId = a,
                    title = "长期事项",
                    sections =
                        listOf(
                            Section(title = "1"),
                            Section(title = "2", completedOn = LocalDate(2026, 1, 1)),
                        ),
                )
            )
        val before = repo.getTaskById(taskId)!!
        repo.moveSection(before.sections.last().id, 0)
        val reordered = repo.getTaskById(taskId)!!
        assertEquals(before.sections.reversed().map { it.id }, reordered.sections.map { it.id })
        repo.upsertTask(reordered.copy(categoryId = b))
        val moved = repo.getTaskById(taskId)!!
        assertEquals(b, moved.categoryId)
        assertEquals(0, moved.orderIndex)
        assertEquals(reordered.sections.map { it.id }, moved.sections.map { it.id })
        assertFalse(moved.isCompleted)
        assertEquals(taskId, scheduled.last().id)
    }

    @Test
    fun deletingAndMovingItemsKeepsOrdersContiguous() = runBlocking {
        val c = repo.upsertCategory(Category(name = "A"))
        val ids = (1..8).map { repo.upsertTask(Task(categoryId = c, title = "$it")) }
        repo.moveTask(ids.last(), 1)
        assertEquals(
            listOf(ids[0], ids[7]) + ids.subList(1, 7),
            repo.getTasks().filter { it.categoryId == c }.map { it.id },
        )
        repo.deleteTask(repo.getTaskById(ids[3])!!)
        assertEquals((0..6).toList(), repo.getTasks().map { it.orderIndex })
        repo.deleteCategory(repo.getCategories().single())
        assertTrue(repo.getTasks().isEmpty())
        assertTrue(db.sectionDao().getInTask(ids[0]).isEmpty())
    }

    @Test
    fun invalidImportPreservesDataAndValidImportRoundTrips() = runBlocking {
        val c = repo.upsertCategory(Category(name = "A"))
        repo.upsertTask(
            Task(categoryId = c, title = "测试 100%", sections = listOf(Section(content = "正文")))
        )
        val snapshot = repo.exportSnapshot()
        try {
            repo.replaceData(
                snapshot.copy(tasks = snapshot.tasks.map { it.copy(categoryId = 999) })
            )
            fail("Should reject")
        } catch (_: IllegalArgumentException) {}
        assertEquals(snapshot.tasks, repo.getTasks())
        repo.replaceData(RhineBackup.decode(RhineBackup.json.encodeToString(snapshot)))
        assertEquals(snapshot.tasks, repo.getTasks())
        assertEquals(1, repo.searchTasks("100%").size)
        assertEquals(1, repo.getTaskSummariesFlow(c, 0, 10).first().size)
        assertEquals(1, repo.getCategoryCountsFlow().first().single().count)
        val taskId = snapshot.tasks.single().id
        assertEquals(1, repo.getSectionSummariesFlow(taskId, 0, 10).first().size)
    }

    @Test
    fun rejectedFullTaskSaveDoesNotPartiallyDeleteSections() = runBlocking {
        val c = repo.upsertCategory(Category(name = "A"))
        val id =
            repo.upsertTask(
                Task(categoryId = c, title = "任务", sections = listOf(Section(title = "记录")))
            )
        val old = repo.getTaskById(id)!!
        try {
            repo.upsertTask(old.copy(sections = listOf(Section(id = 999, title = "非法标识"))))
            fail("Should reject")
        } catch (_: IllegalArgumentException) {}
        assertEquals(old, repo.getTaskById(id))
    }
}

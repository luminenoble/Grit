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
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import com.shub39.grit.core.data.notification.GritNotificationManager
import com.shub39.grit.core.interfaces.AlarmScheduler
import com.shub39.grit.core.tasks.*
import com.shub39.grit.shared.ui.rhine.*
import com.shub39.grit.tasks.data.database.TaskDatabase
import com.shub39.grit.tasks.data.repository.TasksRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.*
import org.junit.Assert.*

class RhineMutationRepositoryTest {
    private lateinit var db: TaskDatabase
    private lateinit var repo: TasksRepository

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder<TaskDatabase>(context).build()
        db.withWriteTransaction {}
        repo =
            TasksRepository(
                context,
                db,
                object : AlarmScheduler {
                    override fun schedule(task: Task) {}

                    override fun cancel(task: Task) {}

                    override fun cancelAll() {}
                },
                GritNotificationManager(context),
            )
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun distantAppendReturnsOnlyOneRealItemAndPreservesBodies() = runBlocking {
        val a = repo.upsertCategory(Category(name = "A"))
        val b = repo.upsertCategory(Category(name = "B"))
        repeat(100) { repo.upsertTask(Task(categoryId = a, title = "A$it")) }
        val old =
            repo.upsertTask(
                Task(
                    categoryId = b,
                    title = "B1",
                    description = "long".repeat(2000),
                    titleColor = "#426E79",
                    isToday = true,
                    reminder = LocalDateTime(2030, 1, 1, 9, 30),
                    sections =
                        listOf(
                            Section(
                                content = "body".repeat(3000),
                                completedOn = LocalDate(2026, 1, 2),
                            )
                        ),
                )
            )
        val unchanged = repo.getTaskById(old)!!
        val created = repo.upsertTask(Task(categoryId = b, title = "B2", orderIndex = 99))
        val source = RepositoryRhineDataSource(repo)
        val (catalog, located) = source.createdSnapshot(created, false)
        assertEquals(RhineCamera(2f, 1f), located.camera)
        assertEquals(created, located.focus.itemId)
        assertEquals(102, repo.getTasks().size)
        assertEquals(2, catalog.counts.first { it.categoryId == b }.count)
        assertEquals(
            listOf(0, 1),
            repo.getTaskSummariesFlow(b, 0, 500).first().map { it.orderIndex },
        )
        assertEquals(unchanged, repo.getTaskById(old))
        assertEquals(102, repo.getTasksFlow().first().values.sumOf { it.size })
    }

    @Test
    fun sectionAppendDeleteSuccessorTailEmptyAndParentCascade() = runBlocking {
        val c = repo.upsertCategory(Category(name = "C"))
        val a =
            repo.upsertTask(
                Task(
                    categoryId = c,
                    title = "long",
                    sections = (1..100).map { Section(title = "S$it") },
                )
            )
        val b = repo.upsertTask(Task(categoryId = c, title = "short", sections = listOf(Section())))
        val source = SectionRhineDataSource(repo, c)
        val id = repo.upsertSection(Section(taskId = b, orderIndex = 99))
        assertEquals(RhineCamera(2f, 1f), source.createdSnapshot(id, false).second.camera)
        assertEquals(listOf(0, 1), repo.getTaskById(b)!!.sections.map { it.orderIndex })
        assertEquals("unname", repo.getSectionById(id)!!.title)
        assertNull(repo.getSectionById(id)!!.completedOn)
        val third = repo.upsertSection(Section(taskId = b, title = "third"))
        val before = RhineSnapshot(RhineCamera(2f, 1f), RhineFocus(b, id, 2, 1))
        repo.deleteSection(id)
        assertEquals(third, source.deletedSnapshot(before, false).second.focus.itemId)
        repo.deleteSection(third)
        assertEquals(RhineCamera(1f, 1f), source.deletedSnapshot(before, false).second.camera)
        repo.deleteSection(repo.getTaskById(b)!!.sections.single().id)
        assertEquals(RhineCamera(0f, 1f), source.deletedSnapshot(before, false).second.camera)
        repo.deleteTask(repo.getTaskById(a)!!)
        assertEquals(1, source.catalog().first().bounds.maxU)
        assertEquals(listOf(0), repo.getTasks().map { it.orderIndex })
        repo.deleteCategory(repo.getCategories().single())
        assertTrue(repo.getTasks().isEmpty())
        assertNull(repo.getSectionById(third))
        assertEquals(
            RhineCamera(0f, 0f),
            RepositoryRhineDataSource(repo).deletedSnapshot(before, true).second.camera,
        )
    }

    @Test
    fun failedCreatesLeaveNoPartialDataOrEpochChangeAndReusedImportChangesEpoch() = runBlocking {
        val c = repo.upsertCategory(Category(name = "C"))
        val t = repo.upsertTask(Task(categoryId = c, title = "T"))
        val before = repo.exportSnapshot()
        val generation = repo.dataGeneration().first()
        try {
            repo.upsertTask(
                Task(
                    categoryId = c,
                    title = "bad",
                    sections = listOf(Section(completedOn = LocalDate(2099, 1, 1))),
                )
            )
            fail()
        } catch (_: IllegalArgumentException) {}
        try {
            repo.upsertSection(Section(taskId = t, completedOn = LocalDate(2099, 1, 1)))
            fail()
        } catch (_: IllegalArgumentException) {}
        try {
            repo.upsertTask(Task(categoryId = 999, title = "bad"))
            fail()
        } catch (_: IllegalArgumentException) {}
        assertEquals(before.tasks, repo.getTasks())
        assertEquals(before.categories, repo.getCategories())
        assertEquals(generation, repo.dataGeneration().first())
        repo.replaceData(before)
        assertNotEquals(generation, repo.dataGeneration().first())
        assertEquals(before.tasks, repo.getTasks())
        assertEquals(before.categories, repo.getCategories())
    }

    @Test
    fun deletingCategoryKeepsContinuousOrderAndNewParentHasZeroFocus() = runBlocking {
        val ids = (1..3).map { repo.upsertCategory(Category(name = "C$it")) }
        val source = RepositoryRhineDataSource(repo)
        val created = source.createdSnapshot(ids.last(), true).second
        assertEquals(RhineCamera(0f, 2f), created.camera)
        val before = RhineSnapshot(RhineCamera(0f, 1f), RhineFocus(ids[1], null, 0, 1))
        repo.deleteCategory(repo.getCategories()[1])
        assertEquals(ids[2], source.deletedSnapshot(before, true).second.focus.categoryId)
        assertEquals(listOf(0, 1), repo.getCategories().map { it.orderIndex })
    }
}

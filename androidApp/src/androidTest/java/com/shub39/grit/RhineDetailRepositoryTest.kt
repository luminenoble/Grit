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

class RhineDetailRepositoryTest {
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
    fun siblingsCountsColorsDatesAndEmptyTaskStayInCategory() = runBlocking {
        val a = repo.upsertCategory(Category(name = "A", hideCompleted = true))
        val b = repo.upsertCategory(Category(name = "B"))
        val long =
            repo.upsertTask(
                Task(
                    categoryId = a,
                    title = "long",
                    titleColor = "#426E79",
                    isCompleted = true,
                    sections =
                        (1..12).map {
                            Section(
                                title = "S$it",
                                content = "长正文".repeat(1000),
                                completedOn = if (it % 3 == 0) LocalDate(2026, 1, 2) else null,
                                titleColor = if (it == 9) "#C0392B,#1E6FD9" else null,
                            )
                        },
                )
            )
        val empty = repo.upsertTask(Task(categoryId = a, title = "empty"))
        repo.upsertTask(Task(categoryId = b, title = "must not leak", sections = listOf(Section())))
        val source = SectionRhineDataSource(repo, a)
        val catalog = RhineCatalog(source.categories().first(), source.counts().first())
        val columns = source.window(catalog, SlotWindow(8, 12, 0, 2)).first()
        assertEquals(listOf(long, empty), columns.map { it.id })
        assertEquals(13, catalog.bounds.maxU)
        assertEquals(2, catalog.bounds.maxV)
        assertEquals(4, columns[0].completedCount)
        // The column is the Task in its colour; each Section block has its own, default none.
        assertEquals("#426E79", columns[0].titleColor)
        assertEquals("#C0392B,#1E6FD9", columns[0].items[9]!!.titleColor)
        assertNull(columns[0].items[8]!!.titleColor)
        assertEquals(LocalDate(2026, 1, 2), columns[0].items[9]!!.completedOn)
        assertTrue(columns[1].items.isEmpty())
        assertEquals(3, repo.getTasks().size)
    }

    @Test
    fun sectionSaveKeepsIdentityBodyAndIndependentTaskCompletion() = runBlocking {
        val c = repo.upsertCategory(Category(name = "A"))
        val id =
            repo.upsertTask(
                Task(
                    categoryId = c,
                    title = "T",
                    description = "任务正文".repeat(300),
                    sections = listOf(Section(title = "old", content = "Section 正文".repeat(400))),
                )
            )
        val before = repo.getTaskById(id)!!
        val section = repo.getSectionById(before.sections.single().id)!!
        val changed = section.copy(title = "new", completedOn = LocalDate(2026, 1, 2))
        assertEquals(section.id, repo.upsertSection(changed))
        val after = repo.getTasksFlow().first().values.flatten().single()
        assertEquals(before.description, after.description)
        assertEquals(section.id, after.sections.single().id)
        assertEquals(section.content, after.sections.single().content)
        assertFalse(after.isCompleted)
        repo.upsertTask(
            after.copy(
                isCompleted = true,
                isToday = true,
                titleColor = "#426E79",
                reminder = LocalDateTime(2030, 1, 1, 9, 30),
            )
        )
        assertEquals(changed.completedOn, repo.getSectionById(section.id)!!.completedOn)
        assertEquals(
            changed.completedOn,
            repo.getSectionSummaryFlow(section.id).first()!!.completedOn,
        )
        repo.upsertSection(repo.getSectionById(section.id)!!.copy(completedOn = null))
        assertTrue(repo.getTaskById(id)!!.isCompleted)
        assertNull(repo.getSectionById(section.id)!!.completedOn)
    }

    @Test
    fun invalidDatesAndFailedWholeTaskSaveLeaveAllDataUntouched() = runBlocking {
        val c =
            repo.upsertCategory(Category(name = "A", description = "说明", titleColor = "#426E79"))
        val id =
            repo.upsertTask(
                Task(
                    categoryId = c,
                    title = "T",
                    sections = listOf(Section(content = "keep"), Section(content = "also keep")),
                )
            )
        val before = repo.getTaskById(id)!!
        try {
            repo.upsertSection(
                before.sections[0].copy(content = "bad", completedOn = LocalDate(2099, 1, 1))
            )
            fail("Future date")
        } catch (_: IllegalArgumentException) {}
        assertEquals(before, repo.getTaskById(id))
        try {
            repo.upsertTask(
                before.copy(
                    title = "must roll back",
                    sections = listOf(before.sections[0].copy(completedOn = LocalDate(2099, 1, 1))),
                )
            )
            fail("Future date in transaction")
        } catch (_: IllegalArgumentException) {}
        assertEquals(before, repo.getTaskById(id))
        val category = repo.getCategories().single()
        try {
            repo.upsertCategory(category.copy(titleColor = "bad"))
            fail("Invalid color")
        } catch (_: IllegalArgumentException) {}
        assertEquals(category, repo.getCategories().single())
    }
}

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

class RhineAdjustRepositoryTest {
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
    fun taskAndSectionEightToTwoPreserveEveryBusinessField() = runBlocking {
        val c = repo.upsertCategory(Category(name = "C"))
        val ids =
            (1..8).map {
                repo.upsertTask(
                    Task(
                        categoryId = c,
                        title = "T$it",
                        description = "long".repeat(2000),
                        titleColor = "#426E79",
                        isToday = true,
                        isCompleted = it == 8,
                        reminder = LocalDateTime(2030, 1, 1, 9, 30),
                        sections =
                            (1..8).map {
                                Section(
                                    title = "S$it",
                                    content = "body".repeat(2000),
                                    completedOn = LocalDate(2026, 1, 2),
                                )
                            },
                    )
                )
            }
        val original = repo.getTaskById(ids.last())!!
        val expected = repo.readMoveScope(MoveKind.Task, c)
        repo.moveTask(ids.last(), 1, expected)
        assertEquals(
            listOf(ids[0], ids[7]) + ids.subList(1, 7),
            repo.readMoveScope(MoveKind.Task, c).ids,
        )
        val moved = repo.getTaskById(ids.last())!!
        assertEquals(original.copy(orderIndex = 1, updatedAt = moved.updatedAt), moved)
        assertEquals(
            (0..7).toList(),
            repo.getTaskSummariesFlow(c, 0, 50).first().map { it.orderIndex },
        )
        val section = original.sections.last()
        repo.moveSection(section.id, 1, repo.readMoveScope(MoveKind.Section, original.id))
        val after = repo.getTaskById(original.id)!!
        assertEquals(
            listOf(original.sections[0].id, section.id) +
                original.sections.subList(1, 7).map { it.id },
            after.sections.map { it.id },
        )
        for (old in original.sections) {
            val current = after.sections.first { it.id == old.id }
            assertEquals(
                old.copy(orderIndex = current.orderIndex, updatedAt = current.updatedAt),
                current,
            )
        }
        assertTrue(after.isCompleted)
        assertEquals(
            after,
            repo.getTasksFlow().first().values.flatten().first { it.id == after.id },
        )
    }

    @Test
    fun reverseAdjacentEndsSingleAndEmptyParentOrdering() = runBlocking {
        val cs = (1..8).map { repo.upsertCategory(Category(name = "C$it")) }
        repo.moveCategory(cs[7], 1, repo.readMoveScope(MoveKind.Category, null))
        assertEquals(listOf(cs[0], cs[7]) + cs.subList(1, 7), repo.getCategories().map { it.id })
        repo.moveCategory(cs[7], 7)
        repo.moveCategory(cs[0], 1)
        repo.moveCategory(cs[0], 0)
        assertEquals(cs, repo.getCategories().map { it.id })
        assertEquals((0..7).toList(), repo.getCategories().map { it.orderIndex })
        val t = repo.upsertTask(Task(categoryId = cs[0], title = "single"))
        repo.moveTask(t, 0, repo.readMoveScope(MoveKind.Task, cs[0]))
        assertEquals(listOf(t), repo.readMoveScope(MoveKind.Task, cs[0]).ids)
        assertTrue(repo.readMoveScope(MoveKind.Section, t).ids.isEmpty())
    }

    @Test
    fun staleParentOrderCountImportAndAncestorAreRejectedAtomically() = runBlocking {
        val c = repo.upsertCategory(Category(name = "C"))
        val d = repo.upsertCategory(Category(name = "D"))
        val ids =
            (1..8).map {
                repo.upsertTask(Task(categoryId = c, title = "T$it", sections = listOf(Section())))
            }
        suspend fun rejected(state: MoveScope, id: Long) {
            val before = repo.exportSnapshot()
            try {
                when (state.kind) {
                    MoveKind.Category -> repo.moveCategory(id, 1, state)
                    MoveKind.Task -> repo.moveTask(id, 1, state)
                    MoveKind.Section -> repo.moveSection(id, 0, state)
                }
                fail()
            } catch (_: IllegalArgumentException) {}
            assertEquals(before.tasks, repo.getTasks())
            assertEquals(before.categories, repo.getCategories())
        }
        val start = repo.readMoveScope(MoveKind.Task, c)
        repo.moveTask(ids[0], 3)
        rejected(start, ids[7])
        val beforeMove = repo.readMoveScope(MoveKind.Task, c)
        val beforeAncestor = repo.readMoveScope(MoveKind.Section, ids[7])
        val sectionId = repo.getTaskById(ids[7])!!.sections.single().id
        repo.upsertTask(repo.getTaskById(ids[7])!!.copy(categoryId = d))
        rejected(beforeMove, ids[7])
        rejected(beforeAncestor, sectionId)
        val beforeDelete = repo.readMoveScope(MoveKind.Task, c)
        repo.deleteTask(repo.getTaskById(ids[1])!!)
        rejected(beforeDelete, ids[2])
        val beforeImport = repo.readMoveScope(MoveKind.Task, c)
        repo.replaceData(repo.exportSnapshot())
        rejected(beforeImport, ids[2])
        val before = repo.exportSnapshot()
        try {
            repo.moveTask(ids[2], 999, repo.readMoveScope(MoveKind.Task, c))
            fail()
        } catch (_: IllegalArgumentException) {}
        assertEquals(before.tasks, repo.getTasks())
    }

    @Test
    fun titleSearchEscapesIncludesCompletedAndPagesInParentOrder() = runBlocking {
        val a = repo.upsertCategory(Category(name = "CategoryOnly", hideCompleted = true))
        val b = repo.upsertCategory(Category(name = "B"))
        repeat(60) {
            repo.upsertTask(
                Task(
                    categoryId = a,
                    title = "Alpha 设计 $it",
                    isCompleted = it % 2 == 0,
                    sections = listOf(Section(title = "SectionOnly", content = "BodyOnly")),
                )
            )
        }
        val duplicate =
            repo.upsertTask(Task(categoryId = b, title = "Alpha 设计 0", isCompleted = true))
        val literal = repo.upsertTask(Task(categoryId = b, title = "literal %_\\"))
        assertEquals(50, repo.searchTasks(" ALPHA ", 0, 50).size)
        assertEquals(11, repo.searchTasks("alpha", 50, 50).size)
        assertEquals(duplicate, repo.searchTasks("设计", 50, 50).last().id)
        assertTrue(repo.searchTasks("alpha", 0, 50).any { it.isCompleted })
        for (q in listOf("%", "_", "\\")) assertEquals(
            listOf(literal),
            repo.searchTasks(q).map { it.id },
        )
        for (q in listOf("CategoryOnly", "SectionOnly", "BodyOnly", "", "missing")) assertTrue(
            repo.searchTasks(q).isEmpty()
        )
        for (pair in listOf(-1 to 50, 0 to 0, 0 to 501)) try {
            repo.searchTasks("alpha", pair.first, pair.second)
            fail()
        } catch (_: IllegalArgumentException) {}
        repo.moveTask(duplicate, 0)
        val result = RepositoryRhineDataSource(repo).createdSnapshot(duplicate, false).second
        assertEquals(duplicate, result.focus.itemId)
        assertEquals(RhineCamera(1f, 1f), result.camera)
    }

    @Test
    fun dedicatedDiskDatabaseRetainsOrderAfterReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = java.io.File(context.cacheDir, "rhine05-${java.util.UUID.randomUUID()}.db")
        fun open() = Room.databaseBuilder<TaskDatabase>(context, file.absolutePath).build()
        fun repository(database: TaskDatabase) =
            TasksRepository(
                context,
                database,
                object : AlarmScheduler {
                    override fun schedule(task: Task) {}

                    override fun cancel(task: Task) {}

                    override fun cancelAll() {}
                },
                GritNotificationManager(context),
            )
        var disk = open()
        try {
            var r = repository(disk)
            val c = r.upsertCategory(Category(name = "isolated"))
            val ids = (1..8).map { r.upsertTask(Task(categoryId = c, title = "T$it")) }
            r.moveTask(ids[7], 1, r.readMoveScope(MoveKind.Task, c))
            disk.close()
            disk = open()
            r = repository(disk)
            assertEquals(
                listOf(ids[0], ids[7]) + ids.subList(1, 7),
                r.readMoveScope(MoveKind.Task, c).ids,
            )
        } finally {
            disk.close()
            file.delete()
            java.io.File(file.path + "-wal").delete()
            java.io.File(file.path + "-shm").delete()
        }
    }
}

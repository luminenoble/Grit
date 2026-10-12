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
import com.shub39.grit.core.tasks.*
import com.shub39.grit.shared.ui.rhine.*
import com.shub39.grit.tasks.data.database.*
import com.shub39.grit.tasks.data.toCategory
import kotlin.time.Clock
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RhineBrowseRepositoryTest {
    @Test
    fun emptyMemoryDatabaseStartsReadOnlyFlowsWithoutCreatingObjects() = runBlocking {
        val db =
            Room.inMemoryDatabaseBuilder<TaskDatabase>(
                    ApplicationProvider.getApplicationContext<Context>()
                )
                .build()
        try {
            // Opening the writer initializes Room's in-memory schema before parallel subscriptions.
            db.withWriteTransaction {}
            val counts =
                combine(db.categoryDao().getCategoriesFlow(), db.taskDao().counts()) {
                        categories,
                        counts ->
                        assertTrue(categories.isEmpty())
                        counts
                    }
                    .first()
            assertTrue(counts.isEmpty())
            assertEquals(RhineBounds(0, 0), RhineBounds.from(counts.map { it.count }))
            assertTrue(db.categoryDao().getCategories().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun freshIsolatedDiskDatabaseCanBeReadBeforeAnyInsert() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file =
            java.io.File(
                context.cacheDir,
                "rhine-stage02-empty-" + java.util.UUID.randomUUID() + ".db",
            )
        val db = Room.databaseBuilder<TaskDatabase>(context, file.absolutePath).build()
        try {
            val result =
                combine(db.categoryDao().getCategoriesFlow(), db.taskDao().counts()) {
                        categories,
                        counts ->
                        categories.size + counts.size
                    }
                    .first()
            assertEquals(0, result)
        } finally {
            db.close()
            context.deleteDatabase(file.absolutePath)
        }
    }

    @Test
    fun unevenWindowDoesNotCreateRowsAndCountsIncludeCompletedHiddenTasks() = runBlocking {
        val db =
            Room.inMemoryDatabaseBuilder<TaskDatabase>(
                    ApplicationProvider.getApplicationContext<Context>()
                )
                .build()
        try {
            val now = Clock.System.now()
            for (v in 0..2) {
                val id = v + 1L
                db.categoryDao()
                    .upsert(CategoryEntity(id, "Category $v", "", v, null, true, now, now))
                repeat(if (v == 0) 100 else 1) { i ->
                    db.taskDao()
                        .upsert(
                            TaskEntity(
                                id * 1000 + i,
                                id,
                                "Task ${i + 1}",
                                "large body",
                                i,
                                null,
                                true,
                                false,
                                null,
                                now,
                                now,
                            )
                        )
                }
            }
            val source =
                object : RhineDataSource {
                    override fun categories() =
                        db.categoryDao().getCategoriesFlow().map { cs ->
                            cs.map { it.toCategory() }
                        }

                    override fun counts() = db.taskDao().counts()

                    override fun summaries(categoryId: Long, offset: Int, limit: Int) =
                        db.taskDao().summaries(categoryId, offset, limit)

                    override fun item(id: Long) = db.taskDao().summary(id)
                }
            val catalog = RhineCatalog(source.categories().first(), source.counts().first())
            assertEquals(RhineBounds(101, 3), catalog.bounds)
            val columns = source.window(catalog, SlotWindow(90, 110, 0, 3)).first()
            val input = RhineSceneInput(RhineLayer.Categories, columns, 0, 99f)
            assertEquals("Task 99", input.slot(99, 0).item!!.title)
            assertEquals(RhineBlockKind.Placeholder, input.slot(99, 1).kind)
            assertTrue(columns[1].items.isEmpty())
            assertEquals(100, columns[0].completedCount)
            repeat(10) { source.window(catalog, SlotWindow(98, 105, 0, 3)).first() }
            assertEquals(listOf(100, 1, 1), db.taskDao().counts().first().map { it.count })
            assertEquals(1098L, db.taskDao().summary(1098).first()!!.id)
        } finally {
            db.close()
        }
    }
}

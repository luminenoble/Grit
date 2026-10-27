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
import android.database.sqlite.SQLiteDatabase
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.shub39.grit.core.tasks.IllustrationChange
import com.shub39.grit.core.tasks.IllustrationOwner
import com.shub39.grit.core.tasks.apply
import com.shub39.grit.tasks.data.database.TaskDatabase
import com.shub39.grit.tasks.data.repository.RhineIllustrationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Database v1 → v2 only adds rhine_illustrations, v2 → v3 a nullable Section title colour: stored
 * data survives, and illustrations follow their owners through the cascading keys (docs/视觉优化/03
 * sections 3, 9; docs/视觉优化/06).
 */
class RhineIllustrationMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** A version-1 file exactly as the released schema (schemas/.../1.json) creates it. */
    private fun createV1(name: String) {
        context.deleteDatabase(name)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        listOf(
                "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `titleColor` TEXT, `hideCompleted` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL)",
                "CREATE INDEX IF NOT EXISTS `index_categories_orderIndex` ON `categories` (`orderIndex`)",
                "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `titleColor` TEXT, `isCompleted` INTEGER NOT NULL, `isToday` INTEGER NOT NULL, `reminder` TEXT, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_tasks_categoryId_orderIndex` ON `tasks` (`categoryId`, `orderIndex`)",
                "CREATE TABLE IF NOT EXISTS `sections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskId` INTEGER NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `completedOn` TEXT, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_sections_taskId_orderIndex` ON `sections` (`taskId`, `orderIndex`)",
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7ee6c630a2af411c889ed382e85cba50')",
                "INSERT INTO categories VALUES (1, 'Work', '', 0, '#336699', 0, '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')",
                "INSERT INTO tasks VALUES (1, 1, 'Report', 'body', 0, NULL, 0, 1, NULL, '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')",
                "INSERT INTO sections VALUES (1, 1, 'Draft', 'text', 0, '2026-10-02', '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')",
            )
            .forEach(db::execSQL)
        db.version = 1
        db.close()
    }

    @Test
    fun upgradeAddsSectionColourDefaultingToNone() = runBlocking {
        val name = "rhine-migration-v3-test.db"
        createV1(name)
        val db =
            Room.databaseBuilder<TaskDatabase>(context, context.getDatabasePath(name).absolutePath)
                .build()
        try {
            val section = db.sectionDao().getById(1)!!
            assertEquals("Draft", section.title)
            assertEquals("text", section.content)
            assertNull(section.titleColor)
            db.sectionDao().upsert(section.copy(titleColor = "#C0392B,#1E6FD9"))
            assertEquals("#C0392B,#1E6FD9", db.sectionDao().summaryById(1)!!.titleColor)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun upgradeKeepsDataAndIllustrationsCascade() = runBlocking {
        val name = "rhine-migration-test.db"
        createV1(name)
        val db =
            Room.databaseBuilder<TaskDatabase>(context, context.getDatabasePath(name).absolutePath)
                .build()
        try {
            val category = db.categoryDao().getById(1)!!
            assertEquals("Work", category.name)
            assertEquals("#336699", category.titleColor)
            assertEquals("Report", db.taskDao().getTaskById(1)!!.task.title)

            val repo = RhineIllustrationRepository(context, db)
            repo.apply(IllustrationOwner.Category, 1, IllustrationChange.Set("content://test/1"))
            repo.apply(IllustrationOwner.Task, 1, IllustrationChange.Set("content://test/2"))
            assertEquals("content://test/1", repo.illustration(IllustrationOwner.Category, 1))
            assertEquals(
                mapOf(1L to "content://test/2"),
                repo.illustrationsFlow(IllustrationOwner.Task, setOf(1L)).first(),
            )
            // Replacing keeps one row per owner.
            repo.apply(IllustrationOwner.Category, 1, IllustrationChange.Set("content://test/3"))
            assertEquals("content://test/3", repo.illustration(IllustrationOwner.Category, 1))

            db.categoryDao().delete(category.id)
            assertNull(repo.illustration(IllustrationOwner.Category, 1))
            assertNull(repo.illustration(IllustrationOwner.Task, 1))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}

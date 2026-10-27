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
package com.shub39.grit.tasks.data.database

import androidx.room3.*
import com.shub39.grit.core.data.Converters

@Database(
    entities =
        [CategoryEntity::class, TaskEntity::class, SectionEntity::class, IllustrationEntity::class],
    version = TaskDatabase.SCHEMA_VERSION,
    exportSchema = true,
    // v2 only adds the rhine_illustrations table, v3 a nullable Section title colour; existing rows
    // are untouched.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@ColumnTypeConverters(Converters::class)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TasksDao

    abstract fun categoryDao(): CategoryDao

    abstract fun sectionDao(): SectionDao

    abstract fun illustrationDao(): IllustrationDao

    companion object {
        const val DB_NAME = "grit_rhine_database"
        const val SCHEMA_VERSION = 3
    }
}

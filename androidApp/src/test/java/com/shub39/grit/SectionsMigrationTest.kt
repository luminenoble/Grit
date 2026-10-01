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

import com.shub39.grit.core.data.Converters
import com.shub39.grit.core.data.backup.ExportSchema
import com.shub39.grit.core.data.backup.HabitSchema
import com.shub39.grit.core.data.backup.TaskSchema
import com.shub39.grit.core.tasks.Section
import com.shub39.grit.core.tasks.nextSectionName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SectionsMigrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun legacyDatabaseStringsBecomeNumberedContent() {
        assertEquals(
            listOf(Section("section 1", "read\n中文"), Section("section 2", "")),
            Converters.sectionsFromJson("""["read\n中文", ""]"""),
        )
    }

    @Test
    fun legacyTaskCompletionSurvivesMigration() {
        assertEquals(
            listOf(Section("section 1", "first", true), Section("section 2", "second")),
            Converters.sectionsFromJson("""[{"text":"first","done":true},"second"]"""),
        )
    }

    @Test
    fun legacyTaskBackupUsesStepsAlias() {
        val task =
            json.decodeFromString<TaskSchema>(
                """{"categoryId":1,"title":"task","steps":[{"text":"old content","done":true},"next"]}"""
            )
        assertEquals(
            listOf(Section("section 1", "old content", true), Section("section 2", "next")),
            task.sections,
        )
    }

    @Test
    fun legacyHabitBackupUsesStepsAlias() {
        val habit =
            json.decodeFromString<HabitSchema>(
                """{"title":"habit","description":"","index":0,"time":0,"days":"MONDAY","reminder":false,"steps":["one","two"]}"""
            )
        assertEquals(
            listOf(Section("section 1", "one"), Section("section 2", "two")),
            habit.sections,
        )
    }

    @Test
    fun oldBackupWithoutStepsRemainsEmpty() {
        assertTrue(
            json
                .decodeFromString<TaskSchema>("""{"categoryId":1,"title":"old"}""")
                .sections
                .isEmpty()
        )
    }

    @Test
    fun databaseRoundTripPreservesNamesContentCompletionAndOrder() {
        val sections = listOf(Section("自定义", "long\n\"text\" 🐈", true), Section("section 9"))
        assertEquals(sections, Converters.sectionsFromJson(Converters.sectionsToJson(sections)))
    }

    @Test
    fun exportImportRoundTripPreservesTaskAndHabitSections() {
        val sections = listOf(Section("Custom name", "body\ntext", true), Section("section 2"))
        val backup =
            ExportSchema(
                habits =
                    listOf(
                        HabitSchema(
                            title = "habit",
                            description = "",
                            index = 0,
                            time = 0,
                            days = "",
                            reminder = false,
                            sections = sections,
                        )
                    ),
                habitStatus = emptyList(),
                tasks = listOf(TaskSchema(categoryId = 1, title = "task", sections = sections)),
                categories = emptyList(),
            )
        val encoded = json.encodeToString(backup)
        assertTrue(encoded.contains("\"sections\""))
        assertFalse(encoded.contains("\"steps\""))
        assertEquals(backup, json.decodeFromString<ExportSchema>(encoded))
    }

    @Test
    fun defaultNamesDoNotCollideAfterDeleteAndReorder() {
        assertEquals("section 1", nextSectionName(emptyList()))
        assertEquals(
            "section 5",
            nextSectionName(listOf(Section("section 4"), Section("renamed"), Section("section 1"))),
        )
    }

    @Test
    fun legacyHabitWithoutReminderOrStepsUsesDefaults() {
        val habit =
            json.decodeFromString<HabitSchema>(
                """{"title":"old habit","description":"","index":0,"time":0,"days":"MONDAY"}"""
            )
        assertFalse(habit.reminder)
        assertTrue(habit.sections.isEmpty())
    }

    @Test(expected = SerializationException::class)
    fun malformedDataIsNotSilentlyDiscarded() {
        Converters.sectionsFromJson("""[false]""")
    }
}

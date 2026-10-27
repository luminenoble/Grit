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

import com.shub39.grit.core.now
import com.shub39.grit.core.settings.backup.RhineBackup
import com.shub39.grit.core.tasks.*
import kotlin.time.Instant
import kotlinx.datetime.*
import org.junit.Assert.*
import org.junit.Test

class RhineModelTest {
    private val time = Instant.parse("2026-01-01T00:00:00Z")

    private fun backup(
        sections: List<Section> =
            listOf(
                Section(id = 3, title = "工作", completedOn = LocalDate(2026, 1, 1), createdAt = time)
            )
    ) =
        RhineBackup(
            RhineBackup.FORMAT,
            1,
            time,
            listOf(Category(id = 1, name = "学习", createdAt = time)),
            listOf(
                Task(
                    id = 2,
                    categoryId = 1,
                    title = "项目",
                    titleColor = "#435B64",
                    reminder = LocalDateTime(2027, 1, 1, 8, 0),
                    createdAt = time,
                    sections = sections,
                )
            ),
        )

    @Test
    fun sectionCompletionUsesDateAndDoesNotCompleteTask() {
        val date = LocalDate(2026, 1, 1)
        val completed = Section().toggleCompleted(date)
        assertEquals(date, completed.completedOn)
        assertTrue(completed.isCompleted)
        assertNull(completed.toggleCompleted(date).completedOn)
        assertFalse(Task(categoryId = 1, title = "长期事项", sections = listOf(completed)).isCompleted)
        assertEquals("unname", Section(title = "  ").normalized().title)
    }

    @Test
    fun futureCompletionDatesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Section(completedOn = LocalDate.now().plus(1, DateTimeUnit.DAY)).normalized()
        }
    }

    @Test
    fun backupRoundTripPreservesWorkDatesAndIdentity() {
        val original = backup()
        val encoded = RhineBackup.json.encodeToString(original)
        val decoded = RhineBackup.decode(encoded)
        assertEquals(original.tasks.single().titleColor, decoded.tasks.single().titleColor)
        assertEquals(original.tasks.single().reminder, decoded.tasks.single().reminder)
        assertEquals(LocalDate(2026, 1, 1), decoded.tasks.single().sections.single().completedOn)
        assertEquals(2L, decoded.tasks.single().sections.single().taskId)
        assertFalse(encoded.contains("taskId"))
        assertEquals(original.categories, decoded.categories)
    }

    @Test
    fun titleColoursAreOneToFiveStops() {
        assertEquals(listOf(0x336699), titleColorStops("#336699"))
        assertEquals(
            listOf(0xFF0000, 0x00FF00, 0x0000FF, 0x123456, 0xABCDEF),
            titleColorStops("#FF0000,#00FF00,#0000FF,#123456,#abcdef"),
        )
        for (bad in listOf("", "#12345", "336699", "#FF0000,", "#FF0000, #00FF00")) {
            assertNull(bad, titleColorStops(bad))
            assertThrows(IllegalArgumentException::class.java) { validateTitleColor(bad) }
        }
        // Six stops are one too many.
        assertNull(titleColorStops(List(6) { "#000000" }.joinToString(",")))
        validateTitleColor(null)
    }

    @Test
    fun sectionColoursRoundTripAndOldBackupsStillImport() {
        val coloured =
            backup(
                listOf(
                    Section(id = 3, title = "工作", createdAt = time, titleColor = "#C0392B,#1E6FD9")
                )
            )
        val text = RhineBackup.json.encodeToString(coloured)
        assertEquals(
            "#C0392B,#1E6FD9",
            RhineBackup.decode(text).tasks.single().sections.single().titleColor,
        )
        // Backups written before Sections had a colour carry no such field.
        val old = text.replace(Regex(",\\s*\"titleColor\": \"#C0392B,#1E6FD9\""), "")
        assertFalse(old.contains("#C0392B"))
        assertNull(RhineBackup.decode(old).tasks.single().sections.single().titleColor)
        assertThrows(IllegalArgumentException::class.java) {
            RhineBackup.decode(text.replace("#C0392B,#1E6FD9", "red"))
        }
    }

    @Test
    fun duplicateSectionsAcrossTasksAndOrphanCategoriesAreRejected() {
        val b = backup()
        assertThrows(IllegalArgumentException::class.java) {
            b.copy(tasks = b.tasks + b.tasks.single().copy(id = 4, orderIndex = 1)).validated()
        }
        assertThrows(IllegalArgumentException::class.java) {
            b.copy(categories = emptyList()).validated()
        }
        assertThrows(IllegalArgumentException::class.java) {
            b.copy(tasks = listOf(b.tasks.single().copy(orderIndex = 5))).validated()
        }
        assertThrows(IllegalArgumentException::class.java) { b.copy(formatVersion = 2).validated() }
    }

    @Test
    fun multipleRecordsOnSameDayAndDuplicateTitlesAreAllowed() {
        val b =
            backup(
                listOf(
                    Section(
                        id = 3,
                        title = "重复",
                        completedOn = LocalDate(2026, 1, 1),
                        createdAt = time,
                    ),
                    Section(
                        id = 4,
                        title = "重复",
                        orderIndex = 1,
                        completedOn = LocalDate(2026, 1, 1),
                        createdAt = time,
                    ),
                )
            )
        assertEquals(2, b.validated().tasks.single().sections.size)
    }

    @Test
    fun legacyFormatAndMissingRequiredFieldsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RhineBackup.decode("{\"tasks\":[],\"categories\":[],\"habits\":[]}")
        }
        val text = RhineBackup.json.encodeToString(backup())
        assertThrows(Exception::class.java) {
            RhineBackup.decode(text.replace("\"formatVersion\": 1", "\"formatVersion\": 9"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RhineBackup.decode(text.replace(Regex("\"completedOn\": \"2026-01-01\",?"), ""))
        }
    }

    @Test
    fun movingAnItemInsertsAndShiftsIntermediateItems() {
        val ids = (1L..8L).toList()
        assertEquals(listOf(1L, 8L, 2L, 3L, 4L, 5L, 6L, 7L), movedIds(ids, 8L, 1))
        assertEquals(listOf(1L, 3L, 4L, 5L, 6L, 7L, 8L, 2L), movedIds(ids, 2L, 7))
        assertEquals(ids, movedIds(ids, 1L, 0))
        assertThrows(IllegalArgumentException::class.java) { movedIds(ids, 8L, 8) }
        assertThrows(IllegalArgumentException::class.java) { validateReorder(ids, listOf(1L, 1L)) }
    }
}

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
package com.shub39.grit.core.tasks

import com.shub39.grit.core.now
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Section(
    val id: Long = 0,
    @Transient val taskId: Long = 0,
    val title: String = "unname",
    val content: String = "",
    val orderIndex: Int = 0,
    val completedOn: LocalDate? = null,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = createdAt,
    /** Same format as a Task's title colour; null is the default black. */
    val titleColor: String? = null,
) {
    val isCompleted: Boolean
        get() = completedOn != null

    fun toggleCompleted(today: LocalDate = LocalDate.now()): Section =
        copy(completedOn = if (isCompleted) null else today)

    fun normalized(): Section {
        require(completedOn == null || completedOn <= LocalDate.now()) { "完成日期不能在未来" }
        validateTitleColor(titleColor)
        return copy(title = title.trim().ifBlank { "unname" })
    }
}

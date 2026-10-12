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
package com.shub39.grit.core.data

import androidx.room3.ColumnTypeConverter
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

object Converters {
    @ColumnTypeConverter fun instantToString(value: Instant): String = value.toString()

    @ColumnTypeConverter fun stringToInstant(value: String): Instant = Instant.parse(value)

    @ColumnTypeConverter fun reminderToString(value: LocalDateTime?): String? = value?.toString()

    @ColumnTypeConverter
    fun stringToReminder(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @ColumnTypeConverter fun dateToString(value: LocalDate?): String? = value?.toString()

    @ColumnTypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}

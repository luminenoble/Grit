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
package com.shub39.grit.shared.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.VerticalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.now
import com.shub39.grit.shared.ui.components.PageFill
import com.shub39.grit.shared.ui.habit.HabitState
import com.shub39.grit.shared.ui.habit.ui.component.CalendarMonthHeader
import com.shub39.grit.shared.ui.task.TaskState
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import com.shub39.grit.shared.ui.theme.flexFontRounded
import grit.shared.ui.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource

/**
 * Top-level statistics hub, a peer of Settings in the navigation bar. Lets the user scope the
 * habit heat map to a single habit or all habits, and the task completion summary to a single
 * category or all categories.
 */
@Composable
fun StatsPage(habitState: HabitState, taskState: TaskState, modifier: Modifier = Modifier) =
    PageFill(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.statistics),
                        fontFamily = flexFontEmphasis(),
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { HabitStatsSection(habitState) }
                item { TaskStatsSection(taskState) }
            }
        }
    }

@Composable
private fun HabitStatsSection(state: HabitState) {
    // null = all habits
    var selectedHabitId: Long? by remember { mutableStateOf(null) }

    val habits = state.habitsWithAnalytics
    val selected = habits.firstOrNull { it.habit.id == selectedHabitId }

    val heatMap: Map<LocalDate, Int>
    val maxCount: Int
    if (selected == null) {
        heatMap = state.overallAnalytics.heatMapData
        maxCount = habits.size.coerceAtLeast(1)
    } else {
        heatMap =
            selected.statuses.filter { !it.skipped }.groupingBy { it.date }.eachCount()
        maxCount = 1
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.habits_stats),
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = flexFontRounded()),
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToggleButton(
                checked = selectedHabitId == null,
                onCheckedChange = { selectedHabitId = null },
            ) {
                Text(text = stringResource(Res.string.all_habits))
            }

            habits.forEach { hwa ->
                ToggleButton(
                    checked = selectedHabitId == hwa.habit.id,
                    onCheckedChange = { selectedHabitId = hwa.habit.id },
                ) {
                    Text(text = hwa.habit.title, maxLines = 1)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selected == null) {
                StatTile(
                    label = stringResource(Res.string.consistency),
                    value = "${(state.overallAnalytics.consistency * 100).toInt()}%",
                    modifier = Modifier.weight(1f),
                )
            } else {
                StatTile(
                    label = stringResource(Res.string.streak),
                    value = selected.currentStreak.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(Res.string.consistency),
                    value = "${(selected.consistency * 100).toInt()}%",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        HeatMapCalendar(
            heatMapData = heatMap,
            maxCount = maxCount,
            startingDay = state.startingDay,
        )
    }
}

@Composable
private fun TaskStatsSection(state: TaskState) {
    // null = all categories
    var selectedCategoryId: Long? by remember { mutableStateOf(null) }

    val categories = state.tasks.keys.toList()
    val tasks =
        if (selectedCategoryId == null) state.tasks.values.flatten()
        else state.tasks.entries.firstOrNull { it.key.id == selectedCategoryId }?.value.orEmpty()

    val total = tasks.size
    val completed = tasks.count { it.status }
    val rate = if (total > 0) completed.toFloat() / total else 0f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.tasks_stats),
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = flexFontRounded()),
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToggleButton(
                checked = selectedCategoryId == null,
                onCheckedChange = { selectedCategoryId = null },
            ) {
                Text(text = stringResource(Res.string.all_categories))
            }

            categories.forEach { category ->
                ToggleButton(
                    checked = selectedCategoryId == category.id,
                    onCheckedChange = { selectedCategoryId = category.id },
                ) {
                    Text(text = category.name, maxLines = 1)
                }
            }
        }

        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.completion_rate),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(rate * 100).toInt()}%",
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontFamily = flexFontRounded()
                            ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                LinearProgressIndicator(
                    progress = { rate },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )

                Text(
                    text = stringResource(Res.string.completed_of_total, completed, total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = flexFontRounded()),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HeatMapCalendar(
    heatMapData: Map<LocalDate, Int>,
    maxCount: Int,
    startingDay: kotlinx.datetime.DayOfWeek,
) {
    val today = LocalDate.now()
    val calendarState =
        rememberCalendarState(
            startMonth = YearMonth(year = 2024, month = Month.JANUARY),
            endMonth = YearMonth.now(),
            firstVisibleMonth = YearMonth.now(),
            firstDayOfWeek = startingDay,
        )

    VerticalCalendar(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        state = calendarState,
        monthHeader = { calendarMonth -> CalendarMonthHeader(calendarMonth = calendarMonth) },
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        reverseLayout = true,
        dayContent = { day ->
            if (day.date > today || day.position != DayPosition.MonthDate) return@VerticalCalendar
            val count = heatMapData[day.date]

            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(1.dp)
                        .background(
                            shape = RoundedCornerShape(8.dp),
                            color =
                                when (count) {
                                    null,
                                    0 -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    else ->
                                        MaterialTheme.colorScheme.primary.copy(
                                            alpha =
                                                (count.toFloat() / maxCount).coerceIn(0.15f, 1f)
                                        )
                                },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.date.day.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = flexFontRounded(),
                    color =
                        if (count != null && count > 0 && count.toFloat() / maxCount > 0.5f)
                            MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}

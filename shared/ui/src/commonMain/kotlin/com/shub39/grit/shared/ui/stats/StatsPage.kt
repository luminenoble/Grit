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

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.heatmapcalendar.rememberHeatMapCalendarState
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.minusMonths
import com.kizitonwose.calendar.core.now
import com.shub39.grit.shared.ui.components.PageFill
import com.shub39.grit.shared.ui.habit.HabitState
import com.shub39.grit.shared.ui.habit.HabitsAction
import com.shub39.grit.shared.ui.habit.ui.component.stats.CalendarMap
import com.shub39.grit.shared.ui.habit.ui.component.stats.StartStats
import com.shub39.grit.shared.ui.habit.ui.component.stats.WeekDayBreakdown
import com.shub39.grit.shared.ui.habit.ui.component.stats.WeeklyActivity
import com.shub39.grit.shared.ui.habit.ui.component.stats.WeeklyBooleanHeatMap
import com.shub39.grit.shared.ui.task.TaskState
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import com.shub39.grit.shared.ui.theme.flexFontRounded
import grit.shared.ui.generated.resources.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource

/**
 * Top-level statistics hub, a peer of Settings in the navigation bar. Two click-switched tabs:
 * a habit tab (scoped to a single habit or all habits, reusing the habit analytics widgets) and a
 * task tab (completion rate scoped to a single category or all categories).
 */
@Composable
fun StatsPage(
    habitState: HabitState,
    taskState: TaskState,
    isUserSubscribed: Boolean,
    onNavigateToPaywall: () -> Unit,
    onHabitAction: (HabitsAction) -> Unit,
    modifier: Modifier = Modifier,
) =
    PageFill(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        var tab by rememberSaveable { mutableStateOf(0) }

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

            // Click-switched segmented control (no swipe).
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement =
                    Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                ToggleButton(
                    checked = tab == 0,
                    onCheckedChange = { tab = 0 },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(Res.string.habits_stats))
                }
                ToggleButton(
                    checked = tab == 1,
                    onCheckedChange = { tab = 1 },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(Res.string.tasks_stats))
                }
            }

            AnimatedContent(targetState = tab, modifier = Modifier.fillMaxSize()) { current ->
                if (current == 0) {
                    HabitStatsTab(
                        state = habitState,
                        isUserSubscribed = isUserSubscribed,
                        onNavigateToPaywall = onNavigateToPaywall,
                        onAction = onHabitAction,
                    )
                } else {
                    TaskStatsTab(state = taskState)
                }
            }
        }
    }

@Composable
private fun HabitStatsTab(
    state: HabitState,
    isUserSubscribed: Boolean,
    onNavigateToPaywall: () -> Unit,
    onAction: (HabitsAction) -> Unit,
) {
    var selectedHabitId: Long? by rememberSaveable { mutableStateOf(null) }

    val habits = state.habitsWithAnalytics
    val selected = habits.firstOrNull { it.habit.id == selectedHabitId }

    val currentMonth = remember { YearMonth.now() }
    val heatMapState =
        rememberHeatMapCalendarState(
            startMonth = currentMonth.minusMonths(12),
            endMonth = currentMonth,
            firstVisibleMonth = currentMonth,
            firstDayOfWeek = state.startingDay,
        )
    val calendarState =
        rememberCalendarState(
            startMonth = currentMonth.minusMonths(12),
            endMonth = currentMonth,
            firstVisibleMonth = currentMonth,
            firstDayOfWeek = state.startingDay,
        )

    // Single habit → that habit's marks; all habits → every completion combined.
    val statuses =
        selected?.statuses?.filter { !it.skipped }
            ?: habits.flatMap { it.statuses }.filter { !it.skipped }
    val days = selected?.habit?.days ?: DayOfWeek.entries.toSet()
    val onDateClick: (LocalDate) -> Unit =
        if (selected != null) { date -> onAction(HabitsAction.InsertStatus(selected.habit, date)) }
        else { _ -> }

    val maxWidth = 380.dp
    LazyVerticalStaggeredGrid(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        columns = StaggeredGridCells.Adaptive(minSize = maxWidth),
        contentPadding = PaddingValues(top = 8.dp, bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 16.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
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
        }

        if (selected != null) {
            item {
                StartStats(
                    consistency = selected.consistency,
                    startDate = selected.habit.time.date,
                    bestStreak = selected.bestStreak,
                    currentStreak = selected.currentStreak,
                )
            }
        } else {
            item {
                ConsistencyCard(consistency = state.overallAnalytics.consistency)
            }
        }

        item {
            WeeklyBooleanHeatMap(
                heatMapState = heatMapState,
                days = days,
                statuses = statuses,
                onDateClick = onDateClick,
            )
        }

        item {
            CalendarMap(
                canSeeContent = isUserSubscribed,
                calendarState = calendarState,
                statuses = statuses,
                days = days,
                onNavigateToPaywall = onNavigateToPaywall,
                onNavigateToCalendar = {},
                onDateClick = onDateClick,
            )
        }

        if (selected != null) {
            item {
                WeeklyActivity(
                    lineChartData = selected.weeklyComparisonData,
                    modifier = Modifier.widthIn(max = maxWidth),
                )
            }
        }

        item {
            WeekDayBreakdown(
                canSeeContent = isUserSubscribed,
                weekDayData =
                    selected?.weekDayFrequencyData ?: state.overallAnalytics.weekDayFrequencyData,
                onNavigateToPaywall = onNavigateToPaywall,
                modifier = Modifier.widthIn(max = maxWidth),
            )
        }
    }
}

@Composable
private fun TaskStatsTab(state: TaskState) {
    var selectedCategoryId: Long? by rememberSaveable { mutableStateOf(null) }

    val categories = state.tasks.keys.toList()
    val tasks =
        if (selectedCategoryId == null) state.tasks.values.flatten()
        else state.tasks.entries.firstOrNull { it.key.id == selectedCategoryId }?.value.orEmpty()

    val total = tasks.size
    val completed = tasks.count { it.status }
    val rate = if (total > 0) completed.toFloat() / total else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
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
        }

        item {
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

        // Per-category breakdown when viewing all categories.
        if (selectedCategoryId == null && categories.isNotEmpty()) {
            items(categories, key = { it.id }) { category ->
                val catTasks = state.tasks[category].orEmpty()
                val catTotal = catTasks.size
                val catDone = catTasks.count { it.status }
                val catRate = if (catTotal > 0) catDone.toFloat() / catTotal else 0f

                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text =
                                    stringResource(
                                        Res.string.completed_of_total,
                                        catDone,
                                        catTotal,
                                    ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { catRate },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsistencyCard(consistency: Float) {
    Card(
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${(consistency * 100).toInt()}%",
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = flexFontRounded()),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.consistency),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

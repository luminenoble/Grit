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
package com.shub39.grit.shared.ui.habit.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.heatmapcalendar.rememberHeatMapCalendarState
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.minusMonths
import com.kizitonwose.calendar.core.now
import com.shub39.grit.shared.ui.LocalWindowSizeClass
import com.shub39.grit.shared.ui.components.GritDialog
import com.shub39.grit.shared.ui.habit.HabitState
import com.shub39.grit.shared.ui.habit.HabitsAction
import com.shub39.grit.shared.ui.habit.ui.component.HabitUpsertSheet
import com.shub39.grit.shared.ui.habit.ui.component.stats.CalendarMap
import com.shub39.grit.shared.ui.habit.ui.component.stats.StartStats
import com.shub39.grit.shared.ui.habit.ui.component.stats.WeekDayBreakdown
import com.shub39.grit.shared.ui.habit.ui.component.stats.WeeklyActivity
import com.shub39.grit.shared.ui.habit.ui.component.stats.WeeklyBooleanHeatMap
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import com.shub39.grit.shared.ui.theme.flexFontRounded
import grit.shared.ui.generated.resources.*
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun AnalyticsPage(
    state: HabitState,
    onAction: (HabitsAction) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    isUserSubscribed: Boolean,
    onEditHabit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSizeClass = LocalWindowSizeClass.current

    val currentMonth = remember { YearMonth.now() }
    val currentHabit =
        state.habitsWithAnalytics.find { it.habit.id == state.analyticsHabitId } ?: return

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

    var deleteDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Column(modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection).fillMaxSize()) {
        MediumFlexibleTopAppBar(
            scrollBehavior = scrollBehavior,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = Color.Transparent,
                    containerColor = Color.Transparent,
                ),
            title = {
                Text(
                    text = currentHabit.habit.title,
                    modifier = Modifier.basicMarquee(),
                    fontFamily = flexFontEmphasis(),
                )
            },
            windowInsets =
                if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
                    WindowInsets(0)
                } else {
                    TopAppBarDefaults.windowInsets
                },
            navigationIcon = {
                FilledTonalIconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.nav_arrow_back),
                        contentDescription = "Navigate Back",
                    )
                }
            },
            actions = {
                OutlinedIconButton(
                    onClick = { deleteDialog = true },
                    shapes =
                        IconButtonShapes(
                            shape = CircleShape,
                            pressedShape = MaterialTheme.shapes.small,
                        ),
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.delete),
                        contentDescription = "Delete Habit",
                    )
                }

                FilledIconButton(
                    onClick = onEditHabit,
                    shapes =
                        IconButtonShapes(
                            shape = CircleShape,
                            pressedShape = MaterialTheme.shapes.small,
                        ),
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.edit),
                        contentDescription = "Edit Habit",
                    )
                }
            },
        )

        val maxWidth = 380.dp
        LazyVerticalStaggeredGrid(
            modifier =
                Modifier.fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
            columns = StaggeredGridCells.Adaptive(minSize = maxWidth),
            contentPadding = PaddingValues(top = 16.dp, bottom = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 16.dp,
        ) {
            // Description + steps live here on the detail page, not on the list card.
            if (
                currentHabit.habit.description.isNotBlank() ||
                    currentHabit.habit.steps.isNotEmpty()
            ) {
                item {
                    HabitDetailsCard(
                        description = currentHabit.habit.description,
                        steps = currentHabit.habit.steps,
                        modifier = Modifier.widthIn(max = maxWidth),
                    )
                }
            }

            item {
                StartStats(
                    consistency = currentHabit.consistency,
                    startDate = currentHabit.habit.startDate ?: currentHabit.habit.time.date,
                    bestStreak = currentHabit.bestStreak,
                    currentStreak = currentHabit.currentStreak,
                )
            }

            item {
                WeeklyBooleanHeatMap(
                    heatMapState = heatMapState,
                    statuses = currentHabit.statuses,
                    days = currentHabit.habit.days,
                    onDateClick = { onAction(HabitsAction.InsertStatus(currentHabit.habit, it)) },
                )
            }

            item {
                CalendarMap(
                    canSeeContent = isUserSubscribed,
                    calendarState = calendarState,
                    onNavigateToPaywall = onNavigateToPaywall,
                    statuses = currentHabit.statuses,
                    days = currentHabit.habit.days,
                    onNavigateToCalendar = onNavigateToCalendar,
                    onDateClick = {
                        onAction(HabitsAction.InsertStatus(habit = currentHabit.habit, date = it))
                    },
                )
            }

            item {
                WeeklyActivity(
                    lineChartData = currentHabit.weeklyComparisonData,
                    modifier = Modifier.widthIn(max = maxWidth),
                )
            }

            item {
                WeekDayBreakdown(
                    canSeeContent = isUserSubscribed,
                    weekDayData = currentHabit.weekDayFrequencyData,
                    onNavigateToPaywall = onNavigateToPaywall,
                    modifier = Modifier.widthIn(max = maxWidth),
                )
            }
        }
    }

    // delete dialog
    if (deleteDialog) {
        GritDialog(onDismissRequest = { deleteDialog = false }) {
            Column {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier.size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialShapes.Pill.toShape(),
                            ),
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.warning),
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.delete),
                    style =
                        MaterialTheme.typography.headlineSmall.copy(fontFamily = flexFontEmphasis()),
                )
                Text(
                    text = stringResource(Res.string.delete_warning),
                    style = MaterialTheme.typography.bodyLarge,
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { deleteDialog = false },
                        shapes =
                            ButtonShapes(
                                shape = MaterialTheme.shapes.extraLarge,
                                pressedShape = MaterialTheme.shapes.small,
                            ),
                    ) {
                        Text(stringResource(Res.string.cancel))
                    }

                    TextButton(
                        onClick = {
                            onAction(HabitsAction.DeleteHabit(currentHabit.habit))
                            onAction(HabitsAction.PrepareAnalytics(null))
                            deleteDialog = false
                            onNavigateBack()
                        },
                        shapes =
                            ButtonShapes(
                                shape = MaterialTheme.shapes.extraLarge,
                                pressedShape = MaterialTheme.shapes.small,
                            ),
                    ) {
                        Text(stringResource(Res.string.delete))
                    }
                }
            }
        }
    }
}

/** Description and step routine of a habit, shown on its detail page. */
@Composable
private fun HabitDetailsCard(
    description: String,
    steps: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (description.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.description),
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontFamily = flexFontRounded(),
                            color = MaterialTheme.colorScheme.primary,
                        ),
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (steps.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(Res.string.steps),
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontFamily = flexFontRounded(),
                            color = MaterialTheme.colorScheme.primary,
                        ),
                )
                steps.forEachIndexed { index, step ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.primary
                                ),
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

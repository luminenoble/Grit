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
package com.shub39.grit.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.shub39.grit.widgets.all_tasks_widget.AllTasksWidget
import com.shub39.grit.widgets.all_tasks_widget.AllTasksWidgetReceiver
import com.shub39.grit.widgets.habit_overview_widget.HabitOverviewWidget
import com.shub39.grit.widgets.habit_overview_widget.HabitOverviewWidgetReceiver
import com.shub39.grit.widgets.habit_streak_widget.HabitStreakWidget
import com.shub39.grit.widgets.habit_streak_widget.HabitStreakWidgetReceiver
import com.shub39.grit.widgets.habit_week_chart_widget.HabitWeekChartWidget
import com.shub39.grit.widgets.habit_week_chart_widget.HabitWeekChartWidgetReceiver
import kotlinx.coroutines.CancellationException

/** Resolve IDs from Android's provider registry, never from Glance's cached class mapping. */
object WidgetUpdates {
    suspend fun tasks(context: Context) =
        update(context, AllTasksWidgetReceiver::class.java, AllTasksWidget())

    suspend fun habits(context: Context) {
        update(context, HabitOverviewWidgetReceiver::class.java, HabitOverviewWidget())
        update(context, HabitStreakWidgetReceiver::class.java, HabitStreakWidget())
        update(context, HabitWeekChartWidgetReceiver::class.java, HabitWeekChartWidget())
    }

    private suspend fun update(context: Context, receiver: Class<*>, widget: GlanceAppWidget) {
        val manager = AppWidgetManager.getInstance(context)
        val glanceManager = GlanceAppWidgetManager(context)
        val provider = ComponentName(context, receiver)
        for (id in manager.getAppWidgetIds(provider)) {
            try {
                // The user may remove or replace a widget while refresh is in flight.
                if (manager.getAppWidgetInfo(id)?.provider == provider) {
                    widget.update(context, glanceManager.getGlanceIdBy(id))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("WidgetUpdates", "Failed to update ${receiver.simpleName} ($id)", e)
            }
        }
    }
}

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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shub39.grit.core.interfaces.AlarmScheduler
import com.shub39.grit.core.tasks.TaskRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/** Refresh persisted RemoteViews when the calendar day or device clock changes. */
class WidgetDateReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action !in
                setOf(
                    Intent.ACTION_DATE_CHANGED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                )
        )
            return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (
                    intent.action == Intent.ACTION_TIME_CHANGED ||
                        intent.action == Intent.ACTION_TIMEZONE_CHANGED
                ) {
                    val scheduler = get<AlarmScheduler>()
                    get<TaskRepo>().getTasks().forEach { task ->
                        try {
                            scheduler.schedule(task)
                        } catch (e: Exception) {
                            Log.e("WidgetDateReceiver", "时间变化后调度任务提醒失败", e)
                        }
                    }
                }
                WidgetUpdates.tasks(context)
            } finally {
                pending.finish()
            }
        }
    }
}

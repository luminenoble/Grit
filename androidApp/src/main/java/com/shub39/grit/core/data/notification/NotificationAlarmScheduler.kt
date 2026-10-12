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
package com.shub39.grit.core.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.shub39.grit.core.data.GritIntentReceiver
import com.shub39.grit.core.interfaces.AlarmScheduler
import com.shub39.grit.core.interfaces.IntentActions
import com.shub39.grit.core.now
import com.shub39.grit.core.tasks.Task
import kotlinx.datetime.*
import org.koin.core.annotation.Single

@Single(binds = [AlarmScheduler::class])
class NotificationAlarmScheduler(private val context: Context) : AlarmScheduler {
    private val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pending(task: Task, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, GritIntentReceiver::class.java).apply {
                action = IntentActions.TASK_NOTIFICATION.action
                data = Uri.parse("grit-rhine://task/${task.id}")
                putExtra("task_id", task.id)
            },
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    override fun schedule(task: Task) {
        cancel(task)
        val reminder = task.reminder ?: return
        if (task.id <= 0 || task.isCompleted || reminder <= LocalDateTime.now()) return
        val time = reminder.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val intent = requireNotNull(pending(task, PendingIntent.FLAG_UPDATE_CURRENT))
        if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms())
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent)
    }

    override fun cancel(task: Task) {
        pending(task, PendingIntent.FLAG_NO_CREATE)?.let {
            manager.cancel(it)
            it.cancel()
        }
    }

    override fun cancelAll() {
        if (Build.VERSION.SDK_INT >= 34) manager.cancelAll()
    }
}

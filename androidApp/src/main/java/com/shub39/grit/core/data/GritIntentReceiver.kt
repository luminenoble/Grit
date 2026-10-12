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

import android.content.*
import android.util.Log
import com.shub39.grit.core.data.notification.GritNotificationManager
import com.shub39.grit.core.interfaces.IntentActions
import com.shub39.grit.core.interfaces.SettingsDatastore
import com.shub39.grit.core.tasks.TaskRepo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.core.component.*

class GritIntentReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent?) {
        if (
            intent?.action !in
                setOf(IntentActions.TASK_NOTIFICATION.action, IntentActions.MARK_TASK_DONE.action)
        )
            return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val repo = get<TaskRepo>()
                val task = repo.getTaskById(intent!!.getLongExtra("task_id", -1)) ?: return@launch
                when (intent.action) {
                    IntentActions.MARK_TASK_DONE.action ->
                        repo.upsertTask(task.copy(isCompleted = true))
                    IntentActions.TASK_NOTIFICATION.action ->
                        if (
                            !task.isCompleted &&
                                task.reminder != null &&
                                !get<SettingsDatastore>().getNotificationsFlow().first()
                        )
                            get<GritNotificationManager>().taskNotification(task)
                }
            } catch (e: Exception) {
                Log.e("GritIntentReceiver", "处理任务通知失败", e)
            } finally {
                pending.finish()
            }
        }
    }
}

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

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.*
import com.shub39.grit.R
import com.shub39.grit.core.data.GritIntentReceiver
import com.shub39.grit.core.interfaces.IntentActions
import com.shub39.grit.core.tasks.Task
import org.koin.core.annotation.Single

@Single
class GritNotificationManager(private val context: Context) {
    companion object {
        const val CHANNEL = "grit_rhine_tasks"

        fun createNotificationChannel(context: Context) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Removing the old mixed channel also removes its delivered notifications.
            manager.deleteNotificationChannel("1")
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Grhinet Tasks",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
    }

    fun taskNotification(task: Task) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        )
            return
        val intent =
            Intent(context, GritIntentReceiver::class.java).apply {
                action = IntentActions.MARK_TASK_DONE.action
                data = Uri.parse("grit-rhine://complete/${task.id}")
                putExtra("task_id", task.id)
            }
        val action =
            PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.notif_icon)
                .setContentTitle(task.title)
                .setContentText(task.description)
                .setAutoCancel(true)
                .addAction(R.drawable.notif_icon, "Mark Done", action)
                .build()
        NotificationManagerCompat.from(context).notify("task:${task.id}", 0, notification)
    }

    fun cancelNotification(task: Task) {
        NotificationManagerCompat.from(context).cancel("task:${task.id}", 0)
    }
}

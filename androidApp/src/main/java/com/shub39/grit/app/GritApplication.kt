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
package com.shub39.grit.app

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.shub39.grit.BuildConfig
import com.shub39.grit.billing.BillingInitializer
import com.shub39.grit.core.data.notification.GritNotificationManager
import com.shub39.grit.core.interfaces.AlarmScheduler
import com.shub39.grit.core.tasks.TaskRepo
import com.shub39.grit.di.GritModules
import com.shub39.grit.shared.ui.update.AppVersionHolder
import com.shub39.grit.widgets.WidgetUpdates
import com.shub39.grit.widgets.all_tasks_widget.AllTasksWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.plugin.module.dsl.startKoin

class GritApplication : Application(), KoinComponent {

    override fun onCreate() {
        super.onCreate()
        AppVersionHolder.versionName = BuildConfig.VERSION_NAME

        startKoin<GritModules> {
            androidLogger()
            androidContext(this@GritApplication)
        }

        GritNotificationManager.createNotificationChannel(this)
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val scheduler = get<AlarmScheduler>()
                val prefs = getSharedPreferences("grit_rhine_bootstrap", MODE_PRIVATE)
                if (!prefs.getBoolean("initialized", false)) {
                    // Android 14+ can cancel legacy alarms without reading the old database.
                    scheduler.cancelAll()
                    prefs.edit().putBoolean("initialized", true).apply()
                }
                get<TaskRepo>().getTasks().forEach { task ->
                    try {
                        scheduler.schedule(task)
                    } catch (e: Exception) {
                        Log.e("Grhinet", "任务提醒调度失败", e)
                    }
                }
                WidgetUpdates.tasks(this@GritApplication)
            } catch (e: Exception) {
                Log.e("Grhinet", "启动刷新失败", e)
            }
        }

        BillingInitializer().initialize(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val manager = GlanceAppWidgetManager(applicationContext)

            @SuppressLint("CheckResult")
            MainScope().launch { manager.setWidgetPreviews(AllTasksWidgetReceiver::class) }
        }
    }
}

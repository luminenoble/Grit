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
package com.shub39.grit.tasks.data.repository

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.shub39.grit.core.data.notification.GritNotificationManager
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.core.tasks.TaskRepo
import com.shub39.grit.tasks.data.database.CategoryDao
import com.shub39.grit.tasks.data.database.TasksDao
import com.shub39.grit.tasks.data.toCategory
import com.shub39.grit.tasks.data.toCategoryEntity
import com.shub39.grit.tasks.data.toTask
import com.shub39.grit.tasks.data.toTaskEntity
import com.shub39.grit.widgets.all_tasks_widget.AllTasksWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single(binds = [TaskRepo::class])
class TasksRepository(
    private val context: Context,
    private val tasksDao: TasksDao,
    private val categoryDao: CategoryDao,
    private val notificationManager: GritNotificationManager,
) : TaskRepo {

    // Push the current DB state to the home-screen widget so it never shows stale
    // (e.g. already-deleted) tasks after the app process has been torn down.
    private suspend fun refreshWidgets() {
        runCatching { AllTasksWidget().updateAll(context) }
    }

    private val tasksFlow =
        tasksDao
            .getTasksFlow()
            .map { entities -> entities.map { it.toTask() }.sortedBy { it.index } }
            .flowOn(Dispatchers.IO)

    val categoriesFlow =
        categoryDao
            .getCategoriesFlow()
            .map { entities -> entities.map { it.toCategory() }.sortedBy { it.index } }
            .flowOn(Dispatchers.IO)

    override fun getTasksFlow(): Flow<Map<Category, List<Task>>> {
        return tasksFlow
            .combine(categoriesFlow) { tasks, categories ->
                categories.associateWith { category ->
                    tasks.filter { it.categoryId == category.id }
                }
            }
            .flowOn(Dispatchers.Default)
    }

    override fun getCompletedTasksFlow(): Flow<List<Task>> {
        return tasksFlow.map { tasks -> tasks.filter { it.status } }.flowOn(Dispatchers.IO)
    }

    override suspend fun getTasks(): List<Task> {
        return tasksDao.getTasks().map { it.toTask() }
    }

    override suspend fun getTaskById(id: Long): Task? {
        return tasksDao.getTaskById(id)?.toTask()
    }

    override suspend fun getCategories(): List<Category> {
        return categoryDao.getCategories().map { it.toCategory() }
    }

    override suspend fun updateTaskIndexById(id: Long, index: Int) {
        tasksDao.updateTaskIndexById(id, index)
    }

    override suspend fun upsertTask(task: Task) {
        tasksDao.upsertTask(task.toTaskEntity())

        if (task.status) {
            notificationManager.cancelNotification(task)
        }
        refreshWidgets()
    }

    override suspend fun deleteTask(task: Task) {
        tasksDao.deleteTask(task.toTaskEntity())
        refreshWidgets()
    }

    override suspend fun deleteAllTasks() {
        tasksDao.deleteAllTasks()
        refreshWidgets()
    }

    override suspend fun upsertCategory(category: Category) {
        categoryDao.upsertCategory(category.toCategoryEntity())
        refreshWidgets()
    }

    override suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category.toCategoryEntity())
        refreshWidgets()
    }

    override suspend fun deleteAllCategories() {
        categoryDao.deleteAllCategories()
        refreshWidgets()
    }
}

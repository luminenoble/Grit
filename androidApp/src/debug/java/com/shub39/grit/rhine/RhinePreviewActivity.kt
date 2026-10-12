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
package com.shub39.grit.rhine

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.room3.Room
import androidx.room3.withWriteTransaction
import com.shub39.grit.core.data.notification.GritNotificationManager
import com.shub39.grit.core.interfaces.AlarmScheduler
import com.shub39.grit.core.tasks.*
import com.shub39.grit.shared.ui.rhine.*
import com.shub39.grit.shared.ui.theme.GritTheme
import com.shub39.grit.tasks.data.database.*
import com.shub39.grit.tasks.data.repository.TasksRepository
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** Debug-only deterministic fixtures. Never opens the user's on-disk database. */
class RhinePreviewActivity : FragmentActivity() {
    private lateinit var db: TaskDatabase
    private lateinit var repository: TaskRepo
    private var traceTouch by mutableStateOf<Offset?>(null)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (intent.getBooleanExtra("traceTouch", false)) {
            traceTouch =
                if (
                    event.actionMasked == MotionEvent.ACTION_UP ||
                        event.actionMasked == MotionEvent.ACTION_CANCEL
                )
                    null
                else Offset(event.x, event.y)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        db = Room.inMemoryDatabaseBuilder<TaskDatabase>(this).build()
        val fixture = intent.getStringExtra("dataset") ?: "normal"
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.withWriteTransaction { seed(fixture) } }
            repository =
                TasksRepository(
                    this@RhinePreviewActivity,
                    db,
                    object : AlarmScheduler {
                        override fun schedule(task: Task) {}

                        override fun cancel(task: Task) {}

                        override fun cancelAll() {}
                    },
                    GritNotificationManager(this@RhinePreviewActivity),
                )
            var failDeleteOnce = intent.getBooleanExtra("failDeleteOnce", false)
            suspend fun checkDelete() {
                kotlinx.coroutines.delay(intent.getLongExtra("submitDelayMs", 700L))
                if (failDeleteOnce) {
                    failDeleteOnce = false
                    error("隔离测试：模拟删除失败，请重试")
                }
            }
            var failOnce = intent.getBooleanExtra("failSaveOnce", false)
            fun checkSave() {
                if (failOnce) {
                    failOnce = false
                    error("隔离测试：模拟保存失败，草稿已保留，请重试")
                }
            }
            val editorRepo =
                object : TaskRepo by repository {
                    override suspend fun deleteCategory(category: Category) {
                        checkDelete()
                        repository.deleteCategory(category)
                    }

                    override suspend fun deleteTask(task: Task) {
                        checkDelete()
                        repository.deleteTask(task)
                    }

                    override suspend fun deleteSection(id: Long) {
                        checkDelete()
                        repository.deleteSection(id)
                    }

                    override suspend fun upsertTask(task: Task): Long {
                        kotlinx.coroutines.delay(intent.getLongExtra("submitDelayMs", 500L))
                        checkSave()
                        return repository.upsertTask(task)
                    }

                    override suspend fun upsertCategory(category: Category): Long {
                        kotlinx.coroutines.delay(intent.getLongExtra("submitDelayMs", 500L))
                        checkSave()
                        return repository.upsertCategory(category)
                    }

                    override suspend fun upsertSection(section: Section): Long {
                        kotlinx.coroutines.delay(intent.getLongExtra("submitDelayMs", 500L))
                        checkSave()
                        return repository.upsertSection(section)
                    }
                }
            val source = RepositoryRhineDataSource(repository)
            setContent {
                GritTheme {
                    Box(Modifier.fillMaxSize()) {
                        RhineScreen(
                            onOpenTasks = { finish() },
                            onOpenSettings = { finish() },
                            dataSource = source,
                            editRepository = editorRepo,
                            initialMode =
                                if (intent.getBooleanExtra("detail", false)) RhineMode.Detail
                                else RhineMode.Preview,
                            initialCamera =
                                RhineCamera(
                                    intent.getFloatExtra("u", 0f),
                                    intent.getFloatExtra("v", 0f),
                                ),
                        )
                        if (intent.getBooleanExtra("traceTouch", false)) {
                            // Pointer-free drawing overlay: it observes Android events, never
                            // consumes them.
                            Canvas(Modifier.fillMaxSize()) {
                                traceTouch?.let { point ->
                                    drawCircle(Color(0xFF426E79).copy(alpha = 0.28f), 20f, point)
                                    drawCircle(Color(0xFF426E79), 20f, point, style = Stroke(3f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun seed(fixture: String) {
        val counts =
            when (fixture) {
                "empty" -> emptyList()
                "one-empty" -> listOf(0)
                "uneven" -> listOf(100, 1, 1)
                "detail" -> listOf(2, 5, 2)
                "section-uneven" -> listOf(2)
                else -> listOf(12, 18, 6, 0, 9)
            }
        val names =
            if (fixture == "uneven") listOf("A · 长列", "B · 短列", "C · 短列")
            else listOf("学习", "工作", "生活", "空类别", "阅读")
        val now = Clock.System.now()
        counts.forEachIndexed { v, count ->
            val id = v + 1L
            db.categoryDao()
                .upsert(
                    CategoryEntity(
                        id,
                        if (fixture == "one-empty") "唯一空类别" else names[v],
                        "隔离内存测试数据 · 类别描述 · 包含目标、背景和资料。\n".repeat(5),
                        v,
                        if (v == 1) "#426E79" else null,
                        true,
                        now,
                        now,
                    )
                )
            repeat(count) { i ->
                val title =
                    if (fixture == "detail") listOf("需求梳理", "设计评审", "空任务", "完成发布", "阅读记录")[i % 5]
                    else if (fixture == "uneven") "${('A'.code + v).toChar()} · Task ${i + 1}"
                    else listOf("学习协程", "设计评审", "阅读与记录", "整理资料", "计划下一步", "完成练习")[i % 6]
                db.taskDao()
                    .upsert(
                        TaskEntity(
                            id = id * 1000 + i,
                            categoryId = id,
                            title = title,
                            description = "长期任务描述 · 正文仅在编辑时读取，保存必须完整保留。\n".repeat(30),
                            orderIndex = i,
                            titleColor = if (i % 5 == 1 || i % 5 == 2) "#426E79" else null,
                            isCompleted = i % 4 == 1,
                            isToday = i == 1,
                            reminder = if (i == 1) LocalDateTime(2030, 1, 1, 9, 30) else null,
                            createdAt = now,
                            updatedAt = now,
                        )
                    )
                if (fixture == "detail" || fixture == "section-uneven") {
                    val n =
                        if (fixture == "section-uneven") listOf(100, 1)[i]
                        else listOf(6, 12, 0, 1, 3)[i % 5]
                    repeat(n) { j ->
                        db.sectionDao()
                            .upsert(
                                SectionEntity(
                                    id = (id * 1000 + i) * 100 + j + 1,
                                    taskId = id * 1000 + i,
                                    title =
                                        listOf("需求与范围", "草图评审", "记录讨论结果", "完善细节", "验证与回顾")[j % 5],
                                    content =
                                        "Section 正文 ${j + 1} · 长内容必须保留，不从摘要生成草稿。\n".repeat(35),
                                    orderIndex = j,
                                    completedOn = if (j % 3 == 0) LocalDate(2026, 1, 2) else null,
                                    createdAt = now,
                                    updatedAt = now,
                                )
                            )
                    }
                }
            }
        }
    }

    /** Simulates edits from another page against this Activity's isolated database only. */
    override fun onNewIntent(update: android.content.Intent) {
        super.onNewIntent(update)
        if (!::repository.isInitialized) return
        lifecycleScope.launch {
            val id = update.getLongExtra("taskId", 2001L)
            when (update.getStringExtra("mutation")) {
                "import-reused" -> repository.replaceData(repository.exportSnapshot())
                "rename" ->
                    repository.getTaskById(id)?.let {
                        repository.upsertTask(it.copy(title = "外部修改", titleColor = "#97564A"))
                    }
                "move" ->
                    repository.getTaskById(id)?.let {
                        repository.upsertTask(it.copy(categoryId = 3L))
                    }
                "delete-task" -> repository.getTaskById(id)?.let { repository.deleteTask(it) }
                "delete-category" ->
                    repository
                        .getCategories()
                        .firstOrNull { it.id == 2L }
                        ?.let { repository.deleteCategory(it) }
                "reorder-section" ->
                    repository.moveSection(update.getLongExtra("sectionId", 200101L), 3)
                "delete-section" ->
                    repository.getSectionById(update.getLongExtra("sectionId", 200101L))?.let {
                        repository.deleteSection(it.id)
                    }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        db.close()
    }
}

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
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.room3.Room
import androidx.room3.withWriteTransaction
import com.shub39.grit.core.data.datastore.SettingsDatastoreImpl
import com.shub39.grit.core.data.notification.GritNotificationManager
import com.shub39.grit.core.interfaces.AlarmScheduler
import com.shub39.grit.core.settings.*
import com.shub39.grit.core.tasks.*
import com.shub39.grit.shared.ui.rhine.*
import com.shub39.grit.shared.ui.setting.ui.section.RhineDisplayPage
import com.shub39.grit.shared.ui.theme.GritTheme
import com.shub39.grit.tasks.data.database.*
import com.shub39.grit.tasks.data.repository.TasksRepository
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** Debug-only deterministic fixtures. Never opens the user's on-disk database. */
class RhinePreviewActivity : FragmentActivity() {
    private lateinit var db: TaskDatabase
    private lateinit var repository: TaskRepo
    private val fixtureModel by viewModels<RhineFixtureModel>()
    private lateinit var displayStore: SettingsDatastoreImpl
    private var display by mutableStateOf(RhineDisplayConfig())
    private var moveAttempts = 0
    private var moveCommits = 0
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
        if (intent.getBooleanExtra("enableTaskPage", false))
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        displayStore =
            fixtureModel.store
                ?: SettingsDatastoreImpl(
                        applicationContext,
                        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                            scope = fixtureModel.scope
                        ) {
                            preferencesDataStoreFile("rhine06-isolated")
                        },
                    )
                    .also { fixtureModel.store = it }
        db =
            fixtureModel.db
                ?: Room.inMemoryDatabaseBuilder<TaskDatabase>(applicationContext).build().also {
                    fixtureModel.db = it
                }
        val fixture = intent.getStringExtra("dataset") ?: "normal"
        lifecycleScope.launch {
            if (fixtureModel.repository == null)
                withContext(Dispatchers.IO) { db.withWriteTransaction { seed(fixture) } }
            if (intent.getBooleanExtra("resetDisplay", false))
                displayStore.setRhineDisplay(RhineDisplayConfig())
            display =
                (savedInstanceState?.getString("rhine06-display")
                        ?: intent.getStringExtra("displayConfig"))
                    ?.let { RhineDisplayConfig.decode(it) }
                    ?: displayStore.getRhineDisplay().first()
            repository =
                fixtureModel.repository
                    ?: TasksRepository(
                            applicationContext,
                            db,
                            object : AlarmScheduler {
                                override fun schedule(task: Task) {}

                                override fun cancel(task: Task) {}

                                override fun cancelAll() {}
                            },
                            GritNotificationManager(applicationContext),
                        )
                        .also { fixtureModel.repository = it }
            repository = fixtureModel.metrics.wrap(repository)
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
            var failMoveOnce = intent.getBooleanExtra("failMoveOnce", false)
            var failSearchOnce = intent.getBooleanExtra("failSearchOnce", false)
            suspend fun checkMove() {
                moveAttempts++
                kotlinx.coroutines.delay(intent.getLongExtra("submitDelayMs", 500L))
                if (failMoveOnce) {
                    failMoveOnce = false
                    error("隔离测试：模拟移动失败，请重试")
                }
            }
            val editorRepo =
                object : TaskRepo by repository {
                    override suspend fun moveTask(id: Long, target: Int, expected: MoveScope?) {
                        checkMove()
                        repository.moveTask(id, target, expected)
                        moveCommits++
                    }

                    override suspend fun moveCategory(id: Long, target: Int, expected: MoveScope?) {
                        checkMove()
                        repository.moveCategory(id, target, expected)
                        moveCommits++
                    }

                    override suspend fun moveSection(id: Long, target: Int, expected: MoveScope?) {
                        checkMove()
                        repository.moveSection(id, target, expected)
                        moveCommits++
                    }

                    override suspend fun searchTasks(
                        query: String,
                        offset: Int,
                        limit: Int,
                    ): List<TaskSearchResult> {
                        kotlinx.coroutines.delay(intent.getLongExtra("searchDelayMs", 0L))
                        if (failSearchOnce) {
                            failSearchOnce = false
                            error("隔离测试：搜索失败")
                        }
                        return repository.searchTasks(query, offset, limit)
                    }

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
            val illustrationRepo =
                com.shub39.grit.tasks.data.repository.RhineIllustrationRepository(
                    applicationContext,
                    db,
                )
            val tasksModel =
                if (intent.getBooleanExtra("enableTaskPage", false)) {
                    androidx.lifecycle.ViewModelProvider.create(
                            this@RhinePreviewActivity,
                            object : androidx.lifecycle.ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : androidx.lifecycle.ViewModel> create(
                                    modelClass: Class<T>
                                ): T =
                                    com.shub39.grit.shared.ui.viewmodel.TasksViewModel(
                                        editorRepo,
                                        object : AlarmScheduler {
                                            override fun schedule(task: Task) {}

                                            override fun cancel(task: Task) {}

                                            override fun cancelAll() {}
                                        },
                                        displayStore,
                                        illustrationRepo,
                                    ) as T
                            },
                        )[com.shub39.grit.shared.ui.viewmodel.TasksViewModel::class.java]
                } else null
            val source = RepositoryRhineDataSource(repository)
            val metrics = resources.displayMetrics
            val size =
                androidx.compose.ui.geometry.Size(
                    maxOf(metrics.widthPixels, metrics.heightPixels).toFloat(),
                    minOf(metrics.widthPixels, metrics.heightPixels).toFloat(),
                )
            val initial = RhineCamera(intent.getFloatExtra("u", 0f), intent.getFloatExtra("v", 0f))
            val previewWindow =
                RhineProjection(
                        display.geometry(
                            size.width / metrics.density,
                            size.height / metrics.density,
                        ),
                        metrics.density,
                        size,
                        initial,
                    )
                    .visibleSlots(margin = 3)
                    .queryWindow()
            val previewColumns = source.window(source.catalog().first(), previewWindow).first()
            setContent {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalRhineDrawObserver provides fixtureModel.metrics,
                    // Isolated like the fixture database: never the user's illustrations.
                    LocalRhineIllustrationRepo provides illustrationRepo,
                ) {
                    GritTheme {
                        var showTasks by rememberSaveable { mutableStateOf(false) }
                        var showDisplay by rememberSaveable {
                            mutableStateOf(intent.getBooleanExtra("settings", false))
                        }
                        var settingsSaving by remember { mutableStateOf(false) }
                        var settingsMessage by remember { mutableStateOf<String?>(null) }
                        val sceneHolder =
                            androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                        Box(Modifier.fillMaxSize()) {
                            if (showTasks && tasksModel != null) {
                                val tasksState by tasksModel.state.collectAsState()
                                androidx.compose.foundation.layout.Column(
                                    Modifier.fillMaxSize()
                                        .windowInsetsPadding(WindowInsets.safeDrawing)
                                ) {
                                    androidx.compose.material3.TextButton(
                                        onClick = { showTasks = false }
                                    ) {
                                        androidx.compose.material3.Text("返回隔离 Rhine")
                                    }
                                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                                        androidx.compose.runtime.CompositionLocalProvider(
                                            com.shub39.grit.shared.ui.LocalWindowSizeClass provides
                                                androidx.compose.material3.windowsizeclass
                                                    .calculateWindowSizeClass(
                                                        this@RhinePreviewActivity
                                                    )
                                        ) {
                                            com.shub39.grit.shared.ui.task.ui.TasksPage(
                                                tasksState,
                                                tasksModel::onAction,
                                            )
                                        }
                                    }
                                }
                            } else if (showDisplay) {
                                RhineWindowMode()
                                val input =
                                    RhineSceneInput(
                                        RhineLayer.Categories,
                                        previewColumns,
                                        intent.getFloatExtra("v", 0f).toInt(),
                                        intent.getFloatExtra("u", 0f),
                                    )
                                RhineDisplayPage(
                                    saved = display,
                                    saving = settingsSaving,
                                    message = settingsMessage,
                                    previewInput = input,
                                    onBack = { showDisplay = false },
                                    onSave = { config ->
                                        settingsSaving = true
                                        lifecycleScope.launch {
                                            displayStore.setRhineDisplay(config)
                                            display = displayStore.getRhineDisplay().first()
                                            settingsMessage = "Rhine 显示配置已保存"
                                            settingsSaving = false
                                        }
                                    },
                                )
                            } else
                                sceneHolder.SaveableStateProvider("rhine") {
                                    RhineScreen(
                                        display = display,
                                        onOpenTasks = {
                                            if (tasksModel != null) showTasks = true else finish()
                                        },
                                        onOpenSettings = { showDisplay = true },
                                        dataSource = source,
                                        editRepository = editorRepo,
                                        initialMode =
                                            if (intent.getBooleanExtra("adjust", false))
                                                RhineMode.Adjust
                                            else if (intent.getBooleanExtra("detail", false))
                                                RhineMode.Detail
                                            else RhineMode.Preview,
                                        initialCamera =
                                            RhineCamera(
                                                intent.getFloatExtra("u", 0f),
                                                intent.getFloatExtra("v", 0f),
                                            ),
                                    )
                                }
                            if (intent.getBooleanExtra("traceTouch", false)) {
                                // Pointer-free drawing overlay: it observes Android events, never
                                // consumes them.
                                Canvas(Modifier.fillMaxSize()) {
                                    traceTouch?.let { point ->
                                        drawCircle(
                                            Color(0xFF426E79).copy(alpha = 0.28f),
                                            20f,
                                            point,
                                        )
                                        drawCircle(
                                            Color(0xFF426E79),
                                            20f,
                                            point,
                                            style = Stroke(3f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        window.addOnFrameMetricsAvailableListener(
            { _, frame, _ -> fixtureModel.metrics.frame(frame) },
            android.os.Handler(mainLooper),
        )
    }

    private suspend fun seed(fixture: String) {
        val counts =
            when (fixture) {
                "large" -> List(100) { 100 }
                "sections1000" -> listOf(2)
                "empty" -> emptyList()
                "one-empty" -> listOf(0)
                "uneven" -> listOf(100, 1, 1)
                "adjust" -> listOf(12, 8, 1, 0, 9)
                "search" -> listOf(60, 2, 1)
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
                        if (fixture == "large") "Category ${v + 1}"
                        else if (fixture == "one-empty") "唯一空类别" else names[v],
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
                    if (fixture == "large") {
                        if (i % 10 == 0) "重名项目" else "Task ${i + 1} · 长标题的真实摘要与正文隔离性能测试"
                    } else if (fixture == "adjust") "Task ${i + 1}"
                    else if (fixture == "search")
                        if (i == 2) "literal %_\\" else "Alpha 设计评审 ${i + 1}"
                    else if (fixture == "detail")
                        listOf("需求梳理", "设计评审", "空任务", "完成发布", "阅读记录")[i % 5]
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
                if (
                    fixture == "detail" ||
                        fixture == "section-uneven" ||
                        fixture == "adjust" ||
                        fixture == "sections1000"
                ) {
                    val n =
                        if (fixture == "sections1000") if (i == 0) 1000 else 1
                        else if (fixture == "adjust") if (i == 2) 0 else 12
                        else if (fixture == "section-uneven") listOf(100, 1)[i]
                        else listOf(6, 12, 0, 1, 3)[i % 5]
                    repeat(n) { j ->
                        db.sectionDao()
                            .upsert(
                                SectionEntity(
                                    id =
                                        (id * 1000 + i) *
                                            (if (fixture == "sections1000") 10000 else 100) + j + 1,
                                    taskId = id * 1000 + i,
                                    title =
                                        if (fixture == "adjust") "Section ${j + 1}"
                                        else
                                            listOf("需求与范围", "草图评审", "记录讨论结果", "完善细节", "验证与回顾")[
                                                j % 5],
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
                "inspect-config" ->
                    java.io.File(cacheDir, "rhine07-config.json").writeText(display.encode())
                "reset-perf" -> fixtureModel.metrics.reset()
                "inspect-perf" ->
                    java.io
                        .File(cacheDir, "rhine07-perf.json")
                        .writeText(fixtureModel.metrics.json().toString())
                "display" -> {
                    display = RhineDisplayConfig.decode(update.getStringExtra("displayConfig"))
                }
                "save-display" -> {
                    displayStore.setRhineDisplay(
                        RhineDisplayConfig.decode(update.getStringExtra("displayConfig"))
                    )
                    display = displayStore.getRhineDisplay().first()
                }
                "inspect-display" -> {
                    val value =
                        org.json
                            .JSONObject()
                            .put("display", display.encode())
                            .put(
                                "business",
                                repository
                                    .exportSnapshot()
                                    .copy(exportedAt = kotlin.time.Instant.fromEpochMilliseconds(0))
                                    .toString(),
                            )
                    java.io.File(cacheDir, "rhine06-state.json").writeText(value.toString())
                }
                "inspect" -> {
                    val state = org.json.JSONObject()
                    state.put("attempts", moveAttempts).put("commits", moveCommits)
                    state.put(
                        "categories",
                        org.json.JSONArray(
                            repository.getCategories().map { c ->
                                org.json
                                    .JSONObject()
                                    .put("id", c.id)
                                    .put("order", c.orderIndex)
                                    .put("title", c.name)
                            }
                        ),
                    )
                    state.put(
                        "tasks",
                        org.json.JSONArray(
                            repository.getTasks().map { t ->
                                org.json
                                    .JSONObject()
                                    .put("id", t.id)
                                    .put("parent", t.categoryId)
                                    .put("order", t.orderIndex)
                                    .put("title", t.title)
                                    .put(
                                        "sections",
                                        org.json.JSONArray(
                                            t.sections.map { x ->
                                                org.json
                                                    .JSONObject()
                                                    .put("id", x.id)
                                                    .put("order", x.orderIndex)
                                                    .put("title", x.title)
                                            }
                                        ),
                                    )
                            }
                        ),
                    )
                    java.io.File(cacheDir, "rhine05-state.json").writeText(state.toString())
                }
                "import-reused" -> repository.replaceData(repository.exportSnapshot())
                "light-color" ->
                    repository.getTaskById(id)?.let {
                        repository.upsertTask(it.copy(titleColor = "#FFFFFF"))
                    }
                "long-title" ->
                    repository.getTaskById(id)?.let {
                        repository.upsertTask(
                            it.copy(title = "聚焦长标题单行滚动：这是一段用于核对面内裁剪与完整标题浏览的中文示例文字")
                        )
                    }
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
                "reorder-task" -> repository.moveTask(id, 3)
                "reorder-category" ->
                    repository.moveCategory(update.getLongExtra("categoryId", 1L), 2)
                "reorder-section" ->
                    repository.moveSection(update.getLongExtra("sectionId", 200101L), 3)
                "delete-section" ->
                    repository.getSectionById(update.getLongExtra("sectionId", 200101L))?.let {
                        repository.deleteSection(it.id)
                    }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("rhine06-display", display.encode())
        super.onSaveInstanceState(outState)
    }
}

/**
 * Keeps only the isolated fixture through Activity recreation, like the production singleton
 * repository.
 */
class RhineFixtureModel : androidx.lifecycle.ViewModel() {
    val metrics = RhineMetrics()
    var db: TaskDatabase? = null
    var repository: TaskRepo? = null
    var store: SettingsDatastoreImpl? = null
    val scope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    override fun onCleared() {
        db?.close()
        scope.cancel()
    }
}

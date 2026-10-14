package com.shub39.grit

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room3.Room
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import com.shub39.grit.core.data.GritIntentReceiver
import com.shub39.grit.core.data.notification.GritNotificationManager
import com.shub39.grit.core.data.notification.NotificationAlarmScheduler
import com.shub39.grit.core.interfaces.AlarmScheduler
import com.shub39.grit.core.interfaces.IntentActions
import com.shub39.grit.core.settings.backup.RhineBackup
import com.shub39.grit.core.tasks.*
import com.shub39.grit.rhine.RhineMetrics
import com.shub39.grit.shared.ui.rhine.*
import com.shub39.grit.tasks.data.database.*
import com.shub39.grit.tasks.data.repository.TasksRepository
import com.shub39.grit.widgets.all_tasks_widget.AllTasksWidgetReceiver
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import org.junit.Assert.*
import org.junit.Test

class RhineIntegrationRepositoryTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private fun repository(db: TaskDatabase) =
        TasksRepository(
            context,
            db,
            object : AlarmScheduler {
                override fun schedule(task: Task) {}

                override fun cancel(task: Task) {}

                override fun cancelAll() {}
            },
            GritNotificationManager(context),
        )

    @Test
    fun tenThousandTasksAndThousandSectionsRemainWindowed() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder<TaskDatabase>(context).build()
        try {
            val now = Clock.System.now()
            db.withWriteTransaction {
                repeat(100) { v ->
                    val category = v + 1L
                    db.categoryDao()
                        .upsert(
                            CategoryEntity(category, "Category $v", "", v, null, true, now, now)
                        )
                    repeat(100) { u ->
                        val id = category * 1000 + u
                        db.taskDao()
                            .upsert(
                                TaskEntity(
                                    id,
                                    category,
                                    "重名长标题",
                                    "正文不能被概览读取".repeat(100),
                                    u,
                                    null,
                                    u % 2 == 0,
                                    false,
                                    null,
                                    now,
                                    now,
                                )
                            )
                    }
                }
                repeat(1000) { i ->
                    db.sectionDao()
                        .upsert(
                            SectionEntity(
                                i + 1L,
                                1000,
                                "Section $i",
                                "Section 正文".repeat(100),
                                i,
                                null,
                                now,
                                now,
                            )
                        )
                }
            }
            val stats = RhineMetrics()
            val repo = stats.wrap(repository(db))
            val source = RepositoryRhineDataSource(repo)
            val catalog = source.catalog().first()
            assertEquals(100, catalog.categories.size)
            assertEquals(10000, catalog.counts.sumOf { it.count })
            val columns = source.window(catalog, SlotWindow(90, 105, 49, 51)).first()
            assertEquals(33, columns.sumOf { it.items.size })
            assertTrue(
                columns.filterIndexed { index, _ -> index !in 49..51 }.all { it.items.isEmpty() }
            )
            val lower = SectionRhineDataSource(repo, 1)
            val sections =
                lower.sectionWindow(lower.catalog().first(), SlotWindow(990, 1010, 0, 0)).first()
            assertEquals(11, sections.first().items.size)
            assertEquals(1000, sections.first().itemCount)
            assertEquals(0, stats.json().getInt("bodyReads"))
            assertTrue(stats.json().getInt("maxPage") <= 21)
            val before = repo.getCategoryCountsFlow().first()
            repeat(20) { source.window(catalog, SlotWindow(it * 3, it * 3 + 12, 48, 52)).first() }
            assertEquals(before, repo.getCategoryCountsFlow().first())
            assertEquals(0, stats.json().getInt("activePages"))
            assertEquals(0, stats.json().getInt("bodyReads"))
        } finally {
            db.close()
        }
    }

    @Test
    fun taskAndRhineFlowsUpdateAndBackupReplacementInvalidatesGeneration() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder<TaskDatabase>(context).build()
        try {
            val repo = repository(db)
            val c = repo.upsertCategory(Category(name = "互通"))
            val id =
                repo.upsertTask(
                    Task(
                        categoryId = c,
                        title = "原始",
                        sections = listOf(Section(title = "a"), Section(title = "b")),
                    )
                )
            val source = RepositoryRhineDataSource(repo)
            val observed =
                async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(5000) {
                        source.summaries(c, 0, 10).first { it.single().title == "新标题" }
                    }
                }
            repo.upsertTask(repo.getTaskById(id)!!.copy(title = "新标题"))
            assertEquals(id, observed.await().single().id)
            val date = LocalDate(2026, 1, 2)
            repo.getTaskById(id)!!.sections.forEach {
                repo.upsertSection(it.copy(completedOn = date))
            }
            assertFalse(repo.getTaskById(id)!!.isCompleted)
            assertEquals(
                2,
                repo.getSectionSummariesFlow(id, 0, 10).first().count { it.completedOn == date },
            )
            val current = repo.getTaskById(id)!!
            repo.upsertTask(current.copy(isCompleted = true))
            assertEquals(current.sections, repo.getTaskById(id)!!.sections)
            assertTrue(repo.getTasksFlow().first().values.flatten().single().isCompleted)
            val oldGeneration = repo.dataGeneration().first()
            val backup = repo.exportSnapshot()
            repo.replaceData(RhineBackup.decode(RhineBackup.json.encodeToString(backup)))
            assertNotEquals(oldGeneration, repo.dataGeneration().first())
            assertEquals(backup.tasks, repo.getTasks())
            assertEquals(backup.categories, repo.getCategories())
        } finally {
            db.close()
        }
    }

    @Test
    fun androidReminderCreationModificationCompletionAndCancellationUseStableIdentity() {
        val scheduler = NotificationAlarmScheduler(context)
        val future = (Clock.System.now() + 2.hours).toLocalDateTime(TimeZone.currentSystemDefault())
        val a = Task(id = 9070001, categoryId = 1, title = "隔离提醒", reminder = future)
        val b = a.copy(id = 9070002)
        fun pending(task: Task) =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, GritIntentReceiver::class.java).apply {
                    action = IntentActions.TASK_NOTIFICATION.action
                    data = Uri.parse("grit-rhine://task/${task.id}")
                    putExtra("task_id", task.id)
                },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
        try {
            scheduler.schedule(a)
            scheduler.schedule(b)
            assertNotNull(pending(a))
            assertNotNull(pending(b))
            assertNotEquals(pending(a), pending(b))
            scheduler.schedule(
                a.copy(
                    reminder =
                        (Clock.System.now() + 3.hours).toLocalDateTime(
                            TimeZone.currentSystemDefault()
                        )
                )
            )
            assertNotNull(pending(a))
            scheduler.schedule(a.copy(isCompleted = true))
            assertNull(pending(a))
            assertNotNull(pending(b))
            scheduler.schedule(
                a.copy(
                    reminder =
                        (Clock.System.now() - 1.hours).toLocalDateTime(
                            TimeZone.currentSystemDefault()
                        )
                )
            )
            assertNull(pending(a))
            scheduler.cancel(b)
            assertNull(pending(b))
        } finally {
            scheduler.cancel(a)
            scheduler.cancel(b)
        }
    }

    @Test
    fun hiddenTaskPageStopsBodySubscriptionAndReopeningGetsRhineChanges() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder<TaskDatabase>(context).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file =
            java.io.File(
                context.cacheDir,
                "rhine07-task-subscription-${java.util.UUID.randomUUID()}.preferences_pb",
            )
        val store =
            com.shub39.grit.core.data.datastore.SettingsDatastoreImpl(
                context,
                androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                    scope = scope
                ) {
                    file
                },
            )
        val models = androidx.lifecycle.ViewModelStore()
        val active = java.util.concurrent.atomic.AtomicInteger()
        val emissions = java.util.concurrent.atomic.AtomicInteger()
        try {
            val repo = repository(db)
            val c = repo.upsertCategory(Category(name = "isolated"))
            val id =
                repo.upsertTask(Task(categoryId = c, title = "Before", description = "full body"))
            val tracked =
                object : TaskRepo by repo {
                    override fun getTasksFlow() =
                        repo
                            .getTasksFlow()
                            .onStart { active.incrementAndGet() }
                            .onEach { emissions.incrementAndGet() }
                            .onCompletion { active.decrementAndGet() }
                }
            val model =
                com.shub39.grit.shared.ui.viewmodel.TasksViewModel(
                    tracked,
                    object : AlarmScheduler {
                        override fun schedule(task: Task) {}

                        override fun cancel(task: Task) {}

                        override fun cancelAll() {}
                    },
                    store,
                )
            models.put("task", model)
            val visible = launch { model.state.collect {} }
            withTimeout(5000) {
                model.state.first { it.tasks.values.flatten().any { t -> t.id == id } }
            }
            assertEquals(1, active.get())
            visible.cancelAndJoin()
            withTimeout(5000) { while (active.get() != 0) delay(20) }
            val before = emissions.get()
            repo.upsertTask(repo.getTaskById(id)!!.copy(title = "AfterRhine"))
            delay(200)
            assertEquals(0, active.get())
            assertEquals(before, emissions.get())
            val fresh =
                withTimeout(5000) {
                    model.state.first {
                        it.tasks.values.flatten().any { t -> t.title == "AfterRhine" }
                    }
                }
            assertEquals(id, fresh.tasks.values.flatten().single().id)
            assertEquals("full body", fresh.tasks.values.flatten().single().description)
        } finally {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
                models.clear()
            }
            scope.cancel()
            db.close()
            file.delete()
        }
    }

    @Test
    fun onlyTaskWidgetProviderIsRegistered() {
        val providers =
            AppWidgetManager.getInstance(context).installedProviders.filter {
                it.provider.packageName == context.packageName
            }
        assertEquals(
            listOf(ComponentName(context, AllTasksWidgetReceiver::class.java)),
            providers.map { it.provider },
        )
    }
}

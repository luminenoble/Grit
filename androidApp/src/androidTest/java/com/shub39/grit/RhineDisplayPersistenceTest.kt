package com.shub39.grit

import android.content.Context
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.*
import androidx.test.core.app.ApplicationProvider
import com.shub39.grit.core.data.datastore.SettingsDatastoreImpl
import com.shub39.grit.core.settings.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class RhineDisplayPersistenceTest {
    @Test
    fun androidLargeFontKeepsSingleLineAndScalesTextOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun measured(scale: Float): androidx.compose.ui.text.TextLayoutResult {
            val measurer =
                androidx.compose.ui.text.TextMeasurer(
                    defaultFontFamilyResolver =
                        androidx.compose.ui.text.font.createFontFamilyResolver(context),
                    defaultDensity = androidx.compose.ui.unit.Density(2.625f, scale),
                    defaultLayoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
                )
            return com.shub39.grit.shared.ui.rhine.measureRhineFaceText(
                measurer,
                "长中文标题和 English title".repeat(10),
                androidx.compose.ui.graphics.Color.Black,
                12.sp,
                false,
                500,
            )
        }
        val regular = measured(1f)
        val large = measured(1.6f)
        assertEquals(1, large.lineCount)
        assertTrue(large.isLineEllipsized(0))
        assertTrue(large.size.height > regular.size.height)
    }

    @Test
    fun dedicatedDatastoreReopensAndResetKeepsOtherPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "rhine06-${System.nanoTime()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var data = PreferenceDataStoreFactory.create(scope = scope) { file }
        var store = SettingsDatastoreImpl(context, data)
        try {
            store.setIs24Hr(true)
            store.setNotifications(true)
            store.setStartingPage(Sections.Rhine)
            val c =
                RhineDisplayConfig()
                    .preset(RhineDensity.Compact)
                    .copy(topSp = 12f, taskNumbers = false, reduceMotion = true)
            store.setRhineDisplay(c)
            assertEquals(c, store.getRhineDisplay().first())
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            data = PreferenceDataStoreFactory.create(scope = scope) { file }
            store = SettingsDatastoreImpl(context, data)
            assertEquals(c, store.getRhineDisplay().first())
            store.setRhineDisplay(RhineDisplayConfig())
            assertEquals(RhineDisplayConfig(), store.getRhineDisplay().first())
            assertTrue(store.getIs24Hr().first())
            assertTrue(store.getNotificationsFlow().first())
            assertEquals(Sections.Rhine, store.getStartingSectionPref().first())
            data.edit {
                it[stringPreferencesKey("rhine_display_v1")] = "{\"version\":999,\"topSp\":19}"
            }
            assertEquals(RhineDisplayConfig(), store.getRhineDisplay().first())
            data.edit { it[stringPreferencesKey("rhine_display_v1")] = "broken" }
            assertEquals(RhineDisplayConfig(), store.getRhineDisplay().first())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test
    fun allDisplayChangesLeaveFullBusinessSnapshotAndBackupUnchanged() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db =
            androidx.room3.Room.inMemoryDatabaseBuilder<
                    com.shub39.grit.tasks.data.database.TaskDatabase
                >(
                    context
                )
                .build()
        val file = File(context.cacheDir, "rhine06-business-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store =
            SettingsDatastoreImpl(
                context,
                PreferenceDataStoreFactory.create(scope = scope) { file },
            )
        val repo =
            com.shub39.grit.tasks.data.repository.TasksRepository(
                context,
                db,
                object : com.shub39.grit.core.interfaces.AlarmScheduler {
                    override fun schedule(task: com.shub39.grit.core.tasks.Task) {}

                    override fun cancel(task: com.shub39.grit.core.tasks.Task) {}

                    override fun cancelAll() {}
                },
                com.shub39.grit.core.data.notification.GritNotificationManager(context),
            )
        try {
            val id =
                repo.upsertCategory(
                    com.shub39.grit.core.tasks.Category(
                        name = "C",
                        description = "description",
                        titleColor = "#F0FFFF",
                    )
                )
            repo.upsertTask(
                com.shub39.grit.core.tasks.Task(
                    categoryId = id,
                    title = "T",
                    description = "long body",
                    isCompleted = true,
                    isToday = true,
                    titleColor = "#FFFFFF",
                    sections =
                        listOf(
                            com.shub39.grit.core.tasks.Section(
                                title = "S",
                                content = "content",
                                completedOn = kotlinx.datetime.LocalDate(2026, 1, 2),
                            )
                        ),
                )
            )
            val before = repo.exportSnapshot()
            for (density in RhineDensity.entries) for (shadow in RhineShadow.entries) {
                store.setRhineDisplay(
                    RhineDisplayConfig()
                        .preset(density)
                        .copy(
                            shadow = shadow,
                            placeholderNames = true,
                            reduceMotion = true,
                            topSp = 20f,
                        )
                )
                assertEquals(before, repo.exportSnapshot().copy(exportedAt = before.exportedAt))
            }
            store.setRhineDisplay(RhineDisplayConfig())
            assertEquals(before, repo.exportSnapshot().copy(exportedAt = before.exportedAt))
        } finally {
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}

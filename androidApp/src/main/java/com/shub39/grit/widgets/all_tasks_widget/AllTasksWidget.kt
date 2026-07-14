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
package com.shub39.grit.widgets.all_tasks_widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.shub39.grit.R
import com.shub39.grit.app.MainActivity
import com.shub39.grit.core.interfaces.SettingsDatastore
import com.shub39.grit.core.settings.WidgetTextSize
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.CategoryColors
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.core.tasks.TaskRepo
import com.shub39.grit.widgets.WidgetSize
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class AllTasksWidget : GlanceAppWidget(), KoinComponent {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = get<TaskRepo>()
        val settings = get<SettingsDatastore>()

        provideContent {
            val scope = rememberCoroutineScope()
            val size = LocalSize.current
            val tasks by repo.getTasksFlow().collectAsState(emptyMap())
            val textSize by settings.getWidgetTextSizeFlow().collectAsState(WidgetTextSize.MEDIUM)

            key(size) {
                GlanceTheme {
                    Content(
                        tasks = tasks.filter { it.value.isNotEmpty() },
                        textSize = textSize,
                        onUpdateTaskStatus = {
                            scope.launch { repo.upsertTask(it.copy(status = !it.status)) }
                        },
                        onUpdateWidget = {
                            scope.launch { this@AllTasksWidget.update(context, id) }
                        },
                    )
                }
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val previewItems =
            mapOf(
                Category(name = "Chores", index = 1, color = CategoryColors.GRAY.color) to
                    listOf(
                        Task(
                            id = 1,
                            categoryId = 1,
                            title = "Laundry",
                            index = 1,
                            status = false,
                            reminder = null,
                        ),
                        Task(
                            id = 2,
                            categoryId = 1,
                            title =
                                "Watch a 5 hour long video essay on a video game i will never play",
                            index = 2,
                            status = false,
                            reminder = null,
                        ),
                        Task(
                            id = 3,
                            categoryId = 1,
                            title = "Get Groceries, Meat",
                            index = 3,
                            status = true,
                            reminder = null,
                        ),
                    )
            )

        provideContent {
            Content(tasks = previewItems, onUpdateTaskStatus = {}, onUpdateWidget = {})
        }
    }
}

@Composable
@GlanceComposable
private fun Content(
    tasks: Map<Category, List<Task>>,
    onUpdateTaskStatus: (Task) -> Unit,
    onUpdateWidget: () -> Unit,
    modifier: GlanceModifier = GlanceModifier,
    textSize: WidgetTextSize = WidgetTextSize.MEDIUM,
) {
    val size = LocalSize.current
    val roundedCornerSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Smaller text tightens paddings too, so small widgets fit more tasks.
    val taskFontSize =
        when (textSize) {
            WidgetTextSize.SMALL -> 11.sp
            WidgetTextSize.MEDIUM -> 14.sp
            WidgetTextSize.LARGE -> 17.sp
        }
    val taskTextPadding =
        when (textSize) {
            WidgetTextSize.SMALL -> 4.dp
            WidgetTextSize.MEDIUM -> 8.dp
            WidgetTextSize.LARGE -> 10.dp
        }
    val taskItemSpacing =
        when (textSize) {
            WidgetTextSize.SMALL -> 2.dp
            else -> 4.dp
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_glass_bg))
                .then(
                    if (roundedCornerSupported) GlanceModifier.cornerRadius(24.dp)
                    else GlanceModifier
                )
                .clickable(actionStartActivity<MainActivity>())
    ) {
        TitleBar(
            startIcon = ImageProvider(R.drawable.check_list),
            title = "Tasks",
            actions = {
                if (size.width >= WidgetSize.Width4) {
                    Box(GlanceModifier.padding(horizontal = 16.dp)) {
                        Image(
                            provider = ImageProvider(R.drawable.refresh),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                            modifier = GlanceModifier.clickable { onUpdateWidget() },
                        )
                    }
                }
            },
        )

        LazyColumn(
            modifier = GlanceModifier.padding(horizontal = 8.dp).fillMaxSize(),
            horizontalAlignment = Alignment.Start,
        ) {
            items(tasks.entries.toList(), itemId = { it.key.id }) { taskGroup ->
                Column {
                    Text(
                        text = taskGroup.key.name,
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = taskFontSize,
                                color = GlanceTheme.colors.onSurfaceVariant,
                            ),
                    )
                    Spacer(GlanceModifier.height(taskItemSpacing * 2))
                    taskGroup.value.forEach { task ->
                        val status = task.status

                        Column {
                            Column(
                                modifier =
                                    GlanceModifier.fillMaxWidth()
                                        .background(
                                            ImageProvider(
                                                if (!status) R.drawable.widget_glass_item
                                                else R.drawable.widget_glass_item_done
                                            )
                                        )
                                        .then(
                                            if (roundedCornerSupported)
                                                GlanceModifier.cornerRadius(16.dp)
                                            else GlanceModifier
                                        )
                                        .padding(
                                            horizontal = taskTextPadding,
                                            vertical = taskItemSpacing,
                                        )
                                        .clickable {
                                            onUpdateTaskStatus(task)
                                            onUpdateWidget()
                                        }
                            ) {
                                Text(
                                    text = task.title,
                                    modifier =
                                        GlanceModifier.fillMaxWidth().padding(taskTextPadding),
                                    style =
                                        TextStyle(
                                            fontSize = taskFontSize,
                                            color =
                                                if (!status) {
                                                    GlanceTheme.colors.onSurface
                                                } else GlanceTheme.colors.onSurfaceVariant,
                                            textDecoration =
                                                if (!status) {
                                                    TextDecoration.None
                                                } else TextDecoration.LineThrough,
                                        ),
                                    maxLines = 2,
                                )
                            }
                            Spacer(GlanceModifier.height(taskItemSpacing))
                        }
                    }
                }
            }

            item { Spacer(modifier = GlanceModifier.height(4.dp)) }
        }
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(heightDp = 200, widthDp = 240)
@Preview(heightDp = 200, widthDp = 300)
@Composable
private fun GlancePreview() {
    Content(
        tasks =
            (0..3).associate {
                Category(
                    id = it.toLong(),
                    name = "Category $it",
                    index = it,
                    color = CategoryColors.GRAY.color,
                ) to
                    (0..1).map { taskId ->
                        Task(
                            id = taskId.toLong(),
                            categoryId = it.toLong(),
                            title = "Task $taskId, Category $it",
                            index = it,
                            status = false,
                            reminder = null,
                        )
                    }
            },
        onUpdateTaskStatus = {},
        onUpdateWidget = {},
    )
}

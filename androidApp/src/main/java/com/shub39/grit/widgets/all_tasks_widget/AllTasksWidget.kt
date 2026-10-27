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
import android.graphics.Paint
import android.os.Build
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.shub39.grit.R
import com.shub39.grit.app.MainActivity
import com.shub39.grit.core.interfaces.SettingsDatastore
import com.shub39.grit.core.settings.WidgetTextSize
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.core.tasks.TaskRepo
import com.shub39.grit.core.tasks.titleColorStops
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.RhineBaseColors
import com.shub39.grit.shared.ui.theme.titleColor
import com.shub39.grit.widgets.WidgetSize
import com.shub39.grit.widgets.bodyFontSize
import com.shub39.grit.widgets.captionFontSize
import com.shub39.grit.widgets.gradientTitle
import com.shub39.grit.widgets.itemPadding
import com.shub39.grit.widgets.itemSpacing
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class AllTasksWidget : GlanceAppWidget(), KoinComponent {

    companion object {
        /** Per-widget display scope: SCOPE_ALL, SCOPE_TODAY, or a category id. */
        private val scopeKey = longPreferencesKey("scope_category_id")
        private const val SCOPE_ALL = 0L
        private const val SCOPE_TODAY = -1L
    }

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = get<TaskRepo>()
        val settings = get<SettingsDatastore>()
        val data = repo.getTasksFlow()
        val initialData = data.first()
        // Read the stored size before the first frame: starting from a default made every new
        // widget session draw at Medium first and then jump to the chosen size.
        val textSizes = settings.getWidgetTextSizeFlow()
        val initialTextSize = textSizes.first()

        provideContent {
            val scope = rememberCoroutineScope()
            val size = LocalSize.current
            val tasks by data.collectAsState(initialData)
            val textSize by textSizes.collectAsState(initialTextSize)

            val widgetScope = currentState<Preferences>()[scopeKey] ?: SCOPE_ALL
            val scopedTasks =
                when (widgetScope) {
                    SCOPE_ALL -> tasks
                    SCOPE_TODAY -> tasks.mapValues { entry -> entry.value.filter { it.isToday } }
                    else -> tasks.filterKeys { it.id == widgetScope }
                }
                // Categories flagged hideCompleted keep their done tasks out of the widget too.
                .mapValues { (category, list) ->
                    if (category.hideCompleted) list.filter { !it.isCompleted } else list
                }
            val title =
                when (widgetScope) {
                    SCOPE_ALL -> "Tasks"
                    SCOPE_TODAY -> "Today"
                    else -> tasks.keys.firstOrNull { it.id == widgetScope }?.name ?: "Tasks"
                }

            key(size) {
                GlanceTheme(colors = ColorProviders(RhineBaseColors, RhineBaseColors)) {
                    Content(
                        tasks = scopedTasks.filter { it.value.isNotEmpty() },
                        title = title,
                        textSize = textSize,
                        onUpdateTaskStatus = {
                            scope.launch {
                                val current = repo.getTaskById(it.id) ?: return@launch
                                repo.upsertTask(current.copy(isCompleted = !current.isCompleted))
                            }
                        },
                        onUpdateWidget = {
                            scope.launch { this@AllTasksWidget.update(context, id) }
                        },
                        onCycleScope = {
                            val order = listOf(SCOPE_ALL, SCOPE_TODAY) + tasks.keys.map { it.id }
                            val next =
                                order[
                                    (order.indexOf(widgetScope).coerceAtLeast(0) + 1) % order.size]

                            scope.launch {
                                updateAppWidgetState(context, id) { prefs ->
                                    prefs[scopeKey] = next
                                }
                                this@AllTasksWidget.update(context, id)
                            }
                        },
                    )
                }
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val previewItems =
            mapOf(
                Category(name = "Chores", orderIndex = 1, titleColor = null) to
                    listOf(
                        Task(
                            id = 1,
                            categoryId = 1,
                            title = "Laundry",
                            orderIndex = 1,
                            isCompleted = false,
                            reminder = null,
                        ),
                        Task(
                            id = 2,
                            categoryId = 1,
                            title =
                                "Watch a 5 hour long video essay on a video game i will never play",
                            orderIndex = 2,
                            isCompleted = false,
                            reminder = null,
                        ),
                        Task(
                            id = 3,
                            categoryId = 1,
                            title = "Get Groceries, Meat",
                            orderIndex = 3,
                            isCompleted = true,
                            reminder = null,
                        ),
                    )
            )

        provideContent {
            Content(tasks = previewItems, onUpdateTaskStatus = {}, onUpdateWidget = {})
        }
    }
}

/**
 * One task row (docs/项目概览.md §5): glass background, the title in its stored colour (gradients
 * included) and the step count, all in one layout of our own. Glance text takes no gradient brush,
 * so the title is a TextView whose glyphs each carry their share of it; and Glance misplaces a
 * RemoteViews nested inside its own background container, losing the row's other children, so the
 * whole row is ours.
 */
@Composable
@GlanceComposable
private fun TaskRow(
    task: Task,
    textSize: WidgetTextSize,
    titleWidth: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    fun px(dp: Dp) = (dp.value * metrics.density).roundToInt()
    val pad = textSize.itemPadding
    val titleSp = textSize.bodyFontSize.value
    val stops = titleColorStops(task.titleColor)?.map { it or 0xFF000000.toInt() }
    val paint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, titleSp, metrics)
        }
    val steps = task.sections.size
    val titlePad = if (steps == 0) pad else pad / 2
    val views =
        RemoteViews(context.packageName, R.layout.widget_task_row).apply {
            setInt(
                R.id.widget_task_row,
                "setBackgroundResource",
                if (task.isCompleted) R.drawable.widget_glass_item_done
                else R.drawable.widget_glass_item,
            )
            setViewPadding(
                R.id.widget_task_row,
                px(pad),
                px(textSize.itemSpacing),
                px(pad),
                px(textSize.itemSpacing),
            )
            setViewPadding(R.id.widget_task_title, px(pad), px(titlePad), px(pad), px(titlePad))
            setTextViewTextSize(R.id.widget_task_title, TypedValue.COMPLEX_UNIT_SP, titleSp)
            setTextColor(R.id.widget_task_title, stops?.first() ?: titleColor(null).toArgb())
            setTextViewText(
                R.id.widget_task_title,
                gradientTitle(task.title, stops.orEmpty(), paint, px(titleWidth)),
            )
            // Step progress, e.g. "2/5 sections".
            if (steps > 0) {
                setViewVisibility(R.id.widget_task_steps, View.VISIBLE)
                setViewPadding(R.id.widget_task_steps, px(pad), 0, px(pad), px(pad / 2))
                setTextViewTextSize(
                    R.id.widget_task_steps,
                    TypedValue.COMPLEX_UNIT_SP,
                    textSize.captionFontSize.value,
                )
                setTextColor(R.id.widget_task_steps, GlassTokens.SecondaryText.toArgb())
                setTextViewText(
                    R.id.widget_task_steps,
                    "${task.sections.count { it.isCompleted }}/$steps sections",
                )
            }
        }
    AndroidRemoteViews(views, modifier)
}

@Composable
@GlanceComposable
private fun Content(
    tasks: Map<Category, List<Task>>,
    onUpdateTaskStatus: (Task) -> Unit,
    onUpdateWidget: () -> Unit,
    modifier: GlanceModifier = GlanceModifier,
    textSize: WidgetTextSize = WidgetTextSize.MEDIUM,
    title: String = "Tasks",
    onCycleScope: (() -> Unit)? = null,
) {
    val size = LocalSize.current
    val roundedCornerSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Smaller text tightens paddings too, so small widgets fit more tasks.
    val taskFontSize = textSize.bodyFontSize
    val taskTextPadding = textSize.itemPadding
    val taskItemSpacing = textSize.itemSpacing

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
            title = title,
            actions = {
                if (onCycleScope != null) {
                    Box(GlanceModifier.padding(horizontal = 8.dp)) {
                        Image(
                            provider = ImageProvider(R.drawable.arrow_forward),
                            contentDescription = "Switch category",
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                            modifier = GlanceModifier.clickable { onCycleScope() },
                        )
                    }
                }

                if (size.width >= WidgetSize.Width4) {
                    Box(GlanceModifier.padding(end = 16.dp, start = 8.dp)) {
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
                                color = ColorProvider(GlassTokens.SecondaryText),
                            ),
                    )
                    Spacer(GlanceModifier.height(taskItemSpacing * 2))
                    taskGroup.value.forEach { task ->
                        // The row is the column's only child; the gap below is padding, not a
                        // sibling of the row.
                        Column(GlanceModifier.fillMaxWidth().padding(bottom = taskItemSpacing)) {
                            TaskRow(
                                task = task,
                                textSize = textSize,
                                // List, row and title paddings on both sides.
                                titleWidth = size.width - 16.dp - taskTextPadding * 4,
                                modifier =
                                    GlanceModifier.fillMaxWidth().clickable {
                                        onUpdateTaskStatus(task)
                                    },
                            )
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
                    orderIndex = it,
                    titleColor = null,
                ) to
                    (0..1).map { taskId ->
                        Task(
                            id = taskId.toLong(),
                            categoryId = it.toLong(),
                            title = "Task $taskId, Category $it",
                            orderIndex = it,
                            isCompleted = false,
                            reminder = null,
                        )
                    }
            },
        onUpdateTaskStatus = {},
        onUpdateWidget = {},
    )
}

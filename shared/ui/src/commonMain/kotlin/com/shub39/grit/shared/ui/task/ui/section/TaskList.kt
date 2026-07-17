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
package com.shub39.grit.shared.ui.task.ui.section

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.toShape
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.CategoryColors
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.core.tasks.TaskStep
import com.shub39.grit.shared.ui.LocalWindowSizeClass
import com.shub39.grit.shared.ui.components.Empty
import com.shub39.grit.shared.ui.components.GritDialog
import com.shub39.grit.shared.ui.components.PageFill
import com.shub39.grit.shared.ui.components.detachedItemShape
import com.shub39.grit.shared.ui.components.endItemShape
import com.shub39.grit.shared.ui.components.leadingItemShape
import com.shub39.grit.shared.ui.components.middleItemShape
import com.shub39.grit.shared.ui.task.TaskAction
import com.shub39.grit.shared.ui.task.TaskState
import com.shub39.grit.shared.ui.task.ui.component.AiDecomposeSheet
import com.shub39.grit.shared.ui.task.ui.component.TaskCard
import com.shub39.grit.shared.ui.theme.GritRadius
import com.shub39.grit.shared.ui.theme.accentVariant
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import com.shub39.grit.shared.ui.theme.flexFontRounded
import com.shub39.grit.shared.ui.theme.parseAccentColor
import grit.shared.ui.generated.resources.*
import grit.shared.ui.generated.resources.add
import kotlin.invoke
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Sentinel id for the virtual "Today" category, which is not persisted. */
private const val TODAY_VIEW_ID = -1L

@Composable
fun TaskList(
    state: TaskState,
    onAction: (TaskAction) -> Unit,
    onEditCategories: () -> Unit,
    onOpenTaskDetails: (Task) -> Unit,
    onAddTask: () -> Unit,
    onAddCategory: () -> Unit,
) =
    PageFill {
        val windowSizeClass = LocalWindowSizeClass.current

        var showDeleteDialog by remember { mutableStateOf(false) }
        var showAiSheet by remember { mutableStateOf(false) }
        var editState by remember { mutableStateOf(false) }

        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

        Column(
            modifier =
                Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
        ) {
            TaskListTopBar(
                state = state,
                scrollBehavior = scrollBehavior,
                isReorderMode = editState,
                onReorderToggle = { editState = it },
                onDeleteClick = { showDeleteDialog = true },
                onAiClick = { showAiSheet = true },
                isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded,
            )

            CategorySelector(
                state = state,
                isReorderMode = editState,
                onAction = onAction,
                onAddCategoryClick = onAddCategory,
                onEditCategoriesClick = onEditCategories,
                isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded,
                onReorderModeChange = { editState = it },
            )

            if (windowSizeClass.widthSizeClass != WindowWidthSizeClass.Expanded) {
                CompactTasksView(
                    state = state,
                    isReorderMode = editState,
                    onAction = onAction,
                    onEditTask = onOpenTaskDetails,
                    isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact,
                )
            } else {
                ExpandedTasksView(
                    state = state,
                    onAction = onAction,
                    onEditTask = onOpenTaskDetails,
                )
            }
        }

        MediumFloatingActionButton(
            onClick = onAddTask,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier =
                Modifier.align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .then(
                        if (windowSizeClass.widthSizeClass != WindowWidthSizeClass.Expanded)
                            Modifier
                        else Modifier.navigationBarsPadding()
                    )
                    .animateFloatingActionButton(
                        visible = state.currentCategory != null,
                        alignment = Alignment.BottomEnd,
                        scaleAnimationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        alphaAnimationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.add),
                    contentDescription = null,
                    modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize),
                )
                AnimatedVisibility(
                    visible =
                        state.tasks[state.currentCategory].isNullOrEmpty() ||
                            windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded,
                    enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    Text(
                        text = stringResource(Res.string.add_task),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        if (showDeleteDialog) {
            DeleteTasksDialog(
                onDismiss = { showDeleteDialog = false },
                onConfirm = {
                    onAction(TaskAction.DeleteTasks)
                    showDeleteDialog = false
                },
            )
        }

        if (showAiSheet && state.currentCategory != null) {
            val category = state.currentCategory

            AiDecomposeSheet(
                onDismiss = { showAiSheet = false },
                onCreateTask = { title, description, steps ->
                    onAction(
                        TaskAction.UpsertTask(
                            Task(
                                categoryId = category.id,
                                title = title,
                                description = description,
                                steps = steps.map { TaskStep(it) },
                                index = state.tasks[category]?.size ?: 0,
                                status = false,
                                reminder = null,
                                isToday = state.isTodayView,
                            )
                        )
                    )
                },
            )
        }
    }

@Composable
private fun TaskListTopBar(
    state: TaskState,
    scrollBehavior: TopAppBarScrollBehavior,
    isReorderMode: Boolean,
    onReorderToggle: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    onAiClick: () -> Unit,
    isExpanded: Boolean,
) {
    LargeFlexibleTopAppBar(
        colors =
            TopAppBarDefaults.topAppBarColors(
                scrolledContainerColor = MaterialTheme.colorScheme.surface
            ),
        scrollBehavior = scrollBehavior,
        title = { Text(text = stringResource(Res.string.tasks), fontFamily = flexFontEmphasis()) },
        subtitle = {
            Text(
                text = "${state.completedTasks.size} " + stringResource(Res.string.items_completed),
                fontFamily = flexFontRounded(),
            )
        },
        actions = {
            val motionScheme = MaterialTheme.motionScheme

            FilledTonalIconButton(onClick = onAiClick, enabled = state.currentCategory != null) {
                Icon(
                    imageVector = vectorResource(Res.drawable.check_list),
                    contentDescription = "AI 分解任务",
                )
            }

            AnimatedVisibility(
                visible = state.completedTasks.isNotEmpty(),
                enter = fadeIn(motionScheme.fastEffectsSpec()),
                exit = fadeOut(motionScheme.fastEffectsSpec()),
            ) {
                OutlinedIconButton(
                    onClick = onDeleteClick,
                    shapes =
                        IconButtonShapes(
                            shape = CircleShape,
                            pressedShape = MaterialTheme.shapes.small,
                        ),
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.delete),
                        contentDescription = null,
                    )
                }
            }

            AnimatedVisibility(
                visible = state.tasks.values.isNotEmpty() && !isExpanded,
                enter = fadeIn(motionScheme.fastEffectsSpec()),
                exit = fadeOut(motionScheme.fastEffectsSpec()),
            ) {
                FilledTonalIconToggleButton(
                    checked = isReorderMode,
                    shapes =
                        IconToggleButtonShapes(
                            shape = CircleShape,
                            checkedShape = MaterialTheme.shapes.small,
                            pressedShape = MaterialTheme.shapes.extraSmall,
                        ),
                    onCheckedChange = onReorderToggle,
                    enabled = !state.tasks[state.currentCategory].isNullOrEmpty(),
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.reorder),
                        contentDescription = null,
                    )
                }
            }
        },
    )
}

@Composable
private fun CategorySelector(
    state: TaskState,
    isReorderMode: Boolean,
    onAction: (TaskAction) -> Unit,
    onAddCategoryClick: () -> Unit,
    onEditCategoriesClick: () -> Unit,
    isExpanded: Boolean,
    onReorderModeChange: (Boolean) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
    ) {
        if (!isExpanded) {
            // Virtual "Today" category: tasks flagged isToday across all categories.
            item(key = "today_selector") {
                ToggleButton(
                    checked = state.isTodayView,
                    onCheckedChange = {
                        onAction(TaskAction.SelectTodayView)
                        onReorderModeChange(false)
                    },
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.light_mode),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(Res.string.today))
                }
            }

            items(state.tasks.keys.toList(), key = { it.id }) { category ->
                ToggleButton(
                    checked = !state.isTodayView && category == state.currentCategory,
                    onCheckedChange = {
                        onAction(TaskAction.ChangeCategory(category))
                        onReorderModeChange(false)
                    },
                ) {
                    parseAccentColor(category.color)?.let { accent ->
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accent))
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(text = category.name)
                }
            }
            item {
                Spacer(modifier = Modifier.width(4.dp))
                FilledTonalIconButton(onClick = onAddCategoryClick, enabled = !isReorderMode) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.add),
                        contentDescription = "Add Category",
                    )
                }
                FilledTonalIconButton(onClick = onEditCategoriesClick, enabled = !isReorderMode) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.edit),
                        contentDescription = "Edit Categories",
                    )
                }
            }
        } else {
            item {
                FilledTonalButton(onClick = onAddCategoryClick, enabled = !isReorderMode) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.add),
                        contentDescription = "Add Category",
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(Res.string.add_category))
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onEditCategoriesClick, enabled = !isReorderMode) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.edit),
                        contentDescription = "Edit Categories",
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(Res.string.edit_categories))
                }
            }
        }
    }
}

@Composable
private fun CompactTasksView(
    state: TaskState,
    isReorderMode: Boolean,
    onAction: (TaskAction) -> Unit,
    onEditTask: (Task) -> Unit,
    isCompact: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.padding(horizontal = if (isCompact) 0.dp else 16.dp),
    ) {
        val motionScheme = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = if (state.isTodayView) TODAY_VIEW_ID else state.currentCategory?.id,
            transitionSpec = {
                fadeIn(motionScheme.fastEffectsSpec()) togetherWith
                    fadeOut(motionScheme.fastEffectsSpec())
            },
        ) { categoryId ->
            val isTodayView = categoryId == TODAY_VIEW_ID
            val category = state.tasks.keys.firstOrNull { it.id == categoryId }
            if (isTodayView || category != null) {
                // Categories flagged hideCompleted keep their done tasks out of every view.
                val hiddenDoneCategoryIds =
                    remember(state.tasks.keys) {
                        state.tasks.keys.filter { it.hideCompleted }.map { it.id }.toSet()
                    }
                val sourceTasks =
                    (if (isTodayView) state.todayTasks else state.tasks[category] ?: emptyList())
                        .filter { !(it.status && it.categoryId in hiddenDoneCategoryIds) }
                val accentByCategory =
                    remember(state.tasks.keys) {
                        state.tasks.keys.associate { it.id to parseAccentColor(it.color) }
                    }

                val lazyListState = rememberLazyListState()
                var reorderableTasks by
                    remember(state.tasks.values) {
                        mutableStateOf(
                            sourceTasks.run {
                                if (state.reorderTasks) {
                                    filter { !it.status }
                                } else this
                            }
                        )
                    }
                val reorderableListState =
                    rememberReorderableLazyListState(lazyListState) { from, to ->
                        reorderableTasks =
                            reorderableTasks.toMutableList().apply {
                                add(to.index, removeAt(from.index))
                            }
                    }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(items = reorderableTasks, key = { _, it -> it.id }) { index, task
                        ->
                        ReorderableItem(reorderableListState, key = task.id) {
                            val cardShape =
                                when {
                                    reorderableTasks.size == 1 -> RoundedCornerShape(GritRadius.xl)
                                    index == 0 ->
                                        RoundedCornerShape(
                                            topStart = GritRadius.xl,
                                            topEnd = GritRadius.xl,
                                            bottomStart = GritRadius.join,
                                            bottomEnd = GritRadius.join,
                                        )

                                    index == reorderableTasks.size - 1 ->
                                        RoundedCornerShape(
                                            topStart = GritRadius.join,
                                            topEnd = GritRadius.join,
                                            bottomStart = GritRadius.xl,
                                            bottomEnd = GritRadius.xl,
                                        )

                                    else -> RoundedCornerShape(GritRadius.join)
                                }

                            TaskCard(
                                task = task,
                                dragState = isReorderMode && !isTodayView,
                                reorderIcon = {
                                    Icon(
                                        imageVector = vectorResource(Res.drawable.drag_indicator),
                                        contentDescription = "Drag",
                                        modifier =
                                            Modifier.draggableHandle(
                                                onDragStopped = {
                                                    onAction(
                                                        TaskAction.ReorderTasks(
                                                            reorderableTasks.mapIndexed { i, t ->
                                                                i to t
                                                            }
                                                        )
                                                    )
                                                }
                                            ),
                                    )
                                },
                                is24Hr = state.is24Hour,
                                shape = cardShape,
                                onDetailsClick = { onEditTask(task) },
                                accent = accentByCategory[task.categoryId]?.accentVariant(index),
                                onStepsChange = {
                                    onAction(TaskAction.UpsertTask(task.copy(steps = it)))
                                },
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clip(cardShape)
                                        .clickable {
                                            if (!isReorderMode) {
                                                val updatedTask = task.copy(status = !task.status)

                                                onAction(TaskAction.UpsertTask(updatedTask))
                                            }
                                        }
                                        .then(
                                            // Long press drags to reorder; the Today view mixes
                                            // categories whose indices can't be reordered.
                                            if (!isTodayView) {
                                                Modifier.longPressDraggableHandle(
                                                    onDragStopped = {
                                                        onAction(
                                                            TaskAction.ReorderTasks(
                                                                reorderableTasks.mapIndexed { i, t
                                                                    ->
                                                                    i to t
                                                                }
                                                            )
                                                        )
                                                    }
                                                )
                                            } else {
                                                Modifier
                                            }
                                        ),
                            )
                        }
                    }

                    if (state.reorderTasks) {
                        val completedTasks = sourceTasks.filter { it.status }

                        if (reorderableTasks.isNotEmpty()) {
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                        itemsIndexed(
                            items = completedTasks,
                            key = { _, it -> "completed_task_${it.id}" },
                        ) { index, task ->
                            val cardShape =
                                when {
                                    completedTasks.size == 1 -> RoundedCornerShape(GritRadius.xl)
                                    index == 0 ->
                                        RoundedCornerShape(
                                            topStart = GritRadius.xl,
                                            topEnd = GritRadius.xl,
                                            bottomStart = GritRadius.join,
                                            bottomEnd = GritRadius.join,
                                        )

                                    index == completedTasks.size - 1 ->
                                        RoundedCornerShape(
                                            topStart = GritRadius.join,
                                            topEnd = GritRadius.join,
                                            bottomStart = GritRadius.xl,
                                            bottomEnd = GritRadius.xl,
                                        )

                                    else -> RoundedCornerShape(GritRadius.join)
                                }

                            TaskCard(
                                task = task,
                                dragState = false,
                                reorderIcon = {},
                                is24Hr = state.is24Hour,
                                shape = cardShape,
                                onDetailsClick = { onEditTask(task) },
                                accent = accentByCategory[task.categoryId]?.accentVariant(index),
                                onStepsChange = {
                                    onAction(TaskAction.UpsertTask(task.copy(steps = it)))
                                },
                                modifier =
                                    Modifier.fillMaxWidth().clip(cardShape).clickable {
                                        if (!isReorderMode) {
                                            val updatedTask = task.copy(status = !task.status)

                                            onAction(TaskAction.UpsertTask(updatedTask))
                                        }
                                    },
                            )
                        }
                    }

                    if (reorderableTasks.isEmpty()) {
                        item { Empty(modifier = Modifier.padding(top = 150.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedTasksView(
    state: TaskState,
    onAction: (TaskAction) -> Unit,
    onEditTask: (Task) -> Unit,
) {
    val todayCategory =
        Category(
            id = TODAY_VIEW_ID,
            name = stringResource(Res.string.today),
            index = -1,
            color = CategoryColors.GRAY.color,
        )
    // Categories flagged hideCompleted keep their done tasks out of every view.
    val hiddenDoneCategoryIds =
        remember(state.tasks.keys) {
            state.tasks.keys.filter { it.hideCompleted }.map { it.id }.toSet()
        }
    val tasksAndCategories =
        listOf(
            todayCategory to
                state.todayTasks.filter { !(it.status && it.categoryId in hiddenDoneCategoryIds) }
        ) +
            state.tasks.map { (cat, tasks) ->
                cat to if (cat.hideCompleted) tasks.filter { !it.status } else tasks
            }
    val accentByCategory =
        remember(state.tasks.keys) {
            state.tasks.keys.associate { it.id to parseAccentColor(it.color) }
        }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 350.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 60.dp),
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(tasksAndCategories, key = { it.first.id }) { (category, tasks) ->
            val isTodayPanel = category.id == TODAY_VIEW_ID
            val displayTasks = if (state.reorderTasks) tasks.filter { !it.status } else tasks
            var showReorderDialog by remember { mutableStateOf(false) }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(28.dp),
                modifier =
                    Modifier.widthIn(max = 350.dp)
                        .heightIn(max = 1000.dp)
                        .animateContentSize(
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                        ),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(end = 8.dp).weight(1f),
                            )

                            FilledTonalIconToggleButton(
                                checked = showReorderDialog,
                                onCheckedChange = { showReorderDialog = it },
                                enabled = !isTodayPanel && displayTasks.size > 1,
                            ) {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.reorder),
                                    contentDescription = null,
                                )
                            }
                        }
                    }

                    itemsIndexed(items = displayTasks, key = { _, it -> it.id }) { index, task ->
                        val cardShape =
                            when {
                                displayTasks.size == 1 -> RoundedCornerShape(GritRadius.xl)
                                index == 0 ->
                                    RoundedCornerShape(
                                        topStart = GritRadius.xl,
                                        topEnd = GritRadius.xl,
                                        bottomStart = GritRadius.join,
                                        bottomEnd = GritRadius.join,
                                    )

                                index == displayTasks.size - 1 ->
                                    RoundedCornerShape(
                                        topStart = GritRadius.join,
                                        topEnd = GritRadius.join,
                                        bottomStart = GritRadius.xl,
                                        bottomEnd = GritRadius.xl,
                                    )

                                else -> RoundedCornerShape(GritRadius.join)
                            }

                        TaskCard(
                            task = task,
                            dragState = false,
                            reorderIcon = {},
                            is24Hr = state.is24Hour,
                            shape = cardShape,
                            onDetailsClick = { onEditTask(task) },
                            accent = accentByCategory[task.categoryId]?.accentVariant(index),
                            onStepsChange = {
                                onAction(TaskAction.UpsertTask(task.copy(steps = it)))
                            },
                            modifier =
                                Modifier.animateItem().fillMaxWidth().clip(cardShape).clickable {
                                    val updatedTask = task.copy(status = !task.status)
                                    onAction(TaskAction.UpsertTask(updatedTask))
                                },
                        )
                    }

                    if (state.reorderTasks) {
                        val completedTasks = tasks.filter { it.status }

                        if (completedTasks.isNotEmpty()) {
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                        itemsIndexed(items = completedTasks, key = { _, it -> it.id }) { index, task
                            ->
                            val cardShape =
                                when {
                                    completedTasks.size == 1 -> RoundedCornerShape(GritRadius.xl)
                                    index == 0 ->
                                        RoundedCornerShape(
                                            topStart = GritRadius.xl,
                                            topEnd = GritRadius.xl,
                                            bottomStart = GritRadius.join,
                                            bottomEnd = GritRadius.join,
                                        )

                                    index == completedTasks.size - 1 ->
                                        RoundedCornerShape(
                                            topStart = GritRadius.join,
                                            topEnd = GritRadius.join,
                                            bottomStart = GritRadius.xl,
                                            bottomEnd = GritRadius.xl,
                                        )

                                    else -> RoundedCornerShape(GritRadius.join)
                                }

                            TaskCard(
                                task = task,
                                dragState = false,
                                reorderIcon = {},
                                is24Hr = state.is24Hour,
                                shape = cardShape,
                                onDetailsClick = { onEditTask(task) },
                                accent = accentByCategory[task.categoryId]?.accentVariant(index),
                                onStepsChange = {
                                    onAction(TaskAction.UpsertTask(task.copy(steps = it)))
                                },
                                modifier =
                                    Modifier.fillMaxWidth().clip(cardShape).clickable {
                                        val updatedTask = task.copy(status = !task.status)
                                        onAction(TaskAction.UpsertTask(updatedTask))
                                    },
                            )
                        }
                    }
                    if (tasks.isEmpty()) {
                        item { Empty(modifier = Modifier.padding(32.dp)) }
                    }
                }
            }

            if (showReorderDialog) {
                GritDialog(onDismissRequest = { showReorderDialog = false }, padding = 0.dp) {
                    var reorderableTasks = remember { displayTasks }

                    val listState = rememberLazyListState()
                    val reorderableListState =
                        rememberReorderableLazyListState(listState) { from, to ->
                            reorderableTasks =
                                reorderableTasks.toMutableList().apply {
                                    add(to.index, removeAt(from.index))
                                }

                            onAction(
                                TaskAction.ReorderTasks(
                                    reorderableTasks.mapIndexed { index, task -> index to task }
                                )
                            )
                        }

                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(max = 600.dp)
                                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier.size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialShapes.Pill.toShape(),
                                    ),
                        ) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.reorder),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        Text(
                            text = stringResource(Res.string.reorder_tasks),
                            style =
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = flexFontEmphasis()
                                ),
                        )

                        LazyColumn(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                            state = listState,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            itemsIndexed(items = reorderableTasks, key = { _, it -> it.id }) {
                                index,
                                task ->
                                ReorderableItem(reorderableListState, key = task.id) {
                                    val shape =
                                        when {
                                            reorderableTasks.size == 1 -> detachedItemShape()
                                            index == 0 -> leadingItemShape()
                                            index == reorderableTasks.size - 1 -> endItemShape()
                                            else -> middleItemShape()
                                        }

                                    ListItem(
                                        modifier = Modifier.clip(shape),
                                        colors =
                                            ListItemDefaults.colors(
                                                containerColor =
                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                            ),
                                        headlineContent = {
                                            Text(
                                                text = task.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        trailingContent = {
                                            Icon(
                                                imageVector =
                                                    vectorResource(Res.drawable.drag_indicator),
                                                contentDescription = null,
                                                modifier =
                                                    Modifier.padding(horizontal = 8.dp)
                                                        .draggableHandle(),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteTasksDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    GritDialog(onDismissRequest = onDismiss) {
        Column {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialShapes.Pill.toShape(),
                        ),
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.warning),
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.delete),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = flexFontEmphasis()),
            )
            Text(
                text = stringResource(Res.string.delete_tasks),
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onDismiss,
                    shapes =
                        ButtonShapes(
                            shape = MaterialTheme.shapes.extraLarge,
                            pressedShape = MaterialTheme.shapes.small,
                        ),
                ) {
                    Text(stringResource(Res.string.cancel))
                }

                TextButton(
                    onClick = onConfirm,
                    shapes =
                        ButtonShapes(
                            shape = MaterialTheme.shapes.extraLarge,
                            pressedShape = MaterialTheme.shapes.small,
                        ),
                ) {
                    Text(stringResource(Res.string.delete))
                }
            }
        }
    }
}

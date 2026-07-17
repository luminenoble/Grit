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
package com.shub39.grit.shared.ui.task.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.shub39.grit.shared.ui.components.GritDialog
import com.shub39.grit.shared.ui.components.detachedItemShape
import com.shub39.grit.shared.ui.components.endItemShape
import com.shub39.grit.shared.ui.components.leadingItemShape
import com.shub39.grit.shared.ui.components.listItemColors
import com.shub39.grit.shared.ui.components.middleItemShape
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.CategoryColors
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.shared.ui.navigation.horizontalTransitionMetadata
import com.shub39.grit.shared.ui.task.TaskAction
import com.shub39.grit.shared.ui.task.TaskState
import com.shub39.grit.shared.ui.task.ui.component.CategoryUpsertSheet
import com.shub39.grit.shared.ui.task.ui.component.TaskUpsertSheet
import com.shub39.grit.shared.ui.task.ui.section.TaskList
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import grit.shared.ui.generated.resources.*
import grit.shared.ui.generated.resources.add
import kotlin.invoke
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Serializable
private sealed interface TaskRoutes : NavKey {
    @Serializable data object List : TaskRoutes

    /** Full-screen editor for an existing task, opened from the list. */
    @Serializable data class Detail(val taskId: Long) : TaskRoutes

    /** Full-screen editor for creating a new task in the current category. */
    @Serializable data object AddTask : TaskRoutes

    /** Full-screen category editor; [categoryId] == 0 means create a new category. */
    @Serializable data class CategoryEdit(val categoryId: Long) : TaskRoutes
}

private val taskConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(TaskRoutes.List::class, TaskRoutes.List.serializer())
            subclass(TaskRoutes.Detail::class, TaskRoutes.Detail.serializer())
            subclass(TaskRoutes.AddTask::class, TaskRoutes.AddTask.serializer())
            subclass(TaskRoutes.CategoryEdit::class, TaskRoutes.CategoryEdit.serializer())
        }
    }
}

@Composable
fun TasksPage(state: TaskState, onAction: (TaskAction) -> Unit) {
    var showCategoryEditor by remember { mutableStateOf(false) }
    val backStack = rememberNavBackStack(taskConfiguration, TaskRoutes.List)

    NavDisplay(
        backStack = backStack,
        entryProvider =
            entryProvider {
                entry<TaskRoutes.List> {
                    TaskList(
                        state = state,
                        onAction = onAction,
                        onEditCategories = { showCategoryEditor = true },
                        // Opening task details pushes a full screen instead of a bottom sheet.
                        onOpenTaskDetails = { backStack.add(TaskRoutes.Detail(it.id)) },
                        onAddTask = { backStack.add(TaskRoutes.AddTask) },
                        onAddCategory = { backStack.add(TaskRoutes.CategoryEdit(0L)) },
                    )
                }

                entry<TaskRoutes.AddTask>(metadata = horizontalTransitionMetadata()) {
                    val category = state.currentCategory

                    if (category == null) {
                        LaunchedEffect(Unit) {
                            if (backStack.size != 1) backStack.removeLastOrNull()
                        }
                    } else {
                        TaskUpsertSheet(
                            task =
                                Task(
                                    categoryId = category.id,
                                    title = "",
                                    index = state.tasks[category]?.size ?: 0,
                                    status = false,
                                    reminder = null,
                                    isToday = state.isTodayView,
                                ),
                            categories = state.tasks.keys.toList(),
                            isEditSheet = false,
                            is24Hr = state.is24Hour,
                            fullScreen = true,
                            autoFocusTitle = false,
                            onDismissRequest = {
                                if (backStack.size != 1) backStack.removeLastOrNull()
                            },
                            onUpsert = { onAction(TaskAction.UpsertTask(it)) },
                            onDelete = {},
                        )
                    }
                }

                entry<TaskRoutes.CategoryEdit>(metadata = horizontalTransitionMetadata()) { route ->
                    val isEditing = route.categoryId != 0L
                    val category =
                        if (isEditing) state.tasks.keys.firstOrNull { it.id == route.categoryId }
                        else Category(name = "", color = CategoryColors.GRAY.color)

                    if (category == null) {
                        LaunchedEffect(Unit) {
                            if (backStack.size != 1) backStack.removeLastOrNull()
                        }
                    } else {
                        CategoryUpsertSheet(
                            isEditSheet = isEditing,
                            category = category,
                            fullScreen = true,
                            autoFocusTitle = false,
                            onDismiss = {
                                if (backStack.size != 1) backStack.removeLastOrNull()
                            },
                            onUpsertCategory = {
                                onAction(TaskAction.AddCategory(it))
                                if (backStack.size != 1) backStack.removeLastOrNull()
                            },
                        )
                    }
                }

                entry<TaskRoutes.Detail>(metadata = horizontalTransitionMetadata()) { route ->
                    val task = state.tasks.values.flatten().firstOrNull { it.id == route.taskId }

                    if (task == null) {
                        LaunchedEffect(Unit) {
                            if (backStack.size != 1) backStack.removeLastOrNull()
                        }
                    } else {
                        TaskUpsertSheet(
                            task = task,
                            categories = state.tasks.keys.toList(),
                            isEditSheet = true,
                            is24Hr = state.is24Hour,
                            // A fresh detail screen must not steal focus into the keyboard.
                            fullScreen = true,
                            autoFocusTitle = false,
                            onDismissRequest = {
                                if (backStack.size != 1) backStack.removeLastOrNull()
                            },
                            onUpsert = { onAction(TaskAction.UpsertTask(it)) },
                            onDelete = {
                                onAction(TaskAction.DeleteTask(task))
                                if (backStack.size != 1) backStack.removeLastOrNull()
                            },
                        )
                    }
                }
            },
    )

    if (showCategoryEditor) {
        CategoryEditDialog(
            state = state,
            onAction = onAction,
            onDismissRequest = { showCategoryEditor = false },
            onEditCategory = {
                showCategoryEditor = false
                backStack.add(TaskRoutes.CategoryEdit(it.id))
            },
        )
    }
}

@Composable
private fun CategoryEditDialog(
    state: TaskState,
    onAction: (TaskAction) -> Unit,
    onDismissRequest: () -> Unit,
    onEditCategory: (Category) -> Unit,
) {
    GritDialog(onDismissRequest = onDismissRequest, padding = 0.dp) {
        var categories by remember(state.tasks) { mutableStateOf(state.tasks.keys.toList()) }

        val listState = rememberLazyListState()
        val reorderableListState =
            rememberReorderableLazyListState(listState) { from, to ->
                categories =
                    categories.toMutableList().apply { add(to.index, removeAt(from.index)) }

                onAction(
                    TaskAction.ReorderCategories(
                        categories.mapIndexed { index, category -> index to category }
                    )
                )
            }

        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
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
                    imageVector = vectorResource(Res.drawable.edit),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Text(
                text = stringResource(Res.string.edit_categories),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = flexFontEmphasis()),
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
                itemsIndexed(categories, key = { _, it -> it.id }) { index, category ->
                    var showDeleteDialog by remember { mutableStateOf(false) }

                    ReorderableItem(reorderableListState, key = category.id) {
                        val shape =
                            when {
                                categories.size == 1 -> detachedItemShape()
                                index == 0 -> leadingItemShape()
                                index == categories.size - 1 -> endItemShape()
                                else -> middleItemShape()
                            }

                        ListItem(
                            modifier = Modifier.clip(shape),
                            colors = listItemColors(),
                            headlineContent = { Text(text = category.name, maxLines = 1) },
                            supportingContent = {
                                Text(
                                    text =
                                        "${state.tasks[category]?.size ?: "0"} ${
                                            stringResource(
                                                Res.string.tasks
                                            )
                                        }"
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onEditCategory(category) }) {
                                        Icon(
                                            imageVector = vectorResource(Res.drawable.edit),
                                            contentDescription = "Edit",
                                        )
                                    }

                                    IconButton(
                                        onClick = { showDeleteDialog = true },
                                        enabled = categories.size > 1,
                                    ) {
                                        Icon(
                                            imageVector = vectorResource(Res.drawable.delete),
                                            contentDescription = "Delete",
                                        )
                                    }

                                    AnimatedVisibility(visible = categories.size > 1) {
                                        Icon(
                                            imageVector =
                                                vectorResource(Res.drawable.drag_indicator),
                                            contentDescription = null,
                                            modifier =
                                                Modifier.padding(horizontal = 8.dp)
                                                    .draggableHandle(),
                                        )
                                    }
                                }
                            },
                        )
                    }

                    if (showDeleteDialog) {
                        GritDialog(onDismissRequest = { showDeleteDialog = false }) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.warning),
                                contentDescription = null,
                            )

                            Text(
                                text = stringResource(Res.string.delete),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                            )

                            Text(
                                text = stringResource(Res.string.delete_category),
                                textAlign = TextAlign.Center,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = { showDeleteDialog = false },
                                    shapes =
                                        ButtonShapes(
                                            shape = MaterialTheme.shapes.extraLarge,
                                            pressedShape = MaterialTheme.shapes.small,
                                        ),
                                ) {
                                    Text(stringResource(Res.string.cancel))
                                }

                                TextButton(
                                    onClick = {
                                        onAction(TaskAction.DeleteCategory(category))
                                        showDeleteDialog = false
                                    },
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
            }
        }
    }
}

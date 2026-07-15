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
package com.shub39.grit.shared.ui.task.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.now
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.core.tasks.TaskStep
import com.shub39.grit.core.toFormattedString
import com.shub39.grit.shared.ui.components.ExpressiveSwitch
import com.shub39.grit.shared.ui.components.GritBottomSheet
import com.shub39.grit.shared.ui.components.GritTimePicker
import com.shub39.grit.shared.ui.components.detachedItemShape
import com.shub39.grit.shared.ui.components.listItemColors
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import grit.shared.ui.generated.resources.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** A step being edited, with a stable [uid] independent of its list position. */
private data class StepItem(val uid: Int, val text: String, val done: Boolean = false)

@Composable
expect fun TaskUpsertSheet(
    task: Task,
    categories: List<Category>,
    onDismissRequest: () -> Unit,
    onUpsert: (Task) -> Unit,
    onDelete: () -> Unit,
    is24Hr: Boolean,
    modifier: Modifier = Modifier,
    isEditSheet: Boolean = false,
)

@Composable
fun TaskUpsertSheetContent(
    task: Task,
    categories: List<Category>,
    onDismissRequest: () -> Unit,
    onUpsert: (Task) -> Unit,
    onDelete: () -> Unit,
    is24Hr: Boolean,
    isEditSheet: Boolean = false,
    notificationPermission: Boolean,
    showDateTimePicker: Boolean,
    updateDateTimePickerVisibility: (Boolean) -> Unit,
    onPermissionRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var newTask by remember { mutableStateOf(task) }

    val textFieldState =
        rememberTextFieldState(
            initialText = newTask.title,
            initialSelection = TextRange(newTask.title.length),
        )

    // Steps get a stable local uid so drag reorder and deletion animate correctly.
    var stepUidCounter by remember { mutableStateOf(task.steps.size) }
    var stepItems by remember {
        mutableStateOf(
            task.steps.mapIndexed { index, step -> StepItem(index, step.text, step.done) }
        )
    }
    var newStepText by remember { mutableStateOf("") }

    val timePickerState = rememberTimePickerState(is24Hour = is24Hr)
    val datePickerState = rememberDatePickerState()
    val isValidDateTime =
        if (newTask.reminder != null) {
            newTask.reminder!! > LocalDateTime.now()
        } else true

    GritBottomSheet(
        modifier = modifier.imePadding(),
        padding = 0.dp,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                    imageVector =
                        vectorResource(if (isEditSheet) Res.drawable.edit else Res.drawable.add),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Text(
                text =
                    stringResource(if (isEditSheet) Res.string.edit_task else Res.string.add_task),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = flexFontEmphasis()),
            )
        }

        val sheetListState = rememberLazyListState()
        val stepsReorderState =
            rememberReorderableLazyListState(sheetListState) { from, to ->
                stepItems =
                    stepItems.toMutableList().apply {
                        val fromIndex = indexOfFirst { "step_${it.uid}" == from.key }
                        val toIndex = indexOfFirst { "step_${it.uid}" == to.key }
                        if (fromIndex >= 0 && toIndex >= 0) {
                            add(toIndex, removeAt(fromIndex))
                        }
                    }
            }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large),
            state = sheetListState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    categories.forEach { category ->
                        ToggleButton(
                            checked = category.id == newTask.categoryId,
                            onCheckedChange = { newTask = newTask.copy(categoryId = category.id) },
                            colors = ToggleButtonDefaults.tonalToggleButtonColors(),
                            content = { Text(category.name) },
                        )
                    }
                }
            }

            item {
                val keyboardController = LocalSoftwareKeyboardController.current
                val focusRequester = remember { FocusRequester() }

                LaunchedEffect(Unit) {
                    delay(400.milliseconds)
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }

                OutlinedTextField(
                    state = textFieldState,
                    shape = MaterialTheme.shapes.medium,
                    placeholder = { Text(text = stringResource(Res.string.add_task)) },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.None,
                        ),
                    onKeyboardAction = { defaultAction ->
                        textFieldState.edit { append("\n") }
                        defaultAction()
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }

            item {
                OutlinedTextField(
                    value = newTask.description,
                    onValueChange = { newTask = newTask.copy(description = it) },
                    shape = MaterialTheme.shapes.medium,
                    placeholder = { Text(text = stringResource(Res.string.task_description)) },
                    minLines = 2,
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // steps: add, remove and drag to reorder
            item {
                Text(
                    text = stringResource(Res.string.steps),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            items(stepItems, key = { "step_${it.uid}" }) { step ->
                ReorderableItem(stepsReorderState, key = "step_${step.uid}") {
                    ListItem(
                        modifier = Modifier.clip(MaterialTheme.shapes.medium),
                        colors = listItemColors(),
                        headlineContent = {
                            Text(
                                text = step.text,
                                textDecoration =
                                    if (step.done) TextDecoration.LineThrough
                                    else TextDecoration.None,
                            )
                        },
                        leadingContent = {
                            IconButton(
                                onClick = {
                                    stepItems =
                                        stepItems.map {
                                            if (it.uid == step.uid) it.copy(done = !it.done) else it
                                        }
                                }
                            ) {
                                Icon(
                                    imageVector =
                                        vectorResource(
                                            if (step.done) Res.drawable.check_circle
                                            else Res.drawable.circle_border
                                        ),
                                    contentDescription = "Toggle done",
                                )
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.drag_indicator),
                                    contentDescription = "Reorder",
                                    modifier = Modifier.draggableHandle(),
                                )

                                IconButton(
                                    onClick = {
                                        stepItems = stepItems.filter { it.uid != step.uid }
                                    }
                                ) {
                                    Icon(
                                        imageVector = vectorResource(Res.drawable.delete),
                                        contentDescription = "Delete",
                                    )
                                }
                            }
                        },
                    )
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newStepText,
                        onValueChange = { newStepText = it },
                        shape = MaterialTheme.shapes.medium,
                        placeholder = { Text(text = stringResource(Res.string.add_step)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )

                    FilledTonalIconButton(
                        onClick = {
                            stepItems = stepItems + StepItem(stepUidCounter++, newStepText.trim())
                            newStepText = ""
                        },
                        enabled = newStepText.isNotBlank(),
                    ) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.add),
                            contentDescription = stringResource(Res.string.add_step),
                        )
                    }
                }
            }

            item {
                ListItem(
                    modifier = Modifier.clip(detachedItemShape()),
                    colors = listItemColors(),
                    leadingContent = {
                        Icon(
                            imageVector = vectorResource(Res.drawable.light_mode),
                            contentDescription = null,
                        )
                    },
                    headlineContent = { Text(text = stringResource(Res.string.add_to_today)) },
                    trailingContent = {
                        ExpressiveSwitch(
                            checked = newTask.isToday,
                            onCheckedChange = { newTask = newTask.copy(isToday = it) },
                        )
                    },
                )
            }

            item {
                ListItem(
                    modifier = Modifier.clip(detachedItemShape()),
                    colors = listItemColors(),
                    leadingContent = {
                        Icon(
                            imageVector = vectorResource(Res.drawable.alarm),
                            contentDescription = null,
                        )
                    },
                    headlineContent = { Text(text = stringResource(Res.string.add_reminder)) },
                    supportingContent = {
                        if (newTask.reminder != null) {
                            Column {
                                Text(text = newTask.reminder!!.toFormattedString(is24Hr = is24Hr))
                                if (!isValidDateTime) {
                                    Text(
                                        text = stringResource(Res.string.invalid_date_time),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    },
                    trailingContent = {
                        ExpressiveSwitch(
                            checked = newTask.reminder != null,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (notificationPermission) {
                                        updateDateTimePickerVisibility(true)
                                    } else {
                                        onPermissionRequest()
                                    }
                                } else {
                                    newTask = newTask.copy(reminder = null)
                                }
                            },
                        )
                    },
                )
            }

            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isEditSheet) {
                            OutlinedButton(
                                onClick = onDelete,
                                shapes =
                                    ButtonShapes(
                                        shape = MaterialTheme.shapes.extraLarge,
                                        pressedShape = MaterialTheme.shapes.small,
                                    ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(Res.string.delete))
                            }
                        }

                        Button(
                            onClick = {
                                onUpsert(
                                    newTask.copy(
                                        title = textFieldState.text.toString(),
                                        steps =
                                            stepItems
                                                .filter { it.text.isNotBlank() }
                                                .map { TaskStep(it.text, it.done) },
                                    )
                                )
                                onDismissRequest()
                            },
                            shapes =
                                ButtonShapes(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    pressedShape = MaterialTheme.shapes.small,
                                ),
                            modifier = Modifier.weight(1f),
                            enabled =
                                textFieldState.text.isNotBlank() &&
                                    textFieldState.text.length <= 100 &&
                                    isValidDateTime &&
                                    (newTask.reminder != task.reminder ||
                                        textFieldState.text.toString() != task.title ||
                                        newTask.categoryId != task.categoryId ||
                                        newTask.description != task.description ||
                                        newTask.isToday != task.isToday ||
                                        stepItems.map { TaskStep(it.text, it.done) } != task.steps),
                        ) {
                            Text(
                                stringResource(
                                    if (isEditSheet) Res.string.save else Res.string.add_task
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDateTimePicker) {
        var showTimePicker by remember { mutableStateOf(false) }

        DatePickerDialog(
            onDismissRequest = { updateDateTimePickerVisibility(false) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (datePickerState.selectedDateMillis != null) {
                            newTask =
                                newTask.copy(
                                    reminder =
                                        LocalDateTime(
                                            date =
                                                Instant.fromEpochMilliseconds(
                                                        datePickerState.selectedDateMillis!!
                                                    )
                                                    .toLocalDateTime(TimeZone.UTC)
                                                    .date,
                                            time =
                                                LocalTime(
                                                    hour = timePickerState.hour,
                                                    minute = timePickerState.minute,
                                                ),
                                        )
                                )

                            updateDateTimePickerVisibility(false)
                        }
                    },
                    enabled = datePickerState.selectedDateMillis != null,
                ) {
                    Text(stringResource(Res.string.done))
                }
            },
            dismissButton = {
                IconButton(onClick = { showTimePicker = true }) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.schedule),
                        contentDescription = "Select Time",
                    )
                }
            },
        ) {
            DatePicker(state = datePickerState)

            if (showTimePicker) {
                GritTimePicker(
                    onDismissRequest = { showTimePicker = false },
                    state = timePickerState,
                    onConfirm = { showTimePicker = false },
                )
            }
        }
    }
}

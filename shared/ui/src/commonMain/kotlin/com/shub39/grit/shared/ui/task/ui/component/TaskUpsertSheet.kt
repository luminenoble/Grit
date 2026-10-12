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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.now
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.Section
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.core.toFormattedString
import com.shub39.grit.shared.ui.components.ColorPickerDialog
import com.shub39.grit.shared.ui.components.ExpressiveSwitch
import com.shub39.grit.shared.ui.components.GritBottomSheet
import com.shub39.grit.shared.ui.components.GritTimePicker
import com.shub39.grit.shared.ui.components.detachedItemShape
import com.shub39.grit.shared.ui.components.listItemColors
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import com.shub39.grit.shared.ui.theme.parseAccentColor
import com.shub39.grit.shared.ui.theme.readableTitleColor
import com.shub39.grit.shared.ui.theme.toHexString
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

/** A section being edited, with a stable [uid] independent of its list position. */
private data class SectionItem(val uid: Int, val section: Section)

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
    /** Render as a full navigable screen (with a top bar) instead of a bottom sheet. */
    fullScreen: Boolean = false,
    /** Auto-focus the title field (and raise the keyboard) when shown. */
    autoFocusTitle: Boolean = true,
    saving: Boolean = false,
    restrictStructure: Boolean = false,
    allowDraftChanges: Boolean = true,
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
    fullScreen: Boolean = false,
    autoFocusTitle: Boolean = true,
    saving: Boolean = false,
    restrictStructure: Boolean = false,
    allowDraftChanges: Boolean = true,
) {
    val originalTask = remember(task.id) { task }
    var newTask by remember(task.id) { mutableStateOf(task) }

    val textFieldState =
        rememberTextFieldState(
            initialText = newTask.title,
            initialSelection = TextRange(newTask.title.length),
        )

    // Sections get a stable local uid so drag reorder and deletion animate correctly.
    var sectionUidCounter by remember { mutableStateOf(task.sections.size) }
    var sectionItems by remember {
        mutableStateOf(task.sections.mapIndexed { index, step -> SectionItem(index, step) })
    }
    // Sections are edited in a dedicated dialog (large enough for the full text) instead of
    // inline, so the row only needs to show the name and offer edit/reorder actions.
    var editingSection by remember { mutableStateOf<SectionItem?>(null) }

    var reorderSections by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showUnsaved by remember { mutableStateOf(false) }
    var showTaskDelete by remember { mutableStateOf(false) }
    var movingSection by remember { mutableStateOf<SectionItem?>(null) }
    var targetPosition by remember { mutableStateOf("") }
    val draft =
        newTask.copy(
            title = textFieldState.text.toString(),
            sections =
                sectionItems.mapIndexed { index, item -> item.section.copy(orderIndex = index) },
        )
    val dirty = draft != originalTask
    val requestClose: () -> Unit = {
        if (!saving) {
            if (dirty) showUnsaved = true else onDismissRequest()
        }
    }
    DraftBackHandler(enabled = fullScreen, onBack = requestClose)
    val saveDraft: () -> Unit = {
        if (
            !saving &&
                draft.title.isNotBlank() &&
                (draft.reminder == task.reminder ||
                    draft.reminder == null ||
                    draft.reminder!! > LocalDateTime.now())
        )
            onUpsert(draft)
    }

    val timePickerState = rememberTimePickerState(is24Hour = is24Hr)
    val datePickerState = rememberDatePickerState()
    val isValidDateTime =
        if (newTask.reminder != null) {
            newTask.reminder == task.reminder || newTask.reminder!! > LocalDateTime.now()
        } else true

    val sheetContent: @Composable ColumnScope.() -> Unit = {
        if (!fullScreen) {
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
                            vectorResource(
                                if (isEditSheet) Res.drawable.edit else Res.drawable.add
                            ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Text(
                    text =
                        stringResource(
                            if (isEditSheet) Res.string.edit_task else Res.string.add_task
                        ),
                    style =
                        MaterialTheme.typography.headlineSmall.copy(fontFamily = flexFontEmphasis()),
                )
            }
        }

        val sheetListState = rememberLazyListState()
        val sectionsReorderState =
            rememberReorderableLazyListState(sheetListState) { from, to ->
                sectionItems =
                    sectionItems.toMutableList().apply {
                        val fromIndex = indexOfFirst { "section_${it.uid}" == from.key }
                        val toIndex = indexOfFirst { "section_${it.uid}" == to.key }
                        if (fromIndex >= 0 && toIndex >= 0) {
                            add(toIndex, removeAt(fromIndex))
                        }
                    }
            }

        LazyColumn(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    // Full screen: bound this to the space left under the TopAppBar so it
                    // scrolls internally instead of pushing content past the screen edge.
                    .then(if (fullScreen) Modifier.weight(1f) else Modifier),
            state = sheetListState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    categories.forEach { category ->
                        ToggleButton(
                            enabled = !saving,
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

                LaunchedEffect(autoFocusTitle) {
                    if (autoFocusTitle) {
                        delay(400.milliseconds)
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                }

                OutlinedTextField(
                    enabled = !saving,
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
                    enabled = !saving,
                    value = newTask.description,
                    onValueChange = { newTask = newTask.copy(description = it) },
                    shape = MaterialTheme.shapes.medium,
                    placeholder = { Text(text = stringResource(Res.string.task_description)) },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "标题文字颜色",
                        color = readableTitleColor(newTask.titleColor),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    TextButton(onClick = { showColorPicker = true }, enabled = !saving) {
                        Text("选择")
                    }
                    TextButton(
                        onClick = { newTask = newTask.copy(titleColor = null) },
                        enabled = !saving,
                    ) {
                        Text("默认")
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("任务已完成") },
                    trailingContent = {
                        ExpressiveSwitch(
                            enabled = !saving,
                            checked = newTask.isCompleted,
                            onCheckedChange = { newTask = newTask.copy(isCompleted = it) },
                        )
                    },
                    colors = listItemColors(),
                )
            }
            // sections: add, remove and drag to reorder
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (restrictStructure) "Section · 排序待接入"
                        else stringResource(Res.string.sections),
                        Modifier.padding(top = 12.dp),
                    )
                    TextButton(
                        enabled = !saving && !restrictStructure,
                        onClick = { reorderSections = !reorderSections },
                    ) {
                        Text(
                            stringResource(
                                if (reorderSections) Res.string.section_reorder_done
                                else Res.string.section_reorder
                            )
                        )
                    }
                }
            }

            items(sectionItems, key = { "section_${it.uid}" }) { step ->
                ReorderableItem(sectionsReorderState, key = "section_${step.uid}") {
                    ListItem(
                        modifier = Modifier.clip(MaterialTheme.shapes.medium),
                        colors = listItemColors(),
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${sectionItems.indexOfFirst { it.uid == step.uid } + 1}.",
                                    Modifier.padding(end = 8.dp),
                                )
                                Text(
                                    step.section.title,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f).basicMarquee(),
                                )
                            }
                        },
                        supportingContent = {
                            step.section.completedOn?.let { Text(it.toString()) }
                        },
                        leadingContent = {
                            IconButton(
                                enabled = !reorderSections && !saving,
                                onClick = {
                                    sectionItems =
                                        sectionItems.map {
                                            if (it.uid == step.uid)
                                                it.copy(section = it.section.toggleCompleted())
                                            else it
                                        }
                                },
                            ) {
                                Icon(
                                    imageVector =
                                        vectorResource(
                                            if (step.section.isCompleted) Res.drawable.check_circle
                                            else Res.drawable.circle_border
                                        ),
                                    contentDescription = "Toggle done",
                                )
                            }
                        },
                        trailingContent = {
                            if (reorderSections) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = vectorResource(Res.drawable.drag_indicator),
                                        contentDescription = "拖动排序",
                                        modifier =
                                            if (saving) Modifier else Modifier.draggableHandle(),
                                    )
                                    TextButton(
                                        enabled = !saving,
                                        onClick = {
                                            movingSection = step
                                            targetPosition =
                                                (sectionItems.indexOf(step) + 1).toString()
                                        },
                                    ) {
                                        Text("位置")
                                    }
                                }
                            } else {
                                IconButton(enabled = !saving, onClick = { editingSection = step }) {
                                    Icon(
                                        imageVector = vectorResource(Res.drawable.edit),
                                        contentDescription = "编辑 Section",
                                    )
                                }
                            }
                        },
                    )
                }
            }

            item {
                TextButton(
                    enabled = !saving && !reorderSections && allowDraftChanges,
                    onClick = {
                        val item =
                            SectionItem(
                                sectionUidCounter++,
                                Section(orderIndex = sectionItems.size),
                            )
                        sectionItems = sectionItems + item
                        editingSection = item
                    },
                ) {
                    Icon(imageVector = vectorResource(Res.drawable.add), contentDescription = null)
                    Text(stringResource(Res.string.add_section))
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
                            enabled = !saving,
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
                            enabled = !saving,
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
                        if (isEditSheet && !restrictStructure) {
                            OutlinedButton(
                                onClick = { showTaskDelete = true },
                                enabled = !saving,
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
                            onClick = saveDraft,
                            shapes =
                                ButtonShapes(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    pressedShape = MaterialTheme.shapes.small,
                                ),
                            modifier = Modifier.weight(1f),
                            enabled =
                                !saving &&
                                    draft.title.isNotBlank() &&
                                    isValidDateTime &&
                                    (dirty || !isEditSheet),
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

    if (fullScreen) {
        Column(
            modifier =
                modifier.fillMaxSize().imePadding().background(MaterialTheme.colorScheme.background)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.task_details),
                        fontFamily = flexFontEmphasis(),
                    )
                },
                actions = {
                    if (restrictStructure) {
                        TextButton(onClick = requestClose, enabled = !saving) { Text("取消") }
                        TextButton(
                            onClick = saveDraft,
                            enabled =
                                !saving && draft.title.isNotBlank() && isValidDateTime && dirty,
                        ) {
                            Text(if (saving) "保存中…" else "保存")
                        }
                    }
                },
                navigationIcon = {
                    FilledTonalIconButton(onClick = requestClose, enabled = !saving) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.nav_arrow_back),
                            contentDescription = "Navigate Back",
                        )
                    }
                },
            )
            sheetContent()
        }
    } else {
        GritBottomSheet(
            modifier = modifier.imePadding(),
            padding = 0.dp,
            onDismissRequest = requestClose,
        ) {
            sheetContent()
        }
    }

    if (showColorPicker)
        ColorPickerDialog(
            initialColor =
                parseAccentColor(newTask.titleColor) ?: MaterialTheme.colorScheme.onSurface,
            onSelect = { newTask = newTask.copy(titleColor = it.toHexString()) },
            onDismiss = { showColorPicker = false },
        )
    if (showUnsaved)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUnsaved = false },
            title = { Text("未保存的修改") },
            text = { Text("保存修改，或放弃后返回？") },
            confirmButton = {
                TextButton(
                    enabled = !saving && draft.title.isNotBlank() && isValidDateTime,
                    onClick = {
                        showUnsaved = false
                        saveDraft()
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showUnsaved = false
                            onDismissRequest()
                        }
                    ) {
                        Text("放弃")
                    }
                    TextButton(onClick = { showUnsaved = false }) { Text("继续编辑") }
                }
            },
        )
    if (showTaskDelete)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTaskDelete = false },
            title = { Text("删除任务") },
            text = { Text("删除「${task.title}」及其 ${task.sections.size} 条 Section？") },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        showTaskDelete = false
                        onDelete()
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = { TextButton(onClick = { showTaskDelete = false }) { Text("取消") } },
        )
    movingSection?.let { item ->
        val target = targetPosition.toIntOrNull()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { movingSection = null },
            title = { Text("移动到第几项") },
            text = {
                OutlinedTextField(
                    enabled = !saving,
                    value = targetPosition,
                    onValueChange = { targetPosition = it },
                    label = { Text("1–${sectionItems.size}") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = target != null && target in 1..sectionItems.size,
                    onClick = {
                        sectionItems =
                            sectionItems.toMutableList().apply {
                                val from = indexOfFirst { it.uid == item.uid }
                                if (from >= 0) add(target!! - 1, removeAt(from))
                            }
                        movingSection = null
                    },
                ) {
                    Text("移动")
                }
            },
            dismissButton = { TextButton(onClick = { movingSection = null }) { Text("取消") } },
        )
    }
    editingSection?.let { item ->
        SectionEditor(
            section = item.section,
            allowDelete = allowDraftChanges,
            wide = restrictStructure,
            saving = saving,
            onSave = { section ->
                sectionItems =
                    sectionItems.map { if (it.uid == item.uid) it.copy(section = section) else it }
                editingSection = null
            },
            onDelete = {
                sectionItems = sectionItems.filter { it.uid != item.uid }
                editingSection = null
            },
            onDismiss = { editingSection = null },
        )
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

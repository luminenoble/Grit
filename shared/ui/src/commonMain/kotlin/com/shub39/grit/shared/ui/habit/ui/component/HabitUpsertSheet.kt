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
package com.shub39.grit.shared.ui.habit.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.habits.Habit
import com.shub39.grit.core.now
import com.shub39.grit.core.toFormattedString
import com.shub39.grit.shared.ui.ai.createTaskPlanner
import com.shub39.grit.shared.ui.components.ColorPickerDialog
import com.shub39.grit.shared.ui.components.ExpressiveSwitch
import com.shub39.grit.shared.ui.components.GritBottomSheet
import com.shub39.grit.shared.ui.components.GritTimePicker
import com.shub39.grit.shared.ui.components.detachedItemShape
import com.shub39.grit.shared.ui.components.endItemShape
import com.shub39.grit.shared.ui.components.leadingItemShape
import com.shub39.grit.shared.ui.components.listItemColors
import com.shub39.grit.shared.ui.components.middleItemShape
import com.shub39.grit.shared.ui.theme.GritTheme
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import com.shub39.grit.shared.ui.theme.flexFontRounded
import com.shub39.grit.shared.ui.theme.parseAccentColor
import com.shub39.grit.shared.ui.theme.toHexString
import grit.shared.ui.generated.resources.*
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val TITLE_STRING_LIMIT = 50
private const val DESCRIPTION_STRING_LIMIT = 200

/** A step being edited, with a stable [uid] independent of its list position. */
private data class StepItem(val uid: Int, val text: String)

@Composable
expect fun HabitUpsertSheet(
    habit: Habit,
    onDismissRequest: () -> Unit,
    onUpsertHabit: (Habit) -> Unit,
    is24Hr: Boolean,
    modifier: Modifier = Modifier,
    isEditSheet: Boolean = false,
)

@Composable
fun HabitUpsertSheetContent(
    newHabit: Habit,
    updateHabit: (Habit) -> Unit,
    onDismissRequest: () -> Unit,
    onUpsertHabit: (Habit) -> Unit,
    is24Hr: Boolean,
    isEditSheet: Boolean = false,
    notificationPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var timePickerDialog by remember { mutableStateOf(false) }
    var colorPickerDialog by remember { mutableStateOf(false) }

    // Steps get a stable local uid so drag reorder and deletion animate correctly.
    var stepUidCounter by remember { mutableStateOf(newHabit.steps.size) }
    var stepItems by remember {
        mutableStateOf(newHabit.steps.mapIndexed { index, text -> StepItem(index, text) })
    }
    var newStepText by remember { mutableStateOf("") }

    // AI decomposition of the habit into steps
    val aiScope = rememberCoroutineScope()
    val planner = remember { createTaskPlanner() }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }

    val titleTextFieldState =
        rememberTextFieldState(
            initialText = newHabit.title,
            initialSelection = TextRange(newHabit.title.length),
        )

    val descTextFieldState =
        rememberTextFieldState(
            initialText = newHabit.description,
            initialSelection = TextRange(newHabit.description.length),
        )

    LaunchedEffect(Unit) {
        delay(400.milliseconds)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    GritBottomSheet(
        onDismissRequest = onDismissRequest,
        padding = 0.dp,
        modifier = modifier.imePadding(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.size(50.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialShapes.Pill.toShape(),
                        ),
            ) {
                Icon(
                    imageVector =
                        vectorResource(if (isEditSheet) Res.drawable.edit else Res.drawable.add),
                    contentDescription = "Edit Habit",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Text(
                text =
                    stringResource(
                        if (isEditSheet) Res.string.edit_habit else Res.string.add_habit
                    ),
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
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                OutlinedTextField(
                    state = titleTextFieldState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next,
                        ),
                    label = {
                        if (newHabit.title.length <= TITLE_STRING_LIMIT) {
                            Text(
                                text =
                                    stringResource(
                                        if (isEditSheet) Res.string.update_title
                                        else Res.string.title
                                    )
                            )
                        } else {
                            Text(text = stringResource(Res.string.too_long))
                        }
                    },
                    isError = newHabit.title.length > TITLE_STRING_LIMIT,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }

            item {
                OutlinedTextField(
                    state = descTextFieldState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        if (newHabit.description.length <= DESCRIPTION_STRING_LIMIT) {
                            Text(
                                text =
                                    stringResource(
                                        if (isEditSheet) Res.string.update_description
                                        else Res.string.description
                                    )
                            )
                        } else {
                            Text(text = stringResource(Res.string.too_long))
                        }
                    },
                    isError = newHabit.description.length > DESCRIPTION_STRING_LIMIT,
                )
            }

            // steps: add, remove, drag to reorder, or let AI decompose the habit
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.steps),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )

                    TextButton(
                        onClick = {
                            aiScope.launch {
                                aiLoading = true
                                aiError = null
                                runCatching {
                                        planner.decompose(
                                            (titleTextFieldState.text.toString() +
                                                    "\n" +
                                                    descTextFieldState.text.toString())
                                                .trim()
                                        )
                                    }
                                    .onSuccess { plan ->
                                        stepItems =
                                            plan.steps.mapIndexed { index, text ->
                                                StepItem(stepUidCounter + index, text)
                                            }
                                        stepUidCounter += plan.steps.size
                                    }
                                    .onFailure { aiError = it.message ?: "生成失败" }
                                aiLoading = false
                            }
                        },
                        enabled = titleTextFieldState.text.isNotBlank() && !aiLoading,
                    ) {
                        if (aiLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text(text = stringResource(Res.string.ai_fill_steps))
                        }
                    }
                }

                aiError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            items(stepItems, key = { "step_${it.uid}" }) { step ->
                ReorderableItem(stepsReorderState, key = "step_${step.uid}") {
                    ListItem(
                        modifier = Modifier.clip(MaterialTheme.shapes.medium),
                        colors = listItemColors(),
                        headlineContent = { Text(text = step.text) },
                        leadingContent = {
                            Icon(
                                imageVector = vectorResource(Res.drawable.drag_indicator),
                                contentDescription = "Reorder",
                                modifier = Modifier.draggableHandle(),
                            )
                        },
                        trailingContent = {
                            IconButton(
                                onClick = { stepItems = stepItems.filter { it.uid != step.uid } }
                            ) {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.delete),
                                    contentDescription = "Delete",
                                )
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
                    colors = listItemColors(),
                    modifier = Modifier.clip(detachedItemShape()),
                    leadingContent = {
                        Icon(
                            imageVector = vectorResource(Res.drawable.palette),
                            contentDescription = null,
                        )
                    },
                    headlineContent = { Text(text = stringResource(Res.string.accent_color)) },
                    trailingContent = {
                        Box(
                            modifier =
                                Modifier.size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        parseAccentColor(newHabit.color)
                                            ?: MaterialTheme.colorScheme.primaryContainer
                                    )
                                    .clickable { colorPickerDialog = true }
                        )
                    },
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Card(
                        shape =
                            if (newHabit.days.isEmpty()) detachedItemShape()
                            else leadingItemShape(),
                        modifier = Modifier.animateContentSize(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(text = stringResource(Res.string.select_days))

                            Row(
                                horizontalArrangement =
                                    Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                            ) {
                                DayOfWeek.entries.forEach { dayOfWeek ->
                                    ToggleButton(
                                        checked = newHabit.days.contains(dayOfWeek),
                                        onCheckedChange = {
                                            updateHabit(
                                                newHabit.copy(
                                                    days =
                                                        if (it) {
                                                            newHabit.days + dayOfWeek
                                                        } else {
                                                            newHabit.days - dayOfWeek
                                                        }
                                                )
                                            )
                                        },
                                        enabled =
                                            !(newHabit.days.size == 1 &&
                                                newHabit.days.contains(dayOfWeek)),
                                        modifier = Modifier.weight(1f),
                                        colors = ToggleButtonDefaults.tonalToggleButtonColors(),
                                        content = { Text(text = dayOfWeek.name.take(1)) },
                                    )
                                }
                            }
                        }
                    }

                    if (newHabit.days.isNotEmpty()) {
                        ListItem(
                            colors = listItemColors(),
                            modifier =
                                Modifier.clip(
                                    if (newHabit.reminder) middleItemShape() else endItemShape()
                                ),
                            headlineContent = {
                                Text(text = stringResource(Res.string.add_reminder))
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(Res.string.add_reminder_desc),
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee(),
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.alarm),
                                    contentDescription = "Alarm Icon",
                                )
                            },
                            trailingContent = {
                                ExpressiveSwitch(
                                    checked = newHabit.reminder,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (notificationPermission) {
                                                updateHabit(newHabit.copy(reminder = true))
                                            } else {
                                                onRequestPermission()
                                            }
                                        } else {
                                            updateHabit(newHabit.copy(reminder = false))
                                        }
                                    },
                                )
                            },
                        )

                        if (newHabit.reminder) {
                            ListItem(
                                colors = listItemColors(),
                                modifier = Modifier.clip(endItemShape()),
                                headlineContent = {
                                    Text(
                                        text =
                                            newHabit.time.time.toFormattedString(is24Hr = is24Hr),
                                        style =
                                            MaterialTheme.typography.titleLarge.copy(
                                                fontFamily = flexFontRounded()
                                            ),
                                    )
                                },
                                trailingContent = {
                                    FilledTonalIconButton(onClick = { timePickerDialog = true }) {
                                        Icon(
                                            imageVector = vectorResource(Res.drawable.edit),
                                            contentDescription = "Pick Time",
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        onUpsertHabit(
                            newHabit.copy(
                                title = titleTextFieldState.text.toString(),
                                description = descTextFieldState.text.toString(),
                                steps = stepItems.map { it.text }.filter { it.isNotBlank() },
                            )
                        )
                        onDismissRequest()
                    },
                    modifier = Modifier.padding(bottom = 32.dp).fillMaxWidth(),
                    enabled =
                        descTextFieldState.text.length <= DESCRIPTION_STRING_LIMIT &&
                            titleTextFieldState.text.length <= TITLE_STRING_LIMIT &&
                            titleTextFieldState.text.isNotBlank(),
                ) {
                    Text(
                        text =
                            stringResource(
                                if (isEditSheet) {
                                    Res.string.save
                                } else {
                                    Res.string.add_habit
                                }
                            )
                    )
                }
            }
        }

        if (colorPickerDialog) {
            ColorPickerDialog(
                initialColor =
                    parseAccentColor(newHabit.color) ?: MaterialTheme.colorScheme.primary,
                onSelect = { updateHabit(newHabit.copy(color = it.toHexString())) },
                onDismiss = { colorPickerDialog = false },
            )
        }

        if (timePickerDialog) {
            val timePickerState =
                rememberTimePickerState(
                    initialHour = newHabit.time.hour,
                    initialMinute = newHabit.time.minute,
                    is24Hour = is24Hr,
                )

            GritTimePicker(
                onDismissRequest = { timePickerDialog = false },
                state = timePickerState,
                onConfirm = {
                    updateHabit(
                        newHabit.copy(
                            time =
                                LocalDateTime(
                                    date = newHabit.time.date,
                                    time =
                                        LocalTime(
                                            minute = timePickerState.minute,
                                            hour = timePickerState.hour,
                                        ),
                                )
                        )
                    )
                    timePickerDialog = false
                },
            )
        }
    }
}

@Preview
@Composable
private fun Preview() {
    GritTheme {
        HabitUpsertSheet(
            habit =
                Habit(
                    id = 1,
                    title = "New Habit",
                    description = "A new Habit",
                    time = LocalDateTime.now(),
                    days = DayOfWeek.entries.toSet(),
                    index = 1,
                    reminder = false,
                ),
            onDismissRequest = {},
            onUpsertHabit = {},
            is24Hr = true,
            isEditSheet = true,
        )
    }
}

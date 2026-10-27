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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions.Companion.Default
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.shub39.grit.shared.ui.components.jsonSaver
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
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.shared.ui.components.ExpressiveSwitch
import com.shub39.grit.shared.ui.components.TitleColorRow
import com.shub39.grit.shared.ui.components.GritBottomSheet
import com.shub39.grit.shared.ui.components.detachedItemShape
import com.shub39.grit.shared.ui.components.listItemColors
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import grit.shared.ui.generated.resources.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun CategoryUpsertSheet(
    isEditSheet: Boolean = false,
    modifier: Modifier = Modifier,
    category: Category,
    onDismiss: () -> Unit,
    onUpsertCategory: (Category) -> Unit,
    fullScreen: Boolean = false,
    autoFocusTitle: Boolean = true,
    saving: Boolean = false,
    pinnedActions: Boolean = false,
    /** Called with the current draft whenever it changes, e.g. for a live preview. */
    onDraftChange: ((Category) -> Unit)? = null,
    /** Full screen only: no own background, for hosts that draw a glass panel behind. */
    transparent: Boolean = false,
    /** Extra field shown first in the form (e.g. the illustration picker). */
    extra: (@Composable () -> Unit)? = null,
) {
    // Saveable like the name field below, so a recreated activity keeps the whole draft. Hosts that
    // reuse this sheet for different objects must key it by the editing session.
    var newCategory by rememberSaveable(stateSaver = jsonSaver(Category.serializer())) {
        mutableStateOf(category)
    }

    val textFieldState =
        rememberTextFieldState(
            initialText = newCategory.name,
            initialSelection = TextRange(newCategory.name.length),
        )

    val draft = newCategory.copy(name = textFieldState.text.toString())
    LaunchedEffect(draft) { onDraftChange?.invoke(draft) }

    val body: @Composable ColumnScope.() -> Unit = {
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(autoFocusTitle) {
            if (autoFocusTitle) {
                delay(400)
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier.fillMaxWidth()
                    .then(if (fullScreen) Modifier.weight(1f) else Modifier)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
        ) {
            extra?.invoke()
            if (!fullScreen) {
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
                            if (isEditSheet) Res.string.edit_categories else Res.string.add_category
                        ),
                    style =
                        MaterialTheme.typography.headlineSmall.copy(fontFamily = flexFontEmphasis()),
                )
            }

            OutlinedTextField(
                enabled = !saving,
                state = textFieldState,
                lineLimits = TextFieldLineLimits.SingleLine,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions =
                    Default.copy(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                placeholder = { Text(text = stringResource(Res.string.add_category)) },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )

            OutlinedTextField(
                enabled = !saving,
                value = newCategory.description,
                onValueChange = { newCategory = newCategory.copy(description = it) },
                label = { Text("类别描述") },
                minLines = 3,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            // Category text color is independent of task colors.
            TitleColorRow(
                label = stringResource(Res.string.accent_color),
                value = newCategory.titleColor,
                default = MaterialTheme.colorScheme.onSurface,
                onChange = { newCategory = newCategory.copy(titleColor = it) },
                enabled = !saving,
            )

            // Completed tasks in this category stay hidden in lists and widgets
            ListItem(
                colors = listItemColors(),
                modifier = Modifier.clip(detachedItemShape()),
                leadingContent = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.check_circle),
                        contentDescription = null,
                    )
                },
                headlineContent = { Text(text = stringResource(Res.string.hide_completed)) },
                trailingContent = {
                    ExpressiveSwitch(
                        enabled = !saving,
                        checked = newCategory.hideCompleted,
                        onCheckedChange = { newCategory = newCategory.copy(hideCompleted = it) },
                    )
                },
            )

            Button(
                onClick = {
                    onUpsertCategory(newCategory.copy(name = textFieldState.text.toString()))
                },
                shapes =
                    ButtonShapes(
                        shape = MaterialTheme.shapes.extraLarge,
                        pressedShape = MaterialTheme.shapes.small,
                    ),
                modifier = Modifier.padding(bottom = 32.dp).fillMaxWidth(),
                enabled = !saving && textFieldState.text.isNotBlank(),
            ) {
                Text(text = stringResource(if (isEditSheet) Res.string.done else Res.string.save))
            }
        }
    }

    if (fullScreen) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .imePadding()
                    .background(
                        if (transparent) Color.Transparent else MaterialTheme.colorScheme.background
                    )
        ) {
            TopAppBar(
                colors =
                    if (transparent)
                        TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    else TopAppBarDefaults.topAppBarColors(),
                actions = {
                    if (pinnedActions) {
                        TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
                        TextButton(
                            onClick = {
                                onUpsertCategory(
                                    newCategory.copy(name = textFieldState.text.toString())
                                )
                            },
                            enabled = !saving && textFieldState.text.isNotBlank(),
                        ) {
                            Text(if (saving) "保存中…" else "保存")
                        }
                    }
                },
                title = {
                    Text(
                        text =
                            stringResource(
                                if (isEditSheet) Res.string.edit_categories
                                else Res.string.add_category
                            ),
                        fontFamily = flexFontEmphasis(),
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onDismiss, enabled = !saving) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.nav_arrow_back),
                            contentDescription = "Navigate Back",
                        )
                    }
                },
            )
            body()
        }
    } else {
        GritBottomSheet(
            modifier = modifier.imePadding(),
            padding = 0.dp,
            onDismissRequest = onDismiss,
        ) {
            body()
        }
    }
}

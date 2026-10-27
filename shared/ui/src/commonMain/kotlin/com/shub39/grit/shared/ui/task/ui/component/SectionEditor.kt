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

import com.shub39.grit.shared.ui.components.glass.GlassAlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.now
import com.shub39.grit.core.tasks.Section
import com.shub39.grit.shared.ui.components.TitleColorRow
import kotlinx.datetime.LocalDate

@Composable
fun SectionEditor(
    section: Section,
    onSave: (Section) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    saving: Boolean = false,
    error: String? = null,
    allowDelete: Boolean = true,
    wide: Boolean = false,
    confirmDeletion: Boolean = true,
) {
    val compactIme = wide && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var title by remember(section.id) { mutableStateOf(section.title) }
    var content by remember(section.id) { mutableStateOf(section.content) }
    var titleColor by remember(section.id) { mutableStateOf(section.titleColor) }
    var dateText by
        remember(section.id) { mutableStateOf(section.completedOn?.toString().orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val date =
        if (dateText.isBlank()) null else runCatching { LocalDate.parse(dateText) }.getOrNull()
    val valid = dateText.isBlank() || (date != null && date <= LocalDate.now())
    val fields: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(if (compactIme) 4.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(
                title,
                { title = it },
                label = { Text("Section name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !saving,
            )
            // Each Section has its own colour (solid or gradient); unset is black.
            TitleColorRow(
                label = "文字颜色",
                value = titleColor,
                default = Color.Black,
                onChange = { titleColor = it },
                enabled = !saving,
            )
            OutlinedTextField(
                content,
                { content = it },
                label = { Text("内容") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = if (wide) 3 else 5,
                enabled = !saving,
            )
            Row {
                Checkbox(
                    checked = dateText.isNotBlank(),
                    enabled = !saving,
                    onCheckedChange = { dateText = if (it) LocalDate.now().toString() else "" },
                )
                Text("已完成", Modifier.padding(top = 12.dp))
            }
            OutlinedTextField(
                dateText,
                { dateText = it },
                label = { Text("完成日期 YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !saving,
                isError = !valid,
                supportingText = { Text(if (valid) "留空表示未完成，可补记过去日期" else "请输入有效日期，不能晚于今天") },
            )
        }
    }
    val saveButton: @Composable () -> Unit = {
        TextButton(
            modifier = if (compactIme) Modifier.height(36.dp) else Modifier,
            enabled = valid && !saving,
            onClick = {
                onSave(
                    section
                        .copy(
                            title = title,
                            content = content,
                            completedOn = date,
                            titleColor = titleColor,
                        )
                        .normalized()
                )
            },
        ) {
            Text(if (saving) "保存中…" else "保存")
        }
    }
    val dismissButtons: @Composable () -> Unit = {
        Row {
            if (allowDelete)
                TextButton(
                    modifier = if (compactIme) Modifier.height(36.dp) else Modifier,
                    onClick = { if (confirmDeletion) confirmDelete = true else onDelete() },
                    enabled = !saving,
                ) {
                    Text("删除")
                }
            TextButton(
                onClick = onDismiss,
                enabled = !saving,
                modifier = if (compactIme) Modifier.height(36.dp) else Modifier,
            ) {
                Text("取消")
            }
        }
    }
    if (wide) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { if (!saving) onDismiss() },
            properties =
                androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = !saving,
                    dismissOnClickOutside = !saving,
                ),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(com.shub39.grit.shared.ui.rhine.rhineSafeDrawingInsets())
                    .imePadding()
                    .padding(8.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Surface(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = if (compactIme) 36.dp else 64.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(
                                if (section.id == 0L) "新建 Section" else "编辑 Section",
                                Modifier.weight(1f),
                                style =
                                    if (compactIme) MaterialTheme.typography.titleSmall
                                    else MaterialTheme.typography.titleLarge,
                            )
                            dismissButtons()
                            saveButton()
                        }
                        Box(Modifier.weight(1f)) { fields() }
                    }
                }
            }
        }
    } else
        GlassAlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            title = { Text(if (section.id == 0L) "新建 Section" else "编辑 Section") },
            text = fields,
            confirmButton = saveButton,
            dismissButton = dismissButtons,
        )
    if (confirmDelete)
        GlassAlertDialog(
            onDismissRequest = { if (!saving) confirmDelete = false },
            title = { Text("删除 Section") },
            text = { Text("删除「${title.ifBlank { "unname" }}」？") },
            confirmButton = { TextButton(onClick = onDelete, enabled = !saving) { Text("删除") } },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, enabled = !saving) { Text("取消") }
            },
        )
}

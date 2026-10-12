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
package com.shub39.grit.shared.ui.rhine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shub39.grit.core.tasks.*
import com.shub39.grit.shared.ui.task.ui.component.*

sealed interface RhineEdit {
    data class CategoryDraft(val value: Category) : RhineEdit

    data class TaskDraft(val value: Task, val categories: List<Category>) : RhineEdit

    data class SectionDraft(val value: Section) : RhineEdit
}

@Composable
fun RhineEditor(
    edit: RhineEdit,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSaveCategory: (Category) -> Unit,
    onSaveTask: (Task) -> Unit,
    onSaveSection: (Section) -> Unit,
    onDelete: () -> Unit,
) {
    if (edit is RhineEdit.SectionDraft) {
        SectionEditor(
            edit.value,
            onSaveSection,
            onDelete,
            onDismiss,
            saving,
            error,
            allowDelete = edit.value.id != 0L,
            confirmDeletion = false,
            wide = true,
        )
        return
    }
    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !saving,
                dismissOnClickOutside = !saving,
            ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(0.68f)
                    .widthIn(max = 640.dp)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (
                    (edit is RhineEdit.CategoryDraft && edit.value.id != 0L) ||
                        (edit is RhineEdit.TaskDraft && edit.value.id != 0L)
                ) {
                    TextButton(onClick = onDelete, enabled = !saving) { Text("删除当前对象") }
                }
                error?.let {
                    Text(
                        it,
                        Modifier.fillMaxWidth().padding(12.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                when (edit) {
                    is RhineEdit.CategoryDraft ->
                        CategoryUpsertSheet(
                            isEditSheet = edit.value.id != 0L,
                            category = edit.value,
                            onDismiss = onDismiss,
                            onUpsertCategory = onSaveCategory,
                            fullScreen = true,
                            autoFocusTitle = false,
                            saving = saving,
                            pinnedActions = true,
                        )
                    is RhineEdit.TaskDraft ->
                        TaskUpsertSheet(
                            task = edit.value,
                            categories = edit.categories,
                            onDismissRequest = onDismiss,
                            onUpsert = onSaveTask,
                            onDelete = {},
                            is24Hr = true,
                            isEditSheet = edit.value.id != 0L,
                            fullScreen = true,
                            autoFocusTitle = false,
                            saving = saving,
                            restrictStructure = true,
                            allowDraftChanges = edit.value.id != 0L,
                        )
                    else -> Unit
                }
            }
        }
    }
}

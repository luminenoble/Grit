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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.shub39.grit.shared.ui.components.glass.GlassButton
import com.shub39.grit.shared.ui.components.glass.GlassButtonStyle
import com.shub39.grit.shared.ui.theme.GlassBackdrop
import com.shub39.grit.shared.ui.theme.GlassShapes
import com.shub39.grit.shared.ui.theme.GlassTier
import com.shub39.grit.shared.ui.theme.glassSurface
import com.shub39.grit.core.tasks.*
import com.shub39.grit.shared.ui.task.ui.component.*

sealed interface RhineEdit {
    data class CategoryDraft(val value: Category) : RhineEdit

    data class TaskDraft(val value: Task, val categories: List<Category>) : RhineEdit

    data class SectionDraft(val value: Section) : RhineEdit
}

/**
 * Category/Task editor: the right part of a split screen on thick glass, drawn in the Rhine window
 * so the scene on the left keeps showing the raised block (docs/视觉优化/02 section 7). Sections keep
 * their own editor. [panelAlpha] follows the entry/exit transition (read at layer time); [onDraft] reports the live draft
 * for the preview block. [extra] is shown first in the form.
 */
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
    panelAlpha: () -> Float = { 1f },
    panelFraction: Float = 0.5f,
    onDraft: (RhineEdit) -> Unit = {},
    backdrop: GlassBackdrop? = null,
    extra: (@Composable () -> Unit)? = null,
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
    // Registered before the form so a form's own unsaved-changes handler takes precedence.
    DraftBackHandler(enabled = !saving, onBack = onDismiss)
    Box(
        Modifier.fillMaxSize()
            // The preview side is not interactive; touches never reach the scene underneath.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .windowInsetsPadding(rhineSafeDrawingInsets().exclude(WindowInsets.ime)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier.graphicsLayer {
                    val a = panelAlpha()
                    alpha = a
                    translationX = (1f - a) * 32.dp.toPx()
                }
                .fillMaxHeight()
                .fillMaxWidth(panelFraction)
                .padding(8.dp)
                .glassSurface(GlassTier.Thick, GlassShapes.Panel, backdrop)
                .clip(GlassShapes.Panel)
        ) {
            if (
                (edit is RhineEdit.CategoryDraft && edit.value.id != 0L) ||
                    (edit is RhineEdit.TaskDraft && edit.value.id != 0L)
            ) {
                GlassButton(
                    onClick = onDelete,
                    enabled = !saving,
                    style = GlassButtonStyle.Danger,
                    modifier = Modifier.padding(start = 12.dp, top = 10.dp),
                ) {
                    Text("删除当前对象")
                }
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
                        onDraftChange = { onDraft(RhineEdit.CategoryDraft(it)) },
                        transparent = true,
                        extra = extra,
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
                        onDraftChange = { onDraft(RhineEdit.TaskDraft(it, edit.categories)) },
                        transparent = true,
                        extra = extra,
                    )
                else -> Unit
            }
        }
    }
}

/** Width share of the editor panel: half on wide screens, more on small or large-font ones. */
fun rhineEditorPanelFraction(widthDp: Float, fontScale: Float): Float =
    when {
        widthDp < 640f || fontScale > 1.35f -> maxOf(0.58f, 340f / widthDp)
        widthDp < 840f -> maxOf(0.52f, 360f / widthDp)
        else -> 0.5f
    }.coerceAtMost(0.8f)

/**
 * Label-carrying stand-in for the edited object: a column-start (0) block showing the draft's own
 * name and colour, whatever slot the edit started from (docs/视觉优化/02 section 4.3).
 */
fun rhineDraftSlot(edit: RhineEdit?, base: RhineSlot, index: Int): RhineSlot {
    val (id, title, color) =
        when (edit) {
            is RhineEdit.CategoryDraft -> Triple(edit.value.id, edit.value.name, edit.value.titleColor)
            is RhineEdit.TaskDraft -> Triple(edit.value.id, edit.value.title, edit.value.titleColor)
            else -> return base
        }
    return RhineSlot(
        base.u,
        base.v,
        RhineBlockKind.Zero,
        RhineColumn(
            id = id,
            index = index,
            title = title.ifBlank { "未命名" },
            titleColor = color,
            itemCount = 0,
            items = emptyMap(),
        ),
    )
}

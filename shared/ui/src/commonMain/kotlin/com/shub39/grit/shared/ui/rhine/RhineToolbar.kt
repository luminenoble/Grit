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

import com.shub39.grit.shared.ui.theme.withTitleColor
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shub39.grit.core.settings.RhineDisplayConfig
import com.shub39.grit.shared.ui.components.glass.GlassButton
import com.shub39.grit.shared.ui.components.glass.GlassButtonStyle
import com.shub39.grit.shared.ui.components.glass.GlassToolbar
import com.shub39.grit.shared.ui.components.glass.GlassToolbarDivider
import com.shub39.grit.shared.ui.theme.GlassBackdrop
import com.shub39.grit.shared.ui.theme.GlassTokens
import kotlin.math.abs
import kotlin.math.roundToInt

/** How much room the bottom toolbar may take; see docs/Rhine.md §4. */
enum class RhineToolbarProfile {
    /** Two-line focus readout, 52 dp. */
    Full,
    /** One scrolling row, 48 dp (narrow HUD). */
    Narrow,
    /** Icons and focus title only, 40 dp, when the focus block would otherwise be covered. */
    Low,
}

/** Toolbar heights in dp per profile. */
fun RhineToolbarProfile.heightDp(): Float =
    when (this) {
        RhineToolbarProfile.Full -> 52f
        RhineToolbarProfile.Narrow -> 48f
        RhineToolbarProfile.Low -> 40f
    }

/**
 * Picks the toolbar profile so its top edge stays at least 8 dp below the lowest floor point of the
 * focused block. The scene anchor never moves to make room.
 *
 * [footerDp] is the space reserved under the toolbar (footer row and its margin).
 */
fun rhineToolbarProfile(
    geometry: RhineGeometry,
    widthDp: Float,
    heightDp: Float,
    narrow: Boolean,
    footerDp: Float,
): RhineToolbarProfile {
    val lowestFloor =
        geometry.anchor.y * heightDp +
            geometry.blockLength / 2f * abs(geometry.basisV.y) +
            geometry.blockDepth / 2f * abs(geometry.basisU.y)
    val preferred = if (narrow) RhineToolbarProfile.Narrow else RhineToolbarProfile.Full
    val top = heightDp - footerDp - preferred.heightDp()
    return if (top >= lowestFloor + 8f || widthDp <= 0f) preferred else RhineToolbarProfile.Low
}

/**
 * Bottom toolbar hosting what used to live in the persistent right-side panel: back to the parent
 * layer, along-column stepping, column switching, the focus readout, the mode action and delete.
 * Callbacks are the same ones the panel used.
 */
@Composable
internal fun RhineToolbar(
    input: RhineSceneInput,
    style: RhineStyle,
    mode: RhineMode,
    focus: RhineSlot,
    enabled: Boolean,
    parentTitle: String?,
    display: RhineDisplayConfig,
    profile: RhineToolbarProfile,
    backdrop: GlassBackdrop?,
    t: HudText,
    onBack: (() -> Unit)?,
    onAction: () -> Unit,
    onDelete: () -> Unit,
    onStepU: (Int) -> Unit,
    onStepV: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val column = focus.column
    val isCategories = input.layer == RhineLayer.Categories
    val itemWord = if (isCategories) "TASK" else "SECTION"
    val parentWord = if (isCategories) "CATEGORY" else "TASK"
    val action =
        when {
            mode == RhineMode.Preview -> null
            mode == RhineMode.Adjust -> "移动到第 N 项"
            focus.kind == RhineBlockKind.Zero -> "编辑 $parentWord"
            focus.kind == RhineBlockKind.Real ->
                if (isCategories) "进入 Section 层" else "编辑 Section"
            focus.kind == RhineBlockKind.AddParent -> "新建 $parentWord"
            focus.kind == RhineBlockKind.Placeholder -> "尾部新增 $itemWord"
            else -> null
        }
    val actionEnabled =
        enabled &&
            when {
                mode == RhineMode.Adjust -> canAdjustRhine(mode, true, input.focusV, focus)
                focus.kind == RhineBlockKind.Real -> focus.item != null
                else -> true
            }
    val showDelete = mode == RhineMode.Detail && column != null
    val title =
        if (!display.itemNumbers(input.layer) && focus.kind == RhineBlockKind.Real)
            focus.item?.title ?: "载入中…"
        else focusTitle(focus, itemWord)
    val detail = buildList {
        if (!isCategories && parentTitle != null) add(parentTitle)
        add(focusStatus(focus))
        if (profile != RhineToolbarProfile.Full)
            add("DONE ${column?.completedCount ?: 0}/${column?.itemCount ?: 0}")
        add("DATE ${focus.item?.completedOn?.toString() ?: "—"}")
    }

    val groups: @Composable RowScope.() -> Unit = {
        if (onBack != null) {
            HudButton(
                if (profile == RhineToolbarProfile.Low) "←" else "← 返回",
                t,
                enabled,
                "返回类别层",
                11.sp,
                onBack,
            )
            GlassToolbarDivider()
        }
        // Along-column stepping.
        HudButton("↑", t, enabled && input.focusU > 0f, "沿列上一格", 14.sp, { onStepU(-1) })
        if (profile == RhineToolbarProfile.Full)
            TickBar(
                count = input.maxU + 1,
                focus = input.focusU.roundToInt(),
                ink = t.ink,
                modifier = Modifier.width(96.dp).height(14.dp),
            )
        HudButton(
            "↓",
            t,
            enabled && input.focusU < input.maxU,
            "沿列下一格",
            14.sp,
            { onStepU(1) },
        )
        GlassToolbarDivider()
        // Column switch around the center column's name.
        HudButton("‹", t, enabled && input.focusV > 0, "上一列", 15.sp, { onStepV(-1) })
        Column(Modifier.widthIn(max = 150.dp)) {
            if (profile != RhineToolbarProfile.Low)
                Text(
                    if (column == null) "NEW $parentWord"
                    else if (display.parentNumbers(input.layer))
                        "$parentWord ${indexLabel(input.focusV + 1)}/${indexLabel(input.columnCount)}"
                    else parentWord,
                    style = t.style(8.sp, alpha = 0.7f),
                    maxLines = 1,
                )
            Text(
                column?.title ?: "新建",
                style = t.style(10.sp).withTitleColor(column?.titleColor, style.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HudButton("›", t, enabled && input.focusV < input.maxV, "下一列", 15.sp, { onStepV(1) })
        GlassToolbarDivider()
    }
    val focusReadout: @Composable (Modifier) -> Unit = { m ->
        Column(m.padding(horizontal = 6.dp)) {
            Text(
                title,
                style =
                    t.style(12.sp, bold = true).withTitleColor(focus.item?.titleColor, style.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (profile != RhineToolbarProfile.Low)
                Text(
                    detail.joinToString("  ·  "),
                    style = t.style(9.sp).copy(color = GlassTokens.SecondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
        }
    }
    val actions: @Composable RowScope.() -> Unit = {
        if (action != null)
            GlassButton(
                onClick = onAction,
                enabled = actionEnabled,
                style = GlassButtonStyle.Prominent,
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                // Unspecified colour lets the button's content colour apply.
                Text(
                    "$action  →",
                    style = t.style(10.sp, bold = true).copy(color = Color.Unspecified),
                    maxLines = 1,
                )
            }
        if (showDelete)
            GlassButton(
                onClick = onDelete,
                enabled = enabled && (focus.kind != RhineBlockKind.Real || focus.item != null),
                style = GlassButtonStyle.Danger,
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Text(
                    "删除 ${if (focus.kind == RhineBlockKind.Real) itemWord else parentWord}",
                    style = t.style(10.sp).copy(color = Color.Unspecified),
                    maxLines = 1,
                )
            }
    }

    GlassToolbar(modifier.height(profile.heightDp().dp), backdrop) {
        if (profile == RhineToolbarProfile.Full) {
            groups()
            focusReadout(Modifier.weight(1f))
            actions()
        } else {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                groups()
                focusReadout(Modifier.widthIn(min = 160.dp, max = 280.dp))
                actions()
            }
        }
    }
}

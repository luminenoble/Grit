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

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.shub39.grit.shared.ui.theme.GlassShapes
import com.shub39.grit.shared.ui.theme.GlassTier
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.glassSurface

/**
 * Category chip on a glass tray. The selected chip is a bright lens raised off the tray (warm
 * shadow, rim light, semibold label); the others lie flat in quieter ink. No outline.
 */
@Composable
fun CategoryToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = GlassShapes.Capsule
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier =
            modifier.then(
                if (checked) Modifier.glassSurface(GlassTier.Thin, shape, tint = GlassTokens.Raised)
                else Modifier
            ),
        enabled = enabled,
        shapes = ToggleButtonDefaults.shapes(shape, shape, shape),
        colors =
            ToggleButtonDefaults.toggleButtonColors(
                containerColor = Color.Transparent,
                contentColor = GlassTokens.SecondaryText,
                checkedContainerColor = Color.Transparent,
                checkedContentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides
                LocalTextStyle.current.copy(
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
                )
        ) {
            content()
        }
    }
}

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
package com.shub39.grit.widgets

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shub39.grit.core.settings.WidgetTextSize

/**
 * Widget text metrics derived from the user's text size preference. Shared by every widget so the
 * setting applies to tasks and habits alike.
 */

/** Multiplier for text sizes a widget picks itself, e.g. stat numbers and chart labels. */
val WidgetTextSize.scale: Float
    get() =
        when (this) {
            WidgetTextSize.SMALL -> 0.8f
            WidgetTextSize.MEDIUM -> 1f
            WidgetTextSize.LARGE -> 1.2f
        }

/** Text size of a list item's primary line. */
val WidgetTextSize.bodyFontSize: TextUnit
    get() =
        when (this) {
            WidgetTextSize.SMALL -> 11.sp
            WidgetTextSize.MEDIUM -> 14.sp
            WidgetTextSize.LARGE -> 17.sp
        }

/** Text size of a list item's secondary line, e.g. a description or step count. */
val WidgetTextSize.captionFontSize: TextUnit
    get() = bodyFontSize * 0.8f

/** Padding around item text; smaller text tightens it so small widgets fit more rows. */
val WidgetTextSize.itemPadding: Dp
    get() =
        when (this) {
            WidgetTextSize.SMALL -> 4.dp
            WidgetTextSize.MEDIUM -> 8.dp
            WidgetTextSize.LARGE -> 10.dp
        }

/** Gap between list items. */
val WidgetTextSize.itemSpacing: Dp
    get() =
        when (this) {
            WidgetTextSize.SMALL -> 2.dp
            else -> 4.dp
        }

/** Scales a size the widget hardcodes for its own layout. */
fun TextUnit.scaledBy(textSize: WidgetTextSize): TextUnit = this * textSize.scale

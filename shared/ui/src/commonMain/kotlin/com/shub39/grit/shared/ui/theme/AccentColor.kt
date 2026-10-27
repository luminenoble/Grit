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
package com.shub39.grit.shared.ui.theme

import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import com.shub39.grit.core.tasks.titleColorStops

/** Stops of a stored title colour (one for a solid colour), or null when unset or malformed. */
fun parseTitleColors(value: String?): List<Color>? =
    titleColorStops(value)?.map { Color(0xFF000000 or it.toLong()) }

/** The solid colour, or the first stop of a gradient. */
fun parseAccentColor(value: String?): Color? = parseTitleColors(value)?.first()

fun Color.toHexString(): String =
    "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

/** Stored form of [colors]: "#RRGGBB", or a comma-separated gradient. */
fun titleColorValue(colors: List<Color>): String = colors.joinToString(",") { it.toHexString() }

/** Selected text colour exactly as chosen (no contrast adjustment); a gradient's first stop. */
fun titleColor(value: String?, default: Color = RhineBaseColors.onSurface): Color =
    parseAccentColor(value) ?: default

/** Left-to-right brush for the stored colour; a solid colour for one stop or none. */
fun titleBrush(value: String?, default: Color = RhineBaseColors.onSurface): Brush {
    val colors = parseTitleColors(value) ?: return SolidColor(default)
    return if (colors.size == 1) SolidColor(colors[0]) else Brush.horizontalGradient(colors)
}

/**
 * [this] drawn in the stored title colour: a solid colour, or a left-to-right gradient across the
 * text. A gradient brush wins over any `color` passed to Text alongside the style.
 */
fun TextStyle.withTitleColor(
    value: String?,
    default: Color = RhineBaseColors.onSurface,
    alpha: Float = 1f,
): TextStyle {
    val colors = parseTitleColors(value)
    return if (colors == null || colors.size == 1) {
        val c = colors?.first() ?: default
        copy(color = c.copy(alpha = c.alpha * alpha))
    } else copy(brush = Brush.horizontalGradient(colors), alpha = alpha)
}

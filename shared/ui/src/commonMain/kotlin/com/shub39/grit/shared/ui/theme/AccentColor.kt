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

fun parseAccentColor(value: String?): Color? {
    if (value == null || !Regex("#[0-9a-fA-F]{6}").matches(value)) return null
    return Color(value.drop(1).toLong(16).or(0xFF000000).toInt())
}

fun Color.toHexString(): String =
    "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

/** Darken selected text to remain readable against the fixed light surfaces. */
fun readableTitleColor(value: String?): Color {
    val original = parseAccentColor(value) ?: RhineBaseColors.onSurface
    var color = original
    repeat(30) {
        if ((RhineBaseColors.surface.luminance() + 0.05f) / (color.luminance() + 0.05f) >= 4.5f)
            return color
        color = lerp(color, Color.Black, 0.1f)
    }
    return RhineBaseColors.onSurface
}

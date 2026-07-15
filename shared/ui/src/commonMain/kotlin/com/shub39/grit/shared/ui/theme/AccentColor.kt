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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Custom accent colors for categories and habits, stored as strings in the database. Supports hex
 * ("#RRGGBB" / "#AARRGGBB") plus the legacy named values from CategoryColors.
 */
private val legacyNames =
    mapOf(
        "gray" to Color(0xFF9E9E9E),
        "blue" to Color(0xFF5B8DEF),
        "red" to Color(0xFFE5484D),
        "orange" to Color(0xFFF76B15),
        "green" to Color(0xFF46A758),
        "yellow" to Color(0xFFEAB308),
    )

/** Parses a stored accent string to a [Color], or null when unset/unparseable. */
fun parseAccentColor(value: String?): Color? {
    if (value.isNullOrBlank()) return null

    legacyNames[value.lowercase()]?.let {
        return it
    }

    val hex = value.removePrefix("#")
    val argb =
        when (hex.length) {
            6 -> hex.toLongOrNull(16)?.or(0xFF000000)
            8 -> hex.toLongOrNull(16)
            else -> null
        } ?: return null

    return Color(argb.toInt())
}

/** Formats a color back to the stored "#AARRGGBB" form. */
fun Color.toHexString(): String {
    return "#" + toArgb().toUInt().toString(16).padStart(8, '0').uppercase()
}

/** Black or white, whichever reads better on this color. */
fun Color.bestContentColor(): Color {
    return if (luminance() > 0.5f) Color.Black else Color.White
}

/** How many shade variants an accent cycles through before repeating. */
private const val ACCENT_VARIANT_CYCLE = 5

/**
 * Shade variant for list position [position]: the same hue nudged lighter or darker in a cycle of
 * [ACCENT_VARIANT_CYCLE], so stacked cards sharing one accent don't read as a flat block. Neighbors
 * alternate direction to keep adjacent cards visibly distinct.
 */
fun Color.accentVariant(position: Int): Color {
    return when (
        ((position % ACCENT_VARIANT_CYCLE) + ACCENT_VARIANT_CYCLE) % ACCENT_VARIANT_CYCLE
    ) {
        0 -> this
        1 -> lerp(this, Color.White, 0.22f)
        2 -> lerp(this, Color.Black, 0.18f)
        3 -> lerp(this, Color.White, 0.40f)
        else -> lerp(this, Color.Black, 0.34f)
    }
}

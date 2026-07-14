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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Central design tokens for Grit.
 *
 * Inspired by the Ant Design token system (as reused by HuskarUI): a small set of base values from
 * which the whole UI derives its rhythm. Keeping radius, spacing and semantic colors in one place
 * avoids magic numbers scattered across composables and keeps the look consistent.
 */

/** Corner radius scale (base ~ 6dp, Ant Design style). */
object GritRadius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp

    /** Rounded corner used to visually join adjacent items in a grouped list. */
    val join = 6.dp
}

/** Spacing scale used for padding and gaps. */
object GritSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

/**
 * Fixed status colors layered on top of the dynamic Material color scheme. The dynamic seed color
 * still drives the primary palette; these only convey semantic state (done / due / danger).
 */
@Immutable
data class GritSemanticColors(
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
) {
    /** Soft tinted container derived from a status color, safe on both light and dark surfaces. */
    fun container(color: Color): Color = color.copy(alpha = 0.14f)
}

private val LightSemantic =
    GritSemanticColors(
        success = Color(0xFF52C41A), // Ant green-6
        warning = Color(0xFFFAAD14), // Ant gold-6
        error = Color(0xFFFF4D4F), // Ant red-5
        info = Color(0xFF1677FF), // Ant blue-6
    )

private val DarkSemantic =
    GritSemanticColors(
        success = Color(0xFF73D13D), // Ant green-5 (brighter for dark)
        warning = Color(0xFFFFC53D), // Ant gold-5
        error = Color(0xFFFF7875), // Ant red-4
        info = Color(0xFF40A9FF), // Ant blue-5
    )

/** Semantic status colors adapted to the current light/dark theme. */
@Composable
fun gritSemanticColors(dark: Boolean = isSystemInDarkTheme()): GritSemanticColors =
    if (dark) DarkSemantic else LightSemantic

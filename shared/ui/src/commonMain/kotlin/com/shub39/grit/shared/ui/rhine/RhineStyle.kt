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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.shub39.grit.shared.ui.theme.parseTitleColors

/** Material and text defaults for the Rhine scene. Text colors never tint the blocks. */
@Immutable
data class RhineStyle(
    // Sampled from docs/Rhine_reference.png.
    val floor: Color = Color(0xFFEAE5E1),
    val top: Color = Color(0xFFEDE9E6),
    val topFar: Color = Color(0xFFE7E2DD),
    /**
     * Long side face carrying the column label. Shading is graded across the screen: [front] /
     * [frontLow] at the centre, easing to [frontEdge] / [frontLowEdge] towards the borders.
     */
    val front: Color = Color(0xFFB3A99D),
    val frontLow: Color = Color(0xFFA69C90),
    val frontEdge: Color = Color(0xFFDCD6CF),
    val frontLowEdge: Color = Color(0xFFD2CBC3),
    val cap: Color = Color(0xFFD6D1CB),
    val highlight: Color = Color(0xFFFBF9F7),
    /** Crease between faces; darker than the faces so the slats read as hard-edged. */
    val edge: Color = Color(0x2E574B3F),
    val shadow: Color = Color(0xFF8C7764),
    /** Contact shadow opacity at the screen centre and at the borders. */
    val shadowAlphaCenter: Float = 0.10f,
    val shadowAlphaEdge: Float = 0.03f,
    /** Small round fitting near the far end of each long face. */
    val marker: Color = Color(0xFF8A8C8D),
    /**
     * Transparent glass blocks (docs/视觉优化/06): each face is a light veil over whatever stands
     * behind it, the end face a little deeper as it turns from the light, and the edges catch the
     * light as a fine white rim.
     */
    val glassTop: Color = Color(0xC7FFFFFF),
    val glassTopFar: Color = Color(0xA3FBF8F4),
    val glassFront: Color = Color(0x85FFFFFF),
    val glassFrontLow: Color = Color(0x5CB4AA9E),
    val glassCap: Color = Color(0x99ACA194),
    val glassRim: Color = Color(0xF2FFFFFF),
    val glassEdge: Color = Color(0x4D574B3F),
    /** Default text on the light top face. */
    val text: Color = Color(0xFF221F1A),
    /** Default text on the dark side face. */
    val sideText: Color = Color(0xFFF8F4EE),
    val mutedText: Color = Color(0xFF8C857A),
    /** HUD ink. */
    val ink: Color = Color(0xFF100E06),
    val topTextSize: TextUnit = 8.sp,
    val sideTextSize: TextUnit = 7.sp,
) {
    /**
     * Text colours for a stored title colour, exactly as the user chose them (one colour, or the
     * stops of a left-to-right gradient); [default] when none is set.
     */
    fun titleColors(value: String?, default: Color = text): List<Color> =
        parseTitleColors(value) ?: listOf(default)

    /** Side face colours for a block whose centrality is [center] (1 = screen centre, 0 = edge). */
    fun frontAt(center: Float): Color = lerp(frontEdge, front, center)

    fun frontLowAt(center: Float): Color = lerp(frontLowEdge, frontLow, center)

    fun shadowAlphaAt(center: Float): Float =
        shadowAlphaEdge + (shadowAlphaCenter - shadowAlphaEdge) * center
}

fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

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

/**
 * Material and text defaults for the Rhine scene. Blocks take their column's colour
 * (docs/Rhine.md §7.3); title colours only ever change the text.
 */
@Immutable
data class RhineStyle(
    // Sampled from docs/Rhine_reference.png.
    val floor: Color = Color(0xFFEAE5E1),
    val top: Color = Color(0xFFEDE9E6),
    val topFar: Color = Color(0xFFE7E2DD),
    /**
     * Long face carrying the title and picture. Shading is graded across the screen: [front] /
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
    /** Share of the column colour mixed into the opaque matte faces. */
    val matteTintAlpha: Float = 0.30f,
    /**
     * Frosted glass (docs/Rhine.md §7.1), the sharp surface: the column colour, then a milky white
     * over each face, brightest on the top that faces the light, with a soft light from above and a
     * little shade at the foot of the long face, and a fine grain.
     */
    val tintAlpha: Float = 0.30f,
    val frostFront: Color = Color(0x4DFFFFFF),
    val frostTop: Color = Color(0xC7FFFFFF),
    val frostCap: Color = Color(0x22FFFFFF),
    val capShade: Color = Color(0x1F5B4B3A),
    val frontLight: Color = Color(0x3DFFFFFF),
    val footShade: Color = Color(0x24463828),
    val grainAlpha: Float = 0.05f,
    /** White laid over a picture on glass that is not selected. */
    val imageVeil: Float = 0.10f,
    /** Lit edges, and the fine outline in a darkened column colour (or this warm grey). */
    val rim: Color = Color(0xF2FFFFFF),
    val glassEdge: Color = Color(0x73574B3F),
    val edgeInk: Color = Color(0xFF28201A),
    /**
     * Frosted glass, the backdrop: what light passes through the surfaces, blurred as a whole.
     * The column colour is denser here, as it is seen through more than one pane.
     */
    val backdropFront: Color = Color(0x4DB0A69A),
    val backdropTop: Color = Color(0x99FFFFFF),
    val backdropCap: Color = Color(0x66968B7E),
    val backdropTintAlpha: Float = 0.42f,
    /** Outline of the focused block's long face. */
    val focusLine: Color = Color(0x8C221F1A),
    /** Default text on glass and on the light matte top. */
    val text: Color = Color(0xFF221F1A),
    /** Default text on the dark matte long face. */
    val sideText: Color = Color(0xFFF8F4EE),
    val mutedText: Color = Color(0xFF8C857A),
    /** HUD ink. */
    val ink: Color = Color(0xFF100E06),
    /** The block title, on the long face. */
    val titleTextSize: TextUnit = 8.sp,
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

/**
 * Colour stops a block is tinted with: its column's colour, that is the colour of the column's 0
 * block (docs/Rhine.md §7.3). Null for decorations, the new-column slot and uncoloured columns.
 */
fun RhineSlot.tintColors(): List<Color>? =
    when (kind) {
        RhineBlockKind.Zero,
        RhineBlockKind.Real,
        RhineBlockKind.Placeholder -> parseTitleColors(column?.titleColor)
        else -> null
    }

fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

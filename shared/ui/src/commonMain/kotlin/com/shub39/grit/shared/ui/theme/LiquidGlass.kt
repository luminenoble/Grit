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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Liquid glass material, inspired by Apple's Liquid Glass design language.
 *
 * Since real backdrop blur isn't available in common Compose code, the material is built from
 * layers that read as glass on every platform: a translucent tint fill, a vertical specular sheen
 * that fades out towards the middle, and a hairline gradient border that is brightest where light
 * would naturally catch the edge (top-left).
 */
object LiquidGlassDefaults {
    /** Container fill alpha for cards laid over the app background. */
    const val CARD_ALPHA = 0.60f

    /** Fill alpha for the floating navigation pill. */
    const val NAV_ALPHA = 0.72f
}

/**
 * Draws the liquid glass material clipped to [shape].
 *
 * @param fill translucent tint of the pane; pass the alpha you want baked into the color.
 * @param border overrides the specular gradient border with a solid color (e.g. status accents).
 * @param fillEnd when set, the fill becomes a diagonal gradient from [fill] to this color.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    fill: Color,
    border: Color? = null,
    fillEnd: Color? = null,
): Modifier {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val sheenAlpha = if (isDark) 0.09f else 0.30f
    val borderBright = if (isDark) 0.28f else 0.60f
    val borderFaint = if (isDark) 0.06f else 0.12f

    // drawWithCache re-runs only on size/state change; drawOutline avoids Path allocations
    // for simple rounded shapes, keeping per-frame cost low under animateContentSize.
    return clip(shape).drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)

        val fillBrush = fillEnd?.let { Brush.linearGradient(0f to fill, 1f to it) }
        val sheen =
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = sheenAlpha),
                0.45f to Color.Transparent,
                endY = size.height,
            )
        val edge =
            border?.let { SolidColor(it) }
                ?: Brush.linearGradient(
                    0f to Color.White.copy(alpha = borderBright),
                    0.5f to Color.White.copy(alpha = borderFaint),
                    1f to Color.White.copy(alpha = borderBright / 2f),
                )
        val edgeStroke = Stroke(width = 1.dp.toPx() * 2) // half is clipped away by the shape

        onDrawBehind {
            if (fillBrush != null) {
                drawOutline(outline, brush = fillBrush)
            } else {
                drawOutline(outline, color = fill)
            }
            drawOutline(outline, brush = sheen)
            drawOutline(outline, brush = edge, style = edgeStroke)
        }
    }
}

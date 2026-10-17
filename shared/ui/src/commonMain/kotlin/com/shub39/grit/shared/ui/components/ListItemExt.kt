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
package com.shub39.grit.shared.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import com.shub39.grit.shared.ui.theme.GlassShapes
import com.shub39.grit.shared.ui.theme.GlassTier
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.glassSurface
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private const val CONNECTED_CORNER_RADIUS = 4
private const val END_CORNER_RADIUS = 16

@Composable
fun listItemColors(): ListItemColors {
    return ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
}

/** Rows laid on glass ([GlassGroup], [glassItem]) stay clear so the material shows through. */
@Composable
fun glassListItemColors(): ListItemColors =
    ListItemDefaults.colors(containerColor = Color.Transparent)

/** A standalone row on content-tier glass; clipped so its ripple follows [shape]. */
@Composable
fun Modifier.glassItem(shape: Shape): Modifier =
    glassSurface(GlassTier.Content, shape, shadow = false).clip(shape)

/**
 * Settings group: one content-tier glass panel with hairlines between its rows, replacing a column
 * of separately coloured rows.
 */
@Composable
fun GlassGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dividers = remember { mutableListOf<Float>() }
    val inset = with(LocalDensity.current) { 16.dp.toPx() }
    Layout(
        content = content,
        modifier =
            modifier
                .glassSurface(GlassTier.Content, GlassShapes.Card)
                .clip(GlassShapes.Card)
                .drawWithContent {
                    drawContent()
                    for (y in dividers)
                        drawLine(
                            GlassTokens.Hairline.copy(alpha = 0.18f),
                            Offset(inset, y),
                            Offset(size.width - inset, y),
                            strokeWidth = 0.5.dp.toPx(),
                        )
                },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
        layout(width, placeables.sumOf { it.height }) {
            dividers.clear()
            var y = 0
            placeables.forEachIndexed { i, placeable ->
                if (i > 0) dividers += y.toFloat()
                placeable.place(0, y)
                y += placeable.height
            }
        }
    }
}

fun leadingItemShape(
    topRadius: Int = END_CORNER_RADIUS,
    bottomRadius: Int = CONNECTED_CORNER_RADIUS,
): Shape =
    RoundedCornerShape(
        topStart = topRadius.dp,
        topEnd = topRadius.dp,
        bottomEnd = bottomRadius.dp,
        bottomStart = bottomRadius.dp,
    )

fun middleItemShape(radius: Int = CONNECTED_CORNER_RADIUS): Shape =
    RoundedCornerShape(
        topStart = radius.dp,
        topEnd = radius.dp,
        bottomStart = radius.dp,
        bottomEnd = radius.dp,
    )

fun endItemShape(
    topRadius: Int = CONNECTED_CORNER_RADIUS,
    bottomRadius: Int = END_CORNER_RADIUS,
): Shape =
    RoundedCornerShape(
        topStart = topRadius.dp,
        topEnd = topRadius.dp,
        bottomEnd = bottomRadius.dp,
        bottomStart = bottomRadius.dp,
    )

fun detachedItemShape(radius: Int = END_CORNER_RADIUS): Shape = RoundedCornerShape(radius.dp)

@Composable
fun segmentedListItemShapes(
    index: Int,
    count: Int,
    singleElement: Boolean = count == 1,
): ListItemShapes =
    ListItemDefaults.segmentedShapes(
        index,
        count,
        ListItemDefaults.shapes(
            shape = if (singleElement) shapes.large else shapes.extraSmall,
            selectedShape = shapes.extraLargeIncreased,
            pressedShape = shapes.extraLargeIncreased,
            focusedShape = shapes.large,
            hoveredShape = shapes.extraLarge,
            draggedShape = shapes.extraLargeIncreased,
        ),
    )

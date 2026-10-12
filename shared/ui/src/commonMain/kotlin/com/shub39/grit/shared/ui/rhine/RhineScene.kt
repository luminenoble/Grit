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
import androidx.compose.ui.geometry.Offset

enum class RhineLayer {
    /** Columns are Categories, blocks are Tasks. */
    Categories,
    /** Columns are sibling Tasks of one Category, blocks are Sections. */
    Sections,
}

enum class RhineBlockKind {
    /** Column start (slot 0): operates on the parent object itself. */
    Zero,
    /** A stored Task or Section. */
    Real,
    /** Inside the logical bounds of a real column, past its last item. Never stored. */
    Placeholder,
    /** The single column after the last real column, reserved for creating a parent. */
    AddParent,
    /** Outside the logical bounds; drawn only to fill the screen. No business meaning. */
    Decoration,
}

/** Summary of one block's content. Index is the 1-based display order, not a database ID. */
@Immutable
data class RhineItem(
    val id: Long,
    val index: Int,
    val title: String,
    val titleColor: String? = null,
    val isCompleted: Boolean = false,
    val completedOn: kotlinx.datetime.LocalDate? = null,
)

/** One column: a Category (layer 1) or a sibling Task (layer 2). */
@Immutable
data class RhineColumn(
    val id: Long,
    val index: Int,
    val title: String,
    val titleColor: String? = null,
    /** Total real items, used for bounds. May exceed [items] once windowed loading arrives. */
    val itemCount: Int,
    /** Loaded items keyed by their 1-based index. */
    val items: Map<Int, RhineItem>,
    val completedCount: Int = items.values.count { it.isCompleted },
)

/** Everything the scene needs; independent of repositories and of drawing. */
@Immutable
data class RhineSceneInput(
    val layer: RhineLayer,
    val columns: List<RhineColumn>,
    /** Center column index `v` and fractional focus `u` along it. */
    val focusV: Int,
    val focusU: Float,
    val cameraV: Float = focusV.toFloat(),
) {
    /** C: real column count. */
    val columnCount: Int
        get() = columns.size

    /** M: longest real column. */
    val maxItemCount: Int
        get() = columns.maxOfOrNull { it.itemCount } ?: 0

    /** Focusable range `u in 0..M+1`, `v in 0..C`. With no columns U stays at the start. */
    val maxU: Int
        get() = if (columns.isEmpty()) 0 else maxItemCount + 1

    val maxV: Int
        get() = columnCount

    fun slot(u: Int, v: Int): RhineSlot {
        if (u < 0 || v < 0 || u > maxU || v > maxV)
            return RhineSlot(u, v, RhineBlockKind.Decoration)
        if (v == columnCount) return RhineSlot(u, v, RhineBlockKind.AddParent)
        val column = columns[v]
        val kind =
            when {
                u == 0 -> RhineBlockKind.Zero
                u <= column.itemCount -> RhineBlockKind.Real
                else -> RhineBlockKind.Placeholder
            }
        return RhineSlot(
            u,
            v,
            kind,
            column,
            if (kind == RhineBlockKind.Real) column.items[u] else null,
        )
    }
}

@Immutable
data class RhineSlot(
    val u: Int,
    val v: Int,
    val kind: RhineBlockKind,
    val column: RhineColumn? = null,
    val item: RhineItem? = null,
)

/** Every zero and real block carries its parent label on the side face. */
val RhineSlot.showsSideLabel: Boolean
    get() = column != null && (kind == RhineBlockKind.Zero || kind == RhineBlockKind.Real)

/** Local 2D frame of a face: `screen = origin + x * xAxis + y * yAxis`, size in local px. */
@Immutable
data class FaceFrame(
    val origin: Offset,
    val xAxis: Offset,
    val yAxis: Offset,
    val width: Float,
    val height: Float,
) {
    fun map(x: Float, y: Float) = origin + xAxis * x + yAxis * y
}

/** Projected block. Polygons are counter-clockwise or clockwise quads in screen px. */
@Immutable
data class RhineBlock(
    val slot: RhineSlot,
    val height: Float,
    val top: List<Offset>,
    /** Long face at the column-start side, facing the lower right. Carries the column label. */
    val front: List<Offset>,
    /** End cap facing the lower left. */
    val cap: List<Offset>,
    val shadow: List<Offset>,
    val topFrame: FaceFrame,
    val frontFrame: FaceFrame,
    /** Height (px, from the top edge) of [front] not hidden by the block in front of it. */
    val frontVisible: Float,
) {
    private val minX = (top + front + cap).minOf { it.x }
    private val maxX = (top + front + cap).maxOf { it.x }
    private val minY = (top + front + cap).minOf { it.y }
    private val maxY = (top + front + cap).maxOf { it.y }

    fun contains(point: Offset): Boolean =
        point.x in minX..maxX &&
            point.y in minY..maxY &&
            (pointInQuad(point, top) || pointInQuad(point, front) || pointInQuad(point, cap))
}

/** Visible blocks in painter's order: back to front. */
@Immutable
class RhineLayout(val projection: RhineProjection, val blocks: List<RhineBlock>) {
    /** Front-most block under [point]; occluded faces never win. */
    fun hitTest(point: Offset): RhineBlock? = blocks.lastOrNull { it.contains(point) }
}

fun layoutScene(input: RhineSceneInput, projection: RhineProjection): RhineLayout {
    val g = projection.geometry
    val d = projection.density
    val window = projection.visibleSlots(margin = 2)
    val blocks = ArrayList<RhineBlock>(window.count)
    // A block can only hide blocks with larger u or larger v (the view ray runs +U, +V, -Z), so
    // descending u, then descending v, is a valid back-to-front order whatever the heights are.
    for (u in window.maxU downTo window.minU) {
        for (v in window.maxV downTo window.minV) {
            val weight = columnLiftWeight(v, input.cameraV)
            fun height(at: Int) =
                (g.baseHeight + (g.heightOf(at, v, v, input.focusU) - g.baseHeight) * weight) * d
            val h = height(u)
            val hFront = height(u - 1)
            val visible = h - hFront + g.gapU * d / projection.slopeU
            val block = projection.block(input.slot(u, v), h, visible.coerceIn(0f, h))
            // The inverse window is conservative; discard off-screen faces/shadows before draw.
            val extent = block.top + block.front + block.cap + block.shadow
            if (
                extent.maxOf { it.x } >= 0f &&
                    extent.minOf { it.x } <= projection.size.width &&
                    extent.maxOf { it.y } >= 0f &&
                    extent.minOf { it.y } <= projection.size.height
            ) {
                blocks += block
            }
        }
    }
    return RhineLayout(projection, blocks)
}

fun RhineProjection.block(slot: RhineSlot, h: Float, frontVisible: Float = h): RhineBlock {
    val u0 = slot.u * pitchU
    val u1 = u0 + depth
    val v0 = slot.v * pitchV
    val v1 = v0 + length
    val eV = project(0f, 1f, 0f) - project(0f, 0f, 0f)
    val eU = project(1f, 0f, 0f) - project(0f, 0f, 0f)
    val eZ = project(0f, 0f, 1f) - project(0f, 0f, 0f)
    // Contact shadow: the footprint pushed towards the viewer's lower left, longer for taller
    // blocks. Drawn under every block, so it only shows in the gaps.
    val s = h * 0.45f
    return RhineBlock(
        slot = slot,
        height = h,
        top =
            listOf(project(u0, v0, h), project(u0, v1, h), project(u1, v1, h), project(u1, v0, h)),
        front =
            listOf(
                project(u0, v0, 0f),
                project(u0, v1, 0f),
                project(u0, v1, h),
                project(u0, v0, h),
            ),
        cap =
            listOf(
                project(u0, v0, 0f),
                project(u1, v0, 0f),
                project(u1, v0, h),
                project(u0, v0, h),
            ),
        shadow =
            listOf(
                project(u0 - s, v0 - s * 0.5f, 0f),
                project(u0 - s, v1 - s * 0.5f, 0f),
                project(u1, v1, 0f),
                project(u1, v0, 0f),
            ),
        // Top-face text reads along V; its "down" points towards the column start (-U).
        topFrame = FaceFrame(project(u1, v0, h), eV, -eU, length, depth),
        // Front-face text reads along V; its "down" points to the floor.
        frontFrame = FaceFrame(project(u0, v0, h), eV, -eZ, length, frontVisible),
        frontVisible = frontVisible,
    )
}

/** Convex quad test, independent of winding. */
fun pointInQuad(p: Offset, quad: List<Offset>): Boolean {
    var sign = 0
    for (i in quad.indices) {
        val a = quad[i]
        val b = quad[(i + 1) % quad.size]
        val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        if (cross != 0f) {
            val s = if (cross > 0f) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
    }
    return true
}

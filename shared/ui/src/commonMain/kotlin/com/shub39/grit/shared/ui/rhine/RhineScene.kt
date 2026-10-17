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
    val loaded: Boolean = true,
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
    val maxItemCount: Int = columns.maxOfOrNull { it.itemCount } ?: 0

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
    /** Height of the block's underside above the floor, px; non-zero only while lifted. */
    val base: Float = 0f,
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

/**
 * A block lifted off the floor for a transition. [u], [v] name the slot it leaves; the block is drawn
 * at [positionU], [positionV] (which may glide to another slot), [zDp] above the floor, showing
 * [slot] when set (e.g. a draft's own labels) instead of the slot's content. [hideItemId] hides a
 * stored block that the raised one is about to become.
 */
@Immutable
data class RhineRaise(
    val u: Int,
    val v: Int,
    val zDp: Float,
    val slot: RhineSlot? = null,
    val positionU: Float = u.toFloat(),
    val positionV: Float = v.toFloat(),
    val hideItemId: Long? = null,
    /** Keep drawing the slot's own block underneath (e.g. its successor after a delete). */
    val keepBase: Boolean = false,
)

/**
 * Visible blocks in painter's order: back to front. [raised] is the lifted block; once it clears
 * every other block it is drawn last (nothing can cover it) and [raisedClear] is true.
 */
@Immutable
class RhineLayout(
    val projection: RhineProjection,
    val blocks: List<RhineBlock>,
    val raised: RhineBlock? = null,
    val raisedClear: Boolean = false,
) {
    /** Front-most block under [point]; occluded faces never win. */
    fun hitTest(point: Offset): RhineBlock? = blocks.lastOrNull { it.contains(point) }
}

fun layoutScene(
    input: RhineSceneInput,
    projection: RhineProjection,
    move: RhineMove? = null,
    target: Float = 0f,
    lift: Float = 0f,
    raise: RhineRaise? = null,
): RhineLayout {
    val g = projection.geometry
    val d = projection.density
    val window =
        if (move == null) projection.visibleSlots(margin = 2)
        else
            RhineProjection(
                    g.copy(peakLift = g.peakLift + 9f),
                    d,
                    projection.size,
                    RhineCamera(input.focusU, input.cameraV),
                )
                .visibleSlots(margin = 3)
    val blocks = ArrayList<RhineBlock>(window.count)
    var raised: RhineBlock? = null
    // Higher than any block: the raised block can no longer be hidden by anything.
    val raisedClear = raise != null && raise.zDp > g.maxHeight + 9f
    // A block can only hide blocks with larger u or larger v (the view ray runs +U, +V, -Z), so
    // descending u, then descending v, is a valid back-to-front order whatever the heights are.
    for (u in window.maxU downTo window.minU) {
        val vs = (window.maxV downTo window.minV).toMutableList()
        if (move?.parent == true && move.slot.v !in vs) vs.add(move.slot.v)
        for (v in vs) {
            val slot =
                when {
                    move != null && !move.parent && u == move.slot.u && v == move.slot.v ->
                        move.slot
                    move?.parent == true &&
                        v == move.slot.v &&
                        input.columns.getOrNull(v)?.items.isNullOrEmpty() -> {
                        val c = move.slot.column!!
                        RhineSlot(
                            u,
                            v,
                            when {
                                u < 0 || u > input.maxU -> RhineBlockKind.Decoration
                                u == 0 -> RhineBlockKind.Zero
                                u <= c.itemCount -> RhineBlockKind.Real
                                else -> RhineBlockKind.Placeholder
                            },
                            if (u in 0..input.maxU) c else null,
                            c.items[u],
                        )
                    }
                    else -> input.slot(u, v)
                }
            val previewU =
                if (
                    move != null &&
                        !move.parent &&
                        slot.column?.id == move.columnId &&
                        slot.kind == RhineBlockKind.Real
                )
                    previewPosition(u - 1, move.source, target) + 1
                else u.toFloat()
            val previewV =
                if (move != null && move.parent && slot.column != null)
                    previewPosition(v, move.source, target)
                else v.toFloat()
            val picked =
                move != null &&
                    if (move.parent) slot.column?.id == move.id
                    else slot.item?.id == move.id && slot.column?.id == move.columnId
            // Heights follow the temporary positions too, so committing the permutation cannot
            // transfer the peak to the object that used to occupy the destination.
            val weight = columnLiftWeight(0, input.cameraV - previewV)
            fun height(at: Float) =
                (g.baseHeight + (g.heightOf(0, 0, 0, input.focusU - at) - g.baseHeight) * weight) *
                    d
            val h = height(previewU) + if (picked) lift * d else 0f
            val hFront = height(previewU - 1f)
            val visible = h - hFront + g.gapU * d / projection.slopeU
            if (
                raise != null &&
                    raise.hideItemId != null &&
                    (slot.item?.id == raise.hideItemId ||
                        (slot.kind == RhineBlockKind.Zero && slot.column?.id == raise.hideItemId))
            )
                continue
            val isRaised = raise != null && u == raise.u && v == raise.v
            if (isRaised && raise!!.keepBase) {
                raised =
                    projection.block(
                        raise.slot ?: slot,
                        h,
                        h,
                        raise.positionU,
                        raise.positionV,
                        z0 = raise.zDp * d,
                    )
                if (!raisedClear) blocks += raised!!
            }
            val block =
                if (isRaised && !raise!!.keepBase)
                    projection.block(
                        raise!!.slot ?: slot,
                        h,
                        h,
                        raise.positionU,
                        raise.positionV,
                        z0 = raise.zDp * d,
                    )
                else projection.block(slot, h, visible.coerceIn(0f, h), previewU, previewV)
            if (isRaised && !raise!!.keepBase) {
                raised = block
                if (raisedClear) continue
            }
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
    // Keep the picked content in the bounded layout even after its original slot leaves the
    // viewport.
    if (
        move != null &&
            !move.parent &&
            blocks.none { it.slot.item?.id == move.id && it.slot.column?.id == move.columnId }
    ) {
        val u = target + 1
        val h = g.heightOf(0, 0, 0, input.focusU - u) * d + lift * d
        blocks += projection.block(move.slot, h, h, u, move.slot.v.toFloat())
    }
    if (move != null)
        blocks.sortWith(
            compareByDescending<RhineBlock> { b ->
                    if (
                        !move.parent &&
                            b.slot.column?.id == move.columnId &&
                            b.slot.kind == RhineBlockKind.Real
                    )
                        previewPosition(b.slot.u - 1, move.source, target) + 1
                    else b.slot.u.toFloat()
                }
                .thenByDescending { b ->
                    if (move.parent && b.slot.column != null)
                        previewPosition(b.slot.v, move.source, target)
                    else b.slot.v.toFloat()
                }
        )
    if (raise != null && raised == null) {
        // The source slot fell outside the window (e.g. while gliding to a far destination).
        val h = (g.baseHeight + g.centerLift + g.peakLift) * d
        raised =
            projection.block(
                raise.slot ?: input.slot(raise.u, raise.v),
                h,
                h,
                raise.positionU,
                raise.positionV,
                z0 = raise.zDp * d,
            )
        if (!raisedClear) blocks += raised
    }
    return RhineLayout(projection, blocks, raised, raisedClear)
}

fun RhineProjection.block(
    slot: RhineSlot,
    h: Float,
    frontVisible: Float = h,
    positionU: Float = slot.u.toFloat(),
    positionV: Float = slot.v.toFloat(),
    z0: Float = 0f,
): RhineBlock {
    val u0 = positionU * pitchU
    val u1 = u0 + depth
    val v0 = positionV * pitchV
    val v1 = v0 + length
    val eV = project(0f, 1f, 0f) - project(0f, 0f, 0f)
    val eU = project(1f, 0f, 0f) - project(0f, 0f, 0f)
    val eZ = project(0f, 0f, 1f) - project(0f, 0f, 0f)
    // Contact shadow: the footprint pushed towards the viewer's lower left, longer for taller
    // blocks. Drawn under every block, so it only shows in the gaps.
    // Lifted blocks cast a wider, fainter footprint (alpha is handled by the renderer).
    val s = h * 0.45f + z0 * 0.45f
    val top0 = z0 + h
    return RhineBlock(
        slot = slot,
        height = h,
        top =
            listOf(
                project(u0, v0, top0),
                project(u0, v1, top0),
                project(u1, v1, top0),
                project(u1, v0, top0),
            ),
        front =
            listOf(
                project(u0, v0, z0),
                project(u0, v1, z0),
                project(u0, v1, top0),
                project(u0, v0, top0),
            ),
        cap =
            listOf(
                project(u0, v0, z0),
                project(u1, v0, z0),
                project(u1, v0, top0),
                project(u0, v0, top0),
            ),
        shadow =
            listOf(
                project(u0 - s, v0 - s * 0.5f, 0f),
                project(u0 - s, v1 - s * 0.5f, 0f),
                project(u1, v1, 0f),
                project(u1, v0, 0f),
            ),
        // Top-face text reads along V; its "down" points towards the column start (-U).
        topFrame = FaceFrame(project(u1, v0, top0), eV, -eU, length, depth),
        // Front-face text reads along V; its "down" points to the floor.
        frontFrame = FaceFrame(project(u0, v0, top0), eV, -eZ, length, frontVisible),
        frontVisible = frontVisible,
        base = z0,
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

/** Optional developer observer; normal business screens do not install one. */
fun interface RhineDrawObserver {
    fun drawn(layout: RhineLayout)
}

val LocalRhineDrawObserver =
    androidx.compose.runtime.staticCompositionLocalOf<RhineDrawObserver?> { null }

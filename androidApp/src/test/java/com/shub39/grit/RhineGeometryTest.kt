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
package com.shub39.grit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.shub39.grit.shared.ui.rhine.*
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class RhineGeometryTest {
    private val geometry = RhineGeometry()
    private val screen = Size(2400f, 1080f)
    private val density = 2.625f

    private fun projection(camera: RhineCamera = RhineCamera(3f, 1f)) =
        RhineProjection(geometry, density, screen, camera)

    @Test
    fun columnRunsFromLowerRightToUpperLeft() {
        val p = projection()
        val start = p.project(0f, 0f, 0f)
        val further = p.project(10 * p.pitchU, 0f, 0f)
        assertTrue(further.x < start.x)
        assertTrue(further.y < start.y)
    }

    @Test
    fun unprojectInvertsProject() {
        val p = projection()
        for (z in floatArrayOf(0f, 40f)) {
            val back = p.unproject(p.project(123f, 456f, z), z)
            assertEquals(123f, back.x, 0.01f)
            assertEquals(456f, back.y, 0.01f)
        }
    }

    @Test
    fun onlyCenterColumnRisesWithSinglePeak() {
        val focusU = 3f
        val heights = (0..10).map { geometry.heightOf(it, 1, centerV = 1, focusU = focusU) }
        val others = (0..10).map { geometry.heightOf(it, 2, centerV = 1, focusU = focusU) }
        assertTrue(others.all { it == geometry.baseHeight })
        assertTrue(heights.min() > geometry.baseHeight)
        assertEquals(3, heights.indexOf(heights.max()))
        // Monotone falloff on both sides of the peak.
        for (u in 0 until 3) assertTrue(heights[u] < heights[u + 1])
        for (u in 3 until 10) assertTrue(heights[u] > heights[u + 1])
    }

    @Test
    fun landscapeBasisMatchesReferenceAngles() {
        val p = projection()
        fun angle(a: Offset, b: Offset) =
            Math.toDegrees(kotlin.math.atan2((a.y - b.y).toDouble(), (b.x - a.x).toDouble()))
        val o = p.project(0f, 0f, 0f)
        // Long side rises ~29° to the right; a column climbs ~17° towards the upper left.
        assertEquals(29.0, angle(o, p.project(0f, p.length, 0f)), 1.0)
        assertEquals(17.0, 180.0 - angle(o, p.project(p.pitchU, 0f, 0f)), 1.0)
        // Vertical faces stay vertical.
        assertEquals(o.x, p.project(0f, 0f, 50f).x, 0.001f)
        // Slats: long side to pitch about 8:1 and one block ~35–40% of the screen width.
        // Slats: shortened to 2/3 of the reference-matched length on user request (2026-10-09).
        val ratio = geometry.blockLength / (geometry.blockDepth + geometry.gapU)
        assertTrue("ratio $ratio", ratio in 5.5f..6.5f)
        val span = (p.project(0f, p.length, 0f).x - o.x) / screen.width
        assertTrue("span $span", span in 0.18f..0.25f)
    }

    @Test
    fun centerWallSitsLeftOfCentreAtReferencePitch() {
        val input = rhineSample(RhineSample.Standard)
        val p = projection()
        val layout = layoutScene(input, p)
        val peak = layout.blocks.first { it.slot.u == 3 && it.slot.v == 1 }
        // The wall reads at the near (lower-left) end of the peak block.
        val c = peak.top[0]
        assertTrue("x ${c.x / screen.width}", c.x / screen.width in 0.20f..0.40f)
        assertTrue("y ${c.y / screen.height}", c.y / screen.height in 0.30f..0.60f)
        // Blocks of the center column whose top centre lands on screen.
        val onScreen =
            layout.blocks.count { b ->
                b.slot.v == 1 &&
                    (b.top.reduce { a, q -> a + q } / 4f).let {
                        it.x in 0f..screen.width && it.y in 0f..screen.height
                    }
            }
        // Calibrated on the overlay: neighbouring block ends ~4% of the width apart, as in the
        // reference (60 px of 1382), which puts roughly two dozen blocks of a column on screen.
        val pitch = (p.project(0f, 0f, 0f).x - p.project(p.pitchU, 0f, 0f).x) / screen.width
        assertTrue("pitch $pitch", pitch in 0.033f..0.05f)
        assertTrue("visible $onScreen", onScreen in 18..32)
    }

    @Test
    fun everyLabelledBlockKeepsItsSideLabel() {
        val input = rhineSample(RhineSample.Standard)
        for (u in 0..8) assertTrue(input.slot(u, 1).showsSideLabel)
        assertTrue(input.slot(6, 0).showsSideLabel)
        assertFalse(input.slot(-1, 1).showsSideLabel)
        assertFalse(input.slot(0, input.columnCount).showsSideLabel)
    }

    @Test
    fun slotKindsFollowLogicalBounds() {
        val input = rhineSample(RhineSample.ZeroAndPlaceholders)
        // C = 3, M = 3, so v in 0..3 and u in 0..4.
        assertEquals(4, input.maxU)
        assertEquals(RhineBlockKind.Zero, input.slot(0, 0).kind)
        assertEquals(RhineBlockKind.Real, input.slot(3, 0).kind)
        assertEquals(RhineBlockKind.Placeholder, input.slot(4, 0).kind)
        assertEquals(RhineBlockKind.Placeholder, input.slot(1, 1).kind)
        assertEquals(RhineBlockKind.Placeholder, input.slot(2, 2).kind)
        assertEquals(RhineBlockKind.AddParent, input.slot(2, 3).kind)
        assertEquals(RhineBlockKind.Decoration, input.slot(5, 0).kind)
        assertEquals(RhineBlockKind.Decoration, input.slot(-1, 0).kind)
        assertEquals(RhineBlockKind.Decoration, input.slot(0, 4).kind)
        // A short column never repeats its own items in placeholder slots.
        assertNull(input.slot(4, 2).item)
    }

    @Test
    fun emptyDataKeepsUAtStart() {
        val input = RhineSceneInput(RhineLayer.Categories, emptyList(), 0, 0f)
        assertEquals(0, input.maxU)
        assertEquals(RhineBlockKind.AddParent, input.slot(0, 0).kind)
        assertEquals(RhineBlockKind.Decoration, input.slot(1, 0).kind)
    }

    @Test
    fun drawnBlocksScaleWithViewportNotData() {
        val small = rhineSample(RhineSample.ZeroAndPlaceholders)
        val big =
            small.copy(
                columns =
                    small.columns.map { c ->
                        c.copy(
                            itemCount = 10_000,
                            items = (1..10_000).associateWith { RhineItem(it.toLong(), it, "t") },
                        )
                    }
            )
        val p = projection(RhineCamera(0f, 1f))
        assertEquals(layoutScene(small, p).blocks.size, layoutScene(big, p).blocks.size)
        assertTrue(layoutScene(big, p).blocks.size < 400)
    }

    @Test
    fun windowCoversWholeScreen() {
        val input = rhineSample(RhineSample.Standard)
        val p = projection()
        val layout = layoutScene(input, p)
        // Every sampled screen point is covered by some block face or by a gap between blocks;
        // test that nearly all are covered (gaps are narrow).
        var covered = 0
        var total = 0
        for (x in 0..2400 step 40) for (y in 0..1080 step 40) {
            total++
            if (layout.hitTest(Offset(x.toFloat(), y.toFloat())) != null) covered++
        }
        assertTrue("covered $covered of $total", covered > total * 0.85)
    }

    @Test
    fun hitTestPicksFrontMostBlock() {
        val input = rhineSample(RhineSample.Standard)
        val p = projection()
        val layout = layoutScene(input, p)
        // The focused center block's top centre must resolve to the focused block itself, even
        // though raised neighbours are drawn around it.
        val focused = layout.blocks.first { it.slot.u == 3 && it.slot.v == 1 }
        val centre = focused.top.reduce { a, b -> a + b } / 4f
        val front = layout.blocks.first { it.slot.u == 2 && it.slot.v == 1 }
        assertEquals(focused.slot, layout.hitTest(centre)?.slot)

        // The visible strip of the raised front face resolves to the raised block; the hidden
        // lower part belongs to the raised neighbour in front (u - 1).
        val ff = focused.frontFrame
        assertTrue(focused.frontVisible > 0f && focused.frontVisible < focused.height)
        assertEquals(
            focused.slot,
            layout.hitTest(ff.map(ff.width / 2f, focused.frontVisible / 2f))?.slot,
        )
        // The hidden part belongs to a nearer block of the same column (u - 1 or further in front).
        val hidden = ff.map(ff.width / 2f, (focused.frontVisible + focused.height) / 2f)
        val occluder = layout.hitTest(hidden)?.slot
        assertEquals(front.slot.v, occluder?.v)
        assertTrue("occluder $occluder", occluder!!.u < focused.slot.u)
        val behind = layout.blocks.first { it.slot.u == 4 && it.slot.v == 1 }
        // Painter's order: blocks drawn later are in front.
        assertTrue(layout.blocks.indexOf(behind) < layout.blocks.indexOf(focused))
    }

    @Test
    fun painterOrderDrawsOccludersLast() {
        val layout = layoutScene(rhineSample(RhineSample.Standard), projection())
        val order = layout.blocks.map { it.slot.u to it.slot.v }
        // Occluders have smaller u or smaller v; they must come after the blocks they hide.
        for (i in 1 until order.size) {
            val (u0, v0) = order[i - 1]
            val (u1, v1) = order[i]
            assertTrue(u1 < u0 || (u1 == u0 && v1 < v0))
        }
    }

    @Test
    fun lightTextColorIsMadeReadable() {
        val style = RhineStyle()
        for (hex in listOf("#FFFFFF", "#F2E6A0", "#BFEDEA")) {
            val c = style.readable(hex, style.top)
            assertTrue("$hex -> ${contrast(c, style.top)}", contrast(c, style.top) >= 4.5f)
        }
        assertEquals(style.text, style.readable(null, style.top))
        // Side faces are shaded from the centre to the edge; labels stay readable across the range.
        for (face in listOf(style.front, style.frontAt(0.5f), style.frontEdge)) {
            assertTrue(contrast(style.readableSide(null, face), face) >= 4.5f)
            for (hex in listOf("#2F5D8A", "#8A3B2F", "#F4E3B2")) {
                val c = style.readableSide(hex, face)
                assertTrue("$hex -> ${contrast(c, face)}", contrast(c, face) >= 4.5f)
            }
        }
        val dark = style.readable("#2F5D8A", style.top)
        assertTrue(abs(dark.red - Color(0xFF2F5D8A).red) < 0.001f)
    }
}

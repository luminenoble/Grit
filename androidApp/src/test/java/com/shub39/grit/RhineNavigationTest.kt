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
import com.shub39.grit.core.tasks.*
import com.shub39.grit.shared.ui.rhine.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RhineNavigationTest {
    private val geometry = RhineGeometry()

    private fun projection(
        camera: RhineCamera = RhineCamera(99f, 0f),
        size: Size = Size(2400f, 1080f),
    ) = RhineProjection(geometry, 2.625f, size, camera)

    private fun scene(u: Float = 99f) =
        RhineSceneInput(
            RhineLayer.Categories,
            listOf(
                RhineColumn(
                    11,
                    1,
                    "A",
                    itemCount = 100,
                    items = mapOf(99 to RhineItem(199, 99, "A99")),
                ),
                RhineColumn(22, 2, "B", itemCount = 1, items = mapOf(1 to RhineItem(201, 1, "B1"))),
            ),
            0,
            u,
        )

    @Test
    fun firstMeasurementDoesNotLosePendingFocus() {
        val browser = RhineBrowser(RhineCamera(99f, 1f))
        browser.stop()
        assertEquals(RhineCamera(99f, 1f), browser.camera)
        browser.updateBounds(RhineBounds.from(listOf(100, 1, 1)))
        browser.stop()
        assertEquals(RhineCamera(99f, 1f), browser.camera)
        browser.updateBounds(RhineBounds.from(listOf(1)))
        assertEquals(RhineCamera(2f, 1f), browser.camera)
    }

    @Test
    fun longestColumnHasExactlyOneTailSlot() {
        val bounds = RhineBounds.from(listOf(100, 1, 1))
        assertEquals(RhineCamera(101f, 3f), bounds.clamp(RhineCamera(500f, 40f)))
        assertEquals(RhineCamera(0f, 0f), bounds.clamp(RhineCamera(-1f, -1f)))
        val scene = scene()
        assertEquals(RhineBlockKind.Placeholder, scene.slot(101, 0).kind)
        assertEquals(RhineBlockKind.Decoration, scene.slot(102, 0).kind)
    }

    @Test
    fun shortColumnNeverRepeatsFirstTaskAt99() {
        val input = scene()
        assertEquals(RhineBlockKind.Real, input.slot(99, 0).kind)
        assertEquals(199L, input.slot(99, 0).item!!.id)
        assertEquals(RhineBlockKind.Placeholder, input.slot(99, 1).kind)
        assertNull(input.slot(99, 1).item)
        assertEquals(1, input.columns[1].items.size)
        assertEquals(RhineBlockKind.AddParent, input.slot(99, 2).kind)
        assertEquals(RhineBlockKind.Decoration, input.slot(99, 3).kind)
    }

    @Test
    fun emptyBoundsStayFinite() {
        assertEquals(
            RhineCamera(0f, 0f),
            RhineBounds.from(emptyList()).clamp(RhineCamera(99f, 99f)),
        )
        assertEquals(RhineCamera(1f, 1f), RhineBounds.from(listOf(0)).clamp(RhineCamera(99f, 99f)))
    }

    @Test
    fun flingAndRepeatedOutwardDragsStopAtTail() {
        val bounds = RhineBounds.from(listOf(100, 1))
        var motion = RhineMotion(RhineCamera(100f, 0f), RhineCamera(65f, 0f))
        repeat(120) { motion = motion.advance(1f / 60, bounds) }
        assertEquals(101f, motion.camera.u, 0f)
        assertEquals(0f, motion.velocity.u, 0f)
        repeat(100) {
            assertEquals(101f, bounds.clamp(motion.camera.copy(u = motion.camera.u + 5)).u, 0f)
        }
    }

    @Test
    fun gestureUsesInverseProjectionAndLocksAxis() {
        val p = projection()
        val gesture = RhineGestureController(p, 8f)
        assertEquals(RhineCamera(0f, 0f), gesture.delta(Offset(1f, 0f)))
        val delta = gesture.delta(geometry.basisU * -p.pitchU)
        assertEquals(RhineAxis.U, gesture.axis)
        assertEquals(1f, delta.u, 0.03f)
        assertEquals(0f, gesture.delta(geometry.basisV * 300f).v, 0f)
        val across = RhineGestureController(p, 8f)
        assertEquals(1f, across.delta(geometry.basisV * -p.pitchV).v, 0.001f)
        assertEquals(RhineAxis.V, across.axis)
    }

    @Test
    fun resizePreservesStableFocusAndChangesWindowOnly() {
        val input = scene()
        val focus = input.focus()
        val old = projection().visibleSlots()
        val new = projection(size = Size(1600f, 900f)).visibleSlots()
        assertNotEquals(old, new)
        assertEquals(RhineCamera(99f, 0f), focus.resolve(input.columns))
        assertEquals(199L, focus.itemId!!)
        val moved = input.columns.reversed()
        assertEquals(RhineCamera(99f, 1f), focus.resolve(moved))
    }

    @Test
    fun stableIdFollowsNewPosition() {
        val input = scene()
        val updated =
            input.columns.map { c ->
                if (c.id != 11L) c else c.copy(items = mapOf(3 to RhineItem(199, 3, "moved")))
            }
        assertEquals(RhineCamera(3f, 0f), input.focus().resolve(updated))
    }

    @Test
    fun windowSizeAndQueryLookaheadDoNotGrowWithTravel() {
        val start = projection(RhineCamera(0f, 0f)).visibleSlots()
        val far = projection(RhineCamera(10000f, 100f)).visibleSlots()
        assertEquals(start.count, far.count)
        assertTrue(start.count < 1500)
        val q = far.queryWindow()
        assertTrue(q.minU <= far.minU && q.maxU >= far.maxU)
        assertTrue(q.count < 3000)
    }

    @Test
    fun liftCrossfadesContinuouslyAndRestHasOnlyOneRaisedColumn() {
        assertEquals(1f, columnLiftWeight(1, 1f), 0f)
        assertEquals(0f, columnLiftWeight(0, 1f), 0f)
        assertEquals(0.5f, columnLiftWeight(0, 0.5f), 0f)
        assertEquals(0.5f, columnLiftWeight(1, 0.5f), 0f)
        assertEquals(0, centerColumn(0.55f, 0, 3))
        assertEquals(1, centerColumn(0.61f, 0, 3))
        assertEquals(1, centerColumn(0.45f, 1, 3))
        val a =
            layoutScene(scene(99f), projection()).blocks.first { it.slot.u == 99 && it.slot.v == 0 }
        assertEquals(geometry.maxHeight * 2.625f, a.height, 0.001f)
    }

    @Test
    fun foremostVisibleFaceWinsIncludingDecorations() {
        val p = projection(RhineCamera(4f, 0.45f))
        val input = scene(4f).copy(cameraV = 0.45f)
        val layout = layoutScene(input, p)
        var overlaps = 0
        var decorativeOcclusions = 0
        for (x in 0..2400 step 24) for (y in 0..1080 step 18) {
            val point = Offset(x.toFloat(), y.toFloat())
            val hits = layout.blocks.filter { it.contains(point) }
            if (hits.size > 1) {
                overlaps++
                assertSame(hits.last(), layout.hitTest(point))
                if (
                    hits.last().slot.kind == RhineBlockKind.Decoration &&
                        hits.any { it.slot.kind != RhineBlockKind.Decoration }
                )
                    decorativeOcclusions++
            }
        }
        assertTrue(overlaps > 100)
        assertTrue(decorativeOcclusions > 0)
    }

    @Test
    fun sideFaceAndTopResolveToSameBlock() {
        val p = projection()
        val block = p.block(scene().slot(99, 0), geometry.maxHeight * p.density)
        val layout = RhineLayout(p, listOf(block))
        for (face in listOf(block.top, block.front, block.cap)) {
            assertEquals(199L, layout.hitTest(face.reduce { a, b -> a + b } / 4f)!!.slot.item!!.id)
        }
    }

    @Test
    fun windowQueriesAreReadOnlyAndUseActualIndices() = runBlocking {
        val calls = mutableListOf<Triple<Long, Int, Int>>()
        val categories =
            listOf(Category(id = 11, name = "A"), Category(id = 22, name = "B", orderIndex = 1))
        val counts = listOf(CategoryTaskCount(11, 100, 25), CategoryTaskCount(22, 1, 1))
        val source =
            object : RhineDataSource {
                override fun categories() = flowOf(categories)

                override fun counts() = flowOf(counts)

                override fun item(id: Long) = flowOf<TaskSummary?>(null)

                override fun summaries(
                    categoryId: Long,
                    offset: Int,
                    limit: Int,
                ): Flow<List<TaskSummary>> {
                    calls += Triple(categoryId, offset, limit)
                    return flowOf(
                        (offset until minOf(100, offset + limit)).map {
                            TaskSummary(1000L + it, categoryId, "Task ${it + 1}", it, null, false)
                        }
                    )
                }
            }
        val result =
            source.window(RhineCatalog(categories, counts), SlotWindow(90, 110, 0, 2)).first()
        assertEquals(listOf(Triple(11L, 89, 11)), calls)
        assertEquals("Task 99", result[0].items[99]!!.title)
        assertTrue(result[1].items.isEmpty())
        assertEquals(1, result[1].itemCount)
        assertEquals(25, result[0].completedCount)
    }
}

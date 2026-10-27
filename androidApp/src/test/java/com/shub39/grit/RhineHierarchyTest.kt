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

import androidx.compose.ui.geometry.Size
import com.shub39.grit.core.tasks.*
import com.shub39.grit.shared.ui.rhine.*
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RhineHierarchyTest {
    private val categories = listOf(Category(id = 10, name = "A"), Category(id = 20, name = "B"))
    private val catalog =
        RhineCatalog(categories, listOf(CategoryTaskCount(10, 12), CategoryTaskCount(20, 2)))
    private val snapshot = RhineSnapshot(RhineCamera(8f, 0f), RhineFocus(10, 100, 8, 0))

    @Test
    fun refreshedWindowCannotReplaceStableFocusAtTheSameSlot() {
        val browser = RhineBrowser(RhineCamera(8f, 0f))
        browser.updateBounds(RhineBounds(13, 1))
        browser.restore(snapshot)
        val input =
            RhineSceneInput(
                RhineLayer.Sections,
                listOf(
                    RhineColumn(
                        10,
                        1,
                        "T",
                        itemCount = 12,
                        items = mapOf(8 to RhineItem(999, 8, "Other")),
                    )
                ),
                0,
                8f,
            )
        browser.rememberFocus(input)
        assertEquals(100L, browser.focus.itemId)
        browser.follow(RhineCamera(3f, 0f), 10)
        assertEquals(RhineCamera(3f, 0f), browser.camera)
        assertEquals(100L, browser.focus.itemId)
        assertTrue(browser.focusMatchesCamera)
        browser.move(RhineCamera(8f, 0f))
        browser.select(input.slot(8, 0))
        assertEquals(999L, browser.focus.itemId)
    }

    @Test
    fun userNavigationCanReplaceFocusAndBoundsChangesKeepIdentity() {
        val browser = RhineBrowser()
        browser.updateBounds(RhineBounds(13, 2))
        browser.restore(snapshot)
        browser.move(RhineCamera(9f, 1f))
        val input =
            RhineSceneInput(
                RhineLayer.Sections,
                listOf(
                    RhineColumn(10, 1, "A", itemCount = 12, items = emptyMap()),
                    RhineColumn(
                        20,
                        2,
                        "B",
                        itemCount = 10,
                        items = mapOf(9 to RhineItem(209, 9, "new")),
                    ),
                ),
                1,
                9f,
            )
        browser.rememberFocus(input)
        assertEquals(209L, browser.focus.itemId)
        browser.updateBounds(RhineBounds(3, 2))
        assertEquals(209L, browser.focus.itemId)
        assertTrue(browser.focusMatchesCamera)
    }

    @Test
    fun returnRestoresIdentityAfterReorderAndMove() {
        val moved = TaskSummary(100, 20, "changed", 1, null, true)
        assertEquals(RhineCamera(2f, 1f), snapshot.resolve(catalog, moved))
        assertEquals(
            RhineCamera(8f, 0f),
            snapshot.resolve(catalog, TaskSummary(100, 10, "A", 7, null, false)),
        )
    }

    @Test
    fun deletedItemAndParentFallBackToRealNeighbours() {
        val shorter =
            RhineCatalog(categories, listOf(CategoryTaskCount(10, 3), CategoryTaskCount(20, 2)))
        assertEquals(RhineCamera(3f, 0f), snapshot.resolve(shorter, null))
        assertEquals(
            RhineCamera(2f, 0f),
            snapshot.resolve(
                RhineCatalog(categories.drop(1), listOf(CategoryTaskCount(20, 2))),
                null,
            ),
        )
        assertEquals(
            RhineCamera(0f, 0f),
            snapshot.resolve(RhineCatalog(emptyList(), emptyList()), null),
        )
    }

    @Test
    fun emptyTaskReturnsToZero() {
        assertEquals(
            RhineCamera(0f, 0f),
            snapshot.resolve(
                RhineCatalog(categories.take(1), listOf(CategoryTaskCount(10, 0))),
                null,
            ),
        )
    }

    @Test
    fun firstTapOnOtherColumnAndMovingCenterCannotEdit() {
        val column =
            RhineColumn(10, 1, "A", itemCount = 1, items = mapOf(1 to RhineItem(100, 1, "T")))
        val slot = RhineSlot(1, 0, RhineBlockKind.Real, column, column.items[1])
        assertFalse(canOpenRhineDetail(RhineMode.Detail, true, 1, slot))
        assertFalse(canOpenRhineDetail(RhineMode.Detail, false, 0, slot))
        assertFalse(canOpenRhineDetail(RhineMode.Preview, true, 0, slot))
        assertTrue(canOpenRhineDetail(RhineMode.Detail, true, 0, slot))
    }

    @Test
    fun placeholdersAndLoadingItemsCannotOperate() {
        assertFalse(
            canOpenRhineDetail(
                RhineMode.Detail,
                true,
                0,
                RhineSlot(1, 0, RhineBlockKind.Placeholder),
            )
        )
        assertFalse(
            canOpenRhineDetail(
                RhineMode.Detail,
                true,
                0,
                RhineSlot(1, 0, RhineBlockKind.Decoration),
            )
        )
        assertFalse(
            canOpenRhineDetail(RhineMode.Detail, true, 0, RhineSlot(1, 0, RhineBlockKind.Real))
        )
    }

    @Test
    fun secondLayerHasFiniteSharedBoundsAndStableSectionHits() {
        val input =
            RhineSceneInput(
                RhineLayer.Sections,
                listOf(
                    RhineColumn(
                        100,
                        1,
                        "T1",
                        itemCount = 12,
                        items = mapOf(8 to RhineItem(808, 8, "S8")),
                    ),
                    RhineColumn(200, 2, "T2", itemCount = 0, items = emptyMap()),
                ),
                0,
                8f,
            )
        assertEquals(13, input.maxU)
        assertEquals(2, input.maxV)
        assertEquals(RhineBlockKind.Placeholder, input.slot(8, 1).kind)
        assertEquals(RhineBlockKind.AddParent, input.slot(8, 2).kind)
        assertEquals(RhineBlockKind.Decoration, input.slot(14, 0).kind)
        val p = RhineProjection(RhineGeometry(), 2.625f, Size(2400f, 1080f), RhineCamera(8f, 0f))
        val block = p.block(input.slot(8, 0), 280f)
        val layout = RhineLayout(p, listOf(block))
        for (face in listOf(block.top, block.front, block.cap)) assertEquals(
            808L,
            layout.hitTest(face.reduce { a, b -> a + b } / 4f)!!.slot.item!!.id,
        )
    }

    @Test
    fun sessionCacheRestoresPerTaskAndStaysBounded() {
        val session = RhineSession()
        session.rememberPosition(100, snapshot)
        assertEquals(snapshot, session.position(100))
        (200L..240L).forEach { session.rememberPosition(it, snapshot) }
        assertNull(session.position(100))
        assertNull(session.position(200))
        assertEquals(snapshot, session.position(240))
    }

    @Test
    fun sectionWindowOnlyLoadsVisibleSiblingsAndNeverBody() = runBlocking {
        val calls = mutableListOf<String>()
        val repo =
            Proxy.newProxyInstance(
                TaskRepo::class.java.classLoader,
                arrayOf(TaskRepo::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getTaskCountsFlow" -> {
                        assertEquals(10L, args[0])
                        flowOf(
                            listOf(
                                TaskSectionCount(100, 12, 4),
                                TaskSectionCount(200, 0),
                                TaskSectionCount(300, 2),
                            )
                        )
                    }
                    "getTaskSummariesFlow" -> {
                        calls += "task:${args.toList()}"
                        flowOf(listOf(TaskSummary(100, 10, "T", 0, "#426E79", true)))
                    }
                    "getSectionSummariesFlow" -> {
                        calls += "section:${args.toList()}"
                        flowOf(
                            listOf(
                                SectionSummary(808, 100, "S8", 7, LocalDate(2026, 1, 2), "#C0392B")
                            )
                        )
                    }
                    else -> error("Unexpected query: ${method.name}; overview must not load bodies")
                }
            } as TaskRepo
        val source = SectionRhineDataSource(repo, 10)
        val cat = RhineCatalog(source.categories().first(), source.counts().first())
        val columns = source.window(cat, SlotWindow(8, 9, 0, 0)).first()
        assertEquals(listOf("task:[10, 0, 1]", "section:[100, 7, 2]"), calls)
        assertEquals(3, columns.size)
        assertEquals(13, cat.bounds.maxU)
        // The column is the Task in its colour; each Section block has its own.
        assertEquals("#426E79", columns[0].titleColor)
        assertEquals("#C0392B", columns[0].items[8]!!.titleColor)
        assertEquals(LocalDate(2026, 1, 2), columns[0].items[8]!!.completedOn)
        assertEquals(4, columns[0].completedCount)
        assertTrue(columns[1].items.isEmpty())
    }
}

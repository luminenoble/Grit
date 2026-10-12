package com.shub39.grit

import com.shub39.grit.core.tasks.*
import com.shub39.grit.shared.ui.rhine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class RhineMutationTest {
    private fun catalog(a: Int, b: Int) =
        RhineCatalog(
            listOf(Category(id = 10, name = "A"), Category(id = 20, name = "B")),
            listOf(CategoryTaskCount(10, a), CategoryTaskCount(20, b)),
        )

    @Test
    fun creationRequiresStableCenterAndRealParentOrUniqueStart() {
        for (layer in RhineLayer.entries) {
            val input =
                RhineSceneInput(
                    layer,
                    listOf(
                        RhineColumn(10, 1, "A", itemCount = 100, items = emptyMap()),
                        RhineColumn(20, 2, "B", itemCount = 1, items = emptyMap()),
                    ),
                    1,
                    99f,
                )
            val short = input.slot(99, 1)
            assertTrue(canCreateRhine(RhineMode.Detail, true, 1, short))
            assertFalse(canCreateRhine(RhineMode.Preview, true, 1, short))
            assertFalse(canCreateRhine(RhineMode.Detail, false, 1, short))
            assertFalse(canCreateRhine(RhineMode.Detail, true, 0, short))
            assertFalse(canCreateRhine(RhineMode.Detail, true, 1, input.slot(102, 1)))
            assertFalse(canCreateRhine(RhineMode.Detail, true, 3, input.slot(99, 3)))
            assertTrue(canCreateRhine(RhineMode.Detail, true, 2, input.slot(99, 2)))
        }
        val empty = RhineSceneInput(RhineLayer.Categories, emptyList(), 0, 0f)
        assertTrue(canCreateRhine(RhineMode.Detail, true, 0, empty.slot(0, 0)))
        assertFalse(canCreateRhine(RhineMode.Detail, true, 0, empty.slot(1, 0)))
    }

    @Test
    fun deletionUsesSuccessorThenPredecessorThenZeroInBothLayers() {
        val before = RhineSnapshot(RhineCamera(2f, 1f), RhineFocus(20, 999, 2, 1))
        assertEquals(RhineCamera(2f, 1f), deletionCamera(catalog(100, 3), before, false))
        assertEquals(RhineCamera(1f, 1f), deletionCamera(catalog(100, 1), before, false))
        assertEquals(RhineCamera(0f, 1f), deletionCamera(catalog(100, 0), before, false))
        val remaining =
            RhineCatalog(listOf(Category(id = 10, name = "A")), listOf(CategoryTaskCount(10, 1)))
        assertEquals(RhineCamera(0f, 0f), deletionCamera(remaining, before, true))
        assertEquals(
            RhineCamera(0f, 0f),
            deletionCamera(RhineCatalog(emptyList(), emptyList()), before, true),
        )
    }

    @Test
    fun committedIdentityWaitsForCountsAndIgnoresOldWindow() = runBlocking {
        val cs = MutableStateFlow(catalog(100, 1))
        val source =
            object : RhineDataSource {
                override fun catalog() = cs

                override fun categories() = cs.map { it.categories }

                override fun counts() = cs.map { it.counts }

                override fun item(id: Long) = flowOf(TaskSummary(id, 20, "new", 1, null, false))

                override fun summaries(
                    categoryId: Long,
                    offset: Int,
                    limit: Int,
                ): Flow<List<TaskSummary>> = error("No stale-window lookup")
            }
        val result = async { source.createdSnapshot(987, false) }
        yield()
        assertFalse(result.isCompleted)
        cs.value = catalog(100, 2)
        val (_, snapshot) = result.await()
        assertEquals(RhineCamera(2f, 1f), snapshot.camera)
        assertEquals(987L, snapshot.focus.itemId)
        assertEquals(20L, snapshot.focus.categoryId)
        assertEquals(RhineCamera(0f, 1f), source.createdSnapshot(20, true).second.camera)
    }

    @Test
    fun importClearsParentAndEveryCachedTaskEvenWithReusedIds() {
        val session = RhineSession()
        val snapshot = RhineSnapshot(RhineCamera(99f, 1f), RhineFocus(20, 123, 99, 1))
        session.parent = snapshot
        session.categoryId = 20
        session.showingSections = true
        session.rememberPosition(123, snapshot)
        session.invalidate()
        assertNull(session.parent)
        assertNull(session.categoryId)
        assertNull(session.position(123))
        assertFalse(session.showingSections)
    }

    @Test
    fun longestColumnRemovalClampsCameraAndPendingMovement() {
        val browser = RhineBrowser(RhineCamera(99f, 1f))
        browser.updateBounds(RhineBounds(101, 3))
        browser.begin()
        browser.updateBounds(RhineBounds(2, 2))
        browser.move(RhineCamera(100f, 3f))
        assertEquals(RhineCamera(2f, 2f), browser.camera)
        assertFalse(browser.interacting)
    }

    @Test
    fun savedStage03SessionAndGenerationSessionBothRestore() {
        val legacy = listOf("Detail", "20", "true", "true", "99.0", "1.0", "20", "123", "99", "1")
        val restored = RhineSession.saver.restore(legacy)!!
        assertEquals(RhineMode.Detail, restored.mode)
        assertEquals(20L, restored.categoryId)
        assertEquals(123L, restored.parent!!.focus.itemId)
        assertNull(restored.generation)
        val current = RhineSession.saver.restore(listOf("import-token") + legacy)!!
        assertEquals("import-token", current.generation)
        assertEquals(restored.parent, current.parent)
    }
}

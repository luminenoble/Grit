package com.shub39.grit

import com.shub39.grit.core.tasks.*
import com.shub39.grit.shared.ui.rhine.*
import org.junit.Assert.*
import org.junit.Test

class RhineAdjustTest {
    @Test
    fun insertionBothDirectionsAndIdentityPermutation() {
        val ids = (1L..8L).toList()
        assertEquals(listOf(1L, 8L, 2L, 3L, 4L, 5L, 6L, 7L), movedIds(ids, 8, 1))
        for (source in ids.indices) for (target in ids.indices) {
            val result = movedIds(ids, ids[source], target)
            for (i in ids.indices) assertEquals(
                result.indexOf(ids[i]).toFloat(),
                previewPosition(i, source, target.toFloat()),
                0f,
            )
        }
    }

    @Test
    fun onlyStableCenterRealObjectsCanBePicked() {
        val c = RhineColumn(1, 1, "empty", null, 0, emptyMap())
        val zero = RhineSlot(0, 0, RhineBlockKind.Zero, c)
        assertTrue(canAdjustRhine(RhineMode.Adjust, true, 0, zero))
        for (mode in listOf(RhineMode.Preview, RhineMode.Detail)) assertFalse(
            canAdjustRhine(mode, true, 0, zero)
        )
        assertFalse(canAdjustRhine(RhineMode.Adjust, false, 0, zero))
        assertFalse(canAdjustRhine(RhineMode.Adjust, true, 1, zero))
        for (kind in
            listOf(
                RhineBlockKind.Decoration,
                RhineBlockKind.Placeholder,
                RhineBlockKind.AddParent,
                RhineBlockKind.Real,
            )) assertFalse(canAdjustRhine(RhineMode.Adjust, true, 0, zero.copy(kind = kind)))
        assertTrue(
            canAdjustRhine(
                RhineMode.Adjust,
                true,
                0,
                zero.copy(kind = RhineBlockKind.Real, item = RhineItem(8, 8, "T")),
            )
        )
    }

    @Test
    fun loadingFlagIsIndependentOfUserTitle() {
        val column = RhineColumn(1, 1, "载入中…", null, 0, emptyMap())
        val slot = RhineSlot(0, 0, RhineBlockKind.Zero, column)
        assertTrue(canAdjustRhine(RhineMode.Adjust, true, 0, slot))
        assertFalse(
            canAdjustRhine(
                RhineMode.Adjust,
                true,
                0,
                slot.copy(column = column.copy(loaded = false)),
            )
        )
    }

    @Test
    fun positionValidationAndZeroBasedConversion() {
        assertEquals(0, movePosition(" 1 ", 8))
        assertEquals(7, movePosition("8", 8))
        for (value in
            listOf("", " ", "0", "-1", "9", "1.2", "abc", "999999999999", "+1")) assertNull(
            movePosition(value, 8)
        )
        assertNull(movePosition("8", 7))
        assertNull(movePosition("1", 0))
    }

    @Test
    fun generationParentOrderAndAncestorInvalidateEvenReusedIds() {
        val scope = MoveScope(MoveKind.Section, 1, 2, listOf(1, 2, 3), "one")
        scope.validate(scope)
        for (other in
            listOf(
                scope.copy(parentId = 3),
                scope.copy(ancestorId = 4),
                scope.copy(ids = listOf(1, 3, 2)),
                scope.copy(generation = "two"),
                scope.copy(ids = listOf(1, 2)),
            )) {
            try {
                scope.validate(other)
                fail()
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test
    fun latestSearchInputClearsOldPageImmediately() {
        val s = RhineSearchState()
        s.change("old")
        s.pageBy(3)
        s.results = listOf(TaskSearchResult(1, 1, "old", 0, "C", true))
        val version = s.version
        s.change("new")
        assertEquals(0, s.page)
        assertTrue(s.results.isEmpty())
        assertTrue(s.version > version)
        s.pageBy(-1)
        assertEquals(0, s.page)
    }

    @Test
    fun previewPeakFollowsDestinationBeforeCommitForContentAndColumns() {
        val geometry = RhineGeometry()
        val columns =
            listOf(
                RhineColumn(
                    1,
                    1,
                    "A",
                    null,
                    8,
                    (1..8).associateWith { RhineItem(it.toLong(), it, "T$it") },
                ),
                RhineColumn(2, 2, "B", null, 0, emptyMap()),
            )
        val input = RhineSceneInput(RhineLayer.Categories, columns, 0, 8f)
        val before = RhineSnapshot(RhineCamera(8f, 0f), RhineFocus(1, 8, 8, 0))
        val content =
            RhineMove(
                8,
                false,
                RhineLayer.Categories,
                1,
                MoveScope(MoveKind.Task, 1, null, (1L..8L).toList(), "g"),
                before,
                input.slot(8, 0),
            )
        val projection =
            RhineProjection(
                geometry,
                2.625f,
                androidx.compose.ui.geometry.Size(2400f, 1080f),
                RhineCamera(2f, 0f),
            )
        val preview = layoutScene(input.copy(focusU = 2f), projection, content, 1f, 6f)
        assertEquals(
            (geometry.maxHeight + 6f) * 2.625f,
            preview.blocks.first { it.slot.item?.id == 8L }.height,
            .01f,
        )
        val parent =
            RhineMove(
                1,
                true,
                RhineLayer.Categories,
                1,
                MoveScope(MoveKind.Category, null, null, listOf(1, 2), "g"),
                before,
                input.slot(0, 0),
            )
        val p2 =
            RhineProjection(
                geometry,
                2.625f,
                androidx.compose.ui.geometry.Size(2400f, 1080f),
                RhineCamera(0f, 1f),
            )
        val parentPreview =
            layoutScene(input.copy(focusU = 0f, focusV = 1, cameraV = 1f), p2, parent, 1f, 6f)
        assertEquals(
            (geometry.maxHeight + 6f) * 2.625f,
            parentPreview.blocks.first { it.slot.column?.id == 1L && it.slot.u == 0 }.height,
            .01f,
        )
    }
}

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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.shub39.grit.core.settings.RhineDisplayConfig
import com.shub39.grit.core.settings.RhineImageFit
import com.shub39.grit.core.tasks.IllustrationChange
import com.shub39.grit.shared.ui.rhine.*
import org.junit.Assert.*
import org.junit.Test

/** docs/视觉优化/03: fitting, visibility, decode sizes, drafts and settings compatibility. */
class RhineIllustrationTest {
    private val area = Rect(10f, 20f, 210f, 120f) // 200 × 100

    @Test
    fun defaultCropFillsFromTheTopLeftWithoutStretching() {
        val (src, dst) = illustrationRects(IntSize(1600, 1000), area, RhineImageFit.CropTopStart)
        assertEquals(area, dst)
        assertEquals(IntOffset.Zero, src.first)
        // Same aspect as the area: 2:1 out of a 1.6:1 image keeps full width.
        assertEquals(1600, src.second.width)
        assertEquals(800, src.second.height)
    }

    @Test
    fun centreCropAndFitKeepTheAspectRatio() {
        val (src, _) = illustrationRects(IntSize(1600, 1000), area, RhineImageFit.CropCenter)
        assertEquals(IntOffset(0, 100), src.first)
        val (whole, dst) = illustrationRects(IntSize(1000, 1000), area, RhineImageFit.Fit)
        assertEquals(IntSize(1000, 1000), whole.second)
        assertEquals(100f, dst.width, 0.01f)
        assertEquals(100f, dst.height, 0.01f)
        assertEquals(area.center.x, dst.center.x, 0.01f)
    }

    @Test
    fun onlyColumnStartBlocksOrTheRaisedBlockShowIllustrations() {
        val column = RhineColumn(1, 1, "A", itemCount = 1, items = mapOf(1 to RhineItem(9, 1, "t")))
        val input = RhineSceneInput(RhineLayer.Categories, listOf(column), 0, 0f)
        assertTrue(showsIllustration(input.slot(0, 0), raised = false))
        assertFalse(showsIllustration(input.slot(1, 0), raised = false))
        assertFalse(showsIllustration(input.slot(2, 0), raised = false))
        assertFalse(showsIllustration(input.slot(0, 1), raised = false)) // add-parent column
        assertTrue(showsIllustration(input.slot(1, 0), raised = true))
    }

    @Test
    fun decodeSizeIsTheSmallestBucketWithHeadroom() {
        assertEquals(384, illustrationBucket(300f))
        assertEquals(768, illustrationBucket(543f))
        assertEquals(1280, illustrationBucket(920f))
        assertEquals(1280, illustrationBucket(5000f))
    }

    @Test
    fun draftReportsOnlyRealChanges() {
        val draft = IllustrationDraft("content://a")
        assertEquals(IllustrationChange.Keep, draft.change)
        draft.pick("content://b")
        assertEquals(IllustrationChange.Set("content://b"), draft.change)
        draft.pick("content://a")
        assertEquals(IllustrationChange.Keep, draft.change)
        draft.remove()
        assertEquals(IllustrationChange.Remove, draft.change)
        val fresh = IllustrationDraft(null)
        fresh.remove()
        assertEquals(IllustrationChange.Keep, fresh.change)
    }

    @Test
    fun displaySettingsStayCompatible() {
        // Saved before the fit setting existed: default fit, other values kept.
        val old = RhineDisplayConfig.decode("""{"version":1,"topSp":10.0}""")
        assertEquals(RhineImageFit.CropTopStart, old.illustrationFit)
        assertEquals(10f, old.topSp)
        val unknown = RhineDisplayConfig.decode("""{"version":1,"illustrationFit":"Bogus"}""")
        assertEquals(RhineImageFit.CropTopStart, unknown.illustrationFit)
        val chosen = RhineDisplayConfig(illustrationFit = RhineImageFit.Fit)
        assertEquals(chosen, RhineDisplayConfig.decode(chosen.encode()))
    }
}

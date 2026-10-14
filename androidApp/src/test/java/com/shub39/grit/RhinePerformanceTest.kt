package com.shub39.grit

import androidx.compose.ui.geometry.Size
import com.shub39.grit.shared.ui.rhine.*
import org.junit.Assert.*
import org.junit.Test

class RhinePerformanceTest {
    @Test
    fun addingOffscreenDataDoesNotIncreaseDrawnBlocksOrChangeHitTest() {
        fun input(count: Int) =
            RhineSceneInput(
                RhineLayer.Categories,
                List(count) { v ->
                    RhineColumn(v + 1L, v + 1, "Category $v", itemCount = 100, items = emptyMap())
                },
                49,
                50f,
            )
        val projection =
            RhineProjection(RhineGeometry(), 2.625f, Size(2400f, 1080f), RhineCamera(50f, 49f))
        val a = layoutScene(input(100), projection)
        val b = layoutScene(input(10000), projection)
        assertEquals(a.blocks.map { it.top }, b.blocks.map { it.top })
        assertTrue(a.blocks.size < 500)
        val p =
            projection.project(
                50 * projection.pitchU + projection.depth / 2,
                49 * projection.pitchV + projection.length / 2,
                110 * projection.density,
            )
        assertEquals(
            a.hitTest(p)?.slot?.let { it.u to it.v },
            b.hitTest(p)?.slot?.let { it.u to it.v },
        )
    }
}

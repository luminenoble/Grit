package com.shub39.grit

import androidx.compose.ui.geometry.Size
import com.shub39.grit.core.settings.*
import com.shub39.grit.shared.ui.rhine.*
import org.junit.Assert.*
import org.junit.Test

class RhineDisplayTest {
    @Test
    fun defaultsMatchConfirmedBaseline() {
        val c = RhineDisplayConfig()
        assertEquals(RhineGeometry(), c.geometry(2400f / 2.625f, 1080f / 2.625f))
        assertEquals(8f, c.topSp, 0f)
        assertEquals(7f, c.sideSp, 0f)
        assertFalse(c.scrollFocusedTitle)
        assertFalse(c.placeholderNames)
        assertEquals(c, RhineDisplayConfig.decode(c.encode()))
    }

    @Test
    fun invalidNumbersFallBackIndividuallyAndUnknownSchemasFallBackCompletely() {
        val c =
            RhineDisplayConfig(
                    length = Float.NaN,
                    depth = Float.POSITIVE_INFINITY,
                    peak = -1f,
                    topSp = 0f,
                    sideSp = 12f,
                )
                .normalized()
        assertEquals(207f, c.length, 0f)
        assertEquals(18f, c.depth, 0f)
        assertEquals(70f, c.peak, 0f)
        assertEquals(8f, c.topSp, 0f)
        assertEquals(12f, c.sideSp, 0f)
        assertEquals(
            RhineDisplayConfig(),
            RhineDisplayConfig.decode("{\"version\":2,\"topSp\":12}"),
        )
        assertEquals(RhineDisplayConfig(), RhineDisplayConfig.decode("bad json"))
        assertEquals(RhineDisplayConfig(), RhineDisplayConfig.decode("{}"))
        val unknown =
            RhineDisplayConfig.decode(
                "{\"version\":1,\"density\":\"Future\",\"length\":\"NaN\",\"topSp\":12,\"sideSp\":{},\"reduceMotion\":true,\"future\":2}"
            )
        assertEquals(RhineDensity.Standard, unknown.density)
        assertEquals(207f, unknown.length, 0f)
        assertEquals(12f, unknown.topSp, 0f)
        assertTrue(unknown.reduceMotion)
    }

    @Test
    fun switchingModesDoesNotOverwriteCustomDimensionsOrViewportTargets() {
        val c =
            RhineDisplayConfig(
                density = RhineDensity.Custom,
                length = 250f,
                depth = 32f,
                targetRows = 20f,
            )
        val p = c.preset(RhineDensity.Compact)
        assertEquals(250f, p.length, 0f)
        assertEquals(32f, p.depth, 0f)
        val custom = p.preset(RhineDensity.Custom)
        assertEquals(250f, custom.geometry(700f, 300f).blockLength, 0f)
        assertEquals(16f, custom.targetRows, 0f)
    }

    @Test
    fun densityGoalsAreMonotonicAndIndependentOfBusinessBounds() {
        val input = rhineSample(RhineSample.Standard)
        val configs =
            listOf(RhineDensity.Loose, RhineDensity.Standard, RhineDensity.Compact).map {
                RhineDisplayConfig().preset(it)
            }
        val geometries = configs.map { it.geometry(914.2857f, 411.42857f) }
        assertTrue(geometries[0].blockLength > geometries[1].blockLength)
        assertTrue(geometries[1].blockLength > geometries[2].blockLength)
        for (c in configs) {
            assertEquals(input.maxU, input.copy().maxU)
            assertEquals(input.maxV, input.copy().maxV)
            assertEquals(input.focus(), input.copy().focus())
        }
    }

    @Test
    fun extremesKeepProjectionWindowBoundedAndFrontmostHitsRespectDecoration() {
        val configurations =
            listOf(
                RhineDisplayConfig(
                    density = RhineDensity.Custom,
                    length = 100f,
                    depth = 12f,
                    gapU = 8f,
                    gapV = 6f,
                    base = 8f,
                    center = 8f,
                    peak = 0f,
                    spread = 1f,
                ),
                RhineDisplayConfig(
                    density = RhineDensity.Custom,
                    length = 360f,
                    depth = 64f,
                    gapU = 40f,
                    gapV = 40f,
                    base = 48f,
                    center = 64f,
                    peak = 120f,
                    spread = 8f,
                ),
            )
        val columns =
            listOf(
                RhineColumn(
                    1,
                    1,
                    "long",
                    itemCount = 100,
                    items = (1..100).associateWith { RhineItem(it.toLong(), it, "Task $it") },
                ),
                RhineColumn(
                    2,
                    2,
                    "short",
                    itemCount = 1,
                    items = mapOf(1 to RhineItem(101, 1, "short")),
                ),
            )
        for (c in configurations) for (u in listOf(0f, 99f, 101f)) for (v in listOf(0, 1, 2)) {
            val input = RhineSceneInput(RhineLayer.Categories, columns, v, u)
            val g = c.geometry(640f, 360f)
            val p = RhineProjection(g, 2f, Size(1280f, 720f), RhineCamera(u, v.toFloat()))
            assertTrue(p.visibleSlots().count < 2000)
            val layout = layoutScene(input, p)
            for (x in 0..1280 step 80) for (y in 0..720 step 80) {
                val point = androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat())
                val hit = layout.hitTest(point) ?: continue
                assertEquals(hit, layout.blocks.last { it.contains(point) })
                if (hit.slot.kind == RhineBlockKind.Decoration)
                    assertFalse(canAdjustRhine(RhineMode.Adjust, true, v, hit.slot))
            }
            assertEquals(RhineBlockKind.Zero, input.slot(0, 0).kind)
            assertEquals(RhineBlockKind.Placeholder, input.slot(99, 1).kind)
            assertEquals(RhineBlockKind.Decoration, input.slot(102, 1).kind)
        }
    }

    @Test
    fun shadedFacesKeepRawColorReadableAtBothEnds() {
        val style = RhineStyle()
        for (raw in listOf("#FFFFFF", "#FFFFCC", "#426E79")) for (center in listOf(0f, 0.5f, 1f)) {
            val a = style.frontAt(center)
            val b = style.frontLowAt(center)
            val shown = style.readableFace(raw, a, b)
            assertTrue(contrast(shown, a) >= 4.5f)
            assertTrue(contrast(shown, b) >= 4.5f)
            val top = style.readableFace(raw, style.top, style.topFar)
            assertTrue(contrast(top, style.top) >= 4.5f)
            assertTrue(contrast(top, style.topFar) >= 4.5f)
        }
    }

    @Test
    fun hidingNumbersDoesNotChangeIdentityOrOperationPermissions() {
        val c =
            RhineDisplayConfig(
                categoryNumbers = false,
                taskNumbers = false,
                sectionNumbers = false,
                placeholderNames = true,
            )
        val input = rhineSample(RhineSample.Standard)
        assertFalse(c.itemNumbers(RhineLayer.Categories))
        assertFalse(c.parentNumbers(RhineLayer.Sections))
        assertTrue(
            canAdjustRhine(
                RhineMode.Adjust,
                true,
                input.focusV,
                input.slot(input.focusU.toInt(), input.focusV),
            )
        )
        val noParent = input.slot(0, input.maxV + 1)
        assertNull(noParent.column)
        assertEquals(RhineBlockKind.Decoration, noParent.kind)
    }
}

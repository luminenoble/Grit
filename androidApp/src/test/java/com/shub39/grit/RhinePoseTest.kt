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
import com.shub39.grit.shared.ui.rhine.*
import kotlin.math.PI
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** docs/视觉优化/02 section 5.6: the pose must leave the confirmed baseline untouched. */
class RhinePoseTest {
    private val geometry = RhineGeometry()
    private val screen = Size(2400f, 1080f)
    private val density = 2.625f
    private val camera = RhineCamera(3.4f, 1.2f)

    /** The projection exactly as it was before poses existed. */
    private fun legacy(wu: Float, wv: Float, z: Float): Offset {
        val pitchU = (geometry.blockDepth + geometry.gapU) * density
        val pitchV = (geometry.blockLength + geometry.gapV) * density
        val camU = camera.u * pitchU + geometry.blockDepth * density / 2f
        val camV = camera.v * pitchV + geometry.blockLength * density / 2f
        val anchor = Offset(screen.width * geometry.anchor.x, screen.height * geometry.anchor.y)
        val eU = geometry.basisU
        val eV = geometry.basisV
        val eZ = geometry.basisZ
        return Offset(
            anchor.x + (wu - camU) * eU.x + (wv - camV) * eV.x + z * eZ.x,
            anchor.y + (wu - camU) * eU.y + (wv - camV) * eV.y + z * eZ.y,
        )
    }

    @Test
    fun identityPoseIsBitIdenticalToTheOriginalProjection() {
        val p = RhineProjection(geometry, density, screen, camera)
        val random = Random(7)
        repeat(10_000) {
            val wu = random.nextFloat() * 6000f - 1000f
            val wv = random.nextFloat() * 6000f - 1000f
            val z = random.nextFloat() * 400f
            assertEquals(legacy(wu, wv, z), p.project(wu, wv, z))
        }
    }

    @Test
    fun unprojectInvertsAnyPose() {
        val pose =
            RhinePose(
                yaw = 0.6f,
                kh = 1.3f,
                kz = 0.96f,
                zoom = 1.4f,
                pivot = WorldPoint(300f, 500f, 120f),
                screen = Offset(600f, 500f),
            )
        val p = RhineProjection(geometry, density, screen, camera, pose)
        for (z in floatArrayOf(0f, 55f, 300f)) {
            val back = p.unproject(p.project(1234f, 567f, z), z)
            assertEquals(1234f, back.x, 1e-2f)
            assertEquals(567f, back.y, 1e-2f)
        }
    }

    @Test
    fun faceOnAzimuthMatchesTheDesignAndKeepsDrawOrderValid() {
        val azimuth = geometry.viewAzimuth()
        assertEquals(47.62, azimuth * 180.0 / PI, 0.05)
        for (i in 0..20) {
            val yaw = azimuth * i / 20f
            val p = RhineProjection(geometry, density, screen, camera, RhinePose(yaw = yaw, kh = 1.3f))
            // The view ray must keep non-negative U and V components: back-to-front stays
            // "descending u, then descending v" and the v0 end cap never turns away.
            assertTrue("slopeU at $yaw", p.slopeU >= -1e-4f)
            assertTrue("slopeV at $yaw", p.slopeV >= -1e-4f)
        }
        val faceOn = RhineProjection(geometry, density, screen, camera, RhinePose(yaw = azimuth))
        assertEquals(0f, faceOn.slopeV, 1e-4f)
    }

    @Test
    fun positiveYawTurnsTheSceneClockwiseOnScreen() {
        val p = RhineProjection(geometry, density, screen, camera, RhinePose(yaw = 0.3f))
        val v = p.project(0f, 1000f, 0f) - p.project(0f, 0f, 0f)
        // The long side climbs ~29° at rest; turning clockwise flattens it.
        val angle = kotlin.math.atan2(-v.y, v.x) * 180.0 / PI
        assertTrue(angle < 29.0 && angle > 0.0)
    }

    @Test
    fun transitionsEndExactlyOnTheirTargets() {
        val start = RhinePose(pivot = WorldPoint(0f, 0f, 0f), screen = Offset(100f, 100f))
        val end = start.copy(yaw = 0.66f, kh = 1.3f, kz = 0.96f, zoom = 1.3f, screen = Offset(400f, 300f))
        val raised = start.copy(screen = Offset(100f, 60f))
        val entered = editorEnterFrame(1500f, start, raised, end, 122f)
        assertEquals(end, entered.pose)
        assertEquals(122f, entered.liftDp)
        assertEquals(0f, entered.contextAlpha)
        assertEquals(1f, entered.panelAlpha)
        assertEquals(122f, editorEnterFrame(550f, start, raised, end, 122f).liftDp, 1e-3f)
        assertEquals(start, editorEnterFrame(549f, start, raised, end, 122f).pose.copy(screen = start.screen))

        val exited = editorExitFrame(1500f, end, start, 122f)
        assertEquals(start, exited.pose)
        assertEquals(0f, exited.liftDp)
        assertEquals(1f, exited.contextAlpha)
        assertEquals(1f, exited.hudAlpha)
        assertEquals(0f, exited.panelAlpha)
        // Reveal the raised, turned block before it starts descending.
        val revealed = editorExitFrame(500f, end, start, 122f)
        assertEquals(end, revealed.pose)
        assertEquals(1f, revealed.contextAlpha)

        val navEnd = navEnterFrame(1000f, start, end, 88f, 20f)
        assertEquals(RhinePose.Identity, navEnd.pose)
        assertEquals(1f, navEnd.sceneAlpha)
        assertEquals(0f, navEnterFrame(700f, start, end, 88f, 20f).sceneAlpha)
    }

    @Test
    fun raisedBlockAboveTheSceneIsDrawnLast() {
        val input =
            RhineSceneInput(
                RhineLayer.Categories,
                listOf(RhineColumn(1, 1, "A", itemCount = 3, items = (1..3).associateWith { RhineItem(it.toLong(), it, "t$it") })),
                0,
                0f,
            )
        val p = RhineProjection(geometry, density, screen, RhineCamera(0f, 0f))
        val low = layoutScene(input, p, raise = RhineRaise(0, 0, zDp = 20f))
        assertFalse(low.raisedClear)
        assertTrue(low.blocks.any { it === low.raised })
        val high = layoutScene(input, p, raise = RhineRaise(0, 0, zDp = geometry.maxHeight + 12f))
        assertTrue(high.raisedClear)
        assertTrue(high.blocks.none { it === high.raised })
        assertEquals((geometry.maxHeight + 12f) * density, high.raised!!.base, 1e-3f)
    }

    @Test
    fun toolbarKeepsClearOfTheFocusBlock() {
        val wide = RhineDisplayConfigDefaults.geometry(914f, 411f)
        assertEquals(RhineToolbarProfile.Full, rhineToolbarProfile(wide, 914f, 411f, false, 32f))
        val narrow = RhineDisplayConfigDefaults.geometry(731f, 329f)
        assertEquals(RhineToolbarProfile.Narrow, rhineToolbarProfile(narrow, 731f, 329f, true, 16f))
        val huge = RhineGeometry(blockLength = 360f)
        assertEquals(RhineToolbarProfile.Low, rhineToolbarProfile(huge, 800f, 360f, false, 31f))
    }
}

private object RhineDisplayConfigDefaults {
    fun geometry(w: Float, h: Float) = com.shub39.grit.core.settings.RhineDisplayConfig().geometry(w, h)
}

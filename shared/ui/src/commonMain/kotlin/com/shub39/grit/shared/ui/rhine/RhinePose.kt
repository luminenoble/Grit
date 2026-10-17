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

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.shub39.grit.shared.ui.theme.GritMotion
import kotlin.math.atan2

/** A world point in px: U along a column, V across columns, Z up. */
@Immutable data class WorldPoint(val u: Float, val v: Float, val z: Float)

/**
 * Camera pose on top of the fixed reference basis (docs/视觉优化/02 section 5):
 * `screen + zoom · B_k · Rz(yaw) · (p − pivot)`, where `B_k` scales the vertical screen component of
 * the horizontal axes by [kh] and of Z by [kz]. A positive [yaw] turns the scene clockwise on screen,
 * i.e. the camera orbits counterclockwise seen from above. [pivot] null means the camera focus at
 * floor level and [screen] null means the geometry anchor, which together with the neutral values
 * reproduce the original projection exactly.
 */
@Immutable
data class RhinePose(
    val yaw: Float = 0f,
    val kh: Float = 1f,
    val kz: Float = 1f,
    val zoom: Float = 1f,
    val pivot: WorldPoint? = null,
    val screen: Offset? = null,
) {
    val isIdentity: Boolean
        get() = this == Identity

    companion object {
        val Identity = RhinePose()
    }
}

/** Linear interpolation; pivots and screen points must both be set on the two poses. */
fun lerp(a: RhinePose, b: RhinePose, t: Float): RhinePose {
    fun f(x: Float, y: Float) = x + (y - x) * t
    val pa = requireNotNull(a.pivot ?: b.pivot)
    val pb = requireNotNull(b.pivot ?: a.pivot)
    val sa = requireNotNull(a.screen ?: b.screen)
    val sb = requireNotNull(b.screen ?: a.screen)
    return RhinePose(
        f(a.yaw, b.yaw),
        f(a.kh, b.kh),
        f(a.kz, b.kz),
        f(a.zoom, b.zoom),
        WorldPoint(f(pa.u, pb.u), f(pa.v, pb.v), f(pa.z, pb.z)),
        Offset(f(sa.x, sb.x), f(sa.y, sb.y)),
    )
}

/**
 * Horizontal azimuth of the view ray, measured from +U towards +V (radians). Yawing by exactly this
 * much turns the long front face square to the camera and the end cap edge-on; within
 * `[0, viewAzimuth]` the existing face set and back-to-front order stay valid.
 */
fun RhineGeometry.viewAzimuth(): Float {
    val det = basisU.x * basisV.y - basisU.y * basisV.x
    val slopeU = (basisZ.x * basisV.y - basisZ.y * basisV.x) / det
    val slopeV = (basisU.x * basisZ.y - basisU.y * basisZ.x) / det
    return atan2(slopeV, slopeU)
}

/** Editor and navigation targets, relative to the geometry (docs/视觉优化/02 sections 3–5). */
object RhineMotionSpec {
    const val NavYawFraction = 0.5f
    const val EditorYawFraction = 0.8f
    const val EditorKh = 1.30f
    const val EditorKz = 0.96f
    const val NavZoom = 1.06f
    /** Lift as a fraction of the tallest block height. */
    const val NavLift = 0.80f
    /** Extra lift above the tallest block so nothing can cover the raised block. */
    const val EditorLiftExtraDp = 12f
    const val NavFollow = 0.30f
    const val EditorFollow = 0.45f

    const val NavMs = 1000
    const val EditorMs = 1500
}

/** One frame of a Rhine transition; every value is a pure function of elapsed time. */
@Immutable
data class RhineFrame(
    val pose: RhinePose = RhinePose.Identity,
    /** Lift of the selected block, dp. */
    val liftDp: Float = 0f,
    /** Opacity of every block except the selected one. */
    val contextAlpha: Float = 1f,
    /** Opacity of the whole scene (navigation fades). */
    val sceneAlpha: Float = 1f,
    val hudAlpha: Float = 1f,
    val panelAlpha: Float = 0f,
    /** Opacity of the selected block (delete dissolve). */
    val selectedAlpha: Float = 1f,
)

/** Progress of [ms] through the window `[start, end]`, eased. */
fun window(ms: Float, start: Int, end: Int, easing: Easing): Float =
    easing.transform(((ms - start) / (end - start).toFloat()).coerceIn(0f, 1f))

/**
 * Category → Task (1.0 s): lift 0–650, yaw 0–700, scene fade-out 450–700, swap at 700, fade-in
 * 700–1000. [start] and [end] are the neutral and the turned pose around the selected block.
 */
fun navEnterFrame(ms: Float, start: RhinePose, end: RhinePose, liftDp: Float, followPx: Float): RhineFrame {
    val lift = window(ms, 0, 650, GritMotion.EaseOutCubic)
    val turn = window(ms, 0, 700, GritMotion.EaseInOutCubic)
    val fadeOut = window(ms, 450, 700, GritMotion.EaseExit)
    val fadeIn = window(ms, 700, 1000, GritMotion.EaseStandard)
    if (ms >= 700f)
        return RhineFrame(
            sceneAlpha = fadeIn,
            hudAlpha = window(ms, 850, 1000, GritMotion.EaseStandard),
        )
    val pose = lerp(start, end, turn).let { p ->
        p.copy(screen = p.screen!! + Offset(0f, followPx * lift))
    }
    return RhineFrame(
        pose = pose,
        liftDp = liftDp * lift,
        sceneAlpha = 1f - fadeOut,
        hudAlpha = 1f - window(ms, 0, 150, GritMotion.EaseExit),
    )
}

/**
 * Editor entry (1.5 s): lift 0–550 with the camera following, then yaw/tilt/zoom/pan 550–1150,
 * context fade-out 1100–1350 and panel fade-in 1250–1500. [raised] is [start] shifted by the
 * follow offset, i.e. the pose at the end of the lift.
 */
fun editorEnterFrame(
    ms: Float,
    start: RhinePose,
    raised: RhinePose,
    end: RhinePose,
    liftDp: Float,
): RhineFrame {
    val lift = window(ms, 0, 550, GritMotion.EaseOutCubic)
    val turn = window(ms, 550, 1150, GritMotion.EaseInOutCubic)
    val pose = if (ms < 550f) lerp(start, raised, lift) else lerp(raised, end, turn)
    return RhineFrame(
        pose = pose,
        liftDp = liftDp * lift,
        contextAlpha = 1f - window(ms, 1100, 1350, GritMotion.EaseExit),
        hudAlpha = 1f - window(ms, 0, 150, GritMotion.EaseExit),
        panelAlpha = window(ms, 1250, 1500, GritMotion.EaseStandard),
    )
}

/**
 * Editor exit (1.5 s): panel fade-out 0–250, context fade-in 200–500 revealing the raised, turned
 * block, then descent and clockwise return 500–1500 together, HUD fade-in 1350–1500. With
 * [dissolve] (after delete) the block fades 250–600 and only the camera returns 600–1500.
 */
fun editorExitFrame(
    ms: Float,
    from: RhinePose,
    to: RhinePose,
    liftDp: Float,
    dissolve: Boolean = false,
): RhineFrame {
    val back =
        if (dissolve) window(ms, 600, 1500, GritMotion.EaseInOutCubic)
        else window(ms, 500, 1500, GritMotion.EaseInOutCubic)
    return RhineFrame(
        pose = lerp(from, to, back),
        liftDp = liftDp * (1f - back),
        contextAlpha = window(ms, 200, 500, GritMotion.EaseStandard),
        hudAlpha = window(ms, 1350, 1500, GritMotion.EaseStandard),
        panelAlpha = 1f - window(ms, 0, 250, GritMotion.EaseExit),
        selectedAlpha = if (dissolve) 1f - window(ms, 250, 600, GritMotion.EaseExit) else 1f,
    )
}

/**
 * Zoom that fits a block whose 8 corners project to [bounds] (under the target pose at zoom 1) into
 * [preview]: 78 % of the width or 62 % of the height, within `[0.6, 2.4]`.
 */
fun editorZoom(bounds: Rect, preview: Rect): Float =
    minOf(
            0.78f * preview.width / bounds.width.coerceAtLeast(1f),
            0.62f * preview.height / bounds.height.coerceAtLeast(1f),
        )
        .coerceIn(0.6f, 2.4f)

/** Rect helper for preview areas expressed in px. */
fun previewRect(size: Size, left: Float, top: Float, right: Float, bottom: Float) =
    Rect(left, top, size.width - right, size.height - bottom)

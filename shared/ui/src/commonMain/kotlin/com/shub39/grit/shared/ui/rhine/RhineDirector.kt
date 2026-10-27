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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.Job

/** Transition stages; see the state machine in docs/视觉优化/02 section 9. */
enum class RhineStage {
    Idle,
    /** Category → Task: lift and turn, then fade into the Section layer. */
    NavEnter,
    /** Cinematic entry into the Category/Task editor. */
    EditorEnter,
    Editing,
    /** Descent and clockwise return to the default framing. */
    EditorExit,
}

/** Where an editor exit lands. Cameras are browser cameras (slot units). */
data class RhineExit(
    val from: RhineCamera,
    val to: RhineCamera,
    /** Stored block the raised one becomes (a just-created object), hidden until it lands. */
    val hideId: Long? = null,
    /** After a delete the raised block dissolves instead of landing. */
    val dissolve: Boolean = false,
)

/**
 * Single owner of Rhine transitions: one stage, one clock and one job. Every frame derives from
 * [stage] and [ms] (elapsed milliseconds), so a frame is reproducible and a transition can be played
 * backwards when it is interrupted.
 */
@Stable
class RhineDirector {
    var stage by mutableStateOf(RhineStage.Idle)
    var ms by mutableFloatStateOf(0f)
    /** The block being entered or edited. */
    var slot by mutableStateOf<RhineSlot?>(null)
    var exit by mutableStateOf<RhineExit?>(null)
    var job: Job? = null
    /** Bumped on every new transition; work started for an older epoch must not apply. */
    var epoch = 0
        private set

    val active: Boolean
        get() = stage != RhineStage.Idle

    fun begin(stage: RhineStage, slot: RhineSlot?): Int {
        job?.cancel()
        this.stage = stage
        this.slot = slot
        ms = 0f
        exit = null
        return ++epoch
    }

    fun reset() {
        job?.cancel()
        job = null
        stage = RhineStage.Idle
        slot = null
        exit = null
        ms = 0f
        epoch++
    }

    /**
     * Advances (or rewinds) [ms] to [target] in real time. With [instant] (reduced motion) it jumps.
     * [onFrame] runs after every step, e.g. to move the browser camera along.
     */
    suspend fun playTo(target: Float, instant: Boolean, onFrame: (Float) -> Unit = {}) {
        if (instant) {
            ms = target
            onFrame(ms)
            return
        }
        val forward = target >= ms
        var last = withFrameNanos { it }
        while (if (forward) ms < target else ms > target) {
            val now = withFrameNanos { it }
            // Cap a step so a stalled frame does not skip a whole phase.
            val step = ((now - last) / 1_000_000f).coerceAtMost(50f)
            last = now
            ms = if (forward) minOf(target, ms + step) else maxOf(target, ms - step)
            onFrame(ms)
        }
    }
}

/**
 * Turns the director's stage and clock into a [RhineFrame] and the raised block for the current
 * geometry and viewport. Poses are recomputed from the viewport every time, so a resize, a font
 * change or the keyboard simply re-targets the composition.
 *
 * [previewInsets] are the px margins of the preview area (left, top, bottom) and [panelFraction] the
 * share of the width the editor panel takes on the right.
 */
class RhineFramer(
    private val geometry: RhineGeometry,
    private val density: Float,
    private val viewport: Size,
    private val previewInsets: Rect,
    private val panelFraction: Float,
) {
    private val azimuth = geometry.viewAzimuth()
    private val editorLiftDp = geometry.maxHeight + RhineMotionSpec.EditorLiftExtraDp
    private val navLiftDp = geometry.maxHeight * RhineMotionSpec.NavLift

    private fun projection(camera: RhineCamera, pose: RhinePose = RhinePose.Identity) =
        RhineProjection(geometry, density, viewport, camera, pose)

    /** The neutral pose around slot (u, v) as seen from [camera]: identical to the identity pose. */
    fun anchorPose(camera: RhineCamera, u: Float, v: Float): RhinePose {
        val p = projection(camera)
        val pivot = p.slotCenter(u, v, 0f)
        return RhinePose(pivot = pivot, screen = p.project(pivot))
    }

    /** Turned, tilted and zoomed pose that frames the raised block in the left preview area. */
    fun editorPose(u: Float, v: Float): RhinePose {
        val camera = RhineCamera(u, v)
        val pivot = projection(camera).slotCenter(u, v, 0f)
        val unit =
            RhinePose(
                yaw = azimuth * RhineMotionSpec.EditorYawFraction,
                kh = RhineMotionSpec.EditorKh,
                kz = RhineMotionSpec.EditorKz,
                pivot = pivot,
                screen = Offset.Zero,
            )
        val p = projection(camera, unit)
        val lift = editorLiftDp * density
        val h = geometry.maxHeight * density
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (du in floatArrayOf(-p.depth / 2f, p.depth / 2f))
            for (dv in floatArrayOf(-p.length / 2f, p.length / 2f))
                for (z in floatArrayOf(lift, lift + h)) {
                    val c = p.project(pivot.u + du, pivot.v + dv, z)
                    minX = minOf(minX, c.x)
                    minY = minOf(minY, c.y)
                    maxX = maxOf(maxX, c.x)
                    maxY = maxOf(maxY, c.y)
                }
        val bounds = Rect(minX, minY, maxX, maxY)
        val preview =
            Rect(
                previewInsets.left,
                previewInsets.top,
                viewport.width * (1f - panelFraction),
                viewport.height - previewInsets.bottom,
            )
        val zoom = editorZoom(bounds, preview)
        return unit.copy(zoom = zoom, screen = preview.center - bounds.center * zoom)
    }

    /**
     * Frame for [director] while the browser camera is [camera]. [labelled] gives the raised block's
     * content (the draft's own labels while editing).
     */
    fun frame(
        director: RhineDirector,
        camera: RhineCamera,
        labelled: (RhineSlot) -> RhineSlot,
    ): Pair<RhineFrame, RhineRaise?> {
        val slot = director.slot ?: return RhineFrame() to null
        val u = slot.u.toFloat()
        val v = slot.v.toFloat()
        val ms = director.ms
        return when (director.stage) {
            RhineStage.Idle -> RhineFrame() to null
            RhineStage.NavEnter -> {
                val start = anchorPose(camera, u, v)
                val end =
                    start.copy(
                        yaw = azimuth * RhineMotionSpec.NavYawFraction,
                        zoom = RhineMotionSpec.NavZoom,
                    )
                val frame =
                    navEnterFrame(
                        ms,
                        start,
                        end,
                        navLiftDp,
                        RhineMotionSpec.NavFollow * navLiftDp * density,
                    )
                frame to (if (ms < 700f) RhineRaise(slot.u, slot.v, frame.liftDp) else null)
            }
            RhineStage.EditorEnter,
            RhineStage.Editing -> {
                // While the camera still glides onto the block, nothing is posed yet.
                if (director.stage == RhineStage.EditorEnter && ms <= 0f)
                    return RhineFrame() to RhineRaise(slot.u, slot.v, 0f)
                val end = editorPose(u, v)
                val frame =
                    if (director.stage == RhineStage.Editing)
                        RhineFrame(
                            pose = end,
                            liftDp = editorLiftDp,
                            backdrop = 1f,
                            hudAlpha = 0f,
                            panelAlpha = 1f,
                        )
                    else {
                        val start = anchorPose(RhineCamera(u, v), u, v)
                        val raised =
                            start.copy(
                                screen =
                                    start.screen!! +
                                        Offset(
                                            0f,
                                            RhineMotionSpec.EditorFollow * editorLiftDp * density,
                                        )
                            )
                        editorEnterFrame(ms, start, raised, end, editorLiftDp)
                    }
                frame to RhineRaise(slot.u, slot.v, frame.liftDp, labelled(slot))
            }
            RhineStage.EditorExit -> {
                val exit = director.exit ?: return RhineFrame() to null
                val to = anchorPose(exit.to, exit.to.u, exit.to.v)
                val frame = editorExitFrame(ms, editorPose(u, v), to, editorLiftDp, exit.dissolve)
                val back = editorExitProgress(ms, exit.dissolve)
                frame to
                    RhineRaise(
                        slot.u,
                        slot.v,
                        frame.liftDp,
                        labelled(slot),
                        positionU = if (exit.dissolve) u else u + (exit.to.u - u) * back,
                        positionV = if (exit.dissolve) v else v + (exit.to.v - v) * back,
                        hideItemId = exit.hideId,
                        keepBase = exit.dissolve,
                    )
            }
        }
    }
}

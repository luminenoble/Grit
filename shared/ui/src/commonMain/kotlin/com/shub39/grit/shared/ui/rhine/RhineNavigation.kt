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

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

/** Business bounds never depend on the viewport or decorative blocks. */
data class RhineBounds(val maxU: Int, val maxV: Int) {
    fun clamp(camera: RhineCamera) =
        RhineCamera(camera.u.coerceIn(0f, maxU.toFloat()), camera.v.coerceIn(0f, maxV.toFloat()))

    fun snap(camera: RhineCamera) =
        clamp(RhineCamera(camera.u.roundToInt().toFloat(), camera.v.roundToInt().toFloat()))

    companion object {
        fun from(counts: List<Int>) =
            RhineBounds(if (counts.isEmpty()) 0 else counts.maxOrNull()!! + 1, counts.size)
    }
}

enum class RhineAxis {
    U,
    V,
}

/** One pointer owns a gesture. Axis selection compares physical world distance, not slot count. */
class RhineGestureController(private val projection: RhineProjection, private val slop: Float) {
    var axis: RhineAxis? = null
        private set

    private var accumulated = Offset.Zero

    fun delta(screenDelta: Offset): RhineCamera {
        accumulated += screenDelta
        if (axis == null && accumulated.getDistance() < slop) return RhineCamera(0f, 0f)
        val screen = if (axis == null) accumulated else screenDelta
        val world = projection.unproject(screen, 0f) - projection.unproject(Offset.Zero, 0f)
        if (axis == null) axis = if (abs(world.x) >= abs(world.y)) RhineAxis.U else RhineAxis.V
        return RhineCamera(
            if (axis == RhineAxis.U) -world.x / projection.pitchU else 0f,
            if (axis == RhineAxis.V) -world.y / projection.pitchV else 0f,
        )
    }

    fun velocity(screenVelocity: Offset): RhineCamera {
        val world = projection.unproject(screenVelocity, 0f) - projection.unproject(Offset.Zero, 0f)
        return RhineCamera(
            if (axis == RhineAxis.U) (-world.x / projection.pitchU).coerceIn(-65f, 65f) else 0f,
            if (axis == RhineAxis.V) (-world.y / projection.pitchV).coerceIn(-5f, 5f) else 0f,
        )
    }
}

data class RhineMotion(val camera: RhineCamera, val velocity: RhineCamera) {
    fun advance(seconds: Float, bounds: RhineBounds): RhineMotion {
        val decay = exp(-4.5f * seconds)
        val distance = (1f - decay) / 4.5f
        val raw = RhineCamera(camera.u + velocity.u * distance, camera.v + velocity.v * distance)
        val next = bounds.clamp(raw)
        return RhineMotion(
            next,
            RhineCamera(
                if (next.u != raw.u) 0f else velocity.u * decay,
                if (next.v != raw.v) 0f else velocity.v * decay,
            ),
        )
    }

    val moving
        get() = abs(velocity.u) > 0.35f || abs(velocity.v) > 0.05f
}

/** Hysteresis prevents the HUD and stable focus from toggling at the half-column point. */
fun centerColumn(v: Float, previous: Int, maxV: Int): Int =
    (if (abs(v - previous) > 0.60f) v.roundToInt() else previous).coerceIn(0, maxV)

/** Crossfade only the adjacent columns; at rest exactly one column retains the baseline lift. */
fun columnLiftWeight(v: Int, cameraV: Float): Float {
    val distance = abs(v - cameraV).coerceIn(0f, 1f)
    val t = 1f - distance
    return t * t * (3f - 2f * t)
}

/** Chunked query bounds add bounded lookahead and stay unchanged over several animation frames. */
fun SlotWindow.queryWindow(): SlotWindow {
    fun low(n: Int, chunk: Int) = floor(n.toFloat() / chunk).toInt() * chunk
    return SlotWindow(low(minU, 8) - 8, low(maxU, 8) + 15, low(minV, 2) - 2, low(maxV, 2) + 3)
}

/** IDs survive layout size/density changes: neither pixels nor the query window own focus. */
data class RhineFocus(val categoryId: Long?, val itemId: Long?, val u: Int, val v: Int)

fun RhineSceneInput.focus() =
    slot(focusU.roundToInt(), focusV).let { RhineFocus(it.column?.id, it.item?.id, it.u, it.v) }

fun RhineFocus.resolve(columns: List<RhineColumn>): RhineCamera {
    val v = columns.indexOfFirst { it.id == categoryId }.takeIf { it >= 0 } ?: v
    val u = columns.getOrNull(v)?.items?.values?.firstOrNull { it.id == itemId }?.index ?: u
    return RhineBounds.from(columns.map { it.itemCount })
        .clamp(RhineCamera(u.toFloat(), v.toFloat()))
}

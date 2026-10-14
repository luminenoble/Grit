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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor

/**
 * Default block geometry for the Rhine scene. Lengths are dp; the screen basis is dimensionless
 * (screen px per world px).
 *
 * World axes: U runs along a column (index 0 is the column start), V crosses columns, Z points up.
 * [basisU] has negative x and y so a column grows from the lower right towards the upper left.
 */
@Immutable
data class RhineGeometry(
    /** Block extent along V (the long side carrying the titles). */
    val blockLength: Float = 207f,
    /** Block extent along U. */
    val blockDepth: Float = 18f,
    /** Seam along U; also how much of a long face shows above a same-height neighbour. */
    val gapU: Float = 16f,
    val gapV: Float = 12f,
    /** Height of every non-center block. */
    val baseHeight: Float = 16f,
    /** Uniform lift of the whole center column; keeps its lowest block above the others. */
    val centerLift: Float = 24f,
    /** Extra height of the single peak at the focus. */
    val peakLift: Float = 70f,
    /** Gaussian spread of the peak, in U slots; wide enough to read as one raised wall. */
    val peakSpread: Float = 4.5f,
    /**
     * Landscape basis matching the reference: long side rises ~29° to the right, a column runs ~17°
     * towards the upper left, vertical faces stay vertical.
     */
    val basisU: Offset = Offset(-0.959f, -0.282f),
    val basisV: Offset = Offset(0.875f, -0.485f),
    val basisZ: Offset = Offset(0f, -1f),
    /**
     * Where the focused block's floor centre lands, as a fraction of the scene size. Left of centre
     * so the focus panel on the right does not cover the wall.
     */
    val anchor: Offset = Offset(0.34f, 0.60f),
) {
    init {
        require(blockLength > 0f && blockDepth > 0f && baseHeight > 0f)
        require(gapU >= 0f && gapV >= 0f && centerLift > 0f && peakLift >= 0f && peakSpread > 0f)
        require(basisU.x < 0f && basisU.y < 0f) { "U must run from lower right to upper left" }
    }

    val maxHeight: Float
        get() = baseHeight + centerLift + peakLift

    /** Block height in dp. Only [centerV] rises; one peak at [focusU]. */
    fun heightOf(u: Int, v: Int, centerV: Int, focusU: Float): Float {
        if (v != centerV) return baseHeight
        val d = u - focusU
        return baseHeight + centerLift + peakLift * exp(-(d * d) / (2f * peakSpread * peakSpread))
    }
}

/** Fractional slot coordinates of the camera. Integer values centre on a block. */
@Immutable data class RhineCamera(val u: Float, val v: Float)

/**
 * Single projection shared by drawing, hit testing and (later) gestures and bounds.
 *
 * World coordinates are px: a slot (u, v) occupies U in `[u*pitchU, u*pitchU + depth]` and V in
 * `[v*pitchV, v*pitchV + length]`.
 */
class RhineProjection(
    val geometry: RhineGeometry,
    val density: Float,
    val size: Size,
    val camera: RhineCamera,
) {
    val depth = geometry.blockDepth * density
    val length = geometry.blockLength * density
    val pitchU = (geometry.blockDepth + geometry.gapU) * density
    val pitchV = (geometry.blockLength + geometry.gapV) * density

    private val eU = geometry.basisU
    private val eV = geometry.basisV
    private val eZ = geometry.basisZ
    private val anchor = Offset(size.width * geometry.anchor.x, size.height * geometry.anchor.y)
    private val camU = camera.u * pitchU + depth / 2f
    private val camV = camera.v * pitchV + length / 2f
    private val det = eU.x * eV.y - eU.y * eV.x

    init {
        require(det != 0f) { "basisU and basisV must not be parallel" }
    }

    /**
     * Rising by 1 px looks the same as moving [slopeU] px along +U and [slopeV] px along +V, so a
     * raised point hides floor points further along both axes. A face at the start of a slot shows
     * `gap / slope` px above a same-height neighbour in front of it.
     */
    val slopeU = (eZ.x * eV.y - eZ.y * eV.x) / det
    val slopeV = (eU.x * eZ.y - eU.y * eZ.x) / det

    /** Screen point of world (wu, wv) px at height z px. */
    fun project(wu: Float, wv: Float, z: Float): Offset =
        Offset(
            anchor.x + (wu - camU) * eU.x + (wv - camV) * eV.x + z * eZ.x,
            anchor.y + (wu - camU) * eU.y + (wv - camV) * eV.y + z * eZ.y,
        )

    /** Inverse of [project] on the plane at height z, returning world (wu, wv) px. */
    fun unproject(point: Offset, z: Float): Offset {
        val px = point.x - anchor.x - z * eZ.x
        val py = point.y - anchor.y - z * eZ.y
        val a = (px * eV.y - py * eV.x) / det
        val b = (eU.x * py - eU.y * px) / det
        return Offset(camU + a, camV + b)
    }

    /**
     * Slots that may touch the screen, from the screen corners unprojected at the floor and at the
     * tallest block, plus [margin] slots for shadows. Grows with the viewport, never with data.
     */
    fun visibleSlots(margin: Int = 1, extraHeight: Float = 9f): SlotWindow {
        var minU = Float.MAX_VALUE
        var maxU = -Float.MAX_VALUE
        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        val corners =
            listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), size.toOffset())
        for (z in floatArrayOf(0f, (geometry.maxHeight + extraHeight) * density)) {
            for (corner in corners) {
                val w = unproject(corner, z)
                minU = minOf(minU, w.x / pitchU)
                maxU = maxOf(maxU, w.x / pitchU)
                minV = minOf(minV, w.y / pitchV)
                maxV = maxOf(maxV, w.y / pitchV)
            }
        }
        return SlotWindow(
            floor(minU).toInt() - margin,
            ceil(maxU + (geometry.maxHeight + extraHeight) * density * 0.45f / pitchU).toInt() +
                margin,
            floor(minV).toInt() - margin,
            ceil(maxV + (geometry.maxHeight + extraHeight) * density * 0.225f / pitchV).toInt() +
                margin,
        )
    }

    private fun Size.toOffset() = Offset(width, height)
}

/** Inclusive slot ranges. */
@Immutable
data class SlotWindow(val minU: Int, val maxU: Int, val minV: Int, val maxV: Int) {
    val count: Int
        get() = (maxU - minU + 1) * (maxV - minV + 1)
}

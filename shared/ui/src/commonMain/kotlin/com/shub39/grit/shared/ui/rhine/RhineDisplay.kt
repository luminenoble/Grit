package com.shub39.grit.shared.ui.rhine

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.sp
import com.shub39.grit.core.settings.*

/** Viewport goals scale geometry once in dp. fontScale belongs exclusively to sp text. */
fun RhineDisplayConfig.geometry(widthDp: Float, heightDp: Float): RhineGeometry {
    val c = normalized()
    val custom = c.density == RhineDensity.Custom
    val scaleU = if (custom) 1f else (heightDp / (1080f / 2.625f)) * (12f / c.targetRows)
    val scaleV = if (custom) 1f else (widthDp / (2400f / 2.625f)) * (3f / c.targetColumns)
    return RhineGeometry(
        blockLength = if (custom) c.length else (207f * scaleV).coerceIn(100f, 360f),
        blockDepth = if (custom) c.depth else (18f * scaleU).coerceIn(12f, 64f),
        gapU = if (custom) c.gapU else (16f * scaleU).coerceIn(8f, 40f),
        gapV = if (custom) c.gapV else (12f * scaleV).coerceIn(6f, 40f),
        baseHeight = c.base,
        centerLift = c.center,
        peakLift = c.peak,
        peakSpread = c.spread,
    )
}

fun RhineDisplayConfig.style() =
    RhineStyle(topTextSize = normalized().topSp.sp, sideTextSize = normalized().sideSp.sp)

fun RhineDisplayConfig.itemNumbers(layer: RhineLayer) =
    if (layer == RhineLayer.Categories) taskNumbers else sectionNumbers

fun RhineDisplayConfig.parentNumbers(layer: RhineLayer) =
    if (layer == RhineLayer.Categories) categoryNumbers else taskNumbers

/** Actual intersecting top faces, including partial edge blocks and non-business decoration. */
fun visibleFaceCounts(
    input: RhineSceneInput,
    geometry: RhineGeometry,
    density: Float,
    size: Size,
): Pair<Int, Int> {
    val blocks =
        layoutScene(
                input,
                RhineProjection(geometry, density, size, RhineCamera(input.focusU, input.cameraV)),
            )
            .blocks
            .filter {
                it.top.maxOf { p -> p.x } >= 0 &&
                    it.top.minOf { p -> p.x } <= size.width &&
                    it.top.maxOf { p -> p.y } >= 0 &&
                    it.top.minOf { p -> p.y } <= size.height
            }
    return blocks.count { it.slot.v == input.focusV } to blocks.map { it.slot.v }.distinct().size
}

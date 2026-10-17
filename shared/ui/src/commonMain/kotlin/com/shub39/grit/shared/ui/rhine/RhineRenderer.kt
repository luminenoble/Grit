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

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import kotlin.math.sqrt

/** Labels drawn on the faces. Kept separate from geometry so strings can be localized. */
data class RhineLabels(
    val zeroTop: (RhineSlot) -> String,
    val addParentTop: String,
    val sideLabel: (RhineColumn) -> String,
)

fun DrawScope.drawRhineScene(
    layout: RhineLayout,
    input: RhineSceneInput,
    style: RhineStyle,
    labels: RhineLabels,
    measurer: TextMeasurer,
    display: com.shub39.grit.core.settings.RhineDisplayConfig =
        com.shub39.grit.core.settings.RhineDisplayConfig(),
    titlePhase: Float = 0f,
    facePath: Path = Path(),
    contextAlpha: Float = 1f,
    raisedAlpha: Float = 1f,
    /** Decoded illustration for a block (slot, raised), when it has one. */
    illustration: (RhineSlot, Boolean) -> ImageBitmap? = { _, _ -> null },
) {
    drawRect(style.floor)
    val d = layout.projection.density
    val pad = 8f * d
    val raised = layout.raised
    fun drawOne(block: RhineBlock) {
        val center = centrality(block)
        // A block floating off the floor has no contact shadow.
        if (display.shadow != com.shub39.grit.core.settings.RhineShadow.Off && block.base == 0f)
            drawShadow(
                block,
                style,
                center,
                display.shadow == com.shub39.grit.core.settings.RhineShadow.Low,
                facePath,
            )
        drawBlock(block, style, center, facePath)
        val slot = block.slot
        val focused = slot.v == input.focusV && slot.u.toFloat() == input.focusU

        // Side face: the column label, and below it the illustration where one applies.
        val column = slot.column
        val isRaised = block === raised
        val image =
            if (showsIllustration(slot, isRaised)) illustration(slot, isRaised) else null
        val area = image?.let { illustrationArea(block.frontFrame, style, pad) }
        if (
            column != null &&
                (slot.showsSideLabel ||
                    (display.placeholderNames && slot.kind == RhineBlockKind.Placeholder))
        ) {
            drawFaceText(
                frame =
                    if (area != null) block.frontFrame.copy(height = area.top - 3f * d)
                    else block.frontFrame,
                text = labels.sideLabel(column),
                color =
                    style.readableFace(
                        column.titleColor,
                        style.frontAt(center),
                        style.frontLowAt(center),
                    ),
                size = style.sideTextSize,
                bold = false,
                pad = pad,
                measurer = measurer,
                scrollSeconds = if (focused && display.scrollFocusedTitle) titlePhase else 0f,
            )
        }

        if (image != null && area != null)
            drawFaceImage(
                block.frontFrame,
                image,
                area,
                display.illustrationFit,
                style.frontAt(center),
                style.frontLowAt(center),
                style.edge,
            )

        // Top face: item index and title.
        val top =
            when (slot.kind) {
                RhineBlockKind.Real ->
                    slot.item?.let {
                        (if (display.itemNumbers(input.layer)) indexLabel(it.index) + "  "
                        else "") + it.title
                    }
                RhineBlockKind.Zero -> labels.zeroTop(slot)
                RhineBlockKind.AddParent -> if (slot.u == 0) labels.addParentTop else null
                RhineBlockKind.Placeholder -> if (display.placeholderNames) column?.title else null
                else -> null
            }
        if (top != null) {
            val color =
                when (slot.kind) {
                    RhineBlockKind.Real ->
                        style.readableFace(slot.item?.titleColor, style.top, style.topFar)
                    RhineBlockKind.Zero ->
                        style.readableFace(column?.titleColor, style.top, style.topFar)
                    else -> style.mutedText
                }
            drawFaceText(
                frame = block.topFrame,
                text = top,
                color = color,
                size = style.topTextSize,
                bold = slot.kind == RhineBlockKind.Zero || focused,
                pad = pad,
                measurer = measurer,
                trailingMark = slot.item?.isCompleted == true,
                scrollSeconds = if (focused && display.scrollFocusedTitle) titlePhase else 0f,
            )
        }
        if (focused)
            drawPolygon(
                block.top,
                style.shadow.copy(alpha = 0.55f),
                stroke = 1.2f * d,
                path = facePath,
            )
    }
    // Context blocks fade as one layer so overlapping faces never show through each other.
    withAlphaLayer(contextAlpha) { for (block in layout.blocks) drawOne(block) }
    if (raised != null && layout.raisedClear) withAlphaLayer(raisedAlpha) { drawOne(raised) }
}

private inline fun DrawScope.withAlphaLayer(alpha: Float, draw: () -> Unit) {
    if (alpha <= 0f) return
    if (alpha >= 1f) {
        draw()
        return
    }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(
            androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
            androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha },
        )
        draw()
        canvas.restore()
    }
}

/**
 * How close a block sits to the screen centre: 1 at the centre, easing to 0 at the borders. The
 * horizontal axis is weighted less so the gradient follows the wide landscape frame.
 */
private fun DrawScope.centrality(block: RhineBlock): Float {
    val c = block.top.reduce { a, b -> a + b } / 4f
    val dx = (c.x - size.width / 2f) / (size.width * 0.6f)
    val dy = (c.y - size.height / 2f) / (size.height * 0.6f)
    val t = (1f - sqrt(dx * dx + dy * dy)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun DrawScope.drawShadow(
    block: RhineBlock,
    style: RhineStyle,
    center: Float,
    low: Boolean,
    path: Path,
) {
    // Two stacked translucent layers read as a soft contact shadow without a blur pass.
    val alpha = style.shadowAlphaAt(center)
    block.shadow.toPath(path)
    drawPath(path, style.shadow.copy(alpha = alpha * 0.45f))
    if (low) return
    val inner =
        block.shadow.mapIndexed { i, p ->
            if (i < 2) lerpOffset(p, block.shadow[3 - i], 0.45f) else p
        }
    drawPath(inner.toPath(path), style.shadow.copy(alpha = alpha * 0.55f))
}

private fun DrawScope.drawBlock(block: RhineBlock, style: RhineStyle, center: Float, path: Path) {
    val d = density
    val front = block.front
    drawPath(
        front.toPath(path),
        Brush.linearGradient(
            listOf(style.frontAt(center), style.frontLowAt(center)),
            start = front[3],
            end = front[0],
        ),
    )
    val cap = block.cap
    drawPath(block.cap.toPath(path), style.cap)
    // Round fitting on the end face, a fixed distance below the top edge.
    val r = 1.8f * d
    if (block.height > r * 5f) {
        val c = lerpOffset(cap[3], cap[2], 0.5f) + (cap[0] - cap[3]) * (5f * d / block.height)
        drawCircle(style.marker, radius = r, center = c)
    }
    val top = block.top
    drawPath(
        top.toPath(path),
        Brush.linearGradient(listOf(style.top, style.topFar), start = top[0], end = top[3]),
    )
    // Hard creases: every visible edge in the darker edge colour, then a thin highlight set just
    // inside the near top edge, so the slats read with crisp corners as in the reference.
    val crease = 0.9f * d
    drawPolygon(front, style.edge, stroke = crease, path = path)
    drawPolygon(cap, style.edge, stroke = crease, path = path)
    drawPolygon(top, style.edge, stroke = crease, path = path)
    val across = top[3] - top[0]
    val inset = across * (1.3f * d / across.getDistance().coerceAtLeast(1f))
    drawLine(style.highlight, top[0] + inset, top[1] + inset, strokeWidth = 1.1f * d)
}

/** Draws one line of text in a face's local frame, clipped to the face minus [pad]. */
private fun DrawScope.drawFaceText(
    frame: FaceFrame,
    text: String,
    color: Color,
    size: TextUnit,
    bold: Boolean,
    pad: Float,
    measurer: TextMeasurer,
    trailingMark: Boolean = false,
    scrollSeconds: Float = 0f,
) {
    val markSpace = if (trailingMark) 14f * density else 0f
    // Reserve equal space on both sides so a completion mark never shifts the title's centre.
    val maxWidth = (frame.width - pad * 2 - markSpace * 2).toInt()
    if (maxWidth <= 0) return
    var result = measureRhineFaceText(measurer, text, color, size, bold, maxWidth)
    // Faces shorter than the text line show nothing rather than a sliver of glyphs. Slats are thin,
    // so allow the line box to touch the face edges; the glyphs themselves sit inside it.
    if (result.size.height * 0.8f > frame.height) {
        result =
            measureRhineFaceText(
                measurer,
                text,
                color,
                size * (frame.height / (result.size.height * 0.8f)),
                bold,
                maxWidth,
            )
    }
    val lineSize = result.layoutInput.style.fontSize
    val natural =
        if (scrollSeconds > 0f)
            measurer.measure(
                text,
                TextStyle(
                    color = color,
                    fontSize = lineSize,
                    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                ),
                maxLines = 1,
                softWrap = false,
            )
        else null
    val scrolling = natural != null && natural.size.width > maxWidth
    if (scrolling) result = natural!!
    withTransform({ transform(frame.matrix()) }) {
        clipRect(pad * 0.5f, 0f, frame.width - pad * 0.5f, frame.height) {
            val y = (frame.height - result.size.height) / 2f
            val overflow = (result.size.width - maxWidth).coerceAtLeast(0)
            val travel = overflow / (28f * density)
            val cycle = 2f * travel + 2f
            val time = if (cycle > 0) scrollSeconds % cycle else 0f
            val fraction =
                when {
                    time < 1f -> 0f
                    time < 1f + travel -> (time - 1f) / travel
                    time < 2f + travel -> 1f
                    else -> 1f - (time - 2f - travel) / travel
                }
            val x =
                if (scrolling) pad + markSpace - overflow * fraction
                else (frame.width - result.size.width) / 2f
            clipRect(pad + markSpace, 0f, frame.width - pad - markSpace, frame.height) {
                drawText(result, topLeft = Offset(x, y))
            }
            if (trailingMark) {
                val r = 4f * density
                val c = Offset(frame.width - pad - r, frame.height / 2f)
                drawCircle(color.copy(alpha = 0.75f), radius = r, center = c)
                drawLine(
                    Color.White,
                    c + Offset(-r * 0.5f, 0f),
                    c + Offset(-r * 0.1f, r * 0.4f),
                    strokeWidth = 1.2f * density,
                )
                drawLine(
                    Color.White,
                    c + Offset(-r * 0.1f, r * 0.4f),
                    c + Offset(r * 0.55f, -r * 0.4f),
                    strokeWidth = 1.2f * density,
                )
            }
        }
    }
}

/** Face-local to screen transform. */
private fun FaceFrame.matrix() =
    Matrix().apply {
        values[Matrix.ScaleX] = xAxis.x
        values[Matrix.SkewY] = xAxis.y
        values[Matrix.SkewX] = yAxis.x
        values[Matrix.ScaleY] = yAxis.y
        values[Matrix.TranslateX] = origin.x
        values[Matrix.TranslateY] = origin.y
    }

/**
 * Face-local area for an illustration under a one-line label band, with [pad] at the sides and
 * 3 dp above and below; null when the visible face is too small (under 18 × 48 dp).
 */
private fun DrawScope.illustrationArea(frame: FaceFrame, style: RhineStyle, pad: Float): Rect? {
    val gap = 3f * density
    val band = style.sideTextSize.toPx() * 1.6f + pad * 0.5f
    val area = Rect(pad, band + gap, frame.width - pad, frame.height - gap)
    return area.takeIf { it.height >= 18f * density && it.width >= 48f * density }
}

/** Draws [image] into [area] of a face, lit like the face and set into it by a fine edge. */
private fun DrawScope.drawFaceImage(
    frame: FaceFrame,
    image: ImageBitmap,
    area: Rect,
    fit: com.shub39.grit.core.settings.RhineImageFit,
    shadeTop: Color,
    shadeBottom: Color,
    edge: Color,
) {
    val (src, dst) = illustrationRects(IntSize(image.width, image.height), area, fit)
    val corner = CornerRadius(2f * density)
    withTransform({ transform(frame.matrix()) }) {
        clipPath(Path().apply { addRoundRect(RoundRect(area, corner)) }) {
            drawImage(
                image,
                srcOffset = src.first,
                srcSize = src.second,
                dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
                dstSize = IntSize(dst.width.roundToInt(), dst.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            drawRect(
                Brush.verticalGradient(listOf(shadeTop, shadeBottom), area.top, area.bottom),
                topLeft = area.topLeft,
                size = area.size,
                alpha = 0.18f,
            )
        }
        drawRoundRect(edge, area.topLeft, area.size, corner, style = Stroke(0.5f * density))
    }
}

/** Shared face text layout: single line, centred in the available width, with real ellipsis. */
fun measureRhineFaceText(
    measurer: TextMeasurer,
    text: String,
    color: Color,
    size: TextUnit,
    bold: Boolean,
    maxWidth: Int,
): TextLayoutResult =
    measurer.measure(
        text = text,
        style =
            TextStyle(
                color = color,
                fontSize = size,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        constraints = Constraints(minWidth = maxWidth, maxWidth = maxWidth),
    )

private fun DrawScope.drawPolygon(points: List<Offset>, color: Color, stroke: Float, path: Path) =
    drawPath(points.toPath(path), color, style = Stroke(width = stroke))

private fun List<Offset>.toPath(path: Path) =
    path.apply {
        rewind()
        moveTo(this@toPath[0].x, this@toPath[0].y)
        for (i in 1 until size) lineTo(this@toPath[i].x, this@toPath[i].y)
        close()
    }

private fun lerpOffset(a: Offset, b: Offset, t: Float) = a + (b - a) * t

fun indexLabel(index: Int) = index.toString().padStart(2, '0')

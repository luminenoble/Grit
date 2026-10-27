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
import androidx.compose.ui.graphics.TileMode
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

/**
 * Which blocks a draw covers. With transparent glass (docs/视觉优化/06) the scene is drawn twice: a
 * [Blurred] pass of everything but the selected block, blurred as a whole, and a [Sharp] pass of
 * the selected block alone on top. [All] draws the scene in one go.
 */
enum class RhinePass {
    All,
    Blurred,
    Sharp,
}

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
    raisedAlpha: Float = 1f,
    /** Decoded illustration for a block (slot, raised), when it has one. */
    illustration: (RhineSlot, Boolean) -> ImageBitmap? = { _, _ -> null },
    /** Fade-in (0..1) of the focused Task's whole illustration through the matte blocks in front. */
    reveal: Float = 0f,
    /** Transparent glass blocks instead of matte ones. */
    glass: Boolean = false,
    pass: RhinePass = RhinePass.All,
) {
    if (pass != RhinePass.Sharp) drawRect(style.floor)
    val d = layout.projection.density
    val pad = 8f * d
    val raised = layout.raised
    // Matte blocks hide the faces behind them, so the focused Task's whole picture is revealed by
    // turning the blocks in front of it into clear glass (docs/视觉优化/05). Glass needs none of
    // this: the selected block is drawn sharp over everything.
    val task =
        if (glass || reveal <= 0f) null
        else
            layout.blocks.firstOrNull {
                it !== raised &&
                    it.slot.kind == RhineBlockKind.Real &&
                    it.slot.v == input.focusV &&
                    it.slot.u.toFloat() == input.focusU &&
                    illustration(it.slot, false) != null
            }
    val opened = openedFaces(layout, style, pad, illustration, task, reveal)
    // Blocks in front of an opened face turn see-through instead of moving, so the arrangement
    // stays as it is: decorations before a column start (D11), and clear glass in front of the
    // focused Task.
    fun bodyAlpha(block: RhineBlock): Float {
        val slot = block.slot
        if (task != null && task.hides(block)) return 1f - (1f - ClearGlassAlpha) * reveal
        return if (
            slot.u < 0 &&
                opened.any {
                    it.block.slot.kind == RhineBlockKind.Zero && block.overlaps(it.block.front)
                }
        )
            SeeThroughAlpha
        else 1f
    }
    val glint = if (glass) glintBrush(style) else null
    fun drawOne(block: RhineBlock) {
        val center = centrality(block)
        val slot = block.slot
        val focused = slot.v == input.focusV && slot.u.toFloat() == input.focusU
        val column = slot.column
        val isRaised = block === raised
        val open = opened.firstOrNull { it.block === block }
        val face = if (open != null) block.frontFrame.copy(height = block.height) else block.frontFrame
        val image =
            open?.image
                ?: if (isRaised && showsIllustration(slot, true)) illustration(slot, true) else null
        val area = open?.area ?: image?.let { illustrationArea(face, style, pad) }

        // Body: shadow, faces and illustration. It may turn see-through; its text never fades.
        withAlphaLayer(bodyAlpha(block)) {
            // A block floating off the floor has no contact shadow.
            if (display.shadow != com.shub39.grit.core.settings.RhineShadow.Off && block.base == 0f)
                drawShadow(
                    block,
                    style,
                    center,
                    display.shadow == com.shub39.grit.core.settings.RhineShadow.Low,
                    facePath,
                )
            if (glass) drawGlassBlock(block, style, facePath)
            else drawBlock(block, style, center, facePath)
            if (image != null && area != null)
                drawFaceImage(
                    face,
                    image,
                    area,
                    display.illustrationFit,
                    if (glass) style.glassRim else style.frontAt(center),
                    if (glass) style.glassCap else style.frontLowAt(center),
                    if (glass) style.glassEdge else style.edge,
                    open?.alpha ?: 1f,
                )
            // The light lies on the glass, over the picture.
            if (glint != null) drawGlassLight(block, style, glint, facePath)
        }

        // Side face: the column label, above the illustration where one applies. Light glass takes
        // the dark default ink, the matte face the light one.
        if (
            column != null &&
                (slot.showsSideLabel ||
                    (display.placeholderNames && slot.kind == RhineBlockKind.Placeholder))
        ) {
            drawFaceText(
                frame = if (area != null) face.copy(height = area.top - 3f * d) else face,
                text = labels.sideLabel(column),
                colors =
                    style.titleColors(column.titleColor, if (glass) style.text else style.sideText),
                size = style.sideTextSize,
                bold = false,
                pad = pad,
                measurer = measurer,
                scrollSeconds = if (focused && display.scrollFocusedTitle) titlePhase else 0f,
            )
        }

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
            val colors =
                when (slot.kind) {
                    RhineBlockKind.Real -> style.titleColors(slot.item?.titleColor)
                    RhineBlockKind.Zero -> style.titleColors(column?.titleColor)
                    else -> listOf(style.mutedText)
                }
            drawFaceText(
                frame = block.topFrame,
                text = top,
                colors = colors,
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
    // How much of a block this pass draws: the selected block moves from the blurred pass to the
    // sharp one, the others stay blurred.
    fun share(block: RhineBlock): Float =
        when (pass) {
            RhinePass.All -> 1f
            RhinePass.Blurred -> 1f - sharpness(layout, input, block)
            RhinePass.Sharp -> sharpness(layout, input, block)
        }
    for (block in layout.blocks) withAlphaLayer(share(block)) { drawOne(block) }
    if (raised != null && layout.raisedClear)
        withAlphaLayer(raisedAlpha * share(raised)) { drawOne(raised) }
}

/**
 * How sharply [block] is drawn over the blurred scene (docs/视觉优化/06): 1 for the selected block,
 * which is the raised block while one is lifted and otherwise the block under the camera. As the
 * camera travels, sharpness passes from one block to the next within [SharpReach] of a slot.
 */
fun sharpness(layout: RhineLayout, input: RhineSceneInput, block: RhineBlock): Float {
    val raised = layout.raised
    if (raised != null) return if (block === raised) 1f else 0f
    val du = kotlin.math.abs(block.slot.u - input.focusU)
    val dv = kotlin.math.abs(block.slot.v - input.cameraV)
    return (1f - du / SharpReach).coerceIn(0f, 1f) * (1f - dv / SharpReach).coerceIn(0f, 1f)
}

/** Camera travel, in slots, over which a block loses its sharpness. */
private const val SharpReach = 0.6f

/** A long face drawn at full height with its illustration, faded in by [alpha]. */
private class OpenFace(
    val block: RhineBlock,
    val image: ImageBitmap,
    val area: Rect,
    val alpha: Float,
)

/**
 * Every block with an illustration opens its whole long face (docs/视觉优化/07): column starts and,
 * on the first layer, the Tasks. While the focused [task] is revealed, the faces standing in front
 * of it fade their own picture out as its picture comes through, by [reveal].
 */
private fun DrawScope.openedFaces(
    layout: RhineLayout,
    style: RhineStyle,
    pad: Float,
    illustration: (RhineSlot, Boolean) -> ImageBitmap?,
    task: RhineBlock?,
    reveal: Float,
): List<OpenFace> {
    val opened = ArrayList<OpenFace>()
    for (block in layout.blocks) {
        if (block === layout.raised || !showsIllustration(block.slot, false)) continue
        val alpha = if (task != null && task.hides(block)) 1f - reveal else 1f
        if (alpha <= 0f) continue
        val image = illustration(block.slot, false) ?: continue
        val area = illustrationArea(block.frontFrame.copy(height = block.height), style, pad)
        if (area != null) opened += OpenFace(block, image, area, alpha)
    }
    return opened
}

/** Whether [block] stands in front of this one's long face, in the same column. */
private fun RhineBlock.hides(block: RhineBlock): Boolean =
    block.slot.v == slot.v && block.slot.u < slot.u && block.overlaps(front)

/** Opacity of a decoration block standing in front of an illustrated column start. */
private const val SeeThroughAlpha = 0.28f

/** Opacity of the matte blocks in front of the focused Task's illustration once it is shown. */
private const val ClearGlassAlpha = 0.3f

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

/**
 * A transparent glass block (docs/视觉优化/06, 07): every face is a clear veil, so blocks behind and
 * their pictures show through, with a fine crease along each edge. Over the veil lies a thin-film
 * sheen: pastel bands running along each face, whose phase follows the block's place on screen, so
 * they slide from block to block as the camera moves, like light on a turning disc.
 */
private fun DrawScope.drawGlassBlock(block: RhineBlock, style: RhineStyle, path: Path) {
    val d = density
    val front = block.front
    // Brighter along the lit top edge, clearer towards the foot.
    drawPath(
        front.toPath(path),
        Brush.linearGradient(
            listOf(style.glassFront, style.glassFrontLow),
            start = front[3],
            end = front[0],
        ),
    )
    val cap = block.cap
    drawPath(
        cap.toPath(path),
        Brush.linearGradient(
            listOf(style.glassCap, style.glassCap.copy(alpha = style.glassCap.alpha * 0.6f)),
            start = cap[3],
            end = cap[0],
        ),
    )
    val top = block.top
    drawPath(
        top.toPath(path),
        Brush.linearGradient(
            listOf(style.glassTop, style.glassTopFar),
            start = top[0],
            end = top[3],
        ),
    )
    val phase = top[0].x / size.width * 1.4f + top[0].y / size.height * 0.6f
    fun film(face: List<Offset>, from: Offset, to: Offset, alpha: Float) {
        val run = (to - from) * SheenPeriod
        val start = from - run * phase
        drawPath(
            face.toPath(path),
            Brush.linearGradient(
                *style.sheen.toTypedArray(),
                start = start,
                end = start + run,
                tileMode = TileMode.Mirror,
            ),
            alpha = alpha,
        )
    }
    // Along the long face and the top, down the end face.
    film(front, front[3], front[2], 1f)
    film(top, top[0], top[1], 0.5f)
    film(cap, cap[3], cap[0], 0.7f)
    val crease = 0.8f * d
    drawPolygon(front, style.glassEdge, stroke = crease, path = path)
    drawPolygon(cap, style.glassEdge, stroke = crease, path = path)
    drawPolygon(top, style.glassEdge, stroke = crease, path = path)
}

/** Length of one run of the sheen's bands, as a fraction of the face edge it follows. */
private const val SheenPeriod = 0.85f

/**
 * A window reflection fixed on screen: a soft and a sharp steep band left of the centre, which the
 * glass blocks pass through as the camera moves.
 */
private fun DrawScope.glintBrush(style: RhineStyle): Brush {
    val reach = maxOf(size.width, size.height)
    val clear = style.glint.copy(alpha = 0f)
    return Brush.linearGradient(
        0f to clear,
        0.26f to clear,
        0.33f to style.glint.copy(alpha = style.glint.alpha * 0.4f),
        0.40f to clear,
        0.43f to clear,
        0.445f to style.glint,
        0.46f to clear,
        1f to clear,
        start = Offset.Zero,
        // Bands at about 65°, steeper than the long faces climb.
        end = Offset(reach * 0.906f, reach * 0.423f),
    )
}

/**
 * Light on the glass, drawn over the face picture: the window [glint] on every face, the edges facing
 * the light as a fine white rim, and a prismatic line just inside the long face's top edge.
 */
private fun DrawScope.drawGlassLight(block: RhineBlock, style: RhineStyle, glint: Brush, path: Path) {
    val d = density
    val front = block.front
    val cap = block.cap
    val top = block.top
    drawPath(front.toPath(path), glint)
    drawPath(top.toPath(path), glint, alpha = 0.6f)
    drawPath(cap.toPath(path), glint, alpha = 0.7f)
    // Rim light: the near top edge, the top of the end face and a softer line down the front's
    // leading edge.
    val rim = 1.1f * d
    drawLine(style.glassRim, top[0], top[1], strokeWidth = rim)
    drawLine(style.glassRim.copy(alpha = 0.7f), cap[3], cap[2], strokeWidth = rim * 0.8f)
    drawLine(style.glassRim.copy(alpha = 0.45f), front[3], front[0], strokeWidth = rim * 0.7f)
    val down = front[0] - front[3]
    val inset = down * (1.8f * d / down.getDistance().coerceAtLeast(1f))
    drawLine(
        Brush.linearGradient(style.prism, front[3], front[2]),
        front[3] + inset,
        front[2] + inset,
        strokeWidth = 0.9f * d,
    )
}

/**
 * Draws one line of text in a face's local frame, clipped to the face minus [pad]. Several [colors]
 * run as a gradient across the glyphs, left to right.
 */
private fun DrawScope.drawFaceText(
    frame: FaceFrame,
    text: String,
    colors: List<Color>,
    size: TextUnit,
    bold: Boolean,
    pad: Float,
    measurer: TextMeasurer,
    trailingMark: Boolean = false,
    scrollSeconds: Float = 0f,
) {
    val color = colors.first()
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
                if (colors.size == 1) drawText(result, topLeft = Offset(x, y))
                else
                    drawText(
                        result,
                        Brush.horizontalGradient(
                            colors,
                            startX = result.getLineLeft(0),
                            endX = result.getLineRight(0),
                        ),
                        topLeft = Offset(x, y),
                    )
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
    alpha: Float = 1f,
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
                alpha = alpha,
                filterQuality = FilterQuality.Medium,
            )
            drawRect(
                Brush.verticalGradient(listOf(shadeTop, shadeBottom), area.top, area.bottom),
                topLeft = area.topLeft,
                size = area.size,
                alpha = 0.18f * alpha,
            )
        }
        drawRoundRect(
            edge,
            area.topLeft,
            area.size,
            corner,
            style = Stroke(0.5f * density),
            alpha = alpha,
        )
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

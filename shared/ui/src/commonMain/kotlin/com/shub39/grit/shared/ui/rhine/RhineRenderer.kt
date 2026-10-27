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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
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
    val zeroTitle: (RhineSlot) -> String,
    val addParentTitle: String,
)

/**
 * Which part of the scene a draw covers. Matte blocks are drawn [All] at once, or as a [Context] of
 * everything but the selected block and the [Selected] block alone (the editor lays its veil
 * between them). Frosted glass (docs/Rhine.md §7.1) adds a [Backdrop]: what light passes through,
 * which the caller blurs as a whole. Its [Context] and [Selected] are sharp surfaces in which each
 * block first clears whatever it covers, so the backdrop shows only through the glass and every
 * outline stays crisp; the caller draws them into a layer of their own, over the backdrop.
 */
enum class RhinePass {
    All,
    Backdrop,
    Context,
    Selected,
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
    /** Frosted glass blocks instead of matte ones. */
    glass: Boolean = false,
    pass: RhinePass = RhinePass.All,
    /** Fine grain tiled over glass surfaces, from [rhineGrain]. */
    grain: ImageBitmap? = null,
) {
    if (pass != RhinePass.Selected) drawRect(style.floor)
    val d = layout.projection.density
    val pad = 8f * d
    val raised = layout.raised
    // Matte blocks hide the faces behind them, so the focused Task's whole picture is revealed by
    // turning the blocks in front of it into clear glass (docs/Rhine.md §7.2). Glass needs none of
    // this: the selected block is drawn over everything.
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
    val surface = glass && pass != RhinePass.Backdrop
    val grainBrush =
        if (surface && grain != null)
            ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))
        else null
    val band = titleBand(style, pad)
    val shadows = display.shadow != com.shub39.grit.core.settings.RhineShadow.Off
    val lowShadow = display.shadow == com.shub39.grit.core.settings.RhineShadow.Low

    fun drawOne(block: RhineBlock) {
        val center = centrality(block)
        val slot = block.slot
        val focused = slot.v == input.focusV && slot.u.toFloat() == input.focusU
        val column = slot.column
        val isRaised = block === raised
        val open = opened.firstOrNull { it.block === block }
        // On glass the selected block lies over the blocks in front of it, so all of it shows.
        val whole = open != null || (glass && pass == RhinePass.Selected)
        val face = if (whole) block.frontFrame.copy(height = block.height) else block.frontFrame
        val image =
            open?.image
                ?: if (isRaised && showsIllustration(slot, true)) illustration(slot, true) else null
        val area = open?.area ?: image?.let { illustrationArea(face, style, pad) }
        val tint = slot.tintColors()
        val tintWeight = if (slot.kind == RhineBlockKind.Placeholder) 0.5f else 1f

        // Body: shadow, faces and illustration. It may turn see-through; its text never fades.
        withAlphaLayer(bodyAlpha(block)) {
            when {
                pass == RhinePass.Backdrop ->
                    drawBackdropBlock(block, style, tint, tintWeight, facePath)
                glass -> drawFrostedBlock(block, style, tint, tintWeight, grainBrush, facePath)
                else -> {
                    // A block floating off the floor has no contact shadow.
                    if (shadows && block.base == 0f)
                        drawShadow(block, style, center, lowShadow, facePath)
                    drawBlock(block, style, center, tint, tintWeight, facePath)
                }
            }
            if (image != null && area != null)
                drawFaceImage(
                    face,
                    image,
                    area,
                    display.illustrationFit,
                    if (glass) style.rim else style.frontAt(center),
                    if (glass) style.backdropCap else style.frontLowAt(center),
                    if (glass) style.glassEdge else style.edge,
                    open?.alpha ?: 1f,
                    veil =
                        if (surface) style.imageVeil * (1f - sharpness(layout, input, block))
                        else 0f,
                )
            if (surface) drawGlassEdges(block, style, tint, facePath)
        }
        // Text is printed on the surface; light passing through the glass carries none of it.
        if (pass == RhinePass.Backdrop) return

        // Long face: index and title above the picture. Glass and the light matte top take the
        // dark default ink, the dark matte long face the light one.
        val title =
            when (slot.kind) {
                RhineBlockKind.Real ->
                    slot.item?.let {
                        (if (display.itemNumbers(input.layer)) indexLabel(it.index) + "  "
                        else "") + it.title
                    }
                RhineBlockKind.Zero -> labels.zeroTitle(slot)
                RhineBlockKind.AddParent -> if (slot.u == 0) labels.addParentTitle else null
                RhineBlockKind.Placeholder -> if (display.placeholderNames) column?.title else null
                else -> null
            }
        if (title != null) {
            val ink = if (glass) style.text else style.sideText
            val colors =
                when (slot.kind) {
                    RhineBlockKind.Real -> style.titleColors(slot.item?.titleColor, ink)
                    RhineBlockKind.Zero -> style.titleColors(column?.titleColor, ink)
                    else -> listOf(if (glass) style.mutedText else ink.copy(alpha = 0.7f))
                }
            drawFaceText(
                frame = face.copy(height = minOf(band, face.height)),
                text = title,
                colors = colors,
                size = style.titleTextSize,
                bold = slot.kind == RhineBlockKind.Zero || focused,
                pad = pad,
                measurer = measurer,
                trailingMark = slot.item?.isCompleted == true,
                scrollSeconds = if (focused && display.scrollFocusedTitle) titlePhase else 0f,
            )
        }
        if (focused) drawPolygon(block.front, style.focusLine, stroke = 1.2f * d, path = facePath)
    }

    val blocks = if (raised != null && layout.raisedClear) layout.blocks + raised else layout.blocks
    // On glass every contact shadow lies on the floor first, so the blocks standing over it,
    // clearing what they cover, hide it.
    if (glass && shadows && pass != RhinePass.Selected)
        for (block in blocks) {
            if (block.base == 0f)
                drawShadow(block, style, centrality(block), lowShadow, facePath, bodyAlpha(block))
        }
    fun draw(block: RhineBlock, share: Float) {
        val alpha = share * if (block === raised && layout.raisedClear) raisedAlpha else 1f
        if (alpha <= 0f) return
        // Clearing must reach the surface layer itself, never a layer of its own.
        if (surface) clearBlock(block, alpha * bodyAlpha(block), facePath)
        withAlphaLayer(alpha) { drawOne(block) }
    }
    // The selected block moves from the context to the selection as the camera reaches it.
    when (pass) {
        RhinePass.All -> for (block in blocks) draw(block, 1f)
        RhinePass.Context -> for (block in blocks) draw(block, 1f - sharpness(layout, input, block))
        RhinePass.Selected -> for (block in blocks) draw(block, sharpness(layout, input, block))
        RhinePass.Backdrop -> {
            for (block in blocks) draw(block, 1f - sharpness(layout, input, block))
            for (block in blocks) draw(block, sharpness(layout, input, block))
        }
    }
}

/**
 * How fully [block] counts as selected (docs/Rhine.md §7.1): 1 for the selected block, which is the
 * raised block while one is lifted and otherwise the block under the camera. As the camera travels,
 * the selection passes from one block to the next within [SharpReach] of a slot.
 */
fun sharpness(layout: RhineLayout, input: RhineSceneInput, block: RhineBlock): Float {
    val raised = layout.raised
    if (raised != null) return if (block === raised) 1f else 0f
    val du = kotlin.math.abs(block.slot.u - input.focusU)
    val dv = kotlin.math.abs(block.slot.v - input.cameraV)
    return (1f - du / SharpReach).coerceIn(0f, 1f) * (1f - dv / SharpReach).coerceIn(0f, 1f)
}

/** Camera travel, in slots, over which a block loses its selection. */
private const val SharpReach = 0.6f

/** A long face drawn at full height with its illustration, faded in by [alpha]. */
private class OpenFace(
    val block: RhineBlock,
    val image: ImageBitmap,
    val area: Rect,
    val alpha: Float,
)

/**
 * Every block with an illustration opens its whole long face (docs/Rhine.md §9): column starts and,
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
    opacity: Float = 1f,
) {
    // Two stacked translucent layers read as a soft contact shadow without a blur pass.
    val alpha = style.shadowAlphaAt(center) * opacity
    block.shadow.toPath(path)
    drawPath(path, style.shadow.copy(alpha = alpha * 0.45f))
    if (low) return
    val inner =
        block.shadow.mapIndexed { i, p ->
            if (i < 2) lerpOffset(p, block.shadow[3 - i], 0.45f) else p
        }
    drawPath(inner.toPath(path), style.shadow.copy(alpha = alpha * 0.55f))
}

/**
 * The column colour spread evenly over a face (docs/Rhine.md §7.3): one colour, or a gradient along
 * the block's length, left to right as the title reads, in the face's local [frame].
 */
private fun DrawScope.tintFace(frame: FaceFrame, colors: List<Color>, alpha: Float) =
    withTransform({ transform(frame.matrix()) }) {
        drawRect(
            if (colors.size == 1) SolidColor(colors[0])
            else Brush.horizontalGradient(colors, 0f, frame.width),
            size = Size(frame.width, frame.height),
            alpha = alpha,
        )
    }

/** Every face of a block in its column colour; the end face sits at the gradient's start. */
private fun DrawScope.tintBlock(block: RhineBlock, colors: List<Color>, alpha: Float, path: Path) {
    tintFace(block.frontFrame.copy(height = block.height), colors, alpha)
    tintFace(block.topFrame, colors, alpha)
    drawPath(block.cap.toPath(path), colors.first(), alpha = alpha)
}

private fun DrawScope.drawBlock(
    block: RhineBlock,
    style: RhineStyle,
    center: Float,
    tint: List<Color>?,
    tintWeight: Float,
    path: Path,
) {
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
    if (tint != null) tintBlock(block, tint, style.tint * tintWeight, path)
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
 * What light passes through a frosted block (docs/Rhine.md §7.1): translucent faces in the column
 * colour, denser than the surface since it is seen through more than one pane. Blurred as a whole
 * by the caller; its pictures are drawn on top by the scene, its text is not.
 */
private fun DrawScope.drawBackdropBlock(
    block: RhineBlock,
    style: RhineStyle,
    tint: List<Color>?,
    tintWeight: Float,
    path: Path,
) {
    drawPath(block.front.toPath(path), style.backdropFront)
    drawPath(block.top.toPath(path), style.backdropTop)
    drawPath(block.cap.toPath(path), style.backdropCap)
    if (tint != null) tintBlock(block, tint, style.backdropTint * tintWeight, path)
}

/**
 * The sharp surface of a frosted block (docs/Rhine.md §7.1), over the cleared backdrop: the column
 * colour, a milky white brightest on the top, a soft light from above and some shade at the foot of
 * the long face, and a fine grain.
 */
private fun DrawScope.drawFrostedBlock(
    block: RhineBlock,
    style: RhineStyle,
    tint: List<Color>?,
    tintWeight: Float,
    grain: Brush?,
    path: Path,
) {
    val d = density
    if (tint != null) tintBlock(block, tint, style.tint * tintWeight, path)
    drawPath(block.front.toPath(path), style.frostFront)
    if (grain != null && style.grain > 0f) drawPath(path, grain, alpha = style.grain)
    drawPath(block.top.toPath(path), style.frostTop)
    drawPath(block.cap.toPath(path), style.frostCap)
    drawPath(path, style.capShade)
    val face = block.frontFrame.copy(height = block.height)
    withTransform({ transform(face.matrix()) }) {
        val light = minOf(face.height, 34f * d)
        drawRect(
            Brush.verticalGradient(listOf(style.frontLight, Color.Transparent), 0f, light),
            size = Size(face.width, light),
        )
        val foot = minOf(face.height, 6f * d)
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, style.footShade),
                face.height - foot,
                face.height,
            ),
            topLeft = Offset(0f, face.height - foot),
            size = Size(face.width, foot),
        )
    }
}

/**
 * Crisp edges of a frosted block: a fine outline in a darkened column colour, the lit crease where
 * the long face meets the top, and fainter light along the far top edge, the top of the end face
 * and the leading edge.
 */
private fun DrawScope.drawGlassEdges(
    block: RhineBlock,
    style: RhineStyle,
    tint: List<Color>?,
    path: Path,
) {
    val d = density
    val outline =
        if (tint == null) style.glassEdge
        else lerp(tint.average(), style.edgeInk, 0.55f).copy(alpha = style.glassEdge.alpha)
    drawPath(block.outline(path), outline, style = Stroke(0.55f * d))
    val top = block.top
    val front = block.front
    drawLine(style.rim, top[0], top[1], strokeWidth = 0.9f * d)
    drawLine(style.rim.copy(alpha = 0.55f), top[3], top[2], strokeWidth = 0.6f * d)
    drawLine(style.rim.copy(alpha = 0.75f), top[0], top[3], strokeWidth = 0.7f * d)
    drawLine(style.rim.copy(alpha = 0.45f), front[3], front[0], strokeWidth = 0.6f * d)
}

/** Clears what a frosted block covers from the surface layer, so the backdrop shows through it. */
private fun DrawScope.clearBlock(block: RhineBlock, alpha: Float, path: Path) =
    drawPath(block.outline(path), Color.Black, alpha = alpha, blendMode = BlendMode.DstOut)

/** The block's three visible faces as one path. */
private fun RhineBlock.outline(path: Path) =
    path.apply {
        rewind()
        for (face in listOf(front, top, cap)) {
            moveTo(face[0].x, face[0].y)
            for (i in 1 until face.size) lineTo(face[i].x, face[i].y)
            close()
        }
    }

private fun List<Color>.average(): Color =
    Color(
        sumOf { it.red.toDouble() }.toFloat() / size,
        sumOf { it.green.toDouble() }.toFloat() / size,
        sumOf { it.blue.toDouble() }.toFloat() / size,
    )

/**
 * Fine grain for frosted glass: light and dark specks of random strength, one per pixel, tiled over
 * the surfaces at a low opacity. Made once per scene.
 */
fun rhineGrain(size: Int = 64): ImageBitmap {
    val bitmap = ImageBitmap(size, size)
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    val paint = androidx.compose.ui.graphics.Paint()
    val random = kotlin.random.Random(7)
    for (y in 0 until size) for (x in 0 until size) {
        val strength = random.nextFloat()
        paint.color =
            if (random.nextBoolean()) Color.White.copy(alpha = strength)
            else Color(0xFF282828).copy(alpha = strength * 0.67f)
        canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
    }
    return bitmap
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

/** Height of the title band along the top of a long face. */
private fun DrawScope.titleBand(style: RhineStyle, pad: Float) =
    style.titleTextSize.toPx() * 1.6f + pad * 0.5f

/**
 * Face-local area for an illustration under the title band, with [pad] at the sides and 3 dp above
 * and below; null when the visible face is too small (under 18 × 48 dp).
 */
private fun DrawScope.illustrationArea(frame: FaceFrame, style: RhineStyle, pad: Float): Rect? {
    val gap = 3f * density
    val band = titleBand(style, pad)
    val area = Rect(pad, band + gap, frame.width - pad, frame.height - gap)
    return area.takeIf { it.height >= 18f * density && it.width >= 48f * density }
}

/**
 * Draws [image] into [area] of a face, lit like the face and set into it by a fine edge. On frosted
 * glass that is not selected, a thin white [veil] lies over it.
 */
private fun DrawScope.drawFaceImage(
    frame: FaceFrame,
    image: ImageBitmap,
    area: Rect,
    fit: com.shub39.grit.core.settings.RhineImageFit,
    shadeTop: Color,
    shadeBottom: Color,
    edge: Color,
    alpha: Float = 1f,
    veil: Float = 0f,
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
            if (veil > 0f) drawRect(Color.White, area.topLeft, area.size, alpha = veil * alpha)
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

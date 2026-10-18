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
package com.shub39.grit.shared.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shub39.grit.shared.ui.blurPossible
import kotlin.math.ceil

/**
 * Liquid glass material, see docs/视觉优化/01-全局液态玻璃设计系统.md.
 *
 * Glass belongs to the control and navigation layer floating above content. A surface is built from
 * up to six layers: the blurred backdrop (only where a [GlassBackdrop] is available, blur is
 * supported and transparency is not reduced), a warm tint, a top specular sheen, a light rim that
 * is brightest towards the top left, a faint outer hairline and a thin inner shadow along the
 * bottom. Without the backdrop the remaining layers still read as glass, just more opaque.
 */
enum class GlassTier {
    /** Small controls over content: nav pill, FAB, Rhine toolbar, toasts. */
    Thin,
    /** Menus, popovers, scrolled top bars, Rhine search/position overlays. */
    Regular,
    /** Dialogs, sheets and the Rhine editor panel. */
    Thick,
    /** Content such as cards and settings rows: opaque, keeps only the optical edge. */
    Content,
}

/** [Reduced] makes every tier opaque and disables backdrop blur ("降低透明度"). */
enum class GlassMode {
    Standard,
    Reduced,
}

@Immutable
data class GlassSpec(
    val tint: Color,
    val tintAlpha: Float,
    /** Tint opacity when there is no blurred backdrop behind the surface. */
    val fallbackAlpha: Float,
    val blur: Dp,
    val sheen: Float,
    val rimBright: Float,
    val rimFaint: Float,
    val elevation: Dp,
    val shadowAlpha: Float,
)

object GlassTokens {
    /** Warm paper white, a step lighter than the beige surfaces. */
    val Tint = Color(0xFFF4F0EA)
    val ThickTint = Color(0xFFF2EDE5)
    /** Warm brown shadow; grey shadows look dirty on beige. */
    val Shadow = Color(0xFF5B4B3A)
    val Hairline = Color(0xFF5B554B).copy(alpha = 0.10f)
    /** Secondary text on glass; onSurfaceVariant falls short of 4.5:1 over the Rhine faces. */
    val SecondaryText = Color(0xFF57534B)
    /** Destructive tinted glass. */
    val Danger = Color(0xFF8C3B32)
    /** A selected lens raised off its tray: brighter than the tray, carried by its shadow. */
    val Raised = Color(0xFFFFFDF9)
    /** Finished content settles into a deeper beige, as in the widget's done rows. */
    val Settled = Color(0xFFE3DCCE)
    /** Done marks: a muted olive that reads on beige without shouting. */
    val Done = Color(0xFF66704F)

    fun spec(tier: GlassTier): GlassSpec =
        when (tier) {
            GlassTier.Thin -> GlassSpec(Tint, 0.72f, 0.86f, 24.dp, 0.22f, 0.70f, 0.10f, 6.dp, 0.10f)
            GlassTier.Regular ->
                GlassSpec(Tint, 0.80f, 0.90f, 32.dp, 0.20f, 0.65f, 0.10f, 10.dp, 0.12f)
            GlassTier.Thick ->
                GlassSpec(ThickTint, 0.90f, 0.96f, 40.dp, 0.16f, 0.60f, 0.08f, 16.dp, 0.14f)
            GlassTier.Content ->
                GlassSpec(RhineBaseColors.surface, 1f, 1f, 0.dp, 0.10f, 0.55f, 0.06f, 3.dp, 0.08f)
        }
}

val LocalGlassMode = staticCompositionLocalOf { GlassMode.Standard }

object GlassShapes {
    val Capsule = RoundedCornerShape(percent = 50)
    val Toolbar = RoundedCornerShape(22.dp)
    val Panel = RoundedCornerShape(28.dp)
    val Card = RoundedCornerShape(16.dp)
}

/**
 * Content that glass surfaces may sample. The source records its drawing into a graphics layer each
 * time it draws and bumps [frame], so surfaces drawn later in the same frame see fresh content.
 */
@Stable
class GlassBackdrop {
    internal var layer: GraphicsLayer? = null
    internal var origin by mutableStateOf(Offset.Zero)
    internal var size = IntSize.Zero
    internal var frame by mutableIntStateOf(0)
}

@Composable fun rememberGlassBackdrop(): GlassBackdrop = remember { GlassBackdrop() }

/** Records this node's content as the backdrop for glass surfaces drawn above it. */
@Composable
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier {
    val layer = rememberGraphicsLayer()
    backdrop.layer = layer
    return onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            backdrop.size = IntSize(size.width.toInt(), size.height.toInt())
            // Must not observe its own counter: reading it here would invalidate this draw on
            // every write and redraw the source forever.
            Snapshot.withoutReadObservation { backdrop.frame += 1 }
            drawLayer(layer)
        }
}

/** Whether a backdrop blur would actually be drawn for the current mode and platform. */
@Composable
fun glassBlurActive(backdrop: GlassBackdrop?): Boolean =
    backdrop != null && LocalGlassMode.current == GlassMode.Standard && blurPossible()

/**
 * Draws the glass material of [tier] behind the content, clipped to [shape], plus a warm drop
 * shadow. [tint] replaces the tier tint for prominent (accent) or destructive glass; its alpha is
 * ignored in favour of the tier's.
 */
@Composable
fun Modifier.glassSurface(
    tier: GlassTier,
    shape: Shape,
    backdrop: GlassBackdrop? = null,
    tint: Color? = null,
    shadow: Boolean = true,
): Modifier {
    val spec = GlassTokens.spec(tier)
    val reduced = LocalGlassMode.current == GlassMode.Reduced
    val blur = glassBlurActive(backdrop) && tier != GlassTier.Content
    val alpha =
        when {
            tint != null -> 0.90f
            reduced -> 1f
            blur -> spec.tintAlpha
            else -> spec.fallbackAlpha
        }
    val fill = (tint ?: spec.tint).copy(alpha = alpha)
    val blurLayer = if (blur) rememberGraphicsLayer() else null
    var position by remember { mutableStateOf(Offset.Zero) }
    val base =
        if (shadow && spec.elevation > 0.dp)
            shadow(
                spec.elevation,
                shape,
                clip = false,
                ambientColor = GlassTokens.Shadow.copy(alpha = spec.shadowAlpha * 0.6f),
                spotColor = GlassTokens.Shadow.copy(alpha = spec.shadowAlpha),
            )
        else this
    return base
        .then(
            if (blurLayer != null) Modifier.onGloballyPositioned { position = it.positionInRoot() }
            else Modifier
        )
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            val sheen =
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = spec.sheen),
                    0.35f to Color.Transparent,
                    endY = size.height,
                )
            val rim =
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = spec.rimBright),
                    0.5f to Color.White.copy(alpha = spec.rimFaint),
                    1f to Color.White.copy(alpha = spec.rimBright * 0.45f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            val inner =
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    (1f - 1.5.dp.toPx() / size.height.coerceAtLeast(1f)) to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.05f),
                )
            val rimStroke = Stroke(width = 1.dp.toPx() * 2f) // the outer half is clipped away
            val hairline = Stroke(width = 0.5.dp.toPx())
            val radius = spec.blur.toPx()
            val margin = ceil(radius).toInt()
            blurLayer?.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
            onDrawBehind {
                val source = backdrop?.layer
                if (blurLayer != null && source != null) {
                    // Re-run when the source redraws.
                    backdrop.frame
                    val offset = position - backdrop.origin
                    blurLayer.record(
                        IntSize(size.width.toInt() + margin * 2, size.height.toInt() + margin * 2)
                    ) {
                        translate(margin - offset.x, margin - offset.y) { drawLayer(source) }
                    }
                    clipPath(clip) {
                        translate(-margin.toFloat(), -margin.toFloat()) { drawLayer(blurLayer) }
                    }
                }
                drawOutline(outline, fill)
                drawOutline(outline, sheen)
                drawOutline(outline, inner)
                clipPath(clip) { drawOutline(outline, rim, style = rimStroke) }
                drawOutline(outline, GlassTokens.Hairline, style = hairline)
            }
        }
}

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
package com.shub39.grit.shared.ui.components.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.shub39.grit.shared.ui.theme.GlassBackdrop
import com.shub39.grit.shared.ui.theme.GlassShapes
import com.shub39.grit.shared.ui.theme.GlassTier
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.GritMotion
import com.shub39.grit.shared.ui.theme.glassSurface
import grit.shared.ui.generated.resources.Res
import grit.shared.ui.generated.resources.check
import grit.shared.ui.generated.resources.close
import org.jetbrains.compose.resources.vectorResource

enum class GlassButtonStyle {
    Plain,
    /** Tinted glass for the primary action. */
    Prominent,
    Danger,
}

/** Capsule button on [GlassTier.Thin] glass with a springy press. */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: GlassButtonStyle = GlassButtonStyle.Plain,
    backdrop: GlassBackdrop? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, GritMotion.pressSpring())
    val tint =
        when (style) {
            GlassButtonStyle.Plain -> null
            GlassButtonStyle.Prominent -> MaterialTheme.colorScheme.primary
            GlassButtonStyle.Danger -> GlassTokens.Danger
        }
    val ink =
        when (style) {
            GlassButtonStyle.Plain -> MaterialTheme.colorScheme.onSurface
            GlassButtonStyle.Prominent -> MaterialTheme.colorScheme.onPrimary
            GlassButtonStyle.Danger -> Color.White
        }
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glassSurface(GlassTier.Thin, GlassShapes.Capsule, backdrop, tint)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 40.dp)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides ink.copy(alpha = if (enabled) 1f else 0.38f),
            content = { content() },
        )
    }
}

/** Groups controls on one [GlassTier.Thin] capsule sampling [backdrop]. */
@Composable
fun GlassToolbar(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.glassSurface(GlassTier.Thin, GlassShapes.Toolbar, backdrop).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun GlassToolbarDivider(height: Dp = 24.dp) {
    Box(
        Modifier.padding(horizontal = 4.dp)
            .width(0.5.dp)
            .height(height)
            .background(GlassTokens.Shadow.copy(alpha = 0.22f))
    )
}

/** Short status capsule, e.g. Rhine notices. Timing and placement stay with the caller. */
@Composable
fun GlassToast(
    text: String,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .glassSurface(GlassTier.Thin, GlassShapes.Capsule, backdrop)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            Modifier.weight(1f, fill = false),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke(this)
    }
}

/** Single choice on a glass capsule; a lens slides to the selected option. */
@Composable
fun <T> GlassSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val density = LocalDensity.current
    val index = options.indexOf(selected)
    val lens = bounds[index]
    val lensX by
        animateDpAsState(
            with(density) { (lens?.first ?: 0f).toDp() },
            MaterialTheme.motionScheme.fastSpatialSpec(),
        )
    val lensW by
        animateDpAsState(
            with(density) { (lens?.second ?: 0f).toDp() },
            MaterialTheme.motionScheme.fastSpatialSpec(),
        )
    Box(modifier.glassSurface(GlassTier.Thin, GlassShapes.Capsule).padding(4.dp)) {
        if (lens != null)
            Box(
                Modifier.offset(x = lensX)
                    .width(lensW)
                    .height(36.dp)
                    .glassSurface(
                        GlassTier.Content,
                        GlassShapes.Capsule,
                        tint = MaterialTheme.colorScheme.primaryContainer,
                    )
            )
        Row(Modifier.selectableGroup()) {
            options.forEachIndexed { i, option ->
                val chosen = i == index
                val ink by
                    animateColorAsState(
                        if (chosen) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                Box(
                    Modifier.onPlaced { bounds[i] = it.positionInParent().x to it.size.width.toFloat() }
                        .height(36.dp)
                        .selectable(
                            selected = chosen,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        color = ink.copy(alpha = if (enabled) 1f else 0.38f),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Switch with a recessed glass track and a glossy thumb; keeps [Role.Switch] semantics. */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val thumbX by
        animateDpAsState(if (checked) 22.dp else 2.dp, GritMotion.pressSpring())
    val track by
        animateColorAsState(
            if (checked) MaterialTheme.colorScheme.primary else Color(0xFFDCD4C7)
        )
    Box(
        modifier
            .size(52.dp, 32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
    ) {
        Canvas(Modifier.size(52.dp, 32.dp)) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(track, cornerRadius = r)
            // Recessed groove: a dark band along the top inner edge.
            drawRoundRect(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.10f),
                    0.35f to Color.Transparent,
                ),
                cornerRadius = r,
            )
        }
        Box(
            Modifier.offset(x = thumbX, y = 3.dp)
                .size(26.dp)
                .shadow(3.dp, CircleShape, spotColor = GlassTokens.Shadow)
                .background(
                    Brush.verticalGradient(listOf(Color.White, Color(0xFFF2EEE8))),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                vectorResource(if (checked) Res.drawable.check else Res.drawable.close),
                contentDescription = null,
                tint =
                    if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Material slider with a recessed groove and a glass knob. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val active = MaterialTheme.colorScheme.primary
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        thumb = {
            Box(
                Modifier.size(24.dp)
                    .glassSurface(GlassTier.Thin, CircleShape)
                    .padding(7.dp)
                    .background(active, CircleShape)
            )
        },
        track = { state: SliderState -> GlassSliderTrack(state, active) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassSliderTrack(state: SliderState, active: Color) {
    val range = state.valueRange
    val fraction =
        ((state.value - range.start) / (range.endInclusive - range.start).coerceAtLeast(1e-6f))
            .coerceIn(0f, 1f)
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(GlassTokens.Shadow.copy(alpha = 0.12f), cornerRadius = r)
        drawRoundRect(
            Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.10f), 0.6f to Color.Transparent),
            cornerRadius = r,
        )
        drawRoundRect(active, size = Size(size.width * fraction, size.height), cornerRadius = r)
    }
}

/** Material [AlertDialog] on thick glass; same parameters as the Material one. */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.glassSurface(GlassTier.Thick, GlassShapes.Panel),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = GlassShapes.Panel,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        properties = properties,
    )
}

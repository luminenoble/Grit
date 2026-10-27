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
package com.shub39.grit.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.shub39.grit.core.tasks.MaxTitleColorStops
import com.shub39.grit.shared.ui.components.glass.GlassButton
import com.shub39.grit.shared.ui.components.glass.GlassButtonStyle
import com.shub39.grit.shared.ui.components.glass.GlassSegmented
import com.shub39.grit.shared.ui.theme.GlassTier
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.RhineBaseColors
import com.shub39.grit.shared.ui.theme.parseTitleColors
import com.shub39.grit.shared.ui.theme.titleBrush
import com.shub39.grit.shared.ui.theme.titleColorValue
import com.shub39.grit.shared.ui.theme.toHexString
import com.shub39.grit.shared.ui.theme.withTitleColor
import kotlinx.coroutines.flow.first

/**
 * Last colour copied in the app. Copying also puts the value on the system clipboard as text, so it
 * survives a restart and can be pasted from elsewhere; this copy covers a clipboard that was since
 * replaced by something that is not a colour.
 */
private object TitleColorClipboard {
    var value by mutableStateOf<String?>(null)
}

/** Shown with each new gradient stop, skipping colours already used. */
private val StopPalette =
    listOf(
        Color(0xFFB4462F),
        Color(0xFFD08B2C),
        Color(0xFF6F8A3C),
        Color(0xFF2F7F86),
        Color(0xFF3E5FA8),
        Color(0xFF7A4C9A),
        Color(0xFF221F1A),
    )

/**
 * Title colour picker used app wide (no contrast adjustment: the colour shows exactly as chosen). A
 * solid colour or a left-to-right gradient of up to [MaxTitleColorStops] stops, edited on the wheel
 * or as RGB / hex values, with copy and paste for moving colours between objects. [onSelect] gets
 * the stored value; null restores the default ([default], shown when [value] is unset).
 */
@Composable
fun ColorPickerDialog(
    value: String?,
    default: Color,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    title: String = "文字颜色",
) {
    val initial = remember { parseTitleColors(value) ?: listOf(default) }
    val stops = remember { mutableStateListOf<Color>().apply { addAll(initial) } }
    var selected by remember { mutableIntStateOf(0) }
    var gradient by remember { mutableStateOf(initial.size > 1) }
    var notice by remember { mutableStateOf<String?>(null) }
    val controller = rememberColorPickerController()
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current

    val used = if (gradient) stops.toList() else stops.take(1)
    val result = titleColorValue(used)
    // Stop switches, typed values and pastes move the wheel; the wheel's own reports are stored
    // only when they come from the user.
    fun show(color: Color) = controller.selectByColor(color, fromUser = false)
    fun setCurrent(color: Color) {
        stops[selected] = color
        show(color)
    }
    fun select(i: Int) {
        selected = i
        show(stops[i])
    }
    fun setGradient(on: Boolean) {
        gradient = on
        if (on && stops.size < 2) stops += StopPalette.first { it !in stops }
        if (!on) select(0)
    }
    fun paste() {
        val text = runCatching { clipboard.getText()?.text?.trim() }.getOrNull()
        val colors = parseTitleColors(text) ?: parseTitleColors(TitleColorClipboard.value)
        if (colors == null) {
            notice = "剪贴板中没有颜色"
            return
        }
        stops.clear()
        stops.addAll(colors)
        gradient = colors.size > 1
        select(0)
        notice = "已粘贴"
    }

    // Opaque, so nothing behind the dialog tints the colours being judged. Up to the dialog's full
    // 560 dp rather than the platform width, so landscape gets wheel and fields side by side.
    GritDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(16.dp),
        tier = GlassTier.Content,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints {
            val wide = maxWidth >= 480.dp
            val wheelSize =
                if (wide) minOf(200.dp, maxHeight - 72.dp).coerceAtLeast(140.dp) else 220.dp

            val wheel: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HsvColorPicker(
                        modifier = Modifier.size(wheelSize),
                        controller = controller,
                        initialColor = initial.first(),
                        onColorChanged = { envelope ->
                            if (envelope.fromUser) stops[selected] = envelope.color.copy(alpha = 1f)
                        },
                    )
                    BrightnessSlider(
                        modifier = Modifier.padding(top = 10.dp).width(wheelSize).height(28.dp),
                        controller = controller,
                        initialColor = initial.first(),
                    )
                }
            }

            val controls: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        GlassSegmented(
                            options = listOf(false, true),
                            selected = gradient,
                            onSelect = ::setGradient,
                            label = { if (it) "渐变" else "纯色" },
                        )
                    }
                    ColorPreview(used)
                    if (gradient)
                        StopRow(
                            stops = stops,
                            selected = selected,
                            onSelect = ::select,
                            onAdd = {
                                stops += StopPalette.firstOrNull { it !in stops } ?: stops.last()
                                select(stops.lastIndex)
                            },
                            onRemove = {
                                stops.removeAt(selected)
                                select(selected.coerceAtMost(stops.lastIndex))
                            },
                        )
                    RgbFields(stops[selected], ::setCurrent)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassButton(
                            onClick = {
                                TitleColorClipboard.value = result
                                runCatching { clipboard.setText(AnnotatedString(result)) }
                                notice = "已复制 $result"
                            }
                        ) {
                            Text("复制")
                        }
                        GlassButton(onClick = ::paste) { Text("粘贴") }
                        notice?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassTokens.SecondaryText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Kept below the scrolling part, so they stay in reach on short landscape screens.
            val actions: @Composable () -> Unit = {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            onSelect(null)
                            onDismiss()
                        }
                    ) {
                        Text("默认")
                    }
                    TextButton(onClick = onDismiss) { Text("取消") }
                    GlassButton(
                        style = GlassButtonStyle.Prominent,
                        onClick = {
                            // An unset colour confirmed untouched stays the default.
                            val untouched = value == null && result == titleColorValue(initial)
                            onSelect(if (untouched) null else result)
                            onDismiss()
                        },
                    ) {
                        Text("完成")
                    }
                }
            }

            if (wide)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.align(Alignment.CenterVertically)) { wheel() }
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        ) {
                            controls()
                        }
                        actions()
                    }
                }
            else
                Column {
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        wheel()
                        controls()
                    }
                    actions()
                }
        }
    }
}

/** Sample text in the chosen colour on the app's light surface, with the plain colour band. */
@Composable
private fun ColorPreview(colors: List<Color>) {
    val value = titleColorValue(colors)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RhineBaseColors.surface)
            .border(0.5.dp, GlassTokens.Hairline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "示例文字 Aa 0123",
            style =
                MaterialTheme.typography.titleLarge
                    .copy(fontWeight = FontWeight.SemiBold)
                    .withTitleColor(value),
            maxLines = 1,
        )
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(titleBrush(value)))
        Text(
            value.replace(",", " → "),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = GlassTokens.SecondaryText,
            maxLines = 2,
        )
    }
}

/** Gradient stops left to right; the selected one is ringed and edited by the wheel and fields. */
@Composable
private fun StopRow(
    stops: List<Color>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        stops.forEachIndexed { i, color ->
            Box(
                Modifier.size(30.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(i) }
                    .border(
                        if (i == selected) 2.5.dp else 0.5.dp,
                        if (i == selected) MaterialTheme.colorScheme.onSurface
                        else GlassTokens.Hairline,
                        CircleShape,
                    )
                    .padding(if (i == selected) 4.dp else 0.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        if (stops.size < MaxTitleColorStops) TextButton(onClick = onAdd) { Text("+ 颜色") }
        if (stops.size > 2) TextButton(onClick = onRemove) { Text("移除") }
    }
}

/** R, G, B (0–255) and hex inputs for [color]; valid entries apply at once. */
@Composable
private fun RgbFields(color: Color, onChange: (Color) -> Unit) {
    val hex = color.toHexString()
    val rgb = hex.drop(1).toInt(16)
    val channels = listOf(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("R", "G", "B").forEachIndexed { i, label ->
            ColorField(
                initial = channels[i].toString(),
                key = hex,
                label = label,
                accept = { it.isDigit() },
                maxLength = 3,
                parse = { text ->
                    text
                        .toIntOrNull()
                        ?.takeIf { it in 0..255 }
                        ?.let { v ->
                            val next = channels.toMutableList().also { it[i] = v }
                            Color(next[0], next[1], next[2])
                        }
                },
                onChange = onChange,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        ColorField(
            initial = hex,
            key = hex,
            label = "HEX",
            accept = { it == '#' || it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' },
            maxLength = 7,
            parse = { text -> parseTitleColors("#" + text.removePrefix("#"))?.singleOrNull() },
            onChange = onChange,
            keyboardType = KeyboardType.Ascii,
            modifier = Modifier.weight(1.6f),
        )
    }
}

/**
 * One numeric field. Keeps what the user types (minus rejected characters) and applies it once
 * [parse] accepts it; a new [key] (the colour changed elsewhere) resets it, caret at the end.
 */
@Composable
private fun ColorField(
    initial: String,
    key: String,
    label: String,
    accept: (Char) -> Boolean,
    maxLength: Int,
    parse: (String) -> Color?,
    onChange: (Color) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier,
) {
    var field by
        remember(key) { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    OutlinedTextField(
        value = field,
        onValueChange = { input ->
            val text = input.text.filter(accept).take(maxLength)
            field =
                if (text == input.text) input
                else input.copy(text = text, selection = TextRange(text.length))
            // Caret moves report the same text; only edits apply.
            if (text != initial) parse(text)?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        isError = parse(field.text) == null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}

/**
 * Row showing a stored title colour: its swatch and [label] drawn in that colour. Opens
 * [ColorPickerDialog], which also returns to the default.
 */
@Composable
fun TitleColorRow(
    label: String,
    value: String?,
    default: Color,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var picking by remember { mutableStateOf(false) }
    var closed by remember { mutableIntStateOf(0) }
    val colors = parseTitleColors(value)
    // When the picker closes, a host dialog's window regains focus and hands it to its first text
    // field, opening the keyboard nobody asked for; release it once the window is back.
    val focusManager = LocalFocusManager.current
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(closed) {
        if (closed == 0) return@LaunchedEffect
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        withFrameNanos {}
        focusManager.clearFocus()
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { picking = true }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(32.dp)
                .clip(CircleShape)
                .background(titleBrush(value, default))
                .border(0.5.dp, GlassTokens.Hairline, CircleShape)
        )
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.withTitleColor(value, default),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    colors == null -> "默认"
                    colors.size == 1 -> colors[0].toHexString()
                    else -> "渐变 · ${colors.size} 色"
                },
                style = MaterialTheme.typography.labelSmall,
                color = GlassTokens.SecondaryText,
                maxLines = 1,
            )
        }
        // Back to the default is in the dialog, which keeps this row narrow enough for dialogs.
        TextButton(onClick = { picking = true }, enabled = enabled) { Text("选择") }
    }
    if (picking)
        ColorPickerDialog(
            value = value,
            default = default,
            onSelect = onChange,
            onDismiss = {
                picking = false
                closed++
            },
            title = label,
        )
}

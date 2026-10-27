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
package com.shub39.grit.widgets

import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan

/**
 * A Task title coloured for a widget (docs/项目概览.md §5). Glance text takes no gradient brush, so
 * every glyph gets its own colour from where its centre falls across the widest line, as the Task
 * page's gradient runs across the title. [stops] are ARGB colours; with fewer than two the title is
 * returned as it is and the caller sets the solid colour. Laid out with [paint] in [widthPx] over
 * at most [maxLines]; without a width the glyphs share the gradient evenly in reading order.
 */
fun gradientTitle(
    title: String,
    stops: List<Int>,
    paint: TextPaint,
    widthPx: Int,
    maxLines: Int = 2,
): CharSequence {
    if (stops.size < 2 || title.isEmpty()) return title
    val layout =
        if (widthPx <= 0) null
        else
            StaticLayout.Builder.obtain(title, 0, title.length, paint, widthPx)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
    val widest = layout?.let { l -> (0 until l.lineCount).maxOf { l.getLineWidth(it) } } ?: 0f
    val glyphs = title.codePointCount(0, title.length)
    val text = SpannableString(title)
    var start = 0
    var index = 0
    while (start < title.length) {
        // Whole code points, so a surrogate pair keeps one colour.
        val end = title.offsetByCodePoints(start, 1)
        val t =
            if (layout != null && widest > 0f) {
                val line = layout.getLineForOffset(start)
                val x =
                    layout.getPrimaryHorizontal(start) - layout.getLineLeft(line) +
                        paint.measureText(title, start, end) / 2f
                x / widest
            } else if (glyphs > 1) index / (glyphs - 1f) else 0f
        text.setSpan(
            ForegroundColorSpan(colorAt(stops, t)),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        start = end
        index++
    }
    return text
}

/** The colour at [t] (0..1) of a gradient through evenly spaced ARGB [stops]. */
fun colorAt(stops: List<Int>, t: Float): Int {
    if (stops.size == 1) return stops[0]
    val x = t.coerceIn(0f, 1f) * (stops.size - 1)
    val i = x.toInt().coerceAtMost(stops.size - 2)
    val f = x - i
    val a = stops[i]
    val b = stops[i + 1]
    fun channel(shift: Int): Int {
        val ca = (a ushr shift) and 0xFF
        val cb = (b ushr shift) and 0xFF
        return (ca + (cb - ca) * f + 0.5f).toInt().coerceIn(0, 255) shl shift
    }
    return channel(24) or channel(16) or channel(8) or channel(0)
}

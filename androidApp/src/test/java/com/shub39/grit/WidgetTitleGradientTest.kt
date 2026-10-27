package com.shub39.grit

import com.shub39.grit.widgets.colorAt
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTitleGradientTest {
    private val red = 0xFFC0392B.toInt()
    private val blue = 0xFF1E6FD9.toInt()

    @Test
    fun endsTakeTheOuterStopsAndTheMiddleBlends() {
        assertEquals(red, colorAt(listOf(red, blue), 0f))
        assertEquals(blue, colorAt(listOf(red, blue), 1f))
        assertEquals(0xFF6F5482.toInt(), colorAt(listOf(red, blue), 0.5f))
        // Out-of-range positions (a glyph past the widest line) clamp to the ends.
        assertEquals(red, colorAt(listOf(red, blue), -0.2f))
        assertEquals(blue, colorAt(listOf(red, blue), 1.4f))
    }

    @Test
    fun stopsAreEvenlySpaced() {
        val green = 0xFF2E8B57.toInt()
        assertEquals(green, colorAt(listOf(red, green, blue), 0.5f))
        assertEquals(red, colorAt(listOf(red), 0.7f))
    }
}

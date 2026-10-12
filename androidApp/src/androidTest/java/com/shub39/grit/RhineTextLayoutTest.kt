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
package com.shub39.grit

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.shub39.grit.shared.ui.rhine.measureRhineFaceText
import org.junit.Assert.*
import org.junit.Test

/** Uses Android's real font and paragraph implementation, including Chinese glyphs. */
class RhineTextLayoutTest {
    private fun measurer(): TextMeasurer {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(context),
            defaultDensity = Density(2.625f),
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
    }

    @Test
    fun shortChineseAndEnglishLabelsAreCenteredOnOneLine() {
        val measurer = measurer()
        for (text in listOf("02  工作", "03  Compose", "00  空类别")) {
            val result = measureRhineFaceText(measurer, text, Color.Black, 14.sp, false, 420)
            assertEquals(1, result.lineCount)
            assertFalse(result.isLineEllipsized(0))
            assertEquals(420, result.size.width)
            assertEquals(
                result.size.width / 2f,
                (result.getLineLeft(0) + result.getLineRight(0)) / 2f,
                1f,
            )
        }
    }

    @Test
    fun longChineseAndEnglishLabelsUseOneLineEllipsis() {
        val measurer = measurer()
        for (text in
            listOf(
                "01  为毕业论文整理全部实验数据并重新绘制所有对比图表",
                "04  English long title for clipping check on narrow faces",
            )) {
            for (width in listOf(120, 420)) {
                val result = measureRhineFaceText(measurer, text, Color.Black, 14.sp, true, width)
                assertEquals(1, result.lineCount)
                assertTrue("$text, width=$width", result.isLineEllipsized(0))
                assertEquals(width, result.size.width)
                assertTrue(result.getLineLeft(0) >= 0f)
                assertTrue(result.getLineRight(0) <= width)
            }
        }
    }
}

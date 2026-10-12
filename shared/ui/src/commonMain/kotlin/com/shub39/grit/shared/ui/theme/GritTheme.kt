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

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.shub39.grit.core.theme.Theme
import com.shub39.grit.shared.ui.toFontRes

val RhineBaseColors =
    lightColorScheme(
        primary = Color(0xFF5B554B),
        onPrimary = Color(0xFFFFFBF4),
        primaryContainer = Color(0xFFE2D9C9),
        onPrimaryContainer = Color(0xFF332F29),
        secondary = Color(0xFF686154),
        secondaryContainer = Color(0xFFE6DECF),
        onSecondaryContainer = Color(0xFF3C3A36),
        tertiary = Color(0xFF626A5D),
        tertiaryContainer = Color(0xFFE0E3D6),
        onTertiaryContainer = Color(0xFF33382F),
        background = Color(0xFFECE8E1),
        onBackground = Color(0xFF3C3A36),
        surface = Color(0xFFF0EBE2),
        onSurface = Color(0xFF3C3A36),
        onSurfaceVariant = Color(0xFF625F58),
        surfaceContainer = Color(0xFFE9E3D9),
        surfaceContainerLow = Color(0xFFF2EDE5),
        surfaceContainerHigh = Color(0xFFE3DCCE),
        surfaceContainerHighest = Color(0xFFDED7CB),
        outline = Color(0xFF928B80),
        outlineVariant = Color(0xFFC9C1B4),
    )

@Composable
fun GritTheme(theme: Theme = Theme(), content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = RhineBaseColors,
        motionScheme = MotionScheme.expressive(),
        typography = provideTypography(theme.font.toFontRes()),
        content = content,
    )
}

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
package com.shub39.grit.shared.ui

import com.shub39.grit.core.settings.WidgetTextSize
import com.shub39.grit.core.theme.Fonts
import grit.shared.ui.generated.resources.*
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.StringResource

/** Used in UI Buttons */
fun Fonts.toDisplayString(): String {
    return when (this) {
        POPPINS -> "Poppins"
        INTER -> "Inter"
        MANROPE -> "Manrope"
        MONTSERRAT -> "Montserrat"
        FIGTREE -> "Figtree"
        OUTFIT -> "Outfit"
        GOOGLE_SANS -> "Google Sans"
        SYSTEM_DEFAULT -> "System Default"
    }
}

/** Enum to [FontResource] used internally */
fun Fonts.toFontRes(): FontResource? {
    return when (this) {
        POPPINS -> Res.font.poppins_regular
        INTER -> Res.font.inter
        MANROPE -> Res.font.manrope
        MONTSERRAT -> Res.font.montserrat
        FIGTREE -> Res.font.figtree
        OUTFIT -> Res.font.outfit
        GOOGLE_SANS -> Res.font.google_sans_flex
        SYSTEM_DEFAULT -> null
    }
}

fun WidgetTextSize.toStringRes(): StringResource {
    return when (this) {
        SMALL -> Res.string.text_size_small
        MEDIUM -> Res.string.text_size_medium
        LARGE -> Res.string.text_size_large
    }
}

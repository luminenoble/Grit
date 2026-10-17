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

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring

/** Shared timing and easing; Rhine transitions take their curves from here as well. */
object GritMotion {
    /** Entering, fading in. */
    val EaseStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Fading out, dismissing. */
    val EaseExit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    /** Camera rotation, descent. */
    val EaseInOutCubic: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    /** Lifting. */
    val EaseOutCubic: Easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

    const val FadeShort = 150
    const val FadeMedium = 250

    fun <T> pressSpring() = spring<T>(dampingRatio = 0.75f, stiffness = 700f)
}

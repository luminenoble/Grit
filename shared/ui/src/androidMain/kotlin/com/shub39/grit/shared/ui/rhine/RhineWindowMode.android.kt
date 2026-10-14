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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
actual fun RhineWindowMode() {
    val activity = LocalContext.current.findActivity() ?: return
    // Saved so the orientation from before Rhine survives the recreation caused by rotating.
    val original = rememberSaveable { activity.requestedOrientation }
    DisposableEffect(activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller =
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            // A rotation recreates the activity while Rhine stays on screen; keep landscape then.
            if (!activity.isChangingConfigurations) activity.requestedOrientation = original
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
actual fun rhineSystemAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    DisposableEffect(context) {
        fun refresh() {
            enabled =
                listOf(
                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                        android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE,
                        android.provider.Settings.Global.WINDOW_ANIMATION_SCALE,
                    )
                    .all {
                        android.provider.Settings.Global.getFloat(context.contentResolver, it, 1f) >
                            0f
                    }
        }
        val observer =
            object :
                android.database.ContentObserver(
                    android.os.Handler(android.os.Looper.getMainLooper())
                ) {
                override fun onChange(selfChange: Boolean) {
                    refresh()
                }
            }
        listOf(
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE,
                android.provider.Settings.Global.WINDOW_ANIMATION_SCALE,
            )
            .forEach {
                context.contentResolver.registerContentObserver(
                    android.provider.Settings.Global.getUriFor(it),
                    false,
                    observer,
                )
            }
        refresh()
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return enabled
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
actual fun rhineSafeDrawingInsets(): WindowInsets =
    WindowInsets.safeDrawing
        .union(WindowInsets.statusBarsIgnoringVisibility)
        .union(WindowInsets.navigationBarsIgnoringVisibility)

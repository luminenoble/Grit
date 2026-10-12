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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Camera and motion live independently of the Canvas and the HUD. All movement shares clamp. */
class RhineBrowser(initial: RhineCamera = RhineCamera(0f, 0f)) {
    var camera by mutableStateOf(initial)
        private set

    var center by mutableStateOf(initial.v.roundToInt())
        private set

    private var boundsReady = false
    var bounds by mutableStateOf(RhineBounds(0, 0))
        private set

    var focus by mutableStateOf(RhineFocus(null, null, initial.u.roundToInt(), center))
        private set

    var interacting by mutableStateOf(false)
        private set

    var reconciling by mutableStateOf(false)
    var motion: Job? = null
    var restoreEpoch by mutableIntStateOf(0)
        private set

    fun updateBounds(value: RhineBounds) {
        val keepIdentity = focusMatchesCamera
        if (boundsReady && (value.maxU < bounds.maxU || value.maxV < bounds.maxV)) stop()
        boundsReady = true
        bounds = value
        move(camera)
        if (keepIdentity) focus = focus.copy(u = camera.u.roundToInt(), v = center)
    }

    fun move(value: RhineCamera) {
        camera = bounds.clamp(value)
        center = centerColumn(camera.v, center, bounds.maxV)
    }

    fun rememberFocus(input: RhineSceneInput) {
        if (reconciling) return
        val current = input.focus()
        // Parent catalogs/windows can also arrive in different frames.
        if (
            focus.categoryId != null &&
                current.categoryId != focus.categoryId &&
                current.v == focus.v
        )
            return
        // A replacement window must not replace the focused ID before its summary resolves.
        focus =
            if (
                focus.itemId != null &&
                    current.itemId != focus.itemId &&
                    current.u == focus.u &&
                    current.categoryId == focus.categoryId
            )
                current.copy(itemId = focus.itemId)
            else current
    }

    val focusMatchesCamera: Boolean
        get() =
            !reconciling && !interacting && camera.u.roundToInt() == focus.u && center == focus.v

    /** A repository update follows identity even before the replacement window is available. */
    fun follow(value: RhineCamera, parentId: Long? = focus.categoryId) {
        move(value)
        focus = focus.copy(categoryId = parentId, u = camera.u.roundToInt(), v = center)
    }

    /** Explicit taps choose the hit ID; automatic window refreshes retain existing identity. */
    fun select(slot: RhineSlot) {
        focus = RhineFocus(slot.column?.id, slot.item?.id, slot.u, slot.v)
    }

    fun snapshot() = RhineSnapshot(camera, focus)

    fun restore(snapshot: RhineSnapshot) {
        stop()
        camera = snapshot.camera
        center = camera.v.roundToInt()
        focus = snapshot.focus
        restoreEpoch += 1
    }

    fun forgetItem() {
        focus = focus.copy(itemId = null)
    }

    fun begin() {
        motion?.cancel()
        interacting = true
    }

    fun stop() {
        motion?.cancel()
        interacting = false
        if (boundsReady) move(bounds.snap(camera))
    }

    suspend fun settle(velocity: RhineCamera = RhineCamera(0f, 0f)) {
        try {
            var state = RhineMotion(camera, velocity)
            var last = withFrameNanos { it }
            while (state.moving) {
                val now = withFrameNanos { it }
                state = state.advance(((now - last) / 1_000_000_000f).coerceAtMost(0.05f), bounds)
                move(state.camera)
                last = now
            }
            animateTo(bounds.snap(camera))
        } finally {
            if (currentCoroutineContext().isActive) interacting = false
        }
    }

    suspend fun animateTo(destination: RhineCamera) {
        interacting = true
        try {
            val start = camera
            val target = bounds.snap(destination)
            if (target == start) return
            Animatable(0f).animateTo(1f, tween(240, easing = FastOutSlowInEasing)) {
                move(
                    RhineCamera(
                        start.u + (target.u - start.u) * value,
                        start.v + (target.v - start.v) * value,
                    )
                )
            }
            move(target)
        } finally {
            if (currentCoroutineContext().isActive) interacting = false
        }
    }

    companion object {
        val saver =
            Saver<RhineBrowser, List<String>>(
                save = {
                    listOf(
                        it.camera.u.toString(),
                        it.camera.v.toString(),
                        it.focus.categoryId?.toString() ?: "",
                        it.focus.itemId?.toString() ?: "",
                    )
                },
                restore = { values ->
                    RhineBrowser(RhineCamera(values[0].toFloat(), values[1].toFloat())).apply {
                        focus =
                            RhineFocus(
                                values[2].toLongOrNull(),
                                values[3].toLongOrNull(),
                                camera.u.roundToInt(),
                                center,
                            )
                    }
                },
            )
    }
}

/** Lifecycle owns subscriptions and animation. Queries change by bounded chunks, not frames. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun rememberRhineInput(
    source: RhineDataSource,
    browser: RhineBrowser,
    geometry: RhineGeometry,
    density: Float,
    size: Size,
): RhineSceneInput {
    var columns by remember(source) { mutableStateOf<List<RhineColumn>>(emptyList()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(source, browser, lifecycle, geometry, density, size) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                coroutineScope {
                    val catalogFlow = source.catalog().distinctUntilChanged()
                    var activeCatalog: RhineCatalog? = null
                    var trackedItem: com.shub39.grit.core.tasks.TaskSummary? = null
                    launch {
                        catalogFlow
                            .flatMapLatest { catalog ->
                                activeCatalog = catalog
                                val id = browser.focus.categoryId
                                val saved = browser.snapshot()
                                browser.updateBounds(catalog.bounds)
                                if (
                                    id != null &&
                                        !browser.interacting &&
                                        !browser.reconciling &&
                                        catalog.categories.none { it.id == id }
                                )
                                    browser.restore(
                                        RhineSnapshot(
                                            saved.resolve(catalog, null),
                                            RhineFocus(null, null, saved.focus.u, saved.focus.v),
                                        )
                                    )
                                catalog.categories
                                    .indexOfFirst { it.id == id }
                                    .takeIf { it >= 0 }
                                    ?.let { v ->
                                        if (browser.focusMatchesCamera)
                                            browser.follow(browser.camera.copy(v = v.toFloat()))
                                    }
                                trackedItem
                                    ?.takeIf { it.id == browser.focus.itemId }
                                    ?.let { item ->
                                        val v =
                                            catalog.categories.indexOfFirst {
                                                it.id == item.categoryId
                                            }
                                        if (v >= 0 && browser.focusMatchesCamera)
                                            browser.follow(
                                                RhineCamera(
                                                    (item.orderIndex + 1).toFloat(),
                                                    v.toFloat(),
                                                ),
                                                item.categoryId,
                                            )
                                    }
                                snapshotFlow {
                                        RhineProjection(geometry, density, size, browser.camera)
                                            .visibleSlots(margin = 2)
                                            .queryWindow()
                                    }
                                    .distinctUntilChanged()
                                    .flatMapLatest { source.window(catalog, it) }
                            }
                            .collect { columns = it }
                    }
                    // A single summary subscription follows the stable focused ID across
                    // reorder/move.
                    launch {
                        snapshotFlow {
                                browser.focus.itemId to
                                    (browser.interacting to browser.restoreEpoch)
                            }
                            .distinctUntilChanged()
                            .flatMapLatest { (id, _) ->
                                if (id == null) flowOf(null to null)
                                else source.item(id).map { id to it }
                            }
                            .collect { (id, task) ->
                                if (id != browser.focus.itemId) return@collect
                                trackedItem = task
                                if (
                                    task == null &&
                                        browser.focus.itemId != null &&
                                        !browser.interacting &&
                                        !browser.reconciling
                                ) {
                                    if (browser.focusMatchesCamera)
                                        activeCatalog?.let {
                                            browser.follow(browser.snapshot().resolve(it, null))
                                        }
                                    browser.forgetItem()
                                }
                                if (
                                    task != null &&
                                        browser.focusMatchesCamera &&
                                        task.id == browser.focus.itemId
                                ) {
                                    val v =
                                        activeCatalog?.categories?.indexOfFirst {
                                            it.id == task.categoryId
                                        } ?: -1
                                    if (v >= 0)
                                        browser.follow(
                                            RhineCamera(
                                                (task.orderIndex + 1).toFloat(),
                                                v.toFloat(),
                                            ),
                                            task.categoryId,
                                        )
                                }
                            }
                    }
                }
            } finally {
                browser.stop()
            }
        }
    }
    val input =
        RhineSceneInput(source.layer, columns, browser.center, browser.camera.u, browser.camera.v)
    LaunchedEffect(columns, browser.camera, browser.interacting) {
        if (!browser.interacting && columns.isNotEmpty()) browser.rememberFocus(input)
    }
    return input
}

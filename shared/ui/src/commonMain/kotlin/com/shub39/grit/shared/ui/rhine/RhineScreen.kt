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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shub39.grit.core.tasks.*
import com.shub39.grit.shared.ui.task.ui.component.DraftBackHandler
import grit.shared.ui.generated.resources.Res
import grit.shared.ui.generated.resources.montserrat
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.koin.compose.koinInject

/** Both layers share the same projection, camera and read-only window machinery. */
@Composable
fun RhineScreen(
    onOpenTasks: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    geometry: RhineGeometry = RhineGeometry(),
    style: RhineStyle = RhineStyle(),
    dataSource: RhineDataSource? = null,
    initialCamera: RhineCamera = RhineCamera(0f, 0f),
    editRepository: TaskRepo? = null,
    initialMode: RhineMode = RhineMode.Preview,
) {
    RhineWindowMode()
    val repo = editRepository ?: if (dataSource == null) koinInject<TaskRepo>() else null
    val source = remember(dataSource, repo) { dataSource ?: RepositoryRhineDataSource(repo!!) }
    val session =
        rememberSaveable(saver = RhineSession.saver) { RhineSession().apply { mode = initialMode } }
    val upper = rememberSaveable(saver = RhineBrowser.saver) { RhineBrowser(initialCamera) }
    val lower = rememberSaveable(saver = RhineBrowser.saver) { RhineBrowser() }
    val density = LocalDensity.current.density
    var viewport by remember { mutableStateOf(Size(1f, 1f)) }
    val upperInput = rememberRhineInput(source, upper, geometry, density, viewport)
    val sectionSource =
        remember(session.categoryId, repo) {
            session.categoryId?.let { id -> repo?.let { SectionRhineDataSource(it, id) } }
        }
    val lowerInput =
        sectionSource?.let { rememberRhineInput(it, lower, geometry, density, viewport) }
    val input = if (session.showingSections) lowerInput ?: upperInput else upperInput
    val browser = if (session.showingSections) lower else upper
    val scope = rememberCoroutineScope()
    var transition by remember { mutableStateOf(false) }
    var transitionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var epoch by remember { mutableStateOf(0) }
    val phase = remember { androidx.compose.animation.core.Animatable(0f) }
    var loading by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<RhineEdit?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val blocked = transition || loading || saving || edit != null
    val activeSource = if (session.showingSections) sectionSource!! else source

    fun navigate(u: Float = browser.camera.u, v: Float = browser.camera.v) {
        if (blocked) return
        browser.begin()
        browser.motion = scope.launch { browser.animateTo(RhineCamera(u, v)) }
    }
    fun returnToParent() {
        val snapshot = session.parent ?: return
        if (saving || edit != null || loading) return
        transitionJob?.cancel()
        lower.stop()
        upper.stop()
        epoch += 1
        val token = epoch
        transition = true
        transitionJob =
            scope.launch {
                try {
                    phase.animateTo(1f, androidx.compose.animation.core.tween(180))
                    val catalog = source.catalog().first()
                    val item = snapshot.focus.itemId?.let { repo?.getTaskSummaryFlow(it)?.first() }
                    val camera = snapshot.resolve(catalog, item)
                    upper.updateBounds(catalog.bounds)
                    upper.restore(snapshot.copy(camera = camera))
                    session.clearParent()
                    phase.animateTo(0f, androidx.compose.animation.core.tween(180))
                } finally {
                    if (epoch == token) transition = false
                }
            }
    }
    DraftBackHandler(enabled = edit == null && !loading) {
        if (session.parent != null) returnToParent() else if (!transition) onOpenTasks()
    }
    LaunchedEffect(lowerInput, lower.interacting, session.showingSections) {
        if (session.showingSections && !lower.interacting && !transition) {
            lowerInput?.columns?.getOrNull(lower.center)?.let {
                session.rememberPosition(it.id, lower.snapshot())
            }
        }
    }
    // Missing Category invalidates the entire second layer. A moved/deleted sibling stays within
    // the surviving parent and its finite bounds, rather than leaking into another Category.
    LaunchedEffect(session.categoryId, repo, edit, saving) {
        val id = session.categoryId
        if (id != null && repo != null)
            repo.getCategoriesFlow().collect { categories ->
                if (categories.none { it.id == id } && edit == null) returnToParent()
            }
    }
    LaunchedEffect(repo) {
        repo?.dataGeneration()?.collect { generation ->
            if (session.generation != null && session.generation != generation) {
                transitionJob?.cancel()
                epoch += 1
                transition = false
                phase.snapTo(0f)
                edit = null
                deleting = false
                session.invalidate()
                val start = RhineSnapshot(RhineCamera(0f, 0f), RhineFocus(null, null, 0, 0))
                upper.restore(start)
                lower.restore(start)
                notice = "数据已替换，已返回新数据起点"
            }
            session.generation = generation
        }
    }
    fun activate(slot: RhineSlot, stable: Boolean): Boolean {
        if (
            blocked ||
                !(canOpenRhineDetail(session.mode, stable, browser.center, slot) ||
                    canCreateRhine(session.mode, stable, browser.center, slot)) ||
                repo == null
        )
            return false
        browser.stop()
        error = null
        notice = null
        if (input.layer == RhineLayer.Categories && slot.kind == RhineBlockKind.Real) {
            val item = slot.item ?: return false
            // Preserve the actual parent camera/focus even when another U slot in this
            // stable center column is tapped.
            upper.rememberFocus(input)
            session.parent = upper.snapshot()
            session.categoryId = slot.column!!.id
            transition = true
            epoch += 1
            val token = epoch
            transitionJob =
                scope.launch {
                    try {
                        phase.animateTo(1f, androidx.compose.animation.core.tween(180))
                        val task = repo.getTaskSummaryFlow(item.id).first()
                        if (task == null || task.categoryId != session.categoryId) {
                            returnToParent()
                            return@launch
                        }
                        val saved = session.position(task.id)
                        val counts = repo.getTaskCountsFlow(task.categoryId).first()
                        val v = counts.indexOfFirst { it.taskId == task.id }.coerceAtLeast(0)
                        lower.updateBounds(RhineBounds.from(counts.map { it.count }))
                        lower.restore(
                            saved?.copy(camera = saved.camera.copy(v = v.toFloat()))
                                ?: RhineSnapshot(
                                    RhineCamera(0f, v.toFloat()),
                                    RhineFocus(task.id, null, 0, v),
                                )
                        )
                        session.showingSections = true
                        phase.animateTo(0f, androidx.compose.animation.core.tween(180))
                    } finally {
                        if (epoch == token) transition = false
                    }
                }
        } else {
            loading = true
            scope.launch {
                try {
                    edit =
                        when {
                            slot.kind == RhineBlockKind.AddParent &&
                                input.layer == RhineLayer.Categories ->
                                RhineEdit.CategoryDraft(Category(name = ""))
                            slot.kind == RhineBlockKind.AddParent ->
                                RhineEdit.TaskDraft(
                                    Task(categoryId = session.categoryId!!, title = ""),
                                    repo.getCategories().filter { it.id == session.categoryId },
                                )
                            slot.kind == RhineBlockKind.Placeholder &&
                                input.layer == RhineLayer.Categories ->
                                RhineEdit.TaskDraft(
                                    Task(categoryId = slot.column!!.id, title = ""),
                                    repo.getCategories().filter { it.id == slot.column.id },
                                )
                            slot.kind == RhineBlockKind.Placeholder ->
                                RhineEdit.SectionDraft(Section(taskId = slot.column!!.id))
                            slot.kind == RhineBlockKind.Zero &&
                                input.layer == RhineLayer.Categories ->
                                RhineEdit.CategoryDraft(
                                    requireNotNull(
                                        repo.getCategories().firstOrNull {
                                            it.id == slot.column!!.id
                                        }
                                    ) {
                                        "类别已不存在"
                                    }
                                )
                            slot.kind == RhineBlockKind.Zero ->
                                RhineEdit.TaskDraft(
                                    requireNotNull(repo.getTaskById(slot.column!!.id)) { "任务已不存在" },
                                    repo.getCategories(),
                                )
                            else ->
                                RhineEdit.SectionDraft(
                                    requireNotNull(repo.getSectionById(slot.item!!.id)) {
                                        "Section 已不存在"
                                    }
                                )
                        }
                    if (
                        slot.kind != RhineBlockKind.Placeholder &&
                            slot.kind != RhineBlockKind.AddParent
                    ) {
                        browser.move(RhineCamera(slot.u.toFloat(), slot.v.toFloat()))
                        browser.select(slot)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    notice = e.message ?: "读取失败，请重试"
                } finally {
                    loading = false
                }
            }
        }
        return true
    }
    fun requestDelete() {
        if (blocked || browser.interacting || repo == null) return
        val slot = input.slot(input.focusU.roundToInt(), input.focusV)
        if (slot.column == null) return
        loading = true
        scope.launch {
            try {
                edit =
                    when {
                        input.layer == RhineLayer.Categories && slot.kind != RhineBlockKind.Real ->
                            RhineEdit.CategoryDraft(
                                requireNotNull(
                                    repo.getCategories().firstOrNull { it.id == slot.column.id }
                                )
                            )
                        input.layer == RhineLayer.Categories ->
                            RhineEdit.TaskDraft(
                                requireNotNull(repo.getTaskById(slot.item!!.id)),
                                repo.getCategories(),
                            )
                        slot.kind == RhineBlockKind.Real ->
                            RhineEdit.SectionDraft(
                                requireNotNull(repo.getSectionById(slot.item!!.id))
                            )
                        else ->
                            RhineEdit.TaskDraft(
                                requireNotNull(repo.getTaskById(slot.column.id)),
                                repo.getCategories(),
                            )
                    }
                deleting = true
                error = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = e.message ?: "对象已不存在，请重试"
            } finally {
                loading = false
            }
        }
    }
    fun submit(
        create: Boolean = false,
        parent: Boolean = false,
        remove: Boolean = false,
        save: suspend () -> Long,
    ) {
        if (saving || repo == null) return
        saving = true
        error = null
        val targetBrowser = browser
        val targetSource = activeSource
        val before = browser.snapshot()
        val generation = session.generation
        upper.stop()
        lower.stop()
        upper.reconciling = true
        lower.reconciling = true
        scope.launch {
            var committed = false
            try {
                val id = save()
                committed = true
                if (generation != session.generation) return@launch
                val target =
                    if (create || remove) {
                        kotlinx.coroutines.withTimeoutOrNull(5000) {
                            if (create) targetSource.createdSnapshot(id, parent)
                            else targetSource.deletedSnapshot(before, parent)
                        } ?: error("已提交，但对象或父级已变化")
                    } else null
                if (generation != session.generation) return@launch
                // Close only after the committed identity and counts have been resolved.
                edit = null
                deleting = false
                target?.let { (catalog, snapshot) ->
                    targetBrowser.updateBounds(catalog.bounds)
                    targetBrowser.animateTo(snapshot.camera)
                    targetBrowser.restore(snapshot)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (committed) {
                    edit = null
                    deleting = false
                    notice = "数据已保存；定位已刷新，请重新选择对象"
                    targetBrowser.forgetItem()
                } else error = e.message ?: "提交失败，请重试；草稿已保留"
            } finally {
                upper.reconciling = false
                lower.reconciling = false
                saving = false
            }
        }
    }
    Box(modifier.fillMaxSize().background(style.floor)) {
        RhineCanvas(
            input,
            geometry,
            style,
            Modifier.fillMaxSize()
                .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
                .graphicsLayer {
                    scaleX = 1f + phase.value * 0.025f
                    scaleY = scaleX
                    translationY = -8.dp.toPx() * phase.value
                    alpha = 1f - phase.value * 0.38f
                },
            browser = browser,
            enabled = !blocked,
            onTap = ::activate,
        )
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to style.floor.copy(alpha = 0.7f),
                        0.22f to style.floor.copy(alpha = 0.35f),
                        0.38f to style.floor.copy(alpha = 0f),
                        0.66f to style.floor.copy(alpha = 0f),
                        0.82f to style.floor.copy(alpha = 0.6f),
                        1f to style.floor.copy(alpha = 0.85f),
                    )
                )
        )
        RhineHud(
            input = input,
            style = style,
            mode = session.mode,
            enabled = !blocked,
            parentTitle = upperInput.columns.firstOrNull { it.id == session.categoryId }?.title,
            onBack = if (session.parent != null) ::returnToParent else null,
            onMode = {
                if (!blocked)
                    session.mode =
                        if (session.mode == RhineMode.Preview) RhineMode.Detail
                        else RhineMode.Preview
            },
            onAction = {
                activate(input.slot(input.focusU.roundToInt(), input.focusV), !browser.interacting)
            },
            onDelete = ::requestDelete,
            onStart = { navigate(u = 0f) },
            onStepU = { navigate(u = browser.camera.u.roundToInt() + it.toFloat()) },
            onStepV = { navigate(v = browser.center + it.toFloat()) },
            onOpenTasks = { if (!blocked) onOpenTasks() },
            onOpenSettings = { if (!blocked) onOpenSettings() },
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout),
        )
        if (loading || notice != null)
            Text(
                if (loading) "载入详情…" else notice!!,
                Modifier.align(Alignment.TopCenter)
                    .padding(12.dp)
                    .background(style.floor)
                    .padding(8.dp),
            )
        edit?.let { value ->
            RhineEditor(
                value,
                saving,
                error,
                { if (!saving) edit = null },
                { category ->
                    submit(create = category.id == 0L, parent = true) {
                        repo!!.upsertCategory(category)
                    }
                },
                { task ->
                    submit(create = task.id == 0L, parent = input.layer == RhineLayer.Sections) {
                        repo!!.upsertTask(task)
                    }
                },
                { section -> submit(create = section.id == 0L) { repo!!.upsertSection(section) } },
                {
                    deleting = true
                    error = null
                },
            )
        }
        if (deleting)
            edit?.let { value ->
                AlertDialog(
                    onDismissRequest = { if (!saving) deleting = false },
                    title = { Text("确认删除") },
                    text = {
                        Text(
                            when (value) {
                                is RhineEdit.CategoryDraft ->
                                    "删除 Category「${value.value.name}」及其全部 Task、Section？此操作不可撤销。"
                                is RhineEdit.TaskDraft ->
                                    "删除 Task「${value.value.title}」及其全部 Section？此操作不可撤销。"
                                is RhineEdit.SectionDraft ->
                                    "删除 Section「${value.value.title}」？此操作不可撤销。"
                            } + (error?.let { "\n$it" } ?: "")
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !saving,
                            onClick = {
                                submit(
                                    remove = true,
                                    parent =
                                        value is RhineEdit.CategoryDraft ||
                                            (value is RhineEdit.TaskDraft &&
                                                input.layer == RhineLayer.Sections),
                                ) {
                                    when (value) {
                                        is RhineEdit.CategoryDraft ->
                                            repo!!.deleteCategory(value.value)
                                        is RhineEdit.TaskDraft -> repo!!.deleteTask(value.value)
                                        is RhineEdit.SectionDraft ->
                                            repo!!.deleteSection(value.value.id)
                                    }
                                    0L
                                }
                            },
                        ) {
                            Text(if (saving) "删除中…" else "确认删除")
                        }
                    },
                    dismissButton = {
                        TextButton(enabled = !saving, onClick = { deleting = false }) {
                            Text("取消删除")
                        }
                    },
                )
            }
    }
}

@Composable
fun RhineCanvas(
    input: RhineSceneInput,
    geometry: RhineGeometry,
    style: RhineStyle,
    modifier: Modifier = Modifier,
    browser: RhineBrowser? = null,
    enabled: Boolean = true,
    onTap: (RhineSlot, Boolean) -> Boolean = { _, _ -> false },
) {
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val density = LocalDensity.current.density
    val labels =
        remember(input.layer) {
            RhineLabels(
                zeroTop = { slot ->
                    val c = slot.column
                    if (c == null) "" else "00  " + c.title
                },
                addParentTop =
                    if (input.layer == RhineLayer.Categories) "+  新建 Category" else "+  新建 Task",
                sideLabel = { c -> indexLabel(c.index) + "  " + c.title },
            )
        }
    val currentInput by rememberUpdatedState(input)
    val currentTap by rememberUpdatedState(onTap)
    val scope = rememberCoroutineScope()
    val gestureModifier =
        if (browser == null || !enabled) Modifier
        else
            Modifier.pointerInput(browser, geometry, density) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val wasStable = !browser.interacting
                    browser.begin()
                    val projection =
                        RhineProjection(
                            geometry,
                            density,
                            Size(size.width.toFloat(), size.height.toFloat()),
                            browser.camera,
                        )
                    // Freeze the hit candidate on down; rising faces must not change the selected
                    // object.
                    val hit = layoutScene(currentInput, projection).hitTest(down.position)?.slot
                    val gesture = RhineGestureController(projection, viewConfiguration.touchSlop)
                    val velocity = VelocityTracker()
                    velocity.addPosition(down.uptimeMillis, down.position)
                    var cancelled = false
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (
                            change == null ||
                                event.changes.count { it.pressed } > 1 ||
                                change.isConsumed
                        ) {
                            cancelled = true
                            break
                        }
                        velocity.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) break
                        val delta = gesture.delta(change.positionChange())
                        if (gesture.axis != null) {
                            change.consume()
                            browser.move(
                                RhineCamera(browser.camera.u + delta.u, browser.camera.v + delta.v)
                            )
                        }
                    } while (true)
                    if (cancelled) browser.stop()
                    else if (
                        gesture.axis == null && hit != null && hit.kind != RhineBlockKind.Decoration
                    ) {
                        browser.stop()
                        if (!currentTap(hit, wasStable))
                            browser.motion =
                                scope.launch {
                                    browser.animateTo(RhineCamera(hit.u.toFloat(), hit.v.toFloat()))
                                    browser.select(hit)
                                }
                    } else {
                        val speed = velocity.calculateVelocity()
                        browser.motion =
                            scope.launch {
                                browser.settle(gesture.velocity(Offset(speed.x, speed.y)))
                            }
                    }
                }
            }
    Canvas(modifier.then(gestureModifier).semantics { contentDescription = "Rhine 场景" }) {
        val projection =
            RhineProjection(
                geometry,
                density,
                Size(size.width, size.height),
                RhineCamera(input.focusU, input.cameraV),
            )
        drawRhineScene(layoutScene(input, projection), input, style, labels, measurer)
    }
}

/** Text styles of the HUD; Montserrat for Latin, system fallback for Chinese. */
private class HudText(val family: FontFamily, val ink: Color) {
    fun style(size: TextUnit, bold: Boolean = false, alpha: Float = 1f, tracking: Float = 0.04f) =
        TextStyle(
            fontFamily = family,
            fontSize = size,
            lineHeight = size * 1.15f,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = ink.copy(alpha = alpha),
            letterSpacing = tracking.em,
        )
}

/** Unavailable controls are drawn faint and announce the stage that connects them. */
private const val Pending = 0.38f

/**
 * Text-only HUD laid on the scene. Positions are fractions of the inset area, taken from the
 * reference image (see 修改文档 8.6).
 */
@Composable
private fun RhineHud(
    input: RhineSceneInput,
    style: RhineStyle,
    mode: RhineMode,
    enabled: Boolean,
    parentTitle: String?,
    onBack: (() -> Unit)?,
    onMode: () -> Unit,
    onAction: () -> Unit,
    onDelete: () -> Unit,
    onStart: () -> Unit,
    onStepU: (Int) -> Unit,
    onStepV: (Int) -> Unit,
    onOpenTasks: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = HudText(FontFamily(Font(Res.font.montserrat)), style.ink)
    val focus = input.slot(input.focusU.roundToInt(), input.focusV)
    val column = focus.column
    val isCategories = input.layer == RhineLayer.Categories
    val itemWord = if (isCategories) "TASK" else "SECTION"
    val parentWord = if (isCategories) "CATEGORY" else "TASK"

    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight

        // Brand block: product, layer, mode.
        Column(Modifier.offset(x = w * 0.03f, y = h * 0.10f)) {
            Text("GRIT RHINE", style = t.style(19.sp, bold = true, tracking = 0.02f))
            Text(
                if (isCategories) "CATEGORY LAYER" else "SECTION LAYER",
                style = t.style(9.sp, alpha = 0.8f),
            )
            Text(
                buildAnnotatedString {
                    append("MODE  ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(if (mode == RhineMode.Preview) "PREVIEW" else "DETAIL")
                    }
                },
                style = t.style(16.sp, alpha = 0.85f, tracking = 0.06f),
            )
        }

        // Top-right controls: search (stage 05), mode cycling (stage 03), back to start (stage 02).
        Row(
            Modifier.align(Alignment.TopEnd).padding(end = w * 0.03f).offset(y = h * 0.12f - 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HudButton("⌕  SEARCH", t, enabled = false, description = "搜索，阶段 05 接入")
            HudButton(
                if (mode == RhineMode.Preview) "+  PREVIEW" else "+  DETAIL",
                t,
                enabled = enabled,
                description = "切换预览/详情模式",
                onClick = onMode,
            )
            HudButton(
                "⇤",
                t,
                enabled = enabled && input.focusU > 0f,
                description = "回到开头",
                size = 13.sp,
                onClick = onStart,
            )
        }

        // Focus panel: path, focused title, rule to the right edge, status, mode hint.
        Column(Modifier.offset(x = w * 0.51f, y = h * 0.43f).width(w * 0.49f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null)
                    HudButton(
                        "←",
                        t,
                        enabled = enabled,
                        description = "返回类别层",
                        onClick = onBack,
                        size = 13.sp,
                    )
                HudReadout(
                    (if (!isCategories) "${parentTitle.orEmpty()}  /  " else "") +
                        "$parentWord ${indexLabel(column?.index ?: input.columnCount + 1)}  /  " +
                        (column?.title ?: "新建"),
                    style = t.style(9.sp, alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = w * 0.03f),
                )
            }
            Spacer(Modifier.height(10.dp))
            HudReadout(
                focusTitle(focus, itemWord),
                style =
                    t.style(15.sp, bold = true, tracking = 0.03f)
                        .copy(color = style.readable(focus.item?.titleColor, style.floor)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = w * 0.03f),
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(4.dp).background(t.ink.copy(alpha = 0.7f), CircleShape))
                Spacer(Modifier.width(18.dp))
                Box(Modifier.weight(1f).height(1.dp).background(t.ink.copy(alpha = 0.35f)))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(start = 22.dp, end = w * 0.03f)) {
                HudReadout(focusStatus(focus), style = t.style(9.sp, alpha = 0.7f))
                Spacer(Modifier.weight(1f))
                HudReadout(
                    "DATE ${focus.item?.completedOn?.toString() ?: "—"}",
                    style = t.style(9.sp, alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.padding(start = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                if (mode == RhineMode.Preview)
                    HudReadout("预览模式 · 沿列滑动 / 跨列切换", style = t.style(9.sp, alpha = 0.7f))
                else if (focus.kind == RhineBlockKind.Real || focus.kind == RhineBlockKind.Zero)
                    HudButton(
                        if (focus.kind == RhineBlockKind.Zero) "编辑 $parentWord  →"
                        else if (isCategories) "进入 Section 层  →" else "编辑 Section  →",
                        t,
                        enabled =
                            enabled && (focus.kind == RhineBlockKind.Zero || focus.item != null),
                        description =
                            if (focus.kind == RhineBlockKind.Zero) "编辑 $parentWord"
                            else if (isCategories) "进入 Section 层" else "编辑 Section",
                        onClick = onAction,
                    )
                else if (
                    focus.kind == RhineBlockKind.Placeholder ||
                        focus.kind == RhineBlockKind.AddParent
                )
                    HudButton(
                        if (focus.kind == RhineBlockKind.AddParent) "新建 $parentWord  ＋"
                        else "尾部新增 $itemWord  ＋",
                        t,
                        enabled = enabled,
                        description =
                            if (focus.kind == RhineBlockKind.AddParent) "新建 $parentWord"
                            else "尾部新增 $itemWord",
                        onClick = onAction,
                    )
            }
        }

        if (mode == RhineMode.Detail && column != null)
            Box(Modifier.offset(x = w * 0.80f, y = h * 0.68f)) {
                HudButton(
                    "删除 ${if (focus.kind == RhineBlockKind.Real) itemWord else parentWord}",
                    t,
                    enabled = enabled && (focus.kind != RhineBlockKind.Real || focus.item != null),
                    description = "删除焦点对象",
                    onClick = onDelete,
                )
            }

        // Along-column readout: focused index over the center column's real count.
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = w * 0.03f, bottom = h * 0.10f)
        ) {
            Text(itemWord, style = t.style(9.sp, alpha = 0.7f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    indexLabel(input.focusU.roundToInt()),
                    style = t.style(30.sp, tracking = 0.02f).copy(fontWeight = FontWeight.Normal),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "/  ${indexLabel(column?.itemCount ?: 0)}",
                    style = t.style(11.sp, alpha = 0.55f),
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }

        // U stepper: arrows around a tick bar; the bar marks the focus within the column.
        Row(
            Modifier.offset(x = w * 0.26f, y = h * 0.87f - 12.dp).width(w * 0.19f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudButton(
                "↑",
                t,
                enabled = enabled && input.focusU > 0f,
                description = "沿列上一格",
                size = 14.sp,
                onClick = { onStepU(-1) },
            )
            TickBar(
                count = input.maxU + 1,
                focus = input.focusU.roundToInt(),
                ink = t.ink,
                modifier = Modifier.weight(1f).height(14.dp),
            )
            HudButton(
                "↓",
                t,
                enabled = enabled && input.focusU < input.maxU,
                description = "沿列下一格",
                size = 14.sp,
                onClick = { onStepU(1) },
            )
        }
        Text(
            if (mode == RhineMode.Preview) "拖动浏览 · 点按定位 · 预览" else "详情 · 非中心列先定位 · 只尾插一项",
            style = t.style(8.sp, alpha = 0.55f),
            modifier =
                Modifier.align(Alignment.BottomStart).padding(start = w * 0.26f, bottom = h * 0.05f),
        )

        // V switch: previous / next column around the center column's name.
        Row(
            Modifier.offset(x = w * 0.52f, y = h * 0.87f - 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudButton(
                "←",
                t,
                enabled = enabled && input.focusV > 0,
                description = "上一列",
                size = 13.sp,
                onClick = { onStepV(-1) },
            )
            Column(Modifier.padding(horizontal = 22.dp).widthIn(max = w * 0.14f)) {
                Text(
                    if (column == null) "NEW $parentWord"
                    else
                        "$parentWord ${indexLabel(input.focusV + 1)}/${indexLabel(input.columnCount)}",
                    style = t.style(8.sp, alpha = 0.6f),
                    maxLines = 1,
                )
                Text(
                    column?.title ?: "新建",
                    style =
                        t.style(10.sp)
                            .copy(color = style.readable(column?.titleColor, style.floor)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HudButton(
                "→",
                t,
                enabled = enabled && input.focusV < input.maxV,
                description = "下一列",
                size = 13.sp,
                onClick = { onStepV(1) },
            )
        }

        // Status: completed count of the center column, from counts only.
        val done = column?.completedCount ?: 0
        val total = column?.itemCount ?: 0
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = w * 0.03f, bottom = h * 0.10f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildAnnotatedString {
                    append("DONE  ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("${indexLabel(done)} / ${indexLabel(total)}")
                    }
                },
                style = t.style(10.sp, alpha = 0.85f),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(22.dp).height(3.dp).background(t.ink.copy(alpha = 0.15f))) {
                if (total > 0)
                    Box(
                        Modifier.width(22.dp * (done.toFloat() / total))
                            .height(3.dp)
                            .background(t.ink.copy(alpha = 0.75f))
                    )
            }
        }

        // Footer: data size and sample switch on the left, exits on the right.
        Row(
            Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = w * 0.03f)
                .padding(bottom = h * 0.025f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${indexLabel(input.columnCount)} ${if (isCategories) "CATEGORIES" else "TASKS"}" +
                    "  ·  ${indexLabel(input.columns.sumOf { it.itemCount })} ${itemWord}S",
                style = t.style(8.sp, alpha = 0.6f),
            )
            Spacer(Modifier.weight(1f))
            HudButton("TASK ↗", t, enabled = true, onClick = onOpenTasks, size = 9.sp)
            Text("   /   ", style = t.style(9.sp, alpha = 0.5f))
            HudButton("SETTING ↗", t, enabled = true, onClick = onOpenSettings, size = 9.sp)
        }
    }
}

private fun focusTitle(focus: RhineSlot, itemWord: String): String =
    when (focus.kind) {
        RhineBlockKind.Real -> focus.item?.let { "${indexLabel(it.index)}   ${it.title}" } ?: "载入中…"
        RhineBlockKind.Zero -> "00   ${focus.column?.title ?: ""}"
        RhineBlockKind.Placeholder -> "＋   尾部追加 $itemWord"
        RhineBlockKind.AddParent -> "+   新建 ${if (itemWord == "SECTION") "TASK" else "CATEGORY"}"
        RhineBlockKind.Decoration -> ""
    }

private fun focusStatus(focus: RhineSlot): String =
    when (focus.kind) {
        RhineBlockKind.Real -> if (focus.item?.isCompleted == true) "● 已完成" else "○ 未完成"
        RhineBlockKind.Zero -> "${focus.column?.itemCount ?: 0} 项 · 0 号块"
        RhineBlockKind.Placeholder -> "空位置 · 保存后移到尾部新项"
        RhineBlockKind.AddParent -> "唯一新增列 · 保存后停在 0 号"
        else -> ""
    }

/**
 * Text button laid out at its visible size so the HUD keeps the reference spacing. Compose hit
 * testing extends pointer targets smaller than the 48 dp minimum touch size, so taps near the
 * glyphs still land.
 */
@Composable
private fun HudButton(
    text: String,
    t: HudText,
    enabled: Boolean,
    description: String? = null,
    size: TextUnit = 10.sp,
    onClick: () -> Unit = {},
) {
    Box(
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(
                if (description != null) Modifier.semantics { contentDescription = description }
                else Modifier
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = t.style(size, alpha = if (enabled) 0.9f else Pending), maxLines = 1)
    }
}

@Composable
private fun TickBar(count: Int, focus: Int, ink: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = count.coerceIn(2, 31)
        val step = size.width / (n - 1)
        for (i in 0 until n) {
            val x = i * step
            val major = i == (focus.toFloat() / (count - 1).coerceAtLeast(1) * (n - 1)).roundToInt()
            drawLine(
                ink.copy(alpha = if (major) 0.85f else 0.3f),
                androidx.compose.ui.geometry.Offset(x, if (major) 0f else size.height * 0.3f),
                androidx.compose.ui.geometry.Offset(x, size.height * if (major) 1f else 0.7f),
                strokeWidth = (if (major) 1.6f else 1f) * density,
            )
        }
    }
}

/** A glyph-only floor-coloured halo keeps face labels from crossing the HUD's readouts. */
@Composable
private fun HudReadout(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val stroke = with(LocalDensity.current) { 1.5.dp.toPx() }
    Box(modifier) {
        Text(
            text,
            style = style.copy(color = RhineStyle().floor, drawStyle = Stroke(stroke)),
            maxLines = maxLines,
            overflow = overflow,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(text, style = style, maxLines = maxLines, overflow = overflow)
    }
}

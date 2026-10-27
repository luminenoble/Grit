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

import com.shub39.grit.shared.ui.components.glass.GlassAlertDialog
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.shub39.grit.shared.ui.blurPossible
import com.shub39.grit.shared.ui.theme.GlassMode
import com.shub39.grit.shared.ui.theme.LocalGlassMode
import kotlin.math.ceil
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
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
import com.shub39.grit.core.tasks.apply
import com.shub39.grit.shared.ui.components.glass.GlassButton
import com.shub39.grit.shared.ui.components.glass.GlassButtonStyle
import com.shub39.grit.shared.ui.components.glass.GlassToast
import com.shub39.grit.shared.ui.theme.glassSource
import com.shub39.grit.shared.ui.theme.rememberGlassBackdrop
import com.shub39.grit.shared.ui.task.ui.component.DraftBackHandler
import grit.shared.ui.generated.resources.Res
import grit.shared.ui.generated.resources.montserrat
import kotlin.math.roundToInt
import kotlinx.coroutines.*
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
    geometryOverride: RhineGeometry? = null,
    styleOverride: RhineStyle? = null,
    display: com.shub39.grit.core.settings.RhineDisplayConfig =
        com.shub39.grit.core.settings.RhineDisplayConfig(),
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
    val geometry =
        geometryOverride ?: display.geometry(viewport.width / density, viewport.height / density)
    val style = styleOverride ?: display.style()
    val systemMotion = rhineSystemAnimationsEnabled()
    val reduced = display.reduceMotion || !systemMotion
    upper.reduceMotion = reduced
    lower.reduceMotion = reduced
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
    val director = remember { RhineDirector() }
    /** Live editor draft, mirrored onto the raised preview block. */
    var draft by remember { mutableStateOf<RhineEdit?>(null) }
    val illustrations = LocalRhineIllustrationRepo.current
    // Saved with the screen: a full-screen gallery app can recreate the activity before the
    // picked image comes back, and the open editor then reopens with its draft (below).
    var illustrationDraft by
        rememberSaveable(stateSaver = IllustrationDraft.Saver) {
            mutableStateOf<IllustrationDraft?>(null)
        }
    /**
     * Bumped for every editor opened. The editor is keyed by it, so form state saved for one object
     * can never be restored into an editor opened later for another one.
     */
    var editorSession by rememberSaveable { mutableStateOf(0L) }
    /** The open Category/Task editor, to reopen it after the activity is recreated. */
    var editorRestore by
        rememberSaveable(stateSaver = RhineEditorRestore.Saver) {
            mutableStateOf<RhineEditorRestore?>(null)
        }
    val latestInput = rememberUpdatedState(input)
    val illustrationCache = rememberRhineImageDecoder().let { remember(it) { RhineIllustrationCache(it) } }
    androidx.compose.runtime.DisposableEffect(illustrationCache) {
        onDispose { illustrationCache.clear() }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3000)
            notice = null
        }
    }
    val adjustment = remember { RhineAdjustment() }
    var contentScope by remember { mutableStateOf<MoveScope?>(null) }
    var parentScope by remember { mutableStateOf<MoveScope?>(null) }
    var search by remember { mutableStateOf<RhineSearchState?>(null) }
    var selectionJob by remember { mutableStateOf<Job?>(null) }
    var selecting by remember { mutableStateOf(false) }
    val blocked =
        transition ||
            loading ||
            saving ||
            edit != null ||
            adjustment.submitting ||
            (adjustment.move != null && !adjustment.dragging) ||
            search != null ||
            director.active
    val activeSource = if (session.showingSections) sectionSource!! else source

    fun cancelAdjustment() {
        if (adjustment.submitting) return
        val previous = adjustment.move
        adjustment.clear()
        browser.reconciling = false
        browser.transientSnapshot = null
        if (previous != null)
            browser.restore(
                previous.before.copy(camera = browser.bounds.clamp(previous.before.camera))
            )
    }
    LaunchedEffect(
        repo,
        session.mode,
        input.layer,
        input.columns.getOrNull(browser.center)?.id,
        session.generation,
    ) {
        contentScope = null
        parentScope = null
        if (repo != null && session.mode == RhineMode.Adjust) {
            while (isActive) {
                try {
                    val column = input.columns.getOrNull(browser.center)
                    if (column != null) {
                        contentScope =
                            repo.readMoveScope(
                                if (input.layer == RhineLayer.Categories) MoveKind.Task
                                else MoveKind.Section,
                                column.id,
                            )
                        parentScope =
                            repo.readMoveScope(
                                if (input.layer == RhineLayer.Categories) MoveKind.Category
                                else MoveKind.Task,
                                if (input.layer == RhineLayer.Categories) null
                                else session.categoryId,
                            )
                    }
                } catch (_: IllegalArgumentException) {
                    contentScope = null
                    parentScope = null
                }
                delay(200)
            }
        }
    }
    LaunchedEffect(adjustment.move, adjustment.submitting) {
        val move = adjustment.move
        if (move != null && !adjustment.submitting && repo != null) {
            while (isActive) {
                try {
                    move.scope.validate(repo.readMoveScope(move.scope.kind, move.scope.parentId))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    cancelAdjustment()
                    notice = "对象、归属或顺序已变化，已取消移动"
                    break
                }
                delay(150)
            }
        }
    }
    LaunchedEffect(viewport, display, LocalDensity.current.fontScale, density, reduced) {
        if (!adjustment.submitting) cancelAdjustment()
        upper.stop()
        lower.stop()
        selectionJob?.cancel()
        selecting = false
        search = null
        val cancelledEntry = transition && !session.showingSections
        // An unfinished Category → Task entry is abandoned like the old fade; editor transitions
        // re-target the new viewport by themselves.
        if (director.stage == RhineStage.NavEnter) director.reset()
        transitionJob?.cancel()
        transition = false
        if (cancelledEntry) session.clearParent()
        epoch += 1
        phase.snapTo(0f)
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            director.reset()
            adjustment.clear()
            upper.reconciling = false
            lower.reconciling = false
        }
    }
    fun beginAdjustment(
        slot: RhineSlot,
        stable: Boolean,
        drag: Boolean,
        point: Offset = Offset.Zero,
    ): Boolean {
        if (
            blocked ||
                repo == null ||
                adjustment.move != null ||
                !canAdjustRhine(session.mode, stable, browser.center, slot)
        )
            return false
        val parent = slot.kind == RhineBlockKind.Zero
        val state = (if (parent) parentScope else contentScope) ?: return false
        val id = if (parent) slot.column!!.id else slot.item!!.id
        if (
            state.generation != session.generation ||
                state.ids.indexOf(id) != (if (parent) slot.v else slot.u - 1)
        )
            return false
        browser.stop()
        val before = browser.snapshot()
        browser.reconciling = true
        browser.transientSnapshot = before
        adjustment.move = RhineMove(id, parent, input.layer, slot.column!!.id, state, before, slot)
        adjustment.target = state.ids.indexOf(id)
        adjustment.positionDialog = !drag
        adjustment.dragging = drag
        adjustment.error = null
        adjustment.origin = point
        adjustment.pointer = point
        adjustment.cameraStart = browser.camera
        notice = null
        return true
    }
    fun commitAdjustment(target: Int) {
        val move = adjustment.move ?: return
        if (adjustment.submitting || repo == null) return
        if (target == move.source) {
            cancelAdjustment()
            return
        }
        if (target !in move.scope.ids.indices) return
        adjustment.target = target
        adjustment.frozenInput = input
        adjustment.submitting = true
        adjustment.dragging = false
        val targetBrowser = browser
        val targetSource = activeSource
        val token = adjustment.token
        scope.launch {
            var committed = false
            try {
                move.scope.validate(repo.readMoveScope(move.scope.kind, move.scope.parentId))
                when (move.scope.kind) {
                    MoveKind.Category -> repo.moveCategory(move.id, target, move.scope)
                    MoveKind.Task -> repo.moveTask(move.id, target, move.scope)
                    MoveKind.Section -> repo.moveSection(move.id, target, move.scope)
                }
                committed = true
                if (token != adjustment.token || session.generation != move.scope.generation)
                    return@launch
                val result =
                    withTimeoutOrNull(5000) { targetSource.createdSnapshot(move.id, move.parent) }
                        ?: error("已提交，但对象已变化")
                if (token != adjustment.token || session.generation != move.scope.generation)
                    return@launch
                targetBrowser.updateBounds(result.first.bounds)
                targetBrowser.animateTo(result.second.camera)
                targetBrowser.restore(result.second)
                adjustment.clear()
                notice = "已移动到第 ${target + 1} 项"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == adjustment.token) {
                    if (committed) {
                        adjustment.clear()
                        notice = "排序已保存，位置已变化，请重新选择"
                    } else {
                        adjustment.positionDialog = true
                        adjustment.error = e.message ?: "移动失败，请重试"
                    }
                }
            } finally {
                targetBrowser.reconciling = false
                targetBrowser.transientSnapshot = null
                adjustment.submitting = false
            }
        }
    }
    fun closeSearch() {
        selectionJob?.cancel()
        selecting = false
        search = null
    }
    fun selectResult(id: Long) {
        val state = search ?: return
        if (selecting || repo == null) return
        selecting = true
        val generation = session.generation
        val version = state.version
        selectionJob =
            scope.launch {
                try {
                    val (catalog, snapshot) =
                        withTimeoutOrNull(5000) { source.createdSnapshot(id, false) }
                            ?: error("结果已不存在")
                    ensureActive()
                    require(
                        generation == session.generation &&
                            search === state &&
                            state.version == version
                    )
                    upper.stop()
                    lower.stop()
                    session.invalidate()
                    session.mode = RhineMode.Preview
                    upper.updateBounds(catalog.bounds)
                    upper.restore(snapshot)
                    search = null
                    notice = "已定位到搜索结果"
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    state.error = "结果已删除或位置已变化，请重试搜索"
                } finally {
                    selecting = false
                }
            }
    }
    fun navigate(u: Float = browser.camera.u, v: Float = browser.camera.v) {
        if (blocked || adjustment.dragging) return
        browser.begin()
        browser.motion = scope.launch { browser.animateTo(RhineCamera(u, v)) }
    }
    fun returnToParent() {
        if (adjustment.move != null) {
            cancelAdjustment()
            return
        }
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
                    phase.animateTo(
                        1f,
                        androidx.compose.animation.core.tween(if (reduced) 0 else 180),
                    )
                    val catalog = source.catalog().first()
                    val item = snapshot.focus.itemId?.let { repo?.getTaskSummaryFlow(it)?.first() }
                    val camera = snapshot.resolve(catalog, item)
                    upper.updateBounds(catalog.bounds)
                    upper.restore(snapshot.copy(camera = camera))
                    session.clearParent()
                    phase.animateTo(
                        0f,
                        androidx.compose.animation.core.tween(if (reduced) 0 else 180),
                    )
                } finally {
                    if (epoch == token) transition = false
                }
            }
    }
    LaunchedEffect(lowerInput, lower.interacting, session.showingSections) {
        if (
            session.showingSections && !lower.interacting && !transition && adjustment.move == null
        ) {
            lowerInput?.columns?.getOrNull(lower.center)?.let {
                session.rememberPosition(it.id, lower.snapshot())
            }
        }
    }
    // Missing Category invalidates the entire second layer. A moved/deleted sibling stays within
    // the surviving parent and its finite bounds, rather than leaking into another Category.
    LaunchedEffect(session.categoryId, repo, edit, saving, adjustment.move) {
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
                director.reset()
                draft = null
                edit = null
                editorRestore = null
                deleting = false
                adjustment.clear()
                upper.transientSnapshot = null
                lower.transientSnapshot = null
                upper.reconciling = false
                lower.reconciling = false
                closeSearch()
                session.invalidate()
                val start = RhineSnapshot(RhineCamera(0f, 0f), RhineFocus(null, null, 0, 0))
                upper.restore(start)
                lower.restore(start)
                notice = "数据已替换，已返回新数据起点"
            }
            session.generation = generation
        }
    }
    /** Reads the stored object (or a new draft) that [slot] edits. */
    suspend fun loadEdit(slot: RhineSlot, layer: RhineLayer = input.layer): RhineEdit {
        val repo = repo!!
        return when {
            slot.kind == RhineBlockKind.AddParent && layer == RhineLayer.Categories ->
                RhineEdit.CategoryDraft(Category(name = ""))
            slot.kind == RhineBlockKind.AddParent ->
                RhineEdit.TaskDraft(
                    Task(categoryId = session.categoryId!!, title = ""),
                    repo.getCategories().filter { it.id == session.categoryId },
                )
            slot.kind == RhineBlockKind.Placeholder && layer == RhineLayer.Categories ->
                RhineEdit.TaskDraft(
                    Task(categoryId = slot.column!!.id, title = ""),
                    repo.getCategories().filter { it.id == slot.column.id },
                )
            slot.kind == RhineBlockKind.Placeholder ->
                RhineEdit.SectionDraft(Section(taskId = slot.column!!.id))
            slot.kind == RhineBlockKind.Zero && layer == RhineLayer.Categories ->
                RhineEdit.CategoryDraft(
                    requireNotNull(repo.getCategories().firstOrNull { it.id == slot.column!!.id }) {
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
                    requireNotNull(repo.getSectionById(slot.item!!.id)) { "Section 已不存在" }
                )
        }
    }
    fun finishEditor() {
        illustrationDraft?.let { d ->
            // Release images that were picked but not (or no longer) stored.
            illustrations?.let { repo -> scope.launch { d.discard(repo) } }
        }
        illustrationDraft = null
        editorRestore = null
        edit = null
        draft = null
        deleting = false
        loading = false
        director.reset()
    }
    /**
     * Descent and clockwise return (docs/视觉优化/02 section 8). The browser camera glides from
     * [RhineExit.from] to [RhineExit.to] alongside, so data windows and the peak follow; [after]
     * runs once the default framing is restored.
     */
    fun exitEditor(exit: RhineExit, targetBrowser: RhineBrowser = browser, after: () -> Unit = {}) {
        // Leaving for good: a recreated activity must not reopen this editor.
        editorRestore = null
        director.job?.cancel()
        director.exit = exit
        director.ms = 0f
        director.stage = RhineStage.EditorExit
        val token = director.epoch
        director.job =
            scope.launch {
                director.playTo(RhineMotionSpec.EditorMs.toFloat(), reduced) { ms ->
                    val back = editorExitProgress(ms, exit.dissolve)
                    targetBrowser.move(
                        RhineCamera(
                            exit.from.u + (exit.to.u - exit.from.u) * back,
                            exit.from.v + (exit.to.v - exit.from.v) * back,
                        )
                    )
                }
                if (token != director.epoch) return@launch
                after()
                finishEditor()
            }
    }
    /** Back or cancel while entering or editing: an unfinished entry plays backwards. */
    fun cancelEditor() {
        if (saving) return
        when (director.stage) {
            RhineStage.EditorEnter -> {
                director.job?.cancel()
                editorRestore = null
                loading = false
                val token = director.epoch
                director.job =
                    scope.launch {
                        director.playTo(0f, reduced)
                        if (token == director.epoch) finishEditor()
                    }
            }
            RhineStage.Editing -> exitEditor(RhineExit(browser.camera, browser.camera))
            else -> Unit
        }
    }
    fun activate(slot: RhineSlot, stable: Boolean): Boolean {
        if (session.mode == RhineMode.Adjust) return beginAdjustment(slot, stable, false)
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
            director.begin(RhineStage.NavEnter, slot)
            transitionJob =
                scope.launch {
                    try {
                        // Read the Section layer while the block lifts and turns.
                        val load = async {
                            val task = repo.getTaskSummaryFlow(item.id).first()
                            if (task == null || task.categoryId != session.categoryId) null
                            else task to repo.getTaskCountsFlow(task.categoryId).first()
                        }
                        director.playTo(700f, reduced)
                        val loaded = withTimeoutOrNull(5000) { load.await() }
                        if (loaded == null) {
                            director.reset()
                            returnToParent()
                            return@launch
                        }
                        val (task, counts) = loaded
                        val saved = session.position(task.id)
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
                        director.playTo(RhineMotionSpec.NavMs.toFloat(), reduced)
                    } finally {
                        if (epoch == token) transition = false
                        if (director.stage == RhineStage.NavEnter) director.reset()
                    }
                }
            director.job = transitionJob
        } else if (
            input.layer == RhineLayer.Categories ||
                slot.kind == RhineBlockKind.Zero ||
                slot.kind == RhineBlockKind.AddParent
        ) {
            // Category / Task: cinematic entry into the split editor.
            val token = director.begin(RhineStage.EditorEnter, slot)
            draft = null
            editorSession += 1
            val layer = input.layer
            val creating =
                slot.kind == RhineBlockKind.Placeholder || slot.kind == RhineBlockKind.AddParent
            director.job =
                scope.launch {
                    val load = async {
                        try {
                            val loaded = loadEdit(slot)
                            val owner =
                                if (loaded is RhineEdit.CategoryDraft) IllustrationOwner.Category
                                else IllustrationOwner.Task
                            val id =
                                when (loaded) {
                                    is RhineEdit.CategoryDraft -> loaded.value.id
                                    is RhineEdit.TaskDraft -> loaded.value.id
                                    else -> 0L
                                }
                            val initial =
                                if (id == 0L) null else illustrations?.illustration(owner, id)
                            initial?.let { illustrationCache.retry(it) }
                            illustrationDraft = IllustrationDraft(initial)
                            Result.success(loaded)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                    // Centre the block first so it rises from the peak of the wall.
                    browser.animateTo(RhineCamera(slot.u.toFloat(), slot.v.toFloat()))
                    if (!creating) browser.select(slot)
                    director.playTo(1250f, reduced)
                    // Only a read slower than the lift and turn shows the loading notice.
                    if (!load.isCompleted) loading = true
                    val loaded = load.await()
                    loading = false
                    if (token != director.epoch) return@launch
                    loaded
                        .onSuccess {
                            edit = it
                            editorRestore =
                                RhineEditorRestore(
                                    editorSession,
                                    slot.u,
                                    slot.v,
                                    layer == RhineLayer.Sections,
                                    it.storedId(),
                                )
                        }
                        .onFailure {
                            notice = it.message ?: "读取失败，请重试"
                            director.playTo(0f, reduced)
                            if (token == director.epoch) finishEditor()
                            return@launch
                        }
                    director.playTo(RhineMotionSpec.EditorMs.toFloat(), reduced)
                    if (token == director.epoch) director.stage = RhineStage.Editing
                }
        } else {
            // Sections keep their own editor without the cinematic transition.
            editorSession += 1
            loading = true
            scope.launch {
                try {
                    edit = loadEdit(slot)
                    if (slot.kind != RhineBlockKind.Placeholder) {
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
        if (session.mode != RhineMode.Detail || blocked || browser.interacting || repo == null)
            return
        val slot = input.slot(input.focusU.roundToInt(), input.focusV)
        if (slot.column == null) return
        editorSession += 1
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
    /** The object is stored already; a failed illustration write only warns. */
    suspend fun applyIllustration(owner: IllustrationOwner, id: Long) {
        val change = illustrationDraft?.change ?: return
        try {
            illustrations?.apply(owner, id, change)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            notice = "已保存，插图未保存，请重试"
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
                if (director.stage == RhineStage.Editing) {
                    // The editor leaves by descending into the committed object's place.
                    deleting = false
                    val from = targetBrowser.camera
                    target?.let { (catalog, _) -> targetBrowser.updateBounds(catalog.bounds) }
                    exitEditor(
                        RhineExit(
                            from,
                            target?.second?.camera ?: from,
                            hideId = if (create) id else null,
                            dissolve = remove,
                        ),
                        targetBrowser,
                    ) {
                        target?.let { (_, snapshot) -> targetBrowser.restore(snapshot) }
                    }
                    return@launch
                }
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
                    notice = "数据已保存；定位已刷新，请重新选择对象"
                    targetBrowser.forgetItem()
                    deleting = false
                    if (director.stage == RhineStage.Editing)
                        exitEditor(RhineExit(targetBrowser.camera, targetBrowser.camera), targetBrowser)
                    else edit = null
                } else error = e.message ?: "提交失败，请重试；草稿已保留"
            } finally {
                upper.reconciling = false
                lower.reconciling = false
                saving = false
            }
        }
    }
    // After the activity was recreated (e.g. by a gallery app opened in portrait), reopen the editor
    // that was open, already raised and turned, with its saved draft. Its form state comes back by
    // itself because the editor is keyed by the same session.
    LaunchedEffect(Unit) {
        val restore = editorRestore
        if (restore == null || repo == null) {
            illustrationDraft = null
            return@LaunchedEffect
        }
        val layer = if (restore.sections) RhineLayer.Sections else RhineLayer.Categories
        val ready =
            withTimeoutOrNull(5000) {
                snapshotFlow { latestInput.value }
                    .first { it.layer == layer && it.columns.isNotEmpty() }
            }
        val slot = ready?.slot(restore.u, restore.v)
        val loaded =
            slot?.let {
                try {
                    loadEdit(it, layer)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
        if (slot == null || loaded == null || loaded.storedId() != restore.id || edit != null) {
            editorRestore = null
            illustrationDraft = null
            if (loaded == null || loaded.storedId() != restore.id) notice = "对象已变化，编辑已关闭"
            return@LaunchedEffect
        }
        director.begin(RhineStage.EditorEnter, slot)
        director.ms = RhineMotionSpec.EditorMs.toFloat()
        director.stage = RhineStage.Editing
        if (illustrationDraft == null) {
            val owner =
                if (loaded is RhineEdit.CategoryDraft) IllustrationOwner.Category
                else IllustrationOwner.Task
            illustrationDraft =
                IllustrationDraft(
                    if (restore.id == 0L) null else illustrations?.illustration(owner, restore.id)
                )
        }
        edit = loaded
    }
    DraftBackHandler(enabled = (edit == null && !loading) || director.active) {
        when (director.stage) {
            RhineStage.NavEnter,
            RhineStage.EditorExit -> return@DraftBackHandler
            RhineStage.EditorEnter,
            RhineStage.Editing -> {
                cancelEditor()
                return@DraftBackHandler
            }
            RhineStage.Idle -> Unit
        }
        if (adjustment.submitting) return@DraftBackHandler
        if (adjustment.move != null) cancelAdjustment()
        else if (search != null) closeSearch()
        else if (session.parent != null) returnToParent() else if (!transition) onOpenTasks()
    }
    // The toolbar and toasts sample the scene as their glass backdrop, unless glass blur is off for
    // performance (reduced shadow quality or reduced motion).
    val backdrop = rememberGlassBackdrop()
    // Transition frames are computed at draw time (canvas, HUD and panel layers), so the clock
    // never recomposes this screen.
    val densityObj = LocalDensity.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val safe = rhineSafeDrawingInsets()
    val imeBottom = WindowInsets.ime.getBottom(densityObj)
    val panelFraction = rhineEditorPanelFraction(viewport.width / density, densityObj.fontScale)
    val margin = 16f * density
    val framer =
        RhineFramer(
            geometry,
            density,
            viewport,
            androidx.compose.ui.geometry.Rect(
                safe.getLeft(densityObj, layoutDirection) + margin,
                safe.getTop(densityObj) + margin,
                0f,
                maxOf(safe.getBottom(densityObj), imeBottom) + margin,
            ),
            panelFraction,
        )
    val draftIndex =
        director.slot?.let { slot ->
            when (slot.kind) {
                RhineBlockKind.Zero -> slot.column?.index ?: 0
                // Taken from the slot captured at entry, so saving does not renumber the preview.
                RhineBlockKind.Placeholder -> (slot.column?.itemCount ?: 0) + 1
                else -> slot.v + 1
            }
        } ?: 0
    val currentDraft = draft ?: edit
    val frameOf: () -> Pair<RhineFrame, RhineRaise?> = {
        framer.frame(director, browser.camera) { rhineDraftSlot(currentDraft, it, draftIndex) }
    }
    // Illustrations: only column-start blocks of the centre column and its neighbours can be tall
    // enough to show one, plus the edited block.
    val illustrationOwner =
        if (input.layer == RhineLayer.Categories) IllustrationOwner.Category else IllustrationOwner.Task
    val nearIds =
        (browser.center - 1..browser.center + 1).mapNotNull { input.columns.getOrNull(it)?.id }.toSet()
    val overview by
        remember(illustrations, illustrationOwner, nearIds) {
                illustrations?.illustrationsFlow(illustrationOwner, nearIds)
                    ?: kotlinx.coroutines.flow.flowOf(emptyMap())
            }
            .collectAsState(emptyMap())
    val overviewBucket = illustrationBucket(geometry.blockLength * density)
    val editorBucket = illustrationBucket(geometry.blockLength * density * 1.6f)
    val editorUri =
        illustrationDraft?.current?.takeIf {
            director.stage != RhineStage.Idle && director.stage != RhineStage.NavEnter
        }
    LaunchedEffect(overview, editorUri, overviewBucket, editorBucket) {
        overview.values.forEach { launch { illustrationCache.load(it, overviewBucket) } }
        editorUri?.let { launch { illustrationCache.load(it, editorBucket) } }
    }
    // The focused Task on the first layer shows its whole illustration once the camera rests
    // (docs/视觉优化/05). Its neighbours are read and decoded ahead, so stepping reveals at once.
    val focusU = input.focusU.roundToInt()
    val nearTasks =
        if (input.layer != RhineLayer.Categories) emptySet()
        else
            input.columns
                .getOrNull(input.focusV)
                ?.items
                ?.filterKeys { it in focusU - 3..focusU + 3 }
                ?.values
                ?.mapTo(HashSet()) { it.id } ?: emptySet()
    val taskImages by
        remember(illustrations, nearTasks) {
                illustrations
                    ?.takeIf { nearTasks.isNotEmpty() }
                    ?.illustrationsFlow(IllustrationOwner.Task, nearTasks)
                    ?: kotlinx.coroutines.flow.flowOf(emptyMap())
            }
            .collectAsState(emptyMap())
    // The collected map outlives a layer switch; Section IDs must never look up Task images.
    fun taskImage(item: RhineItem, layer: RhineLayer) =
        if (layer == RhineLayer.Categories)
            taskImages[item.id]?.let { illustrationCache.bitmap(it, overviewBucket) }
        else null
    LaunchedEffect(taskImages, overviewBucket) {
        taskImages.values.forEach { launch { illustrationCache.load(it, overviewBucket) } }
    }
    val revealed =
        input
            .takeIf { it.focusU == focusU.toFloat() && !transition && !director.active }
            ?.slot(focusU, input.focusV)
            ?.item
            ?.takeIf { adjustment.move == null && taskImage(it, input.layer) != null }
            ?.id
    val reveal = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(revealed, reduced) {
        reveal.snapTo(0f)
        if (revealed != null)
            reveal.animateTo(1f, androidx.compose.animation.core.tween(if (reduced) 0 else 280))
    }
    val illustrationOf: (RhineSlot, Boolean) -> androidx.compose.ui.graphics.ImageBitmap? =
        { slot, raised ->
            if (raised) editorUri?.let { illustrationCache.bitmap(it, editorBucket) }
            else if (slot.kind == RhineBlockKind.Real) slot.item?.let { taskImage(it, input.layer) }
            else
                slot.column
                    ?.takeIf { slot.kind == RhineBlockKind.Zero }
                    ?.let { overview[it.id] }
                    ?.let { illustrationCache.bitmap(it, overviewBucket) }
        }
    val glassBackdrop =
        backdrop.takeIf {
            display.shadow == com.shub39.grit.core.settings.RhineShadow.Standard && !reduced
        }
    Box(modifier.fillMaxSize().background(style.floor)) {
        Box(Modifier.fillMaxSize().glassSource(backdrop)) {
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
                    alpha = (1f - phase.value * 0.38f) * frameOf().first.sceneAlpha
                },
            browser = browser,
            enabled = !blocked,
            onTap = ::activate,
            adjustment = adjustment,
            onPickup = { slot, stable, point -> beginAdjustment(slot, stable, true, point) },
            onDrop = { commitAdjustment(adjustment.target) },
            onCancel = ::cancelAdjustment,
            display = display,
            reduced = reduced,
            transition = frameOf,
            illustration = illustrationOf,
            reveal = { reveal.value },
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
        }
        RhineHud(
            input = input,
            style = style,
            mode = session.mode,
            focusOverride = adjustment.move?.takeIf { !adjustment.positionDialog }?.slot,
            enabled = !blocked,
            parentTitle = upperInput.columns.firstOrNull { it.id == session.categoryId }?.title,
            onBack = if (session.parent != null) ::returnToParent else null,
            onMode = {
                if (!blocked) {
                    cancelAdjustment()
                    session.mode =
                        RhineMode.entries[(session.mode.ordinal + 1) % RhineMode.entries.size]
                    notice =
                        when (session.mode) {
                            RhineMode.Preview -> "预览 · 点按定位，滑动浏览"
                            RhineMode.Detail -> "详情 · 编辑、新增或删除"
                            RhineMode.Adjust -> "调整 · 长按拾起，点按输入序号"
                        }
                }
            },
            onAction = {
                activate(input.slot(input.focusU.roundToInt(), input.focusV), !browser.interacting)
            },
            onDelete = ::requestDelete,
            onSearch = {
                if (!blocked && !adjustment.dragging) {
                    browser.stop()
                    search = RhineSearchState()
                }
            },
            onStart = { navigate(u = 0f) },
            onStepU = { navigate(u = browser.camera.u.roundToInt() + it.toFloat()) },
            onStepV = { navigate(v = browser.center + it.toFloat()) },
            onOpenTasks = {
                if (!blocked) {
                    cancelAdjustment()
                    onOpenTasks()
                }
            },
            onOpenSettings = {
                if (!blocked) {
                    cancelAdjustment()
                    onOpenSettings()
                }
            },
            display = display,
            geometry = geometry,
            backdrop = glassBackdrop,
            // The keyboard of an in-window overlay must not reflow the HUD underneath it.
            modifier =
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = frameOf().first.hudAlpha }
                    .windowInsetsPadding(rhineSafeDrawingInsets().exclude(WindowInsets.ime)),
        )
        if (adjustment.dragging) {
            val m = adjustment.move!!
            GlassToast(
                "${if (m.parent) "整列" else "内容"} · ${m.label}    ${m.source + 1} → ${adjustment.target + 1}",
                Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(rhineSafeDrawingInsets())
                    .padding(top = 12.dp)
                    .widthIn(max = 540.dp),
                glassBackdrop,
            ) {
                TextButton(onClick = ::cancelAdjustment) { Text("取消移动") }
            }
        }
        adjustment.move
            ?.takeIf { adjustment.positionDialog }
            ?.let {
                RhinePositionPanel(
                    it,
                    adjustment.submitting,
                    adjustment.error,
                    ::cancelAdjustment,
                    ::commitAdjustment,
                    initialPosition = adjustment.target,
                    backdrop = glassBackdrop,
                )
            }
        search?.let { state ->
            repo?.let {
                RhineSearchPanel(
                    it,
                    state,
                    session.generation,
                    selecting,
                    ::closeSearch,
                    ::selectResult,
                    backdrop = glassBackdrop,
                )
            }
        }
        // Temporary top information: unchanged texts and timing, on a glass capsule.
        if (adjustment.submitting && !adjustment.positionDialog)
            GlassToast(
                "正在保存排序…",
                Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(rhineSafeDrawingInsets())
                    .padding(12.dp),
                glassBackdrop,
            )
        if (loading || notice != null)
            GlassToast(
                if (loading) "载入详情…" else notice!!,
                Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(rhineSafeDrawingInsets())
                    .padding(12.dp)
                    .widthIn(max = 560.dp),
                glassBackdrop,
            )
        val cinematic = director.stage != RhineStage.Idle && director.stage != RhineStage.NavEnter
        edit?.takeIf { it is RhineEdit.SectionDraft || cinematic }?.let { value ->
          key(editorSession) {
            RhineEditor(
                value,
                saving,
                error,
                { if (cinematic) cancelEditor() else if (!saving) edit = null },
                { category ->
                    submit(create = category.id == 0L, parent = true) {
                        repo!!.upsertCategory(category).also {
                            applyIllustration(IllustrationOwner.Category, it)
                        }
                    }
                },
                { task ->
                    submit(create = task.id == 0L, parent = input.layer == RhineLayer.Sections) {
                        repo!!.upsertTask(task).also { applyIllustration(IllustrationOwner.Task, it) }
                    }
                },
                { section -> submit(create = section.id == 0L) { repo!!.upsertSection(section) } },
                {
                    deleting = true
                    error = null
                },
                panelAlpha = { frameOf().first.panelAlpha },
                panelFraction = panelFraction,
                onDraft = { draft = it },
                extra =
                    illustrationDraft?.let { d ->
                        { IllustrationField(d, illustrations, enabled = !saving) }
                    },
                extraDirty = illustrationDraft?.change?.let { it != IllustrationChange.Keep } == true,
            )
          }
        }
        if (deleting)
            edit?.let { value ->
                GlassAlertDialog(
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
                        GlassButton(
                            enabled = !saving,
                            style = GlassButtonStyle.Danger,
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
                        GlassButton(enabled = !saving, onClick = { deleting = false }) {
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
    adjustment: RhineAdjustment? = null,
    onPickup: (RhineSlot, Boolean, Offset) -> Boolean = { _, _, _ -> false },
    onDrop: () -> Unit = {},
    onCancel: () -> Unit = {},
    display: com.shub39.grit.core.settings.RhineDisplayConfig =
        com.shub39.grit.core.settings.RhineDisplayConfig(),
    reduced: Boolean = display.reduceMotion || !rhineSystemAnimationsEnabled(),
    /** Current transition frame and raised block, read at draw time. */
    transition: () -> Pair<RhineFrame, RhineRaise?> = { RhineFrame() to null },
    /** Decoded illustration for a block (slot, raised), read at draw time. */
    illustration: (RhineSlot, Boolean) -> androidx.compose.ui.graphics.ImageBitmap? = { _, _ -> null },
    /** Fade-in of the focused Task's illustration, read at draw time. */
    reveal: () -> Float = { 0f },
) {
    val measurer = rememberTextMeasurer(cacheSize = 384)
    // Transparent glass blocks, with everything but the selected block blurred (docs/视觉优化/06).
    // Without blur, with Reduce transparency or with low shadows the blocks stay matte and sharp.
    val glassOn =
        blurPossible() &&
            LocalGlassMode.current == GlassMode.Standard &&
            display.shadow == com.shub39.grit.core.settings.RhineShadow.Standard
    val sceneLayer = rememberGraphicsLayer()
    val nearLayer = rememberGraphicsLayer()
    val farLayer = rememberGraphicsLayer()
    val facePath = remember { androidx.compose.ui.graphics.Path() }
    val drawObserver = LocalRhineDrawObserver.current
    val density = LocalDensity.current.density
    val labels =
        remember(input.layer, display) {
            RhineLabels(
                zeroTop = { slot ->
                    val c = slot.column
                    if (c == null) ""
                    else "${if (display.parentNumbers(input.layer)) "00  " else ""}✎  " + c.title
                },
                addParentTop =
                    if (input.layer == RhineLayer.Categories) "+  新建 Category" else "+  新建 Task",
                sideLabel = { c ->
                    (if (display.parentNumbers(input.layer)) indexLabel(c.index) + "  " else "") +
                        c.title
                },
            )
        }
    val currentInput by rememberUpdatedState(input)
    val currentTap by rememberUpdatedState(onTap)
    val currentPickup by rememberUpdatedState(onPickup)
    val currentDrop by rememberUpdatedState(onDrop)
    val currentCancel by rememberUpdatedState(onCancel)
    var canvasSize by remember { mutableStateOf(Size(1f, 1f)) }
    val preview = remember { androidx.compose.animation.core.Animatable(0f) }
    var lift by remember { mutableStateOf(0f) }
    var titlePhase by remember { mutableStateOf(0f) }
    LaunchedEffect(
        display.scrollFocusedTitle,
        reduced,
        input.layer,
        input.focusV,
        input.focusU,
        geometry,
        LocalDensity.current.fontScale,
    ) {
        titlePhase = 0f
        if (display.scrollFocusedTitle && !reduced) {
            val start = withFrameNanos { it }
            while (isActive) {
                titlePhase = ((withFrameNanos { it } - start) / 1_000_000_000f)
            }
        }
    }
    LaunchedEffect(adjustment?.move, adjustment?.target, reduced) {
        if (adjustment?.move != null)
            preview.animateTo(
                adjustment.target.toFloat(),
                androidx.compose.animation.core.tween(if (reduced) 0 else 240),
            )
    }
    LaunchedEffect(adjustment?.dragging, reduced, geometry, density) {
        val move = adjustment?.move
        if (adjustment != null && adjustment.dragging && move != null && browser != null) {
            preview.snapTo(move.source.toFloat())
            var last = withFrameNanos { it }
            val start = last
            while (isActive && adjustment.dragging) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(.05f)
                last = now
                lift =
                    6f +
                        (if (reduced) 0f else 1f) *
                            3f *
                            kotlin.math
                                .sin((now - start) / 900_000_000.0 * 2 * kotlin.math.PI)
                                .toFloat()
                val edge = 48f * density
                val x = adjustment.pointer.x
                val amount =
                    when {
                        x < edge -> -(1f - x / edge).coerceIn(0f, 1f)
                        x > canvasSize.width - edge ->
                            ((x - canvasSize.width + edge) / edge).coerceIn(0f, 1f)
                        else -> 0f
                    }
                if (amount != 0f) {
                    val camera = browser.camera
                    browser.move(
                        if (move.parent)
                            camera.copy(
                                v =
                                    (camera.v + amount * .65f * dt).coerceIn(
                                        0f,
                                        (move.count - 1).toFloat(),
                                    )
                            )
                        else
                            camera.copy(
                                u = (camera.u - amount * 4f * dt).coerceIn(1f, move.count.toFloat())
                            )
                    )
                }
                adjustment.update(
                    RhineProjection(geometry, density, canvasSize, browser.camera),
                    browser.camera,
                    currentInput,
                )
            }
        }
        lift = 0f
    }
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
                    var elapsed = 0L
                    var cancelled = false
                    var picked = false
                    var longPressed = false
                    try {
                        do {
                            val event =
                                if (!longPressed && gesture.axis == null) {
                                    withTimeoutOrNull(
                                        (viewConfiguration.longPressTimeoutMillis - (elapsed))
                                            .coerceAtLeast(1L)
                                    ) {
                                        awaitPointerEvent()
                                    }
                                } else awaitPointerEvent()
                            if (event == null) {
                                longPressed = true
                                picked = hit != null && currentPickup(hit, wasStable, down.position)
                                continue
                            }
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
                            elapsed = change.uptimeMillis - down.uptimeMillis
                            if (!change.pressed) break
                            if (picked) {
                                change.consume()
                                if (adjustment?.dragging != true) {
                                    cancelled = true
                                    break
                                }
                                adjustment.pointer = change.position
                                adjustment.update(projection, browser.camera, currentInput)
                            } else {
                                val delta = gesture.delta(change.positionChange())
                                if (gesture.axis != null) {
                                    change.consume()
                                    browser.move(
                                        RhineCamera(
                                            browser.camera.u + delta.u,
                                            browser.camera.v + delta.v,
                                        )
                                    )
                                }
                            }
                        } while (true)
                        if (picked) {
                            if (cancelled) currentCancel()
                            else if (adjustment?.dragging == true) currentDrop()
                        } else if (cancelled || (longPressed && gesture.axis == null))
                            browser.stop()
                        else if (
                            gesture.axis == null &&
                                hit != null &&
                                hit.kind != RhineBlockKind.Decoration
                        ) {
                            browser.stop()
                            if (!currentTap(hit, wasStable))
                                browser.motion =
                                    scope.launch {
                                        browser.animateTo(
                                            RhineCamera(hit.u.toFloat(), hit.v.toFloat())
                                        )
                                        browser.select(hit)
                                    }
                        } else {
                            val speed = velocity.calculateVelocity()
                            browser.motion =
                                scope.launch {
                                    browser.settle(gesture.velocity(Offset(speed.x, speed.y)))
                                }
                        }
                    } finally {
                        if (picked && adjustment?.dragging == true) currentCancel()
                    }
                }
            }
    Canvas(
        modifier
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .then(gestureModifier)
            .semantics { contentDescription = "Rhine 场景" }
    ) {
        val (frame, raise) = transition()
        val projection =
            RhineProjection(
                geometry,
                density,
                Size(size.width, size.height),
                RhineCamera(input.focusU, input.cameraV),
                frame.pose,
            )
        val drawing =
            if (adjustment?.submitting == true && !adjustment.positionDialog)
                adjustment.frozenInput?.copy(
                    focusU = input.focusU,
                    focusV = input.focusV,
                    cameraV = input.cameraV,
                ) ?: input
            else input
        val layout =
            layoutScene(
                drawing,
                projection,
                adjustment?.move?.takeIf {
                    adjustment.dragging || (adjustment.submitting && !adjustment.positionDialog)
                },
                preview.value,
                if (adjustment?.submitting == true) 6f else lift,
                raise,
            )
        drawObserver?.drawn(layout)
        val revealed = reveal()
        fun DrawScope.scene(pass: RhinePass) =
            drawRhineScene(
                layout,
                drawing,
                style,
                labels,
                measurer,
                display,
                titlePhase,
                facePath,
                contextAlpha = frame.contextAlpha,
                raisedAlpha = frame.selectedAlpha,
                illustration = illustration,
                reveal = revealed,
                glass = glassOn,
                pass = pass,
            )
        if (!glassOn) {
            scene(RhinePass.All)
            return@Canvas
        }
        // The scene without the selected block is recorded once and blurred twice on reduced
        // layers: lightly around the selection, more towards the far columns and the borders. The
        // selected block is then drawn sharp on top, so pictures and text behind or in front of it
        // never cross it.
        val full = IntSize(size.width.toInt(), size.height.toInt())
        // Recording from inside this draw must not hand this scope over as its own density.
        val recordDensity = androidx.compose.ui.unit.Density(density, fontScale)
        sceneLayer.record(recordDensity, layoutDirection, full) { scene(RhinePass.Blurred) }
        fun blurred(
            layer: androidx.compose.ui.graphics.layer.GraphicsLayer,
            scale: Float,
            blur: Dp,
        ) {
            layer.renderEffect =
                BlurEffect(blur.toPx() * scale, blur.toPx() * scale, TileMode.Clamp)
            layer.record(
                recordDensity,
                layoutDirection,
                IntSize(ceil(size.width * scale).toInt(), ceil(size.height * scale).toInt()),
            ) {
                scale(scale, scale, Offset.Zero) { drawLayer(sceneLayer) }
            }
        }
        blurred(nearLayer, NearScale, NearBlur)
        blurred(farLayer, FarScale, FarBlur)
        scale(1f / NearScale, 1f / NearScale, Offset.Zero) { drawLayer(nearLayer) }
        val focus =
            layout.raised?.let { b -> b.top.reduce { a, c -> a + c } / 4f }
                ?: layout.blocks
                    .firstOrNull {
                        it.slot.v == drawing.focusV && it.slot.u == drawing.focusU.roundToInt()
                    }
                    ?.let { b -> b.top.reduce { a, c -> a + c } / 4f }
                ?: Offset(size.width * geometry.anchor.x, size.height * geometry.anchor.y)
        drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint()) }
        scale(1f / FarScale, 1f / FarScale, Offset.Zero) { drawLayer(farLayer) }
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent,
                0.22f to Color.Transparent,
                1f to Color.Black,
                center = focus,
                radius = size.width * 0.6f,
            ),
            blendMode = BlendMode.DstIn,
        )
        drawIntoCanvas { it.restore() }
        scene(RhinePass.Sharp)
    }
}

/** Blur layers are drawn at these fractions of the scene size. */
private const val NearScale = 0.5f
private const val FarScale = 0.25f

/** Blur of the blocks around the selection, and of the far scene. */
private val NearBlur = 3.dp
private val FarBlur = 10.dp

/** Text styles of the HUD; Montserrat for Latin, system fallback for Chinese. */
internal class HudText(val family: FontFamily, val ink: Color) {
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
internal const val Pending = 0.38f

/**
 * Text-only HUD laid on the scene. Positions are fractions of the inset area, taken from the
 * reference image (see 修改文档 8.6).
 */
@Composable
private fun RhineHud(
    input: RhineSceneInput,
    style: RhineStyle,
    mode: RhineMode,
    focusOverride: RhineSlot?,
    enabled: Boolean,
    parentTitle: String?,
    onBack: (() -> Unit)?,
    onMode: () -> Unit,
    onAction: () -> Unit,
    onDelete: () -> Unit,
    onSearch: () -> Unit,
    onStart: () -> Unit,
    onStepU: (Int) -> Unit,
    onStepV: (Int) -> Unit,
    onOpenTasks: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    display: com.shub39.grit.core.settings.RhineDisplayConfig,
    geometry: RhineGeometry,
    backdrop: com.shub39.grit.shared.ui.theme.GlassBackdrop?,
) {
    val t = HudText(FontFamily(Font(Res.font.montserrat)), style.ink)
    val focus = focusOverride ?: input.slot(input.focusU.roundToInt(), input.focusV)
    val column = focus.column
    val isCategories = input.layer == RhineLayer.Categories
    val itemWord = if (isCategories) "TASK" else "SECTION"
    val parentWord = if (isCategories) "CATEGORY" else "TASK"

    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight

        val narrow = w < 700.dp || h < 340.dp || LocalDensity.current.fontScale > 1.35f
        // Room kept under the toolbar: the footer row in the wide HUD, a margin otherwise.
        val footerDp = h.value * 0.025f + if (narrow) 8f else 22f
        val profile = rhineToolbarProfile(geometry, w.value, h.value, narrow, footerDp)
        val toolbar: @Composable (Modifier) -> Unit = { m ->
            RhineToolbar(
                input = input,
                style = style,
                mode = mode,
                focus = focus,
                enabled = enabled,
                parentTitle = parentTitle,
                display = display,
                profile = profile,
                backdrop = backdrop,
                t = t,
                onBack = onBack,
                onAction = onAction,
                onDelete = onDelete,
                onStepU = onStepU,
                onStepV = onStepV,
                modifier = m,
            )
        }

        if (narrow) {
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Grhinet", style = t.style(14.sp, true))
                        Text(
                            if (isCategories) "CATEGORY LAYER" else "SECTION LAYER",
                            style = t.style(8.sp),
                        )
                        Text("MODE  ${mode.name.uppercase()}", style = t.style(9.sp))
                    }
                    HudButton("SEARCH", t, enabled, "搜索 Task 标题", 9.sp, onSearch)
                    Spacer(Modifier.width(12.dp))
                    HudButton(mode.name.uppercase(), t, enabled, "切换预览/详情模式", 9.sp, onMode)
                    Spacer(Modifier.width(12.dp))
                    HudButton("⇤", t, enabled && input.focusU > 0f, "回到开头", 12.sp, onStart)
                    // The footer exits move up here so the toolbar keeps clear of the scene.
                    Spacer(Modifier.width(12.dp))
                    HudButton("TASK ↗", t, enabled, onClick = onOpenTasks, size = 9.sp)
                    Spacer(Modifier.width(8.dp))
                    HudButton("SETTING ↗", t, enabled, onClick = onOpenSettings, size = 9.sp)
                }
                Spacer(Modifier.weight(1f))
                toolbar(Modifier.fillMaxWidth())
            }
            return@BoxWithConstraints
        }

        // Brand block: product, layer, mode.
        Column(Modifier.offset(x = w * 0.03f, y = h * 0.10f)) {
            Text("Grhinet", style = t.style(19.sp, bold = true, tracking = 0.02f))
            Text(
                if (isCategories) "CATEGORY LAYER" else "SECTION LAYER",
                style = t.style(9.sp, alpha = 0.8f),
            )
            Text(
                buildAnnotatedString {
                    append("MODE  ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(mode.name.uppercase())
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
            HudButton(
                "⌕  SEARCH",
                t,
                enabled = enabled,
                description = "搜索 Task 标题",
                onClick = onSearch,
            )
            HudButton(
                "+  ${mode.name.uppercase()}",
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

        // Along-column readout: focused index over the center column's real count.
        Column(
            Modifier.align(Alignment.BottomStart)
                .padding(start = w * 0.03f, bottom = (footerDp + profile.heightDp() + 10f).dp)
        ) {
            Text(itemWord, style = t.style(9.sp, alpha = 0.7f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (display.itemNumbers(input.layer)) indexLabel(input.focusU.roundToInt())
                    else "•",
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

        // Bottom toolbar: replaces the right-side focus panel, the U stepper and the V switch.
        toolbar(
            Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = w * 0.03f)
                .padding(bottom = footerDp.dp)
                .widthIn(max = 980.dp)
                .fillMaxWidth()
        )

        // Status: completed count of the center column, from counts only.
        val done = column?.completedCount ?: 0
        val total = column?.itemCount ?: 0
        Row(
            Modifier.align(Alignment.BottomEnd)
                .padding(end = w * 0.03f, bottom = (footerDp + profile.heightDp() + 12f).dp),
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
            HudButton("TASK ↗", t, enabled = enabled, onClick = onOpenTasks, size = 9.sp)
            Text("   /   ", style = t.style(9.sp, alpha = 0.5f))
            HudButton("SETTING ↗", t, enabled = enabled, onClick = onOpenSettings, size = 9.sp)
        }
    }
}

internal fun focusTitle(focus: RhineSlot, itemWord: String): String =
    when (focus.kind) {
        RhineBlockKind.Real -> focus.item?.let { "${indexLabel(it.index)}   ${it.title}" } ?: "载入中…"
        RhineBlockKind.Zero -> "00   ${focus.column?.title ?: ""}"
        RhineBlockKind.Placeholder -> "＋   尾部追加 $itemWord"
        RhineBlockKind.AddParent -> "+   新建 ${if (itemWord == "SECTION") "TASK" else "CATEGORY"}"
        RhineBlockKind.Decoration -> ""
    }

internal fun focusStatus(focus: RhineSlot): String =
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
internal fun HudButton(
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
internal fun TickBar(count: Int, focus: Int, ink: Color, modifier: Modifier = Modifier) {
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

package com.shub39.grit.shared.ui.rhine

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.shub39.grit.core.tasks.*
import kotlin.math.roundToInt

/** A gesture owns this identity until release; none of these fields are saved across recreation. */
data class RhineMove(
    val id: Long,
    val parent: Boolean,
    val layer: RhineLayer,
    val columnId: Long,
    val scope: MoveScope,
    val before: RhineSnapshot,
    val slot: RhineSlot,
) {
    val source = scope.ids.indexOf(id)
    val count = scope.ids.size
    val label = if (parent) slot.column!!.title else slot.item!!.title
}

fun canAdjustRhine(mode: RhineMode, stable: Boolean, center: Int, slot: RhineSlot) =
    mode == RhineMode.Adjust &&
        stable &&
        slot.v == center &&
        slot.column != null &&
        (slot.kind == RhineBlockKind.Zero ||
            (slot.kind == RhineBlockKind.Real && slot.item != null)) &&
        slot.column.loaded

fun movePosition(text: String, count: Int): Int? =
    text
        .trim()
        .takeIf { it.matches(Regex("[0-9]+")) }
        ?.toIntOrNull()
        ?.takeIf { it in 1..count }
        ?.minus(1)

/** Continuous insertion preview. Real zero blocks are never part of a content permutation. */
fun previewPosition(index: Int, source: Int, target: Float): Float =
    when {
        index == source -> target
        index < source -> index + (index + 1 - target).coerceIn(0f, 1f)
        else -> index - (target - index + 1).coerceIn(0f, 1f)
    }

class RhineAdjustment {
    var move by mutableStateOf<RhineMove?>(null)
    var dragging by mutableStateOf(false)
    var target by mutableStateOf(0)
    var pointer by mutableStateOf(Offset.Zero)
    var origin = Offset.Zero
    var cameraStart = RhineCamera(0f, 0f)
    var submitting by mutableStateOf(false)
    var positionDialog by mutableStateOf(false)
    var frozenInput: RhineSceneInput? = null
    var error by mutableStateOf<String?>(null)
    var token by mutableStateOf(0)

    fun clear() {
        token++
        move = null
        dragging = false
        positionDialog = false
        frozenInput = null
        error = null
    }

    fun update(projection: RhineProjection, camera: RhineCamera, input: RhineSceneInput) {
        val m = move ?: return
        val world =
            projection.unproject(pointer - origin, 0f) - projection.unproject(Offset.Zero, 0f)
        val delta = RhineCamera(world.x / projection.pitchU, world.y / projection.pitchV)
        val offset =
            if (m.parent) delta.v + camera.v - cameraStart.v else delta.u + camera.u - cameraStart.u
        val candidate = (m.source + offset).roundToInt().coerceIn(0, m.count - 1)
        val loaded =
            if (m.parent) input.columns.getOrNull(candidate)?.loaded == true
            else
                input.columns.firstOrNull { it.id == m.columnId }?.items?.get(candidate + 1) != null
        if (candidate == m.source || loaded) target = candidate
    }
}

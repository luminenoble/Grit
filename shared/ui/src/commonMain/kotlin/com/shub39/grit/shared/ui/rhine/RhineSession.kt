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

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import com.shub39.grit.core.tasks.TaskSummary

enum class RhineMode {
    Preview,
    Detail,
}

data class RhineSnapshot(val camera: RhineCamera, val focus: RhineFocus) {
    /** Stable IDs win; missing parents/items fall back to a real neighbour or the empty start. */
    fun resolve(catalog: RhineCatalog, item: TaskSummary?): RhineCamera {
        if (catalog.categories.isEmpty()) return RhineCamera(0f, 0f)
        val parent = item?.categoryId ?: focus.categoryId
        val v =
            catalog.categories.indexOfFirst { it.id == parent }.takeIf { it >= 0 }
                ?: camera.v.toInt().coerceIn(0, catalog.categories.lastIndex)
        val count =
            catalog.counts.firstOrNull { it.categoryId == catalog.categories[v].id }?.count ?: 0
        val u =
            when {
                item != null && catalog.categories[v].id == item.categoryId ->
                    (item.orderIndex + 1).toFloat()
                focus.itemId != null ->
                    camera.u.coerceIn(if (count == 0) 0f else 1f, count.toFloat())
                else -> camera.u
            }
        return catalog.bounds.clamp(RhineCamera(u, v.toFloat()))
    }
}

/** One level of history plus a bounded per-Task session cache. No pixel coordinates are stored. */
class RhineSession {
    var generation: String? = null

    fun invalidate() {
        positions.clear()
        clearParent()
    }

    var mode by mutableStateOf(RhineMode.Preview)
    var parent by mutableStateOf<RhineSnapshot?>(null)
    var categoryId by mutableStateOf<Long?>(null)
    var showingSections by mutableStateOf(false)
    private val positions = linkedMapOf<Long, RhineSnapshot>()

    fun rememberPosition(id: Long, snapshot: RhineSnapshot) {
        positions.remove(id)
        positions[id] = snapshot
        while (positions.size > 32) positions.remove(positions.keys.first())
    }

    fun position(id: Long) = positions[id]

    fun clearParent() {
        parent = null
        categoryId = null
        showingSections = false
    }

    companion object {
        private fun encode(s: RhineSnapshot) =
            listOf(
                s.camera.u.toString(),
                s.camera.v.toString(),
                s.focus.categoryId?.toString() ?: "",
                s.focus.itemId?.toString() ?: "",
                s.focus.u.toString(),
                s.focus.v.toString(),
            )

        private fun decode(s: List<String>) =
            RhineSnapshot(
                RhineCamera(s[0].toFloat(), s[1].toFloat()),
                RhineFocus(s[2].toLongOrNull(), s[3].toLongOrNull(), s[4].toInt(), s[5].toInt()),
            )

        val saver =
            Saver<RhineSession, List<String>>(
                save = { session ->
                    buildList {
                        add(session.generation.orEmpty())
                        add(session.mode.name)
                        add(session.categoryId?.toString() ?: "")
                        add(session.showingSections.toString())
                        add((session.parent != null).toString())
                        session.parent?.let { addAll(encode(it)) }
                        session.positions.forEach { (id, snapshot) ->
                            add(id.toString())
                            addAll(encode(snapshot))
                        }
                    }
                },
                restore = { saved ->
                    // Stage 03 saved the mode first, before the replacement-generation token.
                    val legacy = saved.firstOrNull() in RhineMode.entries.map { it.name }
                    val data = if (legacy) saved else saved.drop(1)
                    RhineSession().apply {
                        generation = if (legacy) null else saved[0].ifEmpty { null }
                        mode = RhineMode.valueOf(data[0])
                        categoryId = data[1].toLongOrNull()
                        showingSections = data[2].toBoolean()
                        var offset = 4
                        if (data[3].toBoolean()) {
                            parent = decode(data.subList(offset, offset + 6))
                            offset += 6
                        }
                        while (offset < data.size) {
                            val id = data[offset++].toLong()
                            rememberPosition(id, decode(data.subList(offset, offset + 6)))
                            offset += 6
                        }
                    }
                },
            )
    }
}

/** The first tap on another column only positions it. A moving center cannot open an editor. */
fun canOpenRhineDetail(mode: RhineMode, stable: Boolean, center: Int, slot: RhineSlot) =
    mode == RhineMode.Detail &&
        stable &&
        slot.v == center &&
        (slot.kind == RhineBlockKind.Zero || slot.kind == RhineBlockKind.Real) &&
        slot.column != null &&
        (slot.kind != RhineBlockKind.Real || slot.item != null)

/** Creation shares the same stable-center gate, but never accepts decoration. */
fun canCreateRhine(mode: RhineMode, stable: Boolean, center: Int, slot: RhineSlot) =
    mode == RhineMode.Detail &&
        stable &&
        slot.v == center &&
        (slot.kind == RhineBlockKind.AddParent ||
            (slot.kind == RhineBlockKind.Placeholder && slot.column != null))

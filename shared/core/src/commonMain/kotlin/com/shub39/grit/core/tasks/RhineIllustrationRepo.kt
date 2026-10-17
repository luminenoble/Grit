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
package com.shub39.grit.core.tasks

import kotlinx.coroutines.flow.Flow

enum class IllustrationOwner {
    Category,
    Task,
}

/**
 * Pending illustration edit carried by a save: leave as is, point at a picked image, or remove.
 * Saves that never touch the picker (e.g. toggling completion) use [Keep].
 */
sealed interface IllustrationChange {
    data object Keep : IllustrationChange

    data class Set(val uri: String) : IllustrationChange

    data object Remove : IllustrationChange
}

/**
 * Optional gallery illustration per Category and Task, stored apart from the business model: the
 * value is a device-local content URI, so it is not part of the JSON backup. Rows disappear with
 * their owner. See docs/视觉优化/03-Rhine图片与最终验收.md.
 */
interface RhineIllustrationRepo {
    /** Illustrations of the given owners only; never reads the whole table for a view. */
    fun illustrationsFlow(owner: IllustrationOwner, ids: Set<Long>): Flow<Map<Long, String>>

    suspend fun illustration(owner: IllustrationOwner, id: Long): String?

    /** Points [id] at [uri], or removes its illustration when [uri] is null. */
    suspend fun setIllustration(owner: IllustrationOwner, id: Long, uri: String?)

    /**
     * Keeps read access to a freshly picked [uri] across restarts. False when the platform refuses
     * (e.g. the persisted-grant limit); the picture must then not be saved.
     */
    suspend fun retain(uri: String): Boolean

    /** Gives up access to [uri] unless a stored illustration still uses it. */
    suspend fun releaseIfUnused(uri: String)
}

/** Applies [change] to the owner that was just saved with [id]. */
suspend fun RhineIllustrationRepo.apply(owner: IllustrationOwner, id: Long, change: IllustrationChange) {
    when (change) {
        IllustrationChange.Keep -> Unit
        is IllustrationChange.Set -> {
            val previous = illustration(owner, id)
            setIllustration(owner, id, change.uri)
            if (previous != null && previous != change.uri) releaseIfUnused(previous)
        }
        IllustrationChange.Remove -> {
            val previous = illustration(owner, id) ?: return
            setIllustration(owner, id, null)
            releaseIfUnused(previous)
        }
    }
}

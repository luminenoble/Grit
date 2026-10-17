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
package com.shub39.grit.tasks.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.room3.withWriteTransaction
import com.shub39.grit.core.tasks.IllustrationOwner
import com.shub39.grit.core.tasks.RhineIllustrationRepo
import com.shub39.grit.tasks.data.database.IllustrationEntity
import com.shub39.grit.tasks.data.database.TaskDatabase
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * Illustrations reference picked images by content URI; nothing is copied. Read access is kept
 * with persistable URI grants, taken when an image is picked and released once no row uses it.
 */
@Single(binds = [RhineIllustrationRepo::class])
class RhineIllustrationRepository(private val context: Context, private val db: TaskDatabase) :
    RhineIllustrationRepo {
    private val dao = db.illustrationDao()
    private val resolver
        get() = context.applicationContext.contentResolver

    override fun illustrationsFlow(
        owner: IllustrationOwner,
        ids: Set<Long>,
    ): Flow<Map<Long, String>> {
        if (ids.isEmpty()) return flowOf(emptyMap())
        val rows =
            when (owner) {
                IllustrationOwner.Category -> dao.categoriesFlow(ids.toList())
                IllustrationOwner.Task -> dao.tasksFlow(ids.toList())
            }
        return rows.map { list -> list.associate { it.ownerId to it.uri } }
    }

    override suspend fun illustration(owner: IllustrationOwner, id: Long): String? =
        when (owner) {
            IllustrationOwner.Category -> dao.forCategory(id)
            IllustrationOwner.Task -> dao.forTask(id)
        }

    override suspend fun setIllustration(owner: IllustrationOwner, id: Long, uri: String?) {
        db.withWriteTransaction {
            when (owner) {
                IllustrationOwner.Category -> dao.clearCategory(id)
                IllustrationOwner.Task -> dao.clearTask(id)
            }
            if (uri != null)
                dao.insert(
                    IllustrationEntity(
                        categoryId = id.takeIf { owner == IllustrationOwner.Category },
                        taskId = id.takeIf { owner == IllustrationOwner.Task },
                        uri = uri,
                        updatedAt = Clock.System.now(),
                    )
                )
        }
    }

    override suspend fun retain(uri: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                resolver.takePersistableUriPermission(
                    uri.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                true
            } catch (_: SecurityException) {
                false
            }
        }

    override suspend fun releaseIfUnused(uri: String) {
        if (dao.uses(uri) > 0) return
        withContext(Dispatchers.IO) {
            try {
                resolver.releasePersistableUriPermission(
                    uri.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {}
        }
    }

    /**
     * Releases grants no illustration uses any more (owner deleted, backup imported, draft
     * abandoned while the process died). Runs once per app start in the background.
     */
    suspend fun reconcile() {
        val used = dao.uris().toSet()
        withContext(Dispatchers.IO) {
            for (grant in resolver.persistedUriPermissions) {
                val uri = grant.uri.toString()
                if (uri !in used)
                    try {
                        resolver.releasePersistableUriPermission(
                            grant.uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    } catch (_: SecurityException) {}
            }
        }
    }
}

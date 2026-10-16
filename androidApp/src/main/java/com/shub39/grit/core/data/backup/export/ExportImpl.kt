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
package com.shub39.grit.core.data.backup.export

import com.shub39.grit.core.settings.backup.ExportRepo
import com.shub39.grit.core.settings.backup.RhineBackup
import com.shub39.grit.core.tasks.TaskRepo
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single(binds = [ExportRepo::class])
class ExportImpl(private val taskRepo: TaskRepo) : ExportRepo {
    override suspend fun exportToJson(): Boolean {
        val snapshot = taskRepo.exportSnapshot()
        val text = withContext(Dispatchers.IO) { RhineBackup.json.encodeToString(snapshot) }
        val file =
            FileKit.openFileSaver(
                suggestedName = "Grhinet-${snapshot.exportedAt.toString().replace(":", "")}",
                defaultExtension = "json",
            )
        if (file == null) return false
        withContext(Dispatchers.IO) { file.writeString(text) }
        return true
    }
}

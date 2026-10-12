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
package com.shub39.grit.core.data.backup.restore

import com.shub39.grit.core.settings.backup.*
import com.shub39.grit.core.tasks.TaskRepo
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.*
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single(binds = [RestoreRepo::class])
class RestoreImpl(private val taskRepo: TaskRepo) : RestoreRepo {
    override suspend fun prepareRestore(): RestorePreview? {
        val file =
            FileKit.openFilePicker(mode = FileKitMode.Single, type = FileKitType.File("json"))
                ?: return null
        return withContext(Dispatchers.IO) { RestorePreview(RhineBackup.decode(file.readString())) }
    }

    override suspend fun restoreData(backup: RhineBackup): RestoreResult =
        try {
            withContext(Dispatchers.IO) { taskRepo.replaceData(backup) }
            RestoreResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RestoreResult.Failure(e.message ?: "导入失败，原数据未改变")
        }
}

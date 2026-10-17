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

import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberRhineImageDecoder(): RhineImageDecoder? {
    val resolver = LocalContext.current.applicationContext.contentResolver
    return remember(resolver) {
        RhineImageDecoder { uri, maxEdge ->
            withContext(Dispatchers.IO) {
                try {
                    val source = ImageDecoder.createSource(resolver, uri.toUri())
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        // Decode straight to the bucket size; never the full-resolution original.
                        val w = info.size.width
                        val h = info.size.height
                        val scale = minOf(1f, maxEdge / maxOf(w, h).toFloat())
                        decoder.setTargetSize(
                            (w * scale).toInt().coerceAtLeast(1),
                            (h * scale).toInt().coerceAtLeast(1),
                        )
                    }
                        .asImageBitmap()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Deleted, revoked or undecodable: the caller shows it as missing.
                    null
                }
            }
        }
    }
}

@Composable
actual fun rememberRhineImagePicker(onPicked: (String) -> Unit): (() -> Unit)? {
    val current = rememberUpdatedState(onPicked)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { current.value(it.toString()) }
        }
    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
}

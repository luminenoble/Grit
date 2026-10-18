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
package com.shub39.grit.shared.ui.components

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

private val SaverJson = Json { ignoreUnknownKeys = true }

/**
 * Saves a serializable draft as JSON, so editor forms survive the activity being recreated (e.g.
 * while a full-screen gallery app is in front in another orientation).
 */
fun <T> jsonSaver(serializer: KSerializer<T>): Saver<T, String> =
    Saver(
        save = { SaverJson.encodeToString(serializer, it) },
        restore = { SaverJson.decodeFromString(serializer, it) },
    )

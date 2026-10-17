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
package com.shub39.grit.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.shub39.grit.core.interfaces.ThemeDatastore
import com.shub39.grit.core.theme.Fonts
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single(binds = [ThemeDatastore::class])
class ThemeDatastoreImpl(private val datastore: DataStore<Preferences>) : ThemeDatastore {
    private val fontKey = stringPreferencesKey("font")

    override fun getFontPrefFlow() =
        datastore.data.map { prefs ->
            Fonts.entries.firstOrNull { it.name == prefs[fontKey] } ?: Fonts.FIGTREE
        }

    override suspend fun setFontPref(font: Fonts) {
        datastore.edit { it[fontKey] = font.name }
    }

    private val reduceTransparencyKey = booleanPreferencesKey("reduce_transparency")

    override fun getReduceTransparencyFlow() =
        datastore.data.map { prefs -> prefs[reduceTransparencyKey] ?: false }

    override suspend fun setReduceTransparency(pref: Boolean) {
        datastore.edit { it[reduceTransparencyKey] = pref }
    }
}

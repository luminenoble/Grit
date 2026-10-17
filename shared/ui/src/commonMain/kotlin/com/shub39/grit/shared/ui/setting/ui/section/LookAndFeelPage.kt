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
package com.shub39.grit.shared.ui.setting.ui.section

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.theme.Fonts
import com.shub39.grit.shared.ui.components.ExpressiveSwitch
import com.shub39.grit.shared.ui.components.detachedItemShape
import com.shub39.grit.shared.ui.components.glassItem
import com.shub39.grit.shared.ui.components.glassListItemColors
import com.shub39.grit.shared.ui.setting.*
import com.shub39.grit.shared.ui.toDisplayString

@Composable
fun LookAndFeelPage(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    isUserSubscribed: Boolean,
    onNavigateToPaywall: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Column {
        TopAppBar(
            title = { Text("外观") },
            navigationIcon = { TextButton(onClick = onNavigateBack) { Text("返回") } },
        )
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                ListItem(
                    headlineContent = { Text("降低透明度") },
                    supportingContent = { Text("玻璃材质改为不透明并关闭背景模糊，提升可读性与性能") },
                    trailingContent = {
                        ExpressiveSwitch(
                            checked = state.theme.reduceTransparency,
                            onCheckedChange = {
                                onAction(SettingsAction.ChangeReduceTransparency(it))
                            },
                        )
                    },
                    colors = glassListItemColors(),
                    modifier = Modifier.padding(bottom = 12.dp).glassItem(detachedItemShape()),
                )
            }
            item { Text("字体", Modifier.padding(8.dp), style = MaterialTheme.typography.titleSmall) }
            items(Fonts.entries.size) { index ->
                val font = Fonts.entries[index]
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(font.toDisplayString(), Modifier.padding(16.dp))
                    RadioButton(
                        selected = state.theme.font == font,
                        onClick = { onAction(SettingsAction.ChangeFontPref(font)) },
                    )
                }
            }
        }
    }
}

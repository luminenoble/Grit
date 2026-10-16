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
package com.shub39.grit.shared.ui.task.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.now
import com.shub39.grit.core.tasks.*
import com.shub39.grit.core.toFormattedString
import com.shub39.grit.shared.ui.theme.readableTitleColor
import grit.shared.ui.generated.resources.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.vectorResource

@Composable
fun TaskCard(
    task: Task,
    dragState: Boolean = false,
    reorderIcon: @Composable () -> Unit,
    is24Hr: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    onDetailsClick: (() -> Unit)? = null,
    onSectionsChange: ((List<Section>) -> Unit)? = null,
) {
    var expanded by remember(task.id) { mutableStateOf(false) }
    Card(
        modifier = modifier,
        shape = shape,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border =
            if (task.isCompleted) BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface)
            else null,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDetailsClick != null)
                IconButton(onClick = onDetailsClick) {
                    Icon(vectorResource(Res.drawable.info), "详情")
                }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.title,
                        color = readableTitleColor(task.titleColor),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (task.isCompleted)
                        Icon(vectorResource(Res.drawable.check_circle), "已完成", Modifier.size(18.dp))
                }
                if (task.description.isNotBlank())
                    Text(task.description, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                if (task.sections.isNotEmpty()) {
                    Text(
                        "${task.sections.count { it.isCompleted }}/${task.sections.size} Section",
                        Modifier.clickable { expanded = !expanded }.padding(vertical = 8.dp),
                    )
                    AnimatedVisibility(expanded) {
                        Column {
                            task.sections.forEachIndexed { index, section ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = section.isCompleted,
                                        enabled = onSectionsChange != null,
                                        onCheckedChange = {
                                            onSectionsChange?.invoke(
                                                task.sections.map {
                                                    if (it.id == section.id) it.toggleCompleted()
                                                    else it
                                                }
                                            )
                                        },
                                    )
                                    Text("${index + 1}.", Modifier.padding(end = 8.dp))
                                    Text(
                                        section.title,
                                        Modifier.weight(1f).basicMarquee(),
                                        maxLines = 1,
                                    )
                                    section.completedOn?.let {
                                        Text(
                                            it.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                task.reminder?.let { reminder ->
                    Text(
                        reminder.toFormattedString(is24Hr),
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (!task.isCompleted && reminder < LocalDateTime.now())
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(dragState) { reorderIcon() }
        }
    }
}

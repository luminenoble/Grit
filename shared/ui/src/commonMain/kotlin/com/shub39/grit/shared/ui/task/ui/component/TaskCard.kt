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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shub39.grit.core.now
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.core.toFormattedString
import com.shub39.grit.shared.ui.theme.GritRadius
import com.shub39.grit.shared.ui.theme.GritSpacing
import com.shub39.grit.shared.ui.theme.gritSemanticColors
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
    shape: Shape = RoundedCornerShape(GritRadius.md),
) {
    val semantic = gritSemanticColors()

    // A task with a reminder in the past that is still not done is overdue.
    val isOverdue = !task.status && task.reminder != null && task.reminder!! < LocalDateTime.now()

    val cardContent by
        animateColorAsState(
            targetValue =
                when (task.status) {
                    true -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                },
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "cardContent",
        )
    val cardContainer by
        animateColorAsState(
            targetValue =
                when (task.status) {
                    true -> MaterialTheme.colorScheme.surfaceContainerHighest
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "cardContainer",
        )
    val cardColors =
        CardDefaults.cardColors(containerColor = cardContainer, contentColor = cardContent)

    // Hairline border for subtle depth (Ant Design style); tinted red when overdue.
    val borderColor by
        animateColorAsState(
            targetValue =
                if (isOverdue) semantic.error
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "cardBorder",
        )

    Card(
        modifier =
            modifier.animateContentSize(
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
            ),
        colors = cardColors,
        shape = shape,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(GritSpacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    textDecoration =
                        if (task.status) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                )

                if (task.reminder != null) {
                    val reminderColor = if (isOverdue) semantic.error else cardContent

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GritSpacing.xs),
                    ) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.alarm),
                            contentDescription = "Reminder",
                            modifier = Modifier.size(12.dp),
                            tint = reminderColor,
                        )

                        Text(
                            text = task.reminder!!.toFormattedString(is24Hr),
                            color = reminderColor,
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight =
                                        if (isOverdue) FontWeight.Medium else FontWeight.Light,
                                ),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = dragState, enter = fadeIn(), exit = fadeOut()) {
                reorderIcon()
            }
        }
    }
}

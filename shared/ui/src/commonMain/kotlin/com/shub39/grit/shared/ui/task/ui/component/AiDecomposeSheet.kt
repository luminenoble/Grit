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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shub39.grit.shared.ui.ai.AiState
import com.shub39.grit.shared.ui.ai.AiTasksViewModel
import com.shub39.grit.shared.ui.components.GritBottomSheet
import org.koin.compose.viewmodel.koinViewModel

/**
 * Bottom sheet that lets the user type a goal and have the AI turn it into a single task with a
 * description and concrete steps, review/deselect the steps, then add it to the current category.
 */
@Composable
fun AiDecomposeSheet(
    onDismiss: () -> Unit,
    onCreateTask: (title: String, description: String, steps: List<String>) -> Unit,
) {
    val viewModel: AiTasksViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    GritBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        }
    ) {
        Text(
            text = "AI 分解任务",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        )

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        when (val s = state) {
            AiState.Idle ->
                Button(
                    onClick = { viewModel.generate(input) },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("生成")
                }

            AiState.Loading -> CircularProgressIndicator()

            is AiState.Error -> {
                Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { viewModel.generate(input) },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重试")
                }
            }

            is AiState.Suggestions -> {
                val plan = s.plan
                val selected =
                    remember(plan) { mutableStateListOf(*Array(plan.steps.size) { true }) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = plan.title,
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )

                    if (plan.description.isNotBlank()) {
                        Text(
                            text = plan.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Capped height so a long AI-generated plan scrolls instead of overflowing
                // the bottom sheet and hiding the "add" button below it.
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(plan.steps) { index, step ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selected[index] = !selected[index] }
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected[index],
                                onCheckedChange = { selected[index] = it },
                            )
                            Text(
                                text = step,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val steps = plan.steps.filterIndexed { index, _ -> selected[index] }
                        onCreateTask(plan.title, plan.description, steps)
                        viewModel.reset()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("添加任务")
                }
            }
        }
    }
}

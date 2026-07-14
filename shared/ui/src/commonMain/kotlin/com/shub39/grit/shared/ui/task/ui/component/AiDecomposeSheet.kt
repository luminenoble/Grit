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
import androidx.compose.foundation.layout.padding
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
 * Bottom sheet that lets the user type a goal and have the AI break it into tasks, review/deselect
 * them, then add the chosen ones to the current category.
 */
@Composable
fun AiDecomposeSheet(onDismiss: () -> Unit, onCreateTasks: (List<String>) -> Unit) {
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
        Text(
            text = "输入一个目标，AI 帮你拆成具体的待办事项",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("例如：筹备周末的生日聚会") },
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
                val selected =
                    remember(s.tasks) { mutableStateListOf(*Array(s.tasks.size) { true }) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    s.tasks.forEachIndexed { index, task ->
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
                                text = task.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val titles =
                            s.tasks.filterIndexed { index, _ -> selected[index] }.map { it.title }
                        if (titles.isNotEmpty()) onCreateTasks(titles)
                        viewModel.reset()
                        onDismiss()
                    },
                    enabled = selected.any { it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("添加所选任务")
                }
            }
        }
    }
}

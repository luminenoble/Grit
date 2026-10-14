package com.shub39.grit.shared.ui.rhine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shub39.grit.core.tasks.*
import kotlinx.coroutines.*

/** Results are tagged by input, page and generation even if a repository ignores cancellation. */
class RhineSearchState {
    var query by mutableStateOf("")
    var page by mutableStateOf(0)
    var retry by mutableStateOf(0)
    var results by mutableStateOf<List<TaskSearchResult>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var version by mutableStateOf(0)

    fun change(value: String) {
        version++
        query = value
        page = 0
        results = emptyList()
        error = null
        loading = value.isNotBlank()
    }

    fun pageBy(delta: Int) {
        version++
        page = (page + delta).coerceAtLeast(0)
        results = emptyList()
        loading = true
    }
}

@Composable
fun RhineSearchPanel(
    repo: TaskRepo,
    state: RhineSearchState,
    generation: String?,
    selecting: Boolean,
    onClose: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val version = state.version
    LaunchedEffect(state.query, state.page, state.retry, generation, version) {
        state.results = emptyList()
        state.error = null
        if (state.query.isBlank()) {
            state.loading = false
            return@LaunchedEffect
        }
        state.loading = true
        val query = state.query.trim()
        val offset = state.page * 50
        try {
            delay(300)
            val result = withContext(Dispatchers.Default) { repo.searchTasks(query, offset, 50) }
            ensureActive()
            if (state.version == version) state.results = result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (state.version == version) state.error = e.message ?: "搜索失败"
        } finally {
            if (state.version == version) state.loading = false
        }
    }
    RhinePanel(onClose) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("搜索 Task 标题", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClose, modifier = Modifier.height(36.dp)) { Text("关闭搜索") }
        }
        OutlinedTextField(
            state.query,
            { state.change(it) },
            enabled = !selecting,
            label = { Text("标题关键词") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            when {
                selecting -> Text("正在定位…")
                state.loading -> Text("搜索中…")
                state.error != null -> {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { state.retry++ }) { Text("重试搜索") }
                }
                state.query.isBlank() -> Text("输入标题关键词，包含已完成 Task")
                state.results.isEmpty() -> Text("没有匹配的 Task")
                else ->
                    state.results.forEach { result ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = !selecting) { onSelect(result.id) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(result.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${result.categoryName} · 第 ${result.orderIndex + 1} 项" +
                                    if (result.isCompleted) " · ✓ 已完成" else " · 未完成"
                            )
                        }
                        HorizontalDivider()
                    }
            }
        }
        if (!keyboardVisible)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    enabled = !selecting && !state.loading && state.page > 0,
                    onClick = { state.pageBy(-1) },
                ) {
                    Text("上一页")
                }
                Text("第 ${state.page + 1} 页 · 每页最多 50 项")
                TextButton(
                    enabled = !selecting && !state.loading && state.results.size == 50,
                    onClick = { state.pageBy(1) },
                ) {
                    Text("下一页")
                }
            }
    }
}

@Composable
fun RhinePositionPanel(
    move: RhineMove,
    submitting: Boolean,
    error: String?,
    onClose: () -> Unit,
    onSubmit: (Int) -> Unit,
    initialPosition: Int = move.source,
) {
    var text by remember(move) { mutableStateOf((initialPosition + 1).toString()) }
    val position = movePosition(text, move.count)
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    RhinePanel(onClose) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "移动到第 N 项",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Row {
                TextButton(
                    enabled = !submitting,
                    onClick = onClose,
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("取消移动")
                }
                TextButton(
                    modifier = Modifier.height(36.dp),
                    enabled = !submitting && position != null,
                    onClick = { position?.let(onSubmit) },
                ) {
                    Text(if (submitting) "提交中…" else "确认移动")
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!keyboardVisible) {
                Text(move.label)
                Text(if (move.parent) "整列顺序 · 同级真实父对象" else "只调整当前父对象内的顺序")
            }
            OutlinedTextField(
                text,
                { text = it },
                enabled = !submitting,
                singleLine = true,
                label = { Text("目标序号（1–${move.count}）") },
                isError = position == null,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            if (position == null)
                Text("请输入 1–${move.count} 的整数", color = MaterialTheme.colorScheme.error)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Fixed actions above a separately scrolling body, including with the landscape IME open. */
@Composable
private fun RhinePanel(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.windowInsetsPadding(com.shub39.grit.shared.ui.rhine.rhineSafeDrawingInsets())
                .imePadding()
                .padding(if (keyboardVisible) 4.dp else 8.dp)
                .widthIn(max = 660.dp)
                .fillMaxWidth()
                .fillMaxHeight(),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                Modifier.padding(if (keyboardVisible) 4.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content,
            )
        }
    }
}

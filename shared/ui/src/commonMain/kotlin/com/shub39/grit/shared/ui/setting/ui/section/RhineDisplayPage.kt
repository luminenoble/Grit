package com.shub39.grit.shared.ui.setting.ui.section

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.settings.*
import com.shub39.grit.shared.ui.components.ExpressiveSwitch
import com.shub39.grit.shared.ui.components.glass.GlassButton
import com.shub39.grit.shared.ui.components.glass.GlassButtonStyle
import com.shub39.grit.shared.ui.components.glass.GlassSegmented
import com.shub39.grit.shared.ui.components.glass.GlassSlider
import com.shub39.grit.shared.ui.components.glass.GlassToolbar
import com.shub39.grit.shared.ui.rhine.*

/** The draft affects only this preview; cancel discards it, save writes one local preference. */
@Composable
fun RhineDisplayPage(
    saved: RhineDisplayConfig,
    saving: Boolean = false,
    message: String? = null,
    onSave: (RhineDisplayConfig) -> Unit,
    onBack: () -> Unit,
    previewInput: RhineSceneInput = rhineSample(RhineSample.Standard),
) {
    var draft by
        rememberSaveable(
            stateSaver = Saver(save = { it.encode() }, restore = { RhineDisplayConfig.decode(it) })
        ) {
            mutableStateOf(saved)
        }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var lastSaved by rememberSaveable { mutableStateOf(saved.encode()) }
    LaunchedEffect(saved) {
        if (saved.encode() != lastSaved) {
            draft = saved
            lastSaved = saved.encode()
        }
    }
    val window = LocalWindowInfo.current.containerSize
    val windowDensity = LocalDensity.current.density
    val sceneWidth = maxOf(window.width, window.height).coerceAtLeast(1) / windowDensity
    val sceneHeight = minOf(window.width, window.height).coerceAtLeast(1) / windowDensity
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(com.shub39.grit.shared.ui.rhine.rhineSafeDrawingInsets())
            .imePadding()
    ) {
        GlassToolbar(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            GlassButton(onClick = onBack, enabled = !saving) { Text("取消") }
            Text(
                "Rhine 显示",
                Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            GlassButton(onClick = { draft = RhineDisplayConfig() }, enabled = !saving) {
                Text("恢复默认")
            }
            GlassButton(
                onClick = { onSave(draft) },
                enabled = !saving,
                style = GlassButtonStyle.Prominent,
            ) {
                Text(if (saving) "保存中…" else "保存")
            }
        }
        if (message != null)
            Text(
                message,
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth >= 600.dp
            val preview: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val d = LocalDensity.current.density
                        val g = draft.geometry(sceneWidth, sceneHeight)
                        val scale =
                            minOf(maxWidth.value / sceneWidth, maxHeight.value / sceneHeight)
                        Box(
                            Modifier.align(Alignment.Center)
                                .clipToBounds()
                                .width((sceneWidth * scale).dp)
                                .height((sceneHeight * scale).dp)
                        ) {
                            CompositionLocalProvider(
                                LocalDensity provides
                                    androidx.compose.ui.unit.Density(
                                        d * scale,
                                        LocalDensity.current.fontScale,
                                    )
                            ) {
                                RhineCanvas(
                                    previewInput,
                                    g,
                                    draft.style(),
                                    Modifier.fillMaxSize(),
                                    display = draft,
                                )
                            }
                        }
                        val counts =
                            visibleFaceCounts(
                                previewInput,
                                g,
                                d,
                                Size(sceneWidth * d, sceneHeight * d),
                            )
                        Text(
                            "实际可见 ${counts.first} 块 / ${counts.second} 列（含边缘）",
                            Modifier.align(Alignment.BottomStart)
                                .background(draft.style().floor)
                                .padding(6.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        "预览：${previewInput.columns.getOrNull(previewInput.focusV)?.title.orEmpty()} · U${previewInput.focusU.toInt()} · 横屏场景 · 原始颜色不变",
                        Modifier.padding(6.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            val controls: @Composable (Modifier) -> Unit = { modifier ->
                Column(
                    modifier
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val tabs = listOf("密度/字号", "块体", "标记/效果")
                    GlassSegmented(
                        options = tabs.indices.toList(),
                        selected = tab,
                        onSelect = { tab = it },
                        label = { tabs[it] },
                    )
                    when (tab) {
                        0 -> {
                            Text("密度是视口目标，不限制真实数据数量。")
                            GlassSegmented(
                                options =
                                    listOf(
                                        RhineDensity.Loose,
                                        RhineDensity.Standard,
                                        RhineDensity.Compact,
                                        RhineDensity.Custom,
                                    ),
                                selected = draft.density,
                                onSelect = { value ->
                                    draft =
                                        if (value == RhineDensity.Custom)
                                            draft.copy(density = RhineDensity.Custom)
                                        else draft.preset(value)
                                },
                                label = { value ->
                                    when (value) {
                                        RhineDensity.Loose -> "宽松"
                                        RhineDensity.Standard -> "标准"
                                        RhineDensity.Compact -> "紧凑"
                                        RhineDensity.Custom -> "自定义尺寸"
                                    }
                                },
                            )
                            if (draft.density != RhineDensity.Custom) {
                                ConfigSlider("目标沿列块数", draft.targetRows, 6f..24f) {
                                    draft = draft.copy(targetRows = it)
                                }
                                ConfigSlider("目标列数", draft.targetColumns, 2f..6f) {
                                    draft = draft.copy(targetColumns = it)
                                }
                            } else Text("自定义按 dp 固定尺寸；可见数量由左侧预览计算。")
                            ConfigSlider("标题字号 sp", draft.topSp, 6f..20f) {
                                draft = draft.copy(topSp = it)
                            }
                            Text(
                                "字号跟随系统字体缩放；几何尺寸只使用 dp。",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        1 -> {
                            Text("调整长、深或间隙会进入自定义；高度在所有密度下生效。")
                            ConfigSlider("块长 dp", draft.length, 100f..360f) {
                                draft = draft.copy(density = RhineDensity.Custom, length = it)
                            }
                            ConfigSlider("块深 dp", draft.depth, 4f..16f) {
                                draft = draft.copy(density = RhineDensity.Custom, depth = it)
                            }
                            ConfigSlider("基础高 dp", draft.base, 8f..48f) {
                                draft = draft.copy(base = it)
                            }
                            ConfigSlider("中心抬升 dp", draft.center, 8f..64f) {
                                draft = draft.copy(center = it)
                            }
                            ConfigSlider("峰值额外高 dp", draft.peak, 0f..120f) {
                                draft = draft.copy(peak = it)
                            }
                            ConfigSlider("峰宽 格", draft.spread, 1f..8f) {
                                draft = draft.copy(spread = it)
                            }
                            ConfigSlider("U 间隙 dp", draft.gapU, 8f..40f) {
                                draft = draft.copy(density = RhineDensity.Custom, gapU = it)
                            }
                            ConfigSlider("V 间隙 dp", draft.gapV, 6f..40f) {
                                draft = draft.copy(density = RhineDensity.Custom, gapV = it)
                            }
                        }
                        else -> {
                            ConfigSwitch("Category 序号", draft.categoryNumbers) {
                                draft = draft.copy(categoryNumbers = it)
                            }
                            ConfigSwitch("Task 序号", draft.taskNumbers) {
                                draft = draft.copy(taskNumbers = it)
                            }
                            ConfigSwitch("Section 序号", draft.sectionNumbers) {
                                draft = draft.copy(sectionNumbers = it)
                            }
                            ConfigSwitch("占位显示父名称", draft.placeholderNames) {
                                draft = draft.copy(placeholderNames = it)
                            }
                            ConfigSwitch("聚焦长标题滚动", draft.scrollFocusedTitle) {
                                draft = draft.copy(scrollFocusedTitle = it)
                            }
                            ConfigSwitch("减少动态效果", draft.reduceMotion) {
                                draft = draft.copy(reduceMotion = it)
                            }
                            Text("阴影质量")
                            GlassSegmented(
                                options = RhineShadow.entries,
                                selected = draft.shadow,
                                onSelect = { draft = draft.copy(shadow = it) },
                                label = { value ->
                                    when (value) {
                                        RhineShadow.Off -> "关闭"
                                        RhineShadow.Low -> "低"
                                        RhineShadow.Standard -> "标准"
                                    }
                                },
                            )
                            Text("插图显示")
                            GlassSegmented(
                                options = RhineImageFit.entries,
                                selected = draft.illustrationFit,
                                onSelect = { draft = draft.copy(illustrationFit = it) },
                                label = { value ->
                                    when (value) {
                                        RhineImageFit.CropTopStart -> "填充·左上"
                                        RhineImageFit.CropCenter -> "填充·居中"
                                        RhineImageFit.Fit -> "完整显示"
                                    }
                                },
                            )
                            Text(
                                "插图显示在 0 号块侧面名称下方，保持比例、不拉伸。",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                "0 号始终保留名称与编辑标记。减少动画时保留焦点、拾起和目标反馈。默认长标题居中省略，滚动只作用于聚焦块。",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Text(
                        "恢复默认只修改此草稿，保存后生效。取消不写入。业务 JSON 备份不包含本机显示参数。",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (wide)
                Row(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(1f).fillMaxHeight())
                    controls(Modifier.width(380.dp).fillMaxHeight())
                }
            else
                Column(Modifier.fillMaxSize()) {
                    preview(Modifier.fillMaxWidth().height(180.dp))
                    controls(Modifier.weight(1f))
                }
        }
    }
}

@Composable
private fun ConfigSlider(
    name: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Text("$name：${(value * 10).toInt() / 10f}", style = MaterialTheme.typography.labelMedium)
    GlassSlider(value = value, onValueChange = onChange, valueRange = range)
}

@Composable
private fun ConfigSwitch(name: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f))
        ExpressiveSwitch(checked = value, onCheckedChange = onChange)
    }
}

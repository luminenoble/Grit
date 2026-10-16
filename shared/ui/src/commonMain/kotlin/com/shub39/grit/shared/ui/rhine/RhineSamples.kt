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

/**
 * In-memory scenes for the stage 01 visual baseline. They never touch the repository; stage 02
 * replaces this source with windowed repository summaries producing the same [RhineSceneInput].
 */
enum class RhineSample {
    Standard,
    LongTitleColor,
    ZeroAndPlaceholders,
}

private fun column(
    id: Long,
    index: Int,
    title: String,
    tasks: List<String>,
    color: String? = null,
    taskColors: Map<Int, String> = emptyMap(),
    completed: Set<Int> = emptySet(),
): RhineColumn =
    RhineColumn(
        id = id,
        index = index,
        title = title,
        titleColor = color,
        itemCount = tasks.size,
        items =
            tasks.withIndex().associate { (i, t) ->
                val n = i + 1
                n to RhineItem(id * 1000 + n, n, t, taskColors[n], n in completed)
            },
    )

fun rhineSample(sample: RhineSample): RhineSceneInput =
    when (sample) {
        RhineSample.Standard ->
            RhineSceneInput(
                layer = RhineLayer.Categories,
                columns =
                    listOf(
                        column(
                            1,
                            1,
                            "学习",
                            listOf(
                                "Kotlin 协程",
                                "Compose 绘制",
                                "线性代数",
                                "英语阅读",
                                "算法练习",
                                "论文笔记",
                                "日语五十音",
                            ),
                            completed = setOf(1),
                        ),
                        column(
                            2,
                            2,
                            "工作",
                            listOf(
                                "季度报告",
                                "接口评审",
                                "迁移数据库",
                                "周会纪要",
                                "性能排查",
                                "发布 6.1",
                                "招聘面试",
                                "文档整理",
                            ),
                        ),
                        column(
                            3,
                            3,
                            "生活",
                            listOf("整理书架", "体检预约", "健身计划", "旅行清单", "买菜"),
                            completed = setOf(2),
                        ),
                        column(
                            4,
                            4,
                            "阅读",
                            listOf("三体", "百年孤独", "人类简史", "置身事内", "长安的荔枝", "额尔古纳河右岸"),
                        ),
                        column(5, 5, "项目", listOf("Rrit_Rhine", "个人网站", "照片归档", "家庭账本")),
                    ),
                focusV = 1,
                focusU = 3f,
            )

        RhineSample.LongTitleColor ->
            RhineSceneInput(
                layer = RhineLayer.Categories,
                columns =
                    listOf(
                        column(
                            11,
                            1,
                            "长期研究与写作计划（含季度目标）",
                            listOf(
                                "为毕业论文整理全部实验数据并重新绘制所有对比图表",
                                "阅读 Compose 渲染管线源码",
                                "短标题",
                                "English long title for clipping check on narrow faces",
                                "每周复盘",
                            ),
                            color = "#2F5D8A",
                            taskColors = mapOf(1 to "#B23A48", 2 to "#2E7D5B", 4 to "#7A4FB0"),
                        ),
                        column(
                            12,
                            2,
                            "浅色文字测试",
                            listOf(
                                "浅黄色 #F2E6A0",
                                "浅青色 #BFEDEA",
                                "白色 #FFFFFF",
                                "默认深灰",
                                "橙色 #E8833A",
                                "浅粉 #F6C6D0",
                            ),
                            color = "#F4E3B2",
                            taskColors =
                                mapOf(
                                    1 to "#F2E6A0",
                                    2 to "#BFEDEA",
                                    3 to "#FFFFFF",
                                    5 to "#E8833A",
                                    6 to "#F6C6D0",
                                ),
                        ),
                        column(
                            13,
                            3,
                            "颜色互相独立的类别",
                            listOf("Task 未设颜色", "Task 设为绿色", "Task 设为蓝色", "已完成项目"),
                            color = "#8A3B2F",
                            taskColors = mapOf(2 to "#3B7A3B", 3 to "#2B5797"),
                            completed = setOf(4),
                        ),
                        column(
                            14,
                            4,
                            "数字与符号 2026/10/08 #01",
                            listOf("v6.1.0-rc.1 发布检查", "A/B 测试", "100% 覆盖"),
                        ),
                    ),
                focusV = 1,
                focusU = 2f,
            )

        RhineSample.ZeroAndPlaceholders ->
            RhineSceneInput(
                layer = RhineLayer.Categories,
                columns =
                    listOf(
                        column(21, 1, "收件箱", listOf("回复邮件", "预约维修", "报销单")),
                        column(22, 2, "空类别", emptyList()),
                        column(23, 3, "只有一个", listOf("唯一的 Task")),
                    ),
                // Columns are short; the center column shows its zero block and placeholders, the
                // add-category column and out-of-range decoration fill the rest of the screen.
                focusV = 1,
                focusU = 0f,
            )
    }

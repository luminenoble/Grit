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
package com.shub39.grit.core.settings.backup

import com.shub39.grit.core.tasks.*
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class RhineBackup(
    val format: String,
    val formatVersion: Int,
    val exportedAt: Instant,
    val categories: List<Category>,
    val tasks: List<Task>,
) {
    fun validated(): RhineBackup {
        require(format == FORMAT && formatVersion == VERSION) { "只支持 Grit_Rhine 新格式版本 1" }
        require(
            categories.all { it.id > 0 } &&
                categories.map { it.id }.distinct().size == categories.size
        )
        require(tasks.all { it.id > 0 } && tasks.map { it.id }.distinct().size == tasks.size)
        fun order(indices: List<Int>) =
            require(indices.sorted() == indices.indices.toList()) { "同级排序必须连续" }
        order(categories.map { it.orderIndex })
        categories.forEach {
            require(it.name.isNotBlank())
            validateTitleColor(it.titleColor)
            require(it.updatedAt >= it.createdAt)
        }
        val categoryIds = categories.map { it.id }.toSet()
        tasks.forEach { task ->
            require(task.categoryId in categoryIds && task.title.isNotBlank())
            validateTitleColor(task.titleColor)
            require(task.updatedAt >= task.createdAt)
            order(task.sections.map { it.orderIndex })
        }
        tasks.groupBy { it.categoryId }.values.forEach { order(it.map { task -> task.orderIndex }) }
        val sections = tasks.flatMap { it.sections }
        require(sections.all { it.id > 0 && it.updatedAt >= it.createdAt })
        require(sections.map { it.id }.distinct().size == sections.size)
        return copy(
            tasks =
                tasks.map { task ->
                    task.copy(
                        sections = task.sections.map { it.normalized().copy(taskId = task.id) }
                    )
                }
        )
    }

    companion object {
        const val FORMAT = "grit_rhine"
        const val VERSION = 1
        val json = Json {
            encodeDefaults = true
            prettyPrint = true
        }

        fun decode(text: String): RhineBackup {
            val objectValue = json.parseToJsonElement(text).jsonObject
            fun required(o: JsonObject, keys: List<String>) {
                require(keys.all { it in o }) { "备份缺少必要字段" }
            }
            required(
                objectValue,
                listOf("format", "formatVersion", "exportedAt", "categories", "tasks"),
            )
            objectValue.getValue("categories").jsonArray.forEach {
                required(
                    it.jsonObject,
                    listOf("id", "name", "orderIndex", "createdAt", "updatedAt"),
                )
            }
            objectValue.getValue("tasks").jsonArray.forEach { value ->
                val task = value.jsonObject
                required(
                    task,
                    listOf(
                        "id",
                        "categoryId",
                        "title",
                        "orderIndex",
                        "createdAt",
                        "updatedAt",
                        "sections",
                    ),
                )
                task.getValue("sections").jsonArray.forEach {
                    required(
                        it.jsonObject,
                        listOf("id", "orderIndex", "completedOn", "createdAt", "updatedAt"),
                    )
                }
            }
            return json.decodeFromJsonElement<RhineBackup>(objectValue).validated()
        }
    }
}

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
package com.shub39.grit.shared.ui.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

actual fun createTaskPlanner(): TaskPlanner = DeepSeekTaskPlanner()

/**
 * Calls the DeepSeek chat/completions endpoint (OpenAI-compatible) with plain HttpURLConnection.
 */
private class DeepSeekTaskPlanner : TaskPlanner {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun decompose(summary: String): PlannedTaskPlan =
        withContext(Dispatchers.IO) {
            val key = AiKeyHolder.apiKey
            require(key.isNotBlank()) { "AI 服务未配置密钥" }

            val system =
                "You are a task-planning assistant. Turn the user's goal into ONE actionable " +
                    "task with concrete sub-steps. Reply ONLY with a JSON object of the form " +
                    "{\"title\": \"short task title\", \"description\": \"one or two sentences of " +
                    "context\", \"steps\": [\"step 1\", \"step 2\"]}. Provide 3-8 steps ordered " +
                    "logically, each a short imperative phrase. Write everything in the SAME " +
                    "language as the user's goal. No commentary."

            val payload =
                buildJsonObject {
                        put("model", "deepseek-v4-flash")
                        put("temperature", 0.3)
                        put("stream", false)
                        putJsonObject("response_format") { put("type", "json_object") }
                        putJsonArray("messages") {
                            addJsonObject {
                                put("role", "system")
                                put("content", system)
                            }
                            addJsonObject {
                                put("role", "user")
                                put("content", summary)
                            }
                        }
                    }
                    .toString()

            val conn =
                (URL("https://api.deepseek.com/chat/completions").openConnection()
                        as HttpURLConnection)
                    .apply {
                        requestMethod = "POST"
                        connectTimeout = 30_000
                        readTimeout = 60_000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Authorization", "Bearer $key")
                    }

            val code =
                try {
                    conn.outputStream.use { it.write(payload.encodeToByteArray()) }
                    conn.responseCode
                } catch (e: Exception) {
                    conn.disconnect()
                    throw IllegalStateException("网络请求失败：${e.message}", e)
                }

            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()

            if (code !in 200..299) {
                error("DeepSeek 返回 HTTP $code：${body.take(300)}")
            }

            val content =
                json
                    .parseToJsonElement(body)
                    .jsonObject["choices"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("message")
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonPrimitive
                    ?.content ?: error("响应内容为空")

            val plan = json.parseToJsonElement(content).jsonObject

            val title =
                plan["title"]?.asText()?.trim().orEmpty().ifBlank { error("响应中没有找到 title 字段") }
            val description = plan["description"]?.asText()?.trim().orEmpty()
            val steps =
                plan["steps"]
                    ?.jsonArray
                    .orEmpty()
                    .mapNotNull { it.asText()?.trim() }
                    .filter { it.isNotEmpty() }

            PlannedTaskPlan(title = title, description = description, steps = steps)
        }

    /** Accepts either a plain string entry or an object with a title/task/name field. */
    private fun JsonElement.asText(): String? =
        when (this) {
            is JsonPrimitive -> contentOrNull
            is JsonObject ->
                (this["title"] ?: this["task"] ?: this["name"])?.jsonPrimitive?.contentOrNull
            else -> null
        }
}

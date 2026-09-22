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
package com.shub39.grit.shared.ui.update

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

actual fun createUpdateChecker(): UpdateChecker = GithubUpdateChecker()

/** Reads the releases/latest endpoint of the GitHub API with plain HttpURLConnection. */
private class GithubUpdateChecker : UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun latestRelease(): AppRelease? =
        withContext(Dispatchers.IO) {
            val conn =
                (URL("https://api.github.com/repos/$UPDATE_REPO/releases/latest").openConnection()
                        as HttpURLConnection)
                    .apply {
                        requestMethod = "GET"
                        connectTimeout = 15_000
                        readTimeout = 15_000
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                        // GitHub rejects API requests without a user agent.
                        setRequestProperty("User-Agent", "Grit-Android")
                    }

            val code =
                try {
                    conn.responseCode
                } catch (e: Exception) {
                    conn.disconnect()
                    throw IllegalStateException("网络请求失败：${e.message}", e)
                }

            // A repository without any published release answers 404; that is not an error.
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                conn.disconnect()
                return@withContext null
            }

            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()

            when {
                code == 403 || code == 429 -> error("GitHub 接口限流，请稍后再试")
                code !in 200..299 -> error("GitHub 返回 HTTP $code：${body.take(200)}")
            }

            val release = json.parseToJsonElement(body).jsonObject

            fun field(name: String): String? =
                release[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

            val tag = field("tag_name") ?: error("响应中没有找到 tag_name 字段")

            AppRelease(
                versionName = tag.removePrefix("v").removePrefix("V"),
                title = field("name") ?: tag,
                notes = field("body").orEmpty().trim(),
                pageUrl = field("html_url") ?: LATEST_RELEASE_URL,
            )
        }
}

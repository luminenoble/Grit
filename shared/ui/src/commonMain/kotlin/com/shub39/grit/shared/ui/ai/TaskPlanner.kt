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

/**
 * Holds the AI service key, injected once at app startup (from BuildConfig on Android) so the
 * secret is never committed to source control. Empty on platforms/builds where AI is not
 * configured.
 */
object AiKeyHolder {
    var apiKey: String = ""
}

/** A single task suggested by the AI, before it is persisted. */
data class PlannedTask(val title: String)

/** Breaks a free-form goal into a list of concrete, actionable task titles. */
interface TaskPlanner {
    /** @throws Exception on network / auth / parsing failure. */
    suspend fun decompose(summary: String): List<PlannedTask>
}

/** Platform factory: real DeepSeek implementation on Android, a stub elsewhere. */
expect fun createTaskPlanner(): TaskPlanner

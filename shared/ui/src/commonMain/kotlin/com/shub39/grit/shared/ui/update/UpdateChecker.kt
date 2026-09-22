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

/** GitHub repository the update check reads releases from. */
const val UPDATE_REPO = "luminenoble/Grit"

/** Releases page opened when the user wants to download a newer build. */
const val LATEST_RELEASE_URL = "https://github.com/$UPDATE_REPO/releases/latest"

/** The newest release published on GitHub. */
data class AppRelease(
    /** Tag name without a leading "v", e.g. "6.0.5". */
    val versionName: String,
    val title: String,
    val notes: String,
    /** Release page to open in a browser; downloads hang off it. */
    val pageUrl: String,
)

/**
 * Version name of the running build, injected at startup (from BuildConfig on Android) because the
 * shared module has no access to platform package info. Blank where it was never set.
 */
object AppVersionHolder {
    var versionName: String = ""
}

/** Reads the latest release of [UPDATE_REPO]. */
interface UpdateChecker {
    /**
     * @return the newest release, or null when the repository has no releases yet.
     * @throws Exception on network / parsing failure.
     */
    suspend fun latestRelease(): AppRelease?
}

/** Platform factory: a real GitHub check on Android, a stub elsewhere. */
expect fun createUpdateChecker(): UpdateChecker

/** Outcome of a manual update check, surfaced in the settings page. */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState

    data object Checking : UpdateCheckState

    data class UpToDate(val versionName: String) : UpdateCheckState

    /** The repository exists but has no published release to compare against. */
    data object NoReleases : UpdateCheckState

    data class Available(val release: AppRelease) : UpdateCheckState

    data class Failed(val message: String) : UpdateCheckState
}

/**
 * True when [candidate] is a newer version than [current]. Both are compared as dotted numbers,
 * ignoring a leading "v" and any suffix ("6.0.4-play" counts as 6.0.4), so 6.0.10 sorts above
 * 6.0.9. Unparseable input is treated as "not newer" so a malformed tag never nags the user.
 */
fun isNewerVersion(candidate: String, current: String): Boolean {
    val new = versionParts(candidate)
    val old = versionParts(current)
    if (new.isEmpty() || old.isEmpty()) return false

    for (i in 0 until maxOf(new.size, old.size)) {
        val a = new.getOrElse(i) { 0 }
        val b = old.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

private fun versionParts(version: String): List<Int> =
    version
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .takeWhile { it.isDigit() || it == '.' }
        .split('.')
        .mapNotNull { it.toIntOrNull() }

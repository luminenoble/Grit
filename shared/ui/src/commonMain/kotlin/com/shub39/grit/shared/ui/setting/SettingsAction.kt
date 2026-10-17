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
package com.shub39.grit.shared.ui.setting

import com.shub39.grit.core.settings.Sections
import com.shub39.grit.core.settings.WidgetTextSize
import com.shub39.grit.core.theme.Fonts

sealed interface SettingsAction {
    data class SaveRhineDisplay(val config: com.shub39.grit.core.settings.RhineDisplayConfig) :
        SettingsAction

    data object OnResetBackupState : SettingsAction

    data object OnExport : SettingsAction

    data object OnRestore : SettingsAction

    data object OnConfirmRestore : SettingsAction

    data object OnCancelRestore : SettingsAction

    data class ChangeIs24Hr(val pref: Boolean) : SettingsAction

    data class ChangeStartingPage(val page: Sections) : SettingsAction

    data class ChangePauseNotifications(val pref: Boolean) : SettingsAction

    data class ChangeReorderTasks(val pref: Boolean) : SettingsAction

    data class ChangeFontPref(val font: Fonts) : SettingsAction

    data class ChangeReduceTransparency(val pref: Boolean) : SettingsAction

    data class ChangeBiometricLock(val pref: Boolean) : SettingsAction

    data class ChangeWidgetTextSize(val size: WidgetTextSize) : SettingsAction

    data object OnCheckForUpdates : SettingsAction

    data object OnDismissUpdateResult : SettingsAction
}

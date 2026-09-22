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
package com.shub39.grit.shared.ui.setting.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shub39.grit.shared.ui.components.GritDialog
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import com.shub39.grit.shared.ui.update.AppRelease
import grit.shared.ui.generated.resources.*
import org.jetbrains.compose.resources.vectorResource

/** Shown after a manual update check finds a newer release on GitHub. */
@Composable
fun UpdateAvailableDialog(
    release: AppRelease,
    currentVersion: String,
    onDownload: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    GritDialog(onDismissRequest = onDismissRequest) {
        Column {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialShapes.Pill.toShape(),
                        ),
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.download),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "发现新版本",
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = flexFontEmphasis()),
            )
            Text(
                text = "${release.title}（当前 ${currentVersion.ifBlank { "未知版本" }}）",
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.primary
                    ),
            )

            if (release.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = release.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onDismissRequest,
                    shapes =
                        ButtonShapes(
                            shape = MaterialTheme.shapes.extraLarge,
                            pressedShape = MaterialTheme.shapes.small,
                        ),
                ) {
                    Text(text = "稍后")
                }

                TextButton(
                    onClick = onDownload,
                    shapes =
                        ButtonShapes(
                            shape = MaterialTheme.shapes.extraLarge,
                            pressedShape = MaterialTheme.shapes.small,
                        ),
                ) {
                    Text(text = "前往下载")
                }
            }
        }
    }
}

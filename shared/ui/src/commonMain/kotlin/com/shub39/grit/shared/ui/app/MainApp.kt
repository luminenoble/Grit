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
package com.shub39.grit.shared.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Companion.Compact
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.shub39.grit.shared.ui.LocalWindowSizeClass
import com.shub39.grit.shared.ui.app.AppSections.Companion.toIconRes
import com.shub39.grit.shared.ui.app.AppSections.Companion.toStringRes
import com.shub39.grit.shared.ui.habit.ui.HabitsGraph
import com.shub39.grit.shared.ui.navigation.fadeTransitionMetadata
import com.shub39.grit.shared.ui.setting.ui.SettingsGraph
import com.shub39.grit.shared.ui.task.ui.TasksPage
import com.shub39.grit.shared.ui.theme.LiquidGlassDefaults
import com.shub39.grit.shared.ui.theme.liquidGlass
import com.shub39.grit.shared.ui.viewmodel.HabitViewModel
import com.shub39.grit.shared.ui.viewmodel.SettingsViewModel
import com.shub39.grit.shared.ui.viewmodel.TasksViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainApp(state: MainAppState, onNavigateToPaywall: () -> Unit) {
    val windowSizeClass = LocalWindowSizeClass.current

    val appBackStack =
        rememberNavBackStack(
            AppSections.configuration,
            when (state.startingSection) {
                Tasks -> AppSections.TaskPages
                Habits -> AppSections.HabitPages
            },
        )

    when (windowSizeClass.widthSizeClass) {
        Compact -> {
            Scaffold(
                bottomBar = {
                    AppNavBar(
                        currentRoute = appBackStack.last(),
                        onNavigate = { route ->
                            appBackStack.removeAll { it == route }
                            appBackStack.add(route)
                        },
                    )
                }
            ) { padding ->
                NavDisplay(
                    modifier =
                        Modifier.padding(
                                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                                bottom = padding.calculateBottomPadding(),
                            )
                            .background(MaterialTheme.colorScheme.background),
                    backStack = appBackStack,
                    entryProvider =
                        entryProvider {
                            entry<AppSections.TaskPages>(metadata = fadeTransitionMetadata()) {
                                val tvm: TasksViewModel = koinViewModel()
                                val taskPageState by tvm.state.collectAsStateWithLifecycle()

                                TasksPage(state = taskPageState, onAction = tvm::onAction)
                            }

                            entry<AppSections.SettingsPages>(metadata = fadeTransitionMetadata()) {
                                val svm: SettingsViewModel = koinViewModel()
                                val settingsState by svm.state.collectAsStateWithLifecycle()

                                SettingsGraph(
                                    state = settingsState,
                                    onAction = svm::onAction,
                                    isUserSubscribed = state.isUserSubscribed,
                                    onNavigateToPaywall = onNavigateToPaywall,
                                )
                            }

                            entry<AppSections.HabitPages>(metadata = fadeTransitionMetadata()) {
                                val hvm: HabitViewModel = koinViewModel()
                                val habitsPageState by hvm.state.collectAsStateWithLifecycle()

                                HabitsGraph(
                                    state = habitsPageState,
                                    onAction = hvm::onAction,
                                    isUserSubscribed = state.isUserSubscribed,
                                    onNavigateToPaywall = onNavigateToPaywall,
                                )
                            }
                        },
                )
            }
        }

        else -> {
            Row(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                AppNavRail(
                    currentRoute = appBackStack.last(),
                    onNavigate = { route ->
                        appBackStack.removeAll { it == route }
                        appBackStack.add(route)
                    },
                )

                NavDisplay(
                    modifier =
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
                    backStack = appBackStack,
                    contentAlignment = Alignment.Center,
                    entryProvider =
                        entryProvider {
                            entry<AppSections.TaskPages>(metadata = fadeTransitionMetadata()) {
                                val tvm: TasksViewModel = koinViewModel()
                                val taskPageState by tvm.state.collectAsStateWithLifecycle()

                                TasksPage(state = taskPageState, onAction = tvm::onAction)
                            }

                            entry<AppSections.SettingsPages>(metadata = fadeTransitionMetadata()) {
                                val svm: SettingsViewModel = koinViewModel()
                                val settingsState by svm.state.collectAsStateWithLifecycle()

                                SettingsGraph(
                                    state = settingsState,
                                    onAction = svm::onAction,
                                    isUserSubscribed = state.isUserSubscribed,
                                    onNavigateToPaywall = onNavigateToPaywall,
                                )
                            }

                            entry<AppSections.HabitPages>(metadata = fadeTransitionMetadata()) {
                                val hvm: HabitViewModel = koinViewModel()
                                val habitsPageState by hvm.state.collectAsStateWithLifecycle()

                                HabitsGraph(
                                    state = habitsPageState,
                                    onAction = hvm::onAction,
                                    isUserSubscribed = state.isUserSubscribed,
                                    onNavigateToPaywall = onNavigateToPaywall,
                                )
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun AppNavRail(
    currentRoute: NavKey,
    onNavigate: (AppSections) -> Unit,
    modifier: Modifier = Modifier,
) {
    val railShape = RoundedCornerShape(28.dp)

    NavigationRail(
        modifier =
            modifier
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                .shadow(elevation = 6.dp, shape = railShape, clip = false)
                .liquidGlass(
                    shape = railShape,
                    fill =
                        MaterialTheme.colorScheme.surfaceContainer.copy(
                            alpha = LiquidGlassDefaults.NAV_ALPHA
                        ),
                ),
        containerColor = Color.Transparent,
    ) {
        AppSections.mainRoutes.forEach { route ->
            NavigationRailItem(
                selected = currentRoute == route,
                onClick = {
                    if (currentRoute != route) {
                        onNavigate(route)
                    }
                },
                icon = {
                    Icon(painter = painterResource(route.toIconRes()), contentDescription = null)
                },
                label = { Text(text = stringResource(route.toStringRes())) },
                alwaysShowLabel = false,
            )
        }
    }
}

/** Floating liquid glass pill, morphing the selected item open like the iOS 26 tab bar. */
@Composable
private fun AppNavBar(
    currentRoute: NavKey,
    onNavigate: (AppSections) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(50)

    Box(
        modifier =
            modifier.fillMaxWidth().navigationBarsPadding().padding(top = 4.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier.shadow(elevation = 8.dp, shape = pill, clip = false)
                    .liquidGlass(
                        shape = pill,
                        fill =
                            MaterialTheme.colorScheme.surfaceContainer.copy(
                                alpha = LiquidGlassDefaults.NAV_ALPHA
                            ),
                    )
                    .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSections.mainRoutes.forEach { route ->
                val selected = currentRoute == route

                val itemBackground by
                    animateColorAsState(
                        targetValue =
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "navItemBackground",
                    )
                val itemContent by
                    animateColorAsState(
                        targetValue =
                            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "navItemContent",
                    )

                Row(
                    modifier =
                        Modifier.clip(pill)
                            .background(itemBackground)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = {
                                    if (!selected) {
                                        onNavigate(route)
                                    }
                                },
                            )
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .animateContentSize(
                                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(route.toIconRes()),
                        contentDescription = stringResource(route.toStringRes()),
                        tint = itemContent,
                    )

                    AnimatedVisibility(visible = selected) {
                        Text(
                            text = stringResource(route.toStringRes()),
                            color = itemContent,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

package com.ddupg.runtrip.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ddupg.runtrip.RunTripApplication
import com.ddupg.runtrip.feature.detail.RaceDetailRoute
import com.ddupg.runtrip.feature.form.RaceFormRoute
import com.ddupg.runtrip.feature.home.HomeRoute
import com.ddupg.runtrip.feature.pace.PaceCalculatorScreen
import com.ddupg.runtrip.feature.tools.ToolsScreen
import com.ddupg.runtrip.navigation.RunTripRoutes
import com.ddupg.runtrip.ui.theme.RunTripTheme

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(
        route = RunTripRoutes.HOME,
        label = "比赛",
        icon = Icons.Outlined.CalendarMonth,
    ),
    TopLevelDestination(
        route = RunTripRoutes.TOOLS,
        label = "工具",
        icon = Icons.Outlined.Build,
    ),
)

@Composable
fun RunTripApp() {
    val application = LocalContext.current.applicationContext as RunTripApplication
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    RunTripTheme {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (RunTripRoutes.isTopLevel(currentRoute)) {
                    RunTripBottomNavigation(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = RunTripRoutes.HOME,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(RunTripRoutes.HOME) {
                    HomeRoute(
                        repository = application.raceRepository,
                        onAddRace = { navController.navigate(RunTripRoutes.ADD_RACE) },
                        onOpenRace = { raceId ->
                            navController.navigate(RunTripRoutes.raceDetail(raceId))
                        },
                    )
                }
                composable(RunTripRoutes.TOOLS) {
                    ToolsScreen(
                        onOpenPaceCalculator = {
                            navController.navigate(RunTripRoutes.PACE_CALCULATOR)
                        },
                    )
                }
                composable(RunTripRoutes.PACE_CALCULATOR) {
                    PaceCalculatorScreen(onBack = { navController.popBackStack() })
                }
                composable(RunTripRoutes.ADD_RACE) {
                    RaceFormRoute(
                        repository = application.raceRepository,
                        raceId = null,
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
                composable(
                    route = RunTripRoutes.RACE_DETAIL_PATTERN,
                    arguments = listOf(
                        navArgument(RunTripRoutes.RACE_ID_ARGUMENT) {
                            type = NavType.StringType
                        },
                    ),
                ) { entry ->
                    val raceId = requireNotNull(
                        entry.arguments?.getString(RunTripRoutes.RACE_ID_ARGUMENT),
                    )
                    RaceDetailRoute(
                        repository = application.raceRepository,
                        raceId = raceId,
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(RunTripRoutes.editRace(raceId)) },
                        onDeleted = { navController.popBackStack() },
                    )
                }
                composable(
                    route = RunTripRoutes.EDIT_RACE_PATTERN,
                    arguments = listOf(
                        navArgument(RunTripRoutes.RACE_ID_ARGUMENT) {
                            type = NavType.StringType
                        },
                    ),
                ) { entry ->
                    val raceId = requireNotNull(
                        entry.arguments?.getString(RunTripRoutes.RACE_ID_ARGUMENT),
                    )
                    RaceFormRoute(
                        repository = application.raceRepository,
                        raceId = raceId,
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

@Composable
private fun RunTripBottomNavigation(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar {
        topLevelDestinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination.route) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

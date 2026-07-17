package com.ddupg.runtrip.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ddupg.runtrip.RunTripApplication
import com.ddupg.runtrip.feature.form.RaceFormRoute
import com.ddupg.runtrip.feature.home.HomeRoute
import com.ddupg.runtrip.navigation.RunTripRoutes
import com.ddupg.runtrip.ui.theme.RunTripTheme

@Composable
fun RunTripApp() {
    val application = LocalContext.current.applicationContext as RunTripApplication
    val navController = rememberNavController()

    RunTripTheme {
        NavHost(
            navController = navController,
            startDestination = RunTripRoutes.HOME,
        ) {
            composable(RunTripRoutes.HOME) {
                HomeRoute(
                    repository = application.raceRepository,
                    onAddRace = { navController.navigate(RunTripRoutes.ADD_RACE) },
                    onOpenRace = {},
                )
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
                route = RunTripRoutes.EDIT_RACE_PATTERN,
                arguments = listOf(
                    navArgument(RunTripRoutes.RACE_ID_ARGUMENT) {
                        type = NavType.StringType
                    },
                ),
            ) { entry ->
                RaceFormRoute(
                    repository = application.raceRepository,
                    raceId = entry.arguments?.getString(RunTripRoutes.RACE_ID_ARGUMENT),
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
        }
    }
}

package com.ddupg.runtrip.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ddupg.runtrip.RunTripApplication
import com.ddupg.runtrip.feature.home.HomeRoute
import com.ddupg.runtrip.ui.theme.RunTripTheme

@Composable
fun RunTripApp() {
    val application = LocalContext.current.applicationContext as RunTripApplication

    RunTripTheme {
        HomeRoute(
            repository = application.raceRepository,
            onAddRace = {},
            onOpenRace = {},
        )
    }
}

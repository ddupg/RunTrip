package com.ddupg.runtrip.navigation

object RunTripRoutes {
    const val HOME = "home"
    const val ADD_RACE = "race/new"
    const val RACE_ID_ARGUMENT = "raceId"
    const val EDIT_RACE_PATTERN = "race/{$RACE_ID_ARGUMENT}/edit"

    fun editRace(raceId: String): String = "race/$raceId/edit"
}

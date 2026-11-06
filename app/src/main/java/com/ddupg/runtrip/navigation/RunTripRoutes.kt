package com.ddupg.runtrip.navigation

object RunTripRoutes {
    const val HOME = "home"
    const val TRAINING = "training"
    const val TOOLS = "tools"
    const val ME = "me"
    const val TRAINING_PREFERENCES = "me/training-preferences"
    const val PACE_CALCULATOR = "tools/pace-calculator"
    const val RACE_ID_ARGUMENT = "raceId"
    const val RACE_DETAIL_PATTERN = "race/{$RACE_ID_ARGUMENT}/detail"
    const val EDIT_RACE_PATTERN = "race/{$RACE_ID_ARGUMENT}/edit"

    fun raceDetail(raceId: String): String = "race/$raceId/detail"

    fun editRace(raceId: String): String = "race/$raceId/edit"

    fun isTopLevel(route: String?): Boolean = route == HOME || route == TRAINING || route == TOOLS || route == ME
}

package com.ddupg.runtrip.navigation

object RunTripRoutes {
    const val HOME = "home"
    const val TOOLS = "tools"
    const val PACE_CALCULATOR = "tools/pace-calculator"
    const val ADD_RACE = "race/new"
    const val RACE_ID_ARGUMENT = "raceId"
    const val RACE_DETAIL_PATTERN = "race/{$RACE_ID_ARGUMENT}/detail"
    const val EDIT_RACE_PATTERN = "race/{$RACE_ID_ARGUMENT}/edit"

    fun raceDetail(raceId: String): String = "race/$raceId/detail"

    fun editRace(raceId: String): String = "race/$raceId/edit"

    fun isTopLevel(route: String?): Boolean = route == HOME || route == TOOLS
}

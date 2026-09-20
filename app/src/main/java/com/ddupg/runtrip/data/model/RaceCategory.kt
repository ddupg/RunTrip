package com.ddupg.runtrip.data.model

sealed interface RaceCategory {
    val code: String
    val sportType: SportType
}

enum class SportType(val code: String) {
    ROAD_RUNNING("ROAD_RUNNING"), TRIATHLON("TRIATHLON");
    companion object {
        fun fromCode(code: String): SportType = entries.first { it.code == code }
    }
}

sealed interface RaceDetails {
    val category: RaceCategory
    val sportType: SportType get() = category.sportType
}

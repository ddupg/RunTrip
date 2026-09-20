package com.ddupg.runtrip.data.model

enum class TriathlonCategory(override val code: String) : RaceCategory {
    SPRINT("SPRINT"), STANDARD("STANDARD"), OTHER("OTHER");
    override val sportType: SportType get() = SportType.TRIATHLON
    companion object {
        fun fromCode(code: String): TriathlonCategory = entries.first { it.code == code }
    }
}

data class TriathlonDetails(override val category: TriathlonCategory) : RaceDetails

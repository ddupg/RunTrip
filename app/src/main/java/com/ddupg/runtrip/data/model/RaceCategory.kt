package com.ddupg.runtrip.data.model

enum class RaceCategory(
    val code: String,
) {
    MARATHON("MARATHON"),
    HALF_MARATHON("HALF_MARATHON"),
    TEN_K("TEN_K"),
    OTHER("OTHER"),
    ;

    companion object {
        fun fromCode(code: String): RaceCategory =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown race category code: $code")
    }
}

package com.ddupg.runtrip.data.model

enum class RaceStatus(
    val code: String,
) {
    WATCHING("WATCHING"),
    REGISTRATION_PENDING("REGISTRATION_PENDING"),
    DRAW_PENDING("DRAW_PENDING"),
    DRAW_WON("DRAW_WON"),
    DRAW_LOST("DRAW_LOST"),
    REGISTERED("REGISTERED"),
    WITHDRAWN("WITHDRAWN"),
    FINISHED("FINISHED"),
    ;

    companion object {
        fun fromCode(code: String): RaceStatus =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown race status code: $code")
    }
}

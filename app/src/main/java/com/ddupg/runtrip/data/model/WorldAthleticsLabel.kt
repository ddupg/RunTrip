package com.ddupg.runtrip.data.model

enum class WorldAthleticsLabel(
    val code: String,
) {
    PLATINUM("PLATINUM"),
    GOLD("GOLD"),
    ELITE("ELITE"),
    LABEL("LABEL"),
    ;

    companion object {
        fun fromCode(code: String): WorldAthleticsLabel =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown World Athletics label code: $code")
    }
}

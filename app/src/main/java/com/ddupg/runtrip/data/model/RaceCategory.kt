package com.ddupg.runtrip.data.model

enum class RaceCategory(
    val code: String,
    val displayName: String,
) {
    MARATHON("MARATHON", "全程马拉松"),
    HALF_MARATHON("HALF_MARATHON", "半程马拉松"),
    TEN_K("TEN_K", "10 公里"),
    OTHER("OTHER", "其他"),
    ;

    companion object {
        fun fromCode(code: String): RaceCategory =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown race category code: $code")
    }
}

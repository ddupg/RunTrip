package com.ddupg.runtrip.data.model

enum class WorldAthleticsLabel(
    val code: String,
    val displayName: String,
    val chineseName: String,
) {
    PLATINUM("PLATINUM", "Platinum", "白金标"),
    GOLD("GOLD", "Gold", "金标"),
    ELITE("ELITE", "Elite", "精英标"),
    LABEL("LABEL", "Label", "标牌"),
    ;

    val bilingualDisplayName: String
        get() = "$chineseName（$displayName）"

    companion object {
        fun fromCode(code: String): WorldAthleticsLabel =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown World Athletics label code: $code")
    }
}

package com.ddupg.runtrip.data.model

enum class RaceStatus(
    val code: String,
    val displayName: String,
) {
    WATCHING("WATCHING", "关注中"),
    REGISTRATION_PENDING("REGISTRATION_PENDING", "待报名"),
    DRAW_PENDING("DRAW_PENDING", "待抽签"),
    DRAW_WON("DRAW_WON", "已中签"),
    DRAW_LOST("DRAW_LOST", "未中签"),
    REGISTERED("REGISTERED", "已报名"),
    WITHDRAWN("WITHDRAWN", "已放弃"),
    FINISHED("FINISHED", "已完赛"),
    ;

    companion object {
        fun fromCode(code: String): RaceStatus =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown race status code: $code")
    }
}

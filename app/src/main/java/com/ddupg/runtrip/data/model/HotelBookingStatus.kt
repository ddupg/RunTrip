package com.ddupg.runtrip.data.model

enum class HotelBookingStatus(
    val code: String,
    val displayName: String,
) {
    NOT_BOOKED("NOT_BOOKED", "未预订"),
    BOOKED("BOOKED", "已预订"),
    CANCELLED("CANCELLED", "已取消"),
    ;

    companion object {
        fun fromCode(code: String): HotelBookingStatus =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown hotel booking status code: $code")
    }
}

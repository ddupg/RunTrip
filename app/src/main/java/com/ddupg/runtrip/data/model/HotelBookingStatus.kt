package com.ddupg.runtrip.data.model

enum class HotelBookingStatus(
    val code: String,
) {
    NOT_BOOKED("NOT_BOOKED"),
    BOOKED("BOOKED"),
    CANCELLED("CANCELLED"),
    ;

    companion object {
        fun fromCode(code: String): HotelBookingStatus =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown hotel booking status code: $code")
    }
}

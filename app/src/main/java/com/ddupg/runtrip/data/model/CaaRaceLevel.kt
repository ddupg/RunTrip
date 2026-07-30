package com.ddupg.runtrip.data.model

enum class CaaRaceLevel(
    val code: String,
) {
    A1("A1"),
    A2("A2"),
    B("B"),
    C("C"),
    ;

    companion object {
        fun fromCode(code: String): CaaRaceLevel =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown CAA race level code: $code")
    }
}

package com.ddupg.runtrip.feature.pace

import java.math.BigDecimal
import kotlin.math.roundToLong

internal const val MARATHON_DISTANCE_KM = 42.195
internal const val HALF_MARATHON_DISTANCE_KM = 21.0975

internal enum class PaceDistancePreset(
    val distanceKm: Double?,
    val displayName: String,
) {
    FIVE_K(5.0, "5 公里"),
    TEN_K(10.0, "10 公里"),
    HALF_MARATHON(HALF_MARATHON_DISTANCE_KM, "半马"),
    MARATHON(MARATHON_DISTANCE_KM, "全马"),
    CUSTOM(null, "自定义"),
}

internal data class PaceSplit(
    val distanceKm: Double,
    val elapsedSeconds: Long,
    val isFinish: Boolean,
)

internal data class PaceCalculation(
    val paceSecondsPerKm: Long,
    val splits: List<PaceSplit>,
)

internal fun calculatePace(
    distanceKm: Double,
    finishTimeSeconds: Long,
): PaceCalculation? {
    if (!distanceKm.isFinite() || distanceKm <= 0.0 || finishTimeSeconds <= 0L) {
        return null
    }

    val splits = buildList {
        var splitDistanceKm = 5.0
        while (splitDistanceKm < distanceKm) {
            add(
                PaceSplit(
                    distanceKm = splitDistanceKm,
                    elapsedSeconds = (
                        finishTimeSeconds.toDouble() * splitDistanceKm / distanceKm
                    ).roundToLong(),
                    isFinish = false,
                ),
            )
            splitDistanceKm += 5.0
        }
        add(
            PaceSplit(
                distanceKm = distanceKm,
                elapsedSeconds = finishTimeSeconds,
                isFinish = true,
            ),
        )
    }

    return PaceCalculation(
        paceSecondsPerKm = (finishTimeSeconds.toDouble() / distanceKm).roundToLong(),
        splits = splits,
    )
}

internal fun formatPace(secondsPerKm: Long): String {
    val minutes = secondsPerKm / 60
    val seconds = secondsPerKm % 60
    return "$minutes′${seconds.toString().padStart(2, '0')}″"
}

internal fun formatPaceContentDescription(secondsPerKm: Long): String {
    val minutes = secondsPerKm / 60
    val seconds = secondsPerKm % 60
    return "每公里 $minutes 分 $seconds 秒"
}

internal fun formatElapsedTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    return listOf(hours, minutes, seconds).joinToString(":") {
        it.toString().padStart(2, '0')
    }
}

internal fun formatPaceDistance(distanceKm: Double): String =
    BigDecimal.valueOf(distanceKm).stripTrailingZeros().toPlainString()

internal fun parseCustomDistance(value: String): Double? =
    value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

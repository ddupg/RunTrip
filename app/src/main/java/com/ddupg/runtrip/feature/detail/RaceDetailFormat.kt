package com.ddupg.runtrip.feature.detail

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

fun formatRaceDate(date: LocalDate): String =
    "${date.year} 年 ${date.monthValue.toString().padStart(2, '0')} 月 " +
        "${date.dayOfMonth.toString().padStart(2, '0')} 日 ${date.dayOfWeek.chineseName()}"

fun formatDistance(distanceKm: Double?): String = when {
    distanceKm == null -> "未填写"
    distanceKm % 1.0 == 0.0 -> "${distanceKm.toLong()} km"
    else -> "${BigDecimal.valueOf(distanceKm).stripTrailingZeros().toPlainString()} km"
}

fun formatCny(priceCents: Long?): String = priceCents?.let {
    "¥${BigDecimal.valueOf(it, 2).setScale(2).toPlainString()}"
} ?: "未填写"

private fun DayOfWeek.chineseName(): String = when (this) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

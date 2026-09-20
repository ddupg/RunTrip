package com.ddupg.runtrip.ui.presentation

import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceDetails
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.RoadRunningCategory
import com.ddupg.runtrip.data.model.RoadRunningDetails
import com.ddupg.runtrip.data.model.SportType
import com.ddupg.runtrip.data.model.TriathlonCategory
import com.ddupg.runtrip.data.model.TriathlonDetails
import com.ddupg.runtrip.data.model.TrailRunningDetails
import com.ddupg.runtrip.data.model.TrailRunningCategory
import com.ddupg.runtrip.data.model.WorldAthleticsLabel
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

enum class RaceDateStyle {
    DATE_ONLY,
    WITH_WEEKDAY,
}

enum class RaceLabelDensity {
    FULL,
    COMPACT,
}

data class RaceDisplayText(
    val text: String,
    val isPlaceholder: Boolean = false,
)

object RacePresentation {
    fun sportType(type: SportType, density: RaceLabelDensity = RaceLabelDensity.COMPACT): RaceDisplayText = RaceDisplayText(when (type) {
        SportType.ROAD_RUNNING -> "路跑"
        SportType.TRIATHLON -> "铁三"
        SportType.TRAIL_RUNNING -> if (density == RaceLabelDensity.FULL) "越野跑" else "越野"
    })

    fun raceProject(race: Race, density: RaceLabelDensity): RaceDisplayText =
        RaceDisplayText("${sportType(race.details.sportType).text} · ${when (val details = race.details) {
            is TrailRunningDetails -> distance(details.distanceKm).text
            else -> category(details.category, density).text
        }}")

    fun date(date: LocalDate, style: RaceDateStyle): RaceDisplayText {
        val dateText =
            "${date.year} 年 ${date.monthValue.toString().padStart(2, '0')} 月 " +
                "${date.dayOfMonth.toString().padStart(2, '0')} 日"
        return RaceDisplayText(
            text = when (style) {
                RaceDateStyle.DATE_ONLY -> dateText
                RaceDateStyle.WITH_WEEKDAY -> "$dateText ${weekday(date.dayOfWeek).text}"
            },
        )
    }

    fun weekday(dayOfWeek: DayOfWeek): RaceDisplayText = RaceDisplayText(
        text = when (dayOfWeek) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        },
    )

    fun distance(distanceKm: Double?): RaceDisplayText = when {
        distanceKm == null -> missing()
        distanceKm % 1.0 == 0.0 -> RaceDisplayText("${distanceKm.toLong()} km")
        else -> RaceDisplayText(
            "${BigDecimal.valueOf(distanceKm).stripTrailingZeros().toPlainString()} km",
        )
    }

    fun cny(priceCents: Long?): RaceDisplayText = priceCents?.let {
        RaceDisplayText("¥${BigDecimal.valueOf(it, 2).setScale(2).toPlainString()}")
    } ?: missing()

    fun optionalText(value: String?): RaceDisplayText =
        value?.takeIf { it.isNotBlank() }?.let(::RaceDisplayText) ?: missing()

    fun missing(): RaceDisplayText = RaceDisplayText(
        text = "未填写",
        isPlaceholder = true,
    )

    fun categories(type: SportType): List<RaceCategory> = when (type) {
        SportType.ROAD_RUNNING -> RoadRunningCategory.entries
        SportType.TRIATHLON -> TriathlonCategory.entries
        SportType.TRAIL_RUNNING -> TrailRunningCategory.entries
    }

    fun category(category: RaceCategory, density: RaceLabelDensity): RaceDisplayText =
        RaceDisplayText(when (category) {
            is RoadRunningCategory -> RoadRunningPresentation.category(category, density)
            is TriathlonCategory -> TriathlonPresentation.category(category)
            is TrailRunningCategory -> "自定义距离"
        })

    fun detailFields(details: RaceDetails, density: RaceLabelDensity): List<SportDetailField> = when (details) {
        is RoadRunningDetails -> RoadRunningPresentation.fields(details, density)
        is TriathlonDetails -> emptyList()
        is TrailRunningDetails -> TrailRunningPresentation.fields(details, density)
    }

    fun status(status: RaceStatus): RaceDisplayText = RaceDisplayText(
        text = when (status) {
            RaceStatus.WATCHING -> "关注中"
            RaceStatus.REGISTRATION_PENDING -> "待报名"
            RaceStatus.DRAW_PENDING -> "待抽签"
            RaceStatus.DRAW_WON -> "已中签"
            RaceStatus.DRAW_LOST -> "未中签"
            RaceStatus.REGISTERED -> "已报名"
            RaceStatus.WITHDRAWN -> "已放弃"
            RaceStatus.FINISHED -> "已完赛"
        },
    )

    fun caaRaceLevel(level: CaaRaceLevel): RaceDisplayText =
        RaceDisplayText(level.code)

    fun worldAthleticsLabel(
        label: WorldAthleticsLabel,
        density: RaceLabelDensity,
    ): RaceDisplayText {
        val (englishName, chineseName) = when (label) {
            WorldAthleticsLabel.PLATINUM -> "Platinum" to "白金标"
            WorldAthleticsLabel.GOLD -> "Gold" to "金标"
            WorldAthleticsLabel.ELITE -> "Elite" to "精英标"
            WorldAthleticsLabel.LABEL -> "Label" to "标牌"
        }
        return RaceDisplayText(
            text = when (density) {
                RaceLabelDensity.FULL -> "$chineseName（$englishName）"
                RaceLabelDensity.COMPACT -> englishName
            },
        )
    }

    fun hotelBookingStatus(status: HotelBookingStatus): RaceDisplayText = RaceDisplayText(
        text = when (status) {
            HotelBookingStatus.NOT_BOOKED -> "未预订"
            HotelBookingStatus.BOOKED -> "已预订"
            HotelBookingStatus.CANCELLED -> "已取消"
        },
    )
}

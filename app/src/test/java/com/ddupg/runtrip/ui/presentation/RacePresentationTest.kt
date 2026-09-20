package com.ddupg.runtrip.ui.presentation

import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.RoadRunningCategory
import com.ddupg.runtrip.data.model.WorldAthleticsLabel
import com.ddupg.runtrip.data.model.SportType
import com.ddupg.runtrip.data.model.TriathlonCategory
import com.ddupg.runtrip.data.model.TriathlonDetails
import com.ddupg.runtrip.testing.testRace
import com.ddupg.runtrip.data.model.TrailRunningDetails
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RacePresentationTest {
    @Test
    fun trailShowsRaceDistanceAndElevationWithoutConfusingTravelDistance() {
        val race = testRace(details = TrailRunningDetails(30.5, 1200), travelDistanceKm = 200.0)
        assertEquals("越野 · 30.5 km", RacePresentation.raceProject(race, RaceLabelDensity.COMPACT).text)
        assertEquals(listOf(SportDetailField("累计爬升", "爬升 1200 m")),
            RacePresentation.detailFields(race.details, RaceLabelDensity.COMPACT))
        assertEquals(listOf(SportDetailField("累计爬升", "1200 m")),
            RacePresentation.detailFields(race.details, RaceLabelDensity.FULL))
        assertTrue(RacePresentation.detailFields(TrailRunningDetails(30.0), RaceLabelDensity.FULL).isEmpty())
    }

    @Test
    fun dateStylesShareOneChineseDateSemantic() {
        val date = LocalDate.of(2026, 11, 15)

        assertEquals(
            "2026 年 11 月 15 日",
            RacePresentation.date(date, RaceDateStyle.DATE_ONLY).text,
        )
        assertEquals(
            "2026 年 11 月 15 日 周日",
            RacePresentation.date(date, RaceDateStyle.WITH_WEEKDAY).text,
        )
    }

    @Test
    fun weekdayNamesCoverEveryDay() {
        assertEquals(
            listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
            DayOfWeek.entries.map { RacePresentation.weekday(it).text },
        )
    }

    @Test
    fun distanceUsesStableDecimalsAndTypedMissingState() {
        assertEquals("350 km", RacePresentation.distance(350.0).text)
        assertEquals("350.5 km", RacePresentation.distance(350.5).text)
        assertFalse(RacePresentation.distance(350.5).isPlaceholder)

        val missing = RacePresentation.distance(null)
        assertEquals("未填写", missing.text)
        assertTrue(missing.isPlaceholder)
    }

    @Test
    fun cnyUsesExactCentsAndTypedMissingState() {
        assertEquals("¥350.00", RacePresentation.cny(35_000).text)
        assertFalse(RacePresentation.cny(35_000).isPlaceholder)

        val missing = RacePresentation.cny(null)
        assertEquals("未填写", missing.text)
        assertTrue(missing.isPlaceholder)
    }

    @Test
    fun optionalTextSeparatesAbsenceFromRenderedCopy() {
        assertEquals(
            RaceDisplayText("西湖宾馆"),
            RacePresentation.optionalText("西湖宾馆"),
        )
        assertTrue(RacePresentation.optionalText(null).isPlaceholder)
        assertTrue(RacePresentation.optionalText("   ").isPlaceholder)
    }

    @Test
    fun categoryLabelsSupportFullAndCompactAdapters() {
        assertEquals(
            listOf("全程马拉松", "半程马拉松", "10 公里", "其他"),
            RoadRunningCategory.entries.map {
                RacePresentation.category(it, RaceLabelDensity.FULL).text
            },
        )
        assertEquals(
            listOf("全马", "半马", "10 公里", "其他"),
            RoadRunningCategory.entries.map {
                RacePresentation.category(it, RaceLabelDensity.COMPACT).text
            },
        )
    }

    @Test
    fun triathlonPresentationIncludesSportAndOnlyItsOwnFields() {
        val race = testRace(details = TriathlonDetails(TriathlonCategory.SPRINT))
        assertEquals("铁三 · 半标", RacePresentation.raceProject(race, RaceLabelDensity.FULL).text)
        assertEquals(listOf("半标", "全标", "其他"),
            RacePresentation.categories(SportType.TRIATHLON).map {
                RacePresentation.category(it, RaceLabelDensity.COMPACT).text
            })
        assertTrue(RacePresentation.detailFields(race.details, RaceLabelDensity.FULL).isEmpty())
    }

    @Test
    fun domainLabelsAreCentralizedForEveryPersistedCode() {
        assertEquals(
            listOf("关注中", "待报名", "待抽签", "已中签", "未中签", "已报名", "已放弃", "已完赛"),
            RaceStatus.entries.map { RacePresentation.status(it).text },
        )
        assertEquals(
            listOf("未预订", "已预订", "已取消"),
            HotelBookingStatus.entries.map {
                RacePresentation.hotelBookingStatus(it).text
            },
        )
        assertEquals(
            listOf("A1", "A2", "B", "C"),
            CaaRaceLevel.entries.map { RacePresentation.caaRaceLevel(it).text },
        )
    }

    @Test
    fun worldAthleticsLabelsSupportFullAndCompactAdapters() {
        assertEquals(
            listOf(
                "白金标（Platinum）",
                "金标（Gold）",
                "精英标（Elite）",
                "标牌（Label）",
            ),
            WorldAthleticsLabel.entries.map {
                RacePresentation.worldAthleticsLabel(it, RaceLabelDensity.FULL).text
            },
        )
        assertEquals(
            listOf("Platinum", "Gold", "Elite", "Label"),
            WorldAthleticsLabel.entries.map {
                RacePresentation.worldAthleticsLabel(it, RaceLabelDensity.COMPACT).text
            },
        )
    }
}

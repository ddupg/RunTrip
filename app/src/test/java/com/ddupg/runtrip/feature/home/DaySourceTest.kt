package com.ddupg.runtrip.feature.home

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DaySourceTest {
    @Test
    fun systemDaySourceEmitsNextDateAtLocalMidnight() = runTest {
        var current = ZonedDateTime.of(
            2026,
            7,
            17,
            23,
            59,
            0,
            0,
            ZoneId.of("Asia/Shanghai"),
        )
        val dates = mutableListOf<LocalDate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SystemDaySource { current }.observeToday().take(2).toList(dates)
        }
        runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 7, 17)), dates)

        advanceTimeBy(59_999)
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 7, 17)), dates)

        current = current.plusMinutes(1)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 17),
                LocalDate.of(2026, 7, 18),
            ),
            dates,
        )
    }
}

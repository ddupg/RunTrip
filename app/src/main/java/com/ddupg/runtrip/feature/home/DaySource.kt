package com.ddupg.runtrip.feature.home

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

fun interface DaySource {
    fun observeToday(): Flow<LocalDate>
}

class SystemDaySource(
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
) : DaySource {
    override fun observeToday(): Flow<LocalDate> = flow {
        while (currentCoroutineContext().isActive) {
            val current = now()
            emit(current.toLocalDate())
            val nextDay = current.toLocalDate().plusDays(1).atStartOfDay(current.zone)
            val delayMillis = Duration.between(current.toInstant(), nextDay.toInstant())
                .toMillis()
                .coerceAtLeast(1L)
            delay(delayMillis)
        }
    }.distinctUntilChanged()
}

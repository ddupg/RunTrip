package com.ddupg.runtrip.data

import com.ddupg.runtrip.data.local.RaceDao
import com.ddupg.runtrip.data.local.RaceEntity
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceInput
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.repository.OfflineRaceRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRaceRepositoryTest {
    @Test
    fun createPersistsNormalizedInputAndEnglishCodes() = runTest {
        val dao = FakeRaceDao()
        val repository = OfflineRaceRepository(
            raceDao = dao,
            currentTimeMillis = { 1_000L },
            newId = { "fixed-id" },
        )

        val id = repository.create(
            RaceInput(
                name = "  横店马拉松 ",
                city = " 金华 ",
                raceDate = LocalDate.of(2026, 11, 15),
                category = RaceCategory.MARATHON,
                status = RaceStatus.DRAW_WON,
                travelDistanceKm = 350.0,
                hotelBookingStatus = HotelBookingStatus.BOOKED,
                hotelName = " 万豪万枫 ",
                bookingPlatform = " 携程 ",
                hotelTotalPriceCents = 35_000,
            ),
        )

        val saved = dao.current.single()
        assertEquals("fixed-id", id)
        assertEquals("横店马拉松", saved.name)
        assertEquals("金华", saved.city)
        assertEquals("DRAW_WON", saved.statusCode)
        assertEquals("BOOKED", saved.hotelBookingStatusCode)
        assertEquals(1, saved.recordVersion)
    }

    @Test
    fun quickStatusUpdateUsesCodeAndIncrementsVersion() = runTest {
        val dao = FakeRaceDao()
        var now = 1_000L
        val repository = OfflineRaceRepository(
            raceDao = dao,
            currentTimeMillis = { now },
            newId = { "fixed-id" },
        )
        repository.create(
            RaceInput(
                name = "横店马拉松",
                city = "金华",
                raceDate = LocalDate.of(2026, 11, 15),
                category = RaceCategory.MARATHON,
                status = RaceStatus.DRAW_PENDING,
            ),
        )
        now = 2_000L

        assertTrue(repository.updateStatus("fixed-id", RaceStatus.DRAW_WON))

        val saved = dao.current.single()
        assertEquals("DRAW_WON", saved.statusCode)
        assertEquals(2_000L, saved.updatedAtEpochMillis)
        assertEquals(2, saved.recordVersion)
    }

    @Test
    fun deletePermanentlyRemovesRace() = runTest {
        val dao = FakeRaceDao()
        val repository = OfflineRaceRepository(
            raceDao = dao,
            currentTimeMillis = { 1_000L },
            newId = { "fixed-id" },
        )
        repository.create(
            RaceInput(
                name = "横店马拉松",
                city = "金华",
                raceDate = LocalDate.of(2026, 11, 15),
                category = RaceCategory.MARATHON,
                status = RaceStatus.DRAW_WON,
            ),
        )

        repository.delete("fixed-id")

        assertTrue(dao.current.isEmpty())
    }
}

private class FakeRaceDao : RaceDao {
    private val races = MutableStateFlow<List<RaceEntity>>(emptyList())
    val current: List<RaceEntity> get() = races.value

    override fun observeAll(): Flow<List<RaceEntity>> = races

    override fun observeById(id: String): Flow<RaceEntity?> =
        races.map { entries -> entries.firstOrNull { it.id == id } }

    override suspend fun getById(id: String): RaceEntity? =
        races.value.firstOrNull { it.id == id }

    override suspend fun upsert(race: RaceEntity) {
        races.value = races.value.filterNot { it.id == race.id } + race
    }

    override suspend fun updateStatus(
        id: String,
        statusCode: String,
        updatedAtEpochMillis: Long,
    ): Int {
        var updated = false
        races.value = races.value.map { race ->
            if (race.id == id) {
                updated = true
                race.copy(
                    statusCode = statusCode,
                    updatedAtEpochMillis = updatedAtEpochMillis,
                    recordVersion = race.recordVersion + 1,
                )
            } else {
                race
            }
        }
        return if (updated) 1 else 0
    }

    override suspend fun deleteById(id: String) {
        races.value = races.value.filterNot { it.id == id }
    }
}

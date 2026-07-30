package com.ddupg.runtrip.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ddupg.runtrip.data.local.RunTripDatabase
import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceInput
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.WorldAthleticsLabel
import com.ddupg.runtrip.data.repository.OfflineRaceRepository
import com.ddupg.runtrip.data.repository.RaceRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class OfflineRaceRepositoryTest {
    private lateinit var database: RunTripDatabase
    private lateinit var repository: RaceRepository
    private var now = 1_000L

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            RunTripDatabase::class.java,
        ).build()
        repository = OfflineRaceRepository(
            raceDao = database.raceDao(),
            currentTimeMillis = { now },
            newId = { "fixed-id" },
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun createPersistsNormalizedInputAndLifecycleMetadata() = runTest {
        val id = repository.create(
            RaceInput(
                name = "  横店马拉松 ",
                city = " 金华 ",
                raceDate = LocalDate.of(2026, 11, 15),
                category = RaceCategory.MARATHON,
                status = RaceStatus.DRAW_WON,
                caaRaceLevel = CaaRaceLevel.A1,
                worldAthleticsLabel = WorldAthleticsLabel.PLATINUM,
                travelDistanceKm = 350.0,
                hotelBookingStatus = HotelBookingStatus.BOOKED,
                hotelName = " 万豪万枫 ",
                bookingPlatform = " 携程 ",
                hotelTotalPriceCents = 35_000,
            ),
        )

        val saved = requireNotNull(repository.observeRace(id).first())
        assertEquals("fixed-id", id)
        assertEquals("横店马拉松", saved.name)
        assertEquals("金华", saved.city)
        assertEquals(RaceStatus.DRAW_WON, saved.status)
        assertEquals(CaaRaceLevel.A1, saved.caaRaceLevel)
        assertEquals(WorldAthleticsLabel.PLATINUM, saved.worldAthleticsLabel)
        assertEquals(HotelBookingStatus.BOOKED, saved.hotelBookingStatus)
        assertEquals("万豪万枫", saved.hotelName)
        assertEquals("携程", saved.bookingPlatform)
        assertEquals(1_000L, saved.createdAtEpochMillis)
        assertEquals(1_000L, saved.updatedAtEpochMillis)
        assertEquals(1, saved.recordVersion)
    }

    @Test
    fun fullUpdatePreservesCreationAndAdvancesLifecycleMetadata() = runTest {
        repository.create(baseInput())
        now = 2_000L

        repository.update(
            id = "fixed-id",
            input = RaceInput(
                name = "  杭州半程马拉松 ",
                city = " 杭州 ",
                raceDate = LocalDate.of(2027, 4, 11),
                category = RaceCategory.HALF_MARATHON,
                status = RaceStatus.FINISHED,
                caaRaceLevel = CaaRaceLevel.A2,
                worldAthleticsLabel = WorldAthleticsLabel.GOLD,
                travelDistanceKm = 180.0,
                hotelBookingStatus = HotelBookingStatus.BOOKED,
                hotelName = " 西湖宾馆 ",
                bookingPlatform = " 飞猪 ",
                hotelTotalPriceCents = 28_800,
                hotelNotes = " 含早 ",
                raceNotes = " PB ",
            ),
        )

        val saved = requireNotNull(repository.observeRace("fixed-id").first())
        assertEquals("杭州半程马拉松", saved.name)
        assertEquals("杭州", saved.city)
        assertEquals(LocalDate.of(2027, 4, 11), saved.raceDate)
        assertEquals(RaceCategory.HALF_MARATHON, saved.category)
        assertEquals(RaceStatus.FINISHED, saved.status)
        assertEquals(CaaRaceLevel.A2, saved.caaRaceLevel)
        assertEquals(WorldAthleticsLabel.GOLD, saved.worldAthleticsLabel)
        assertEquals("西湖宾馆", saved.hotelName)
        assertEquals("飞猪", saved.bookingPlatform)
        assertEquals("含早", saved.hotelNotes)
        assertEquals("PB", saved.raceNotes)
        assertEquals(1_000L, saved.createdAtEpochMillis)
        assertEquals(2_000L, saved.updatedAtEpochMillis)
        assertEquals(2, saved.recordVersion)
    }

    @Test
    fun quickStatusUpdateAdvancesVersionThroughRealSql() = runTest {
        repository.create(baseInput())
        now = 2_000L

        assertTrue(repository.updateStatus("fixed-id", RaceStatus.DRAW_WON))

        val saved = requireNotNull(repository.observeRace("fixed-id").first())
        assertEquals(RaceStatus.DRAW_WON, saved.status)
        assertEquals(2_000L, saved.updatedAtEpochMillis)
        assertEquals(2, saved.recordVersion)
        assertFalse(repository.updateStatus("missing-id", RaceStatus.FINISHED))
    }

    @Test
    fun deletePermanentlyRemovesRace() = runTest {
        repository.create(baseInput())

        repository.delete("fixed-id")

        assertNull(repository.observeRace("fixed-id").first())
    }

    private fun baseInput(): RaceInput = RaceInput(
        name = "横店马拉松",
        city = "金华",
        raceDate = LocalDate.of(2026, 11, 15),
        category = RaceCategory.MARATHON,
        status = RaceStatus.DRAW_PENDING,
    )
}

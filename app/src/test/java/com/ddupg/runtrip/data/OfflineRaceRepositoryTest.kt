package com.ddupg.runtrip.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ddupg.runtrip.data.local.RunTripDatabase
import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceInput
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.RoadRunningCategory
import com.ddupg.runtrip.data.model.RoadRunningDetails
import com.ddupg.runtrip.data.model.TriathlonCategory
import com.ddupg.runtrip.data.model.TriathlonDetails
import com.ddupg.runtrip.data.model.WorldAthleticsLabel
import com.ddupg.runtrip.data.repository.OfflineRaceRepository
import com.ddupg.runtrip.data.repository.RaceMutationResult
import com.ddupg.runtrip.data.repository.RaceRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                details = RoadRunningDetails(category = RoadRunningCategory.MARATHON, caaRaceLevel = CaaRaceLevel.A1, worldAthleticsLabel = WorldAthleticsLabel.PLATINUM),
                name = "  横店马拉松 ",
                city = " 金华 ",
                raceDate = LocalDate.of(2026, 11, 15),
                status = RaceStatus.DRAW_WON,
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
        assertEquals(CaaRaceLevel.A1, (saved.details as? RoadRunningDetails)?.caaRaceLevel)
        assertEquals(WorldAthleticsLabel.PLATINUM, (saved.details as? RoadRunningDetails)?.worldAthleticsLabel)
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

        val result = repository.update(
            id = "fixed-id",
            input = RaceInput(
                details = RoadRunningDetails(category = RoadRunningCategory.HALF_MARATHON, caaRaceLevel = CaaRaceLevel.A2, worldAthleticsLabel = WorldAthleticsLabel.GOLD),
                name = "  杭州半程马拉松 ",
                city = " 杭州 ",
                raceDate = LocalDate.of(2027, 4, 11),
                status = RaceStatus.FINISHED,
                travelDistanceKm = 180.0,
                hotelBookingStatus = HotelBookingStatus.BOOKED,
                hotelName = " 西湖宾馆 ",
                bookingPlatform = " 飞猪 ",
                hotelTotalPriceCents = 28_800,
                hotelNotes = " 含早 ",
                raceNotes = " PB ",
            ),
        )

        assertEquals(RaceMutationResult.APPLIED, result)
        val saved = requireNotNull(repository.observeRace("fixed-id").first())
        assertEquals("杭州半程马拉松", saved.name)
        assertEquals("杭州", saved.city)
        assertEquals(LocalDate.of(2027, 4, 11), saved.raceDate)
        assertEquals(RoadRunningCategory.HALF_MARATHON, saved.category)
        assertEquals(RaceStatus.FINISHED, saved.status)
        assertEquals(CaaRaceLevel.A2, (saved.details as? RoadRunningDetails)?.caaRaceLevel)
        assertEquals(WorldAthleticsLabel.GOLD, (saved.details as? RoadRunningDetails)?.worldAthleticsLabel)
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

        assertEquals(
            RaceMutationResult.APPLIED,
            repository.updateStatus("fixed-id", RaceStatus.DRAW_WON),
        )

        val saved = requireNotNull(repository.observeRace("fixed-id").first())
        assertEquals(RaceStatus.DRAW_WON, saved.status)
        assertEquals(2_000L, saved.updatedAtEpochMillis)
        assertEquals(2, saved.recordVersion)
    }

    @Test
    fun deletePermanentlyRemovesRace() = runTest {
        repository.create(baseInput())

        assertEquals(RaceMutationResult.APPLIED, repository.delete("fixed-id"))

        assertNull(repository.observeRace("fixed-id").first())
    }

    @Test
    fun missingMutationsReturnNotFoundWithoutCreatingRecords() = runTest {
        assertEquals(
            RaceMutationResult.NOT_FOUND,
            repository.update("missing-id", baseInput()),
        )
        assertEquals(
            RaceMutationResult.NOT_FOUND,
            repository.updateStatus("missing-id", RaceStatus.FINISHED),
        )
        assertEquals(
            RaceMutationResult.NOT_FOUND,
            repository.delete("missing-id"),
        )
        assertEquals(emptyList<Race>(), repository.observeRaces().first())
    }

    @Test
    fun triathlonLifecycleAndSportSwitchKeepExactlyOneDetail() = runTest {
        val input = baseInput().copy(details = TriathlonDetails(TriathlonCategory.SPRINT))
        val id = repository.create(input)
        assertEquals(input.details, repository.observeRace(id).first()?.details)
        assertEquals(0, rowCount("road_running_details"))
        assertEquals(1, rowCount("triathlon_details"))

        repository.updateStatus(id, RaceStatus.REGISTERED)
        assertEquals(input.details, repository.observeRace(id).first()?.details)
        repository.update(id, input.copy(details = TriathlonDetails(TriathlonCategory.STANDARD)))
        assertEquals(TriathlonDetails(TriathlonCategory.STANDARD), repository.observeRace(id).first()?.details)

        val road = RoadRunningDetails(RoadRunningCategory.HALF_MARATHON, CaaRaceLevel.A1)
        repository.update(id, input.copy(details = road))
        assertEquals(road, repository.observeRace(id).first()?.details)
        assertEquals(1, rowCount("road_running_details"))
        assertEquals(0, rowCount("triathlon_details"))

        repository.update(id, input)
        assertEquals(0, rowCount("road_running_details"))
        assertEquals(1, rowCount("triathlon_details"))
        repository.delete(id)
        assertEquals(0, rowCount("triathlon_details"))
        assertEquals(0, rowCount("races"))
    }

    @Test
    fun detailWriteFailureRollsBackCreationAndSportSwitch() = runTest {
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER reject_triathlon BEFORE INSERT ON triathlon_details
               BEGIN SELECT RAISE(ABORT, 'injected detail failure'); END""",
        )
        val triathlon = baseInput().copy(details = TriathlonDetails(TriathlonCategory.SPRINT))
        val createFailure = runCatching { repository.create(triathlon) }.exceptionOrNull()
        org.junit.Assert.assertNotNull(createFailure)
        assertEquals(0, rowCount("races"))

        val id = repository.create(baseInput())
        val original = repository.observeRace(id).first()
        val updateFailure = runCatching { repository.update(id, triathlon.copy(name = "changed")) }.exceptionOrNull()
        org.junit.Assert.assertNotNull(updateFailure)
        assertEquals(original, repository.observeRace(id).first())
        assertEquals(1, rowCount("road_running_details"))
        assertEquals(0, rowCount("triathlon_details"))
        repository.delete(id)
        assertEquals(0, rowCount("road_running_details"))
    }

    private fun rowCount(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun baseInput(): RaceInput = RaceInput(
        details = RoadRunningDetails(category = RoadRunningCategory.MARATHON),
        name = "横店马拉松",
        city = "金华",
        raceDate = LocalDate.of(2026, 11, 15),
        status = RaceStatus.DRAW_PENDING,
    )
}

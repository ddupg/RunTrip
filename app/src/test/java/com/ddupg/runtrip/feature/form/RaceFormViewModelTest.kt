package com.ddupg.runtrip.feature.form

import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceLocation
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.RoadRunningCategory
import com.ddupg.runtrip.data.model.RoadRunningDetails
import com.ddupg.runtrip.data.model.SportType
import com.ddupg.runtrip.data.model.WorldAthleticsLabel
import com.ddupg.runtrip.data.repository.RaceMutationResult
import com.ddupg.runtrip.testing.TestRaceRepository
import com.ddupg.runtrip.testing.testRace
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RaceFormViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectedLocationSavesAndExistingStructuredLocationLoads() = runTest(testDispatcher) {
        val location = RaceLocation("330000", "浙江省", "330100", "杭州市", null, null, "9.0.0")
        val repository = TestRaceRepository()
        val viewModel = RaceFormViewModel(repository, raceId = null)
        viewModel.updateDraft(validDraft().withLocation(location))
        viewModel.save()
        advanceUntilIdle()
        assertEquals(location, repository.createdInputs.single().location)
        assertEquals("", repository.createdInputs.single().city)
        val edit = RaceFormViewModel(repository, raceId = "created-id")
        advanceUntilIdle()
        assertEquals(location, edit.uiState.value.draft.location)
        edit.updateDraft(edit.uiState.value.draft.copy(raceNotes = "更新备注"))
        edit.save()
        advanceUntilIdle()
        assertEquals(location, repository.updateCalls.single().input.location)
    }

    @Test
    fun draftChangeClearsOnlyErrorsForChangedFields() {
        val viewModel = RaceFormViewModel(TestRaceRepository(), raceId = null)
        val invalidDraft = viewModel.uiState.value.draft

        viewModel.save()
        assertNotNull(viewModel.uiState.value.errors.name)
        assertNotNull(viewModel.uiState.value.errors.location)

        viewModel.updateDraft(invalidDraft.copy(name = "横店马拉松"))

        assertNull(viewModel.uiState.value.errors.name)
        assertNotNull(viewModel.uiState.value.errors.location)
    }

    @Test
    fun saveCreatesRaceFromCompleteDraft() = runTest(testDispatcher) {
        val repository = TestRaceRepository()
        val viewModel = RaceFormViewModel(repository, raceId = null)
        viewModel.updateDraft(
            validDraft().copy(
                sportDrafts = mapOf(SportType.ROAD_RUNNING to RoadRunningDraft(
                    RoadRunningCategory.MARATHON, CaaRaceLevel.A1, WorldAthleticsLabel.PLATINUM,
                )),
                travelDistance = "350.5",
                hotelPrice = "350.50",
                hotelName = "万豪万枫",
                bookingPlatform = "携程",
                hotelNotes = "靠近起点",
                raceNotes = "赛前一天领物",
            ),
        )

        viewModel.save()
        advanceUntilIdle()

        val createdInput = repository.createdInputs.single()
        assertEquals("横店马拉松", createdInput.name)
        assertEquals("金华", createdInput.city)
        assertEquals(LocalDate.of(2026, 11, 15), createdInput.raceDate)
        assertEquals(RaceStatus.DRAW_WON, createdInput.status)
        assertEquals(RoadRunningCategory.MARATHON, createdInput.details.category)
        assertEquals(HotelBookingStatus.BOOKED, createdInput.hotelBookingStatus)
        assertEquals("万豪万枫", createdInput.hotelName)
        assertEquals("携程", createdInput.bookingPlatform)
        assertEquals("靠近起点", createdInput.hotelNotes)
        assertEquals("赛前一天领物", createdInput.raceNotes)
        assertEquals(CaaRaceLevel.A1, (createdInput.details as RoadRunningDetails).caaRaceLevel)
        assertEquals(WorldAthleticsLabel.PLATINUM, (createdInput.details as RoadRunningDetails).worldAthleticsLabel)
        assertEquals(350.5, createdInput.travelDistanceKm)
        assertEquals(35_050L, createdInput.hotelTotalPriceCents)
        assertTrue(viewModel.uiState.value.isSaveComplete)
    }

    @Test
    fun editLoadsRaceIntoDraftAndUpdatesIt() = runTest(testDispatcher) {
        val repository = TestRaceRepository(listOf(existingRace()))
        val viewModel = RaceFormViewModel(repository, raceId = "race-id")

        advanceUntilIdle()

        val loadedState = viewModel.uiState.value
        assertFalse(loadedState.isLoading)
        assertEquals("横店马拉松", loadedState.draft.name)
        assertEquals(CaaRaceLevel.A1, (loadedState.draft.activeSportDraft as RoadRunningDraft).caaRaceLevel)
        assertEquals(WorldAthleticsLabel.PLATINUM, (loadedState.draft.activeSportDraft as RoadRunningDraft).worldAthleticsLabel)
        assertEquals("350.5", loadedState.draft.travelDistance)
        assertEquals("350.5", loadedState.draft.hotelPrice)

        viewModel.updateDraft(loadedState.draft.copy(name = "横店马拉松 2026"))
        viewModel.save()
        advanceUntilIdle()

        val updateCall = repository.updateCalls.single()
        assertEquals("race-id", updateCall.id)
        assertEquals("横店马拉松 2026", updateCall.input.name)
        assertTrue(viewModel.uiState.value.isSaveComplete)
    }

    @Test
    fun missingRaceKeepsEditFormOpenWithExplicitFailure() = runTest(testDispatcher) {
        val repository = TestRaceRepository(listOf(existingRace())).apply {
            updateResult = RaceMutationResult.NOT_FOUND
        }
        val viewModel = RaceFormViewModel(repository, raceId = "race-id")
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaveComplete)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("没有找到这条比赛记录", viewModel.uiState.value.saveError)
    }

    @Test
    fun failedSaveIsRepresentedInUiState() = runTest(testDispatcher) {
        val repository = TestRaceRepository().apply {
            create.failure = IllegalStateException("write failed")
        }
        val viewModel = RaceFormViewModel(repository, raceId = null)
        viewModel.updateDraft(validDraft())

        viewModel.save()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("保存失败，请重试", viewModel.uiState.value.saveError)
    }

    @Test
    fun duplicateSaveWhileWritingCreatesOnlyOneRace() = runTest(testDispatcher) {
        val repository = TestRaceRepository().apply {
            create.gate = CompletableDeferred()
        }
        val viewModel = RaceFormViewModel(repository, raceId = null)
        viewModel.updateDraft(validDraft())

        viewModel.save()
        viewModel.save()
        runCurrent()

        assertEquals(1, repository.create.callCount)
        repository.create.gate?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun closingNewRaceDiscardsDraftAndValidationErrors() {
        val repository = TestRaceRepository()
        val viewModel = RaceFormViewModel(repository, raceId = null)
        viewModel.updateDraft(RaceDraft(name = "未保存的比赛", raceDate = LocalDate.of(2027, 3, 29)))
        viewModel.save()
        assertTrue(viewModel.uiState.value.errors.hasErrors)

        viewModel.resetNewRace()

        assertEquals(RaceFormUiState(), viewModel.uiState.value)
        assertTrue(repository.createdInputs.isEmpty())
    }

    @Test
    fun completedSheetCanStartAnotherRaceWithoutReusingDateOrCompletion() = runTest(testDispatcher) {
        val repository = TestRaceRepository()
        val viewModel = RaceFormViewModel(repository, raceId = null)
        viewModel.updateDraft(validDraft())
        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaveComplete)

        viewModel.resetNewRace()
        assertEquals(RaceFormUiState(), viewModel.uiState.value)
        val nextDate = LocalDate.of(2027, 4, 29)
        viewModel.updateDraft(validDraft().copy(name = "第二场记录", raceDate = nextDate))
        viewModel.save()
        advanceUntilIdle()

        assertEquals(2, repository.createdInputs.size)
        assertEquals(nextDate, repository.createdInputs.last().raceDate)
    }

    @Test
    fun closingCannotResetInFlightSave() = runTest(testDispatcher) {
        val repository = TestRaceRepository().apply { create.gate = CompletableDeferred() }
        val viewModel = RaceFormViewModel(repository, raceId = null)
        viewModel.updateDraft(validDraft())
        viewModel.save()
        runCurrent()

        viewModel.resetNewRace()
        assertTrue(viewModel.uiState.value.isSaving)
        assertEquals(validDraft(), viewModel.uiState.value.draft)
        repository.create.gate?.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaveComplete)
        assertEquals(1, repository.createdInputs.size)
    }

    @Test
    fun newSheetResetDoesNotDiscardAnEdit() = runTest(testDispatcher) {
        val viewModel = RaceFormViewModel(TestRaceRepository(listOf(existingRace())), raceId = "race-id")
        advanceUntilIdle()
        val loadedState = viewModel.uiState.value
        viewModel.resetNewRace()
        assertEquals(loadedState, viewModel.uiState.value)
    }

    private fun validDraft(): RaceDraft = RaceDraft(
        name = "横店马拉松",
        city = "金华",
        raceDate = LocalDate.of(2026, 11, 15),
        sportDrafts = mapOf(SportType.ROAD_RUNNING to RoadRunningDraft(RoadRunningCategory.MARATHON)),
        status = RaceStatus.DRAW_WON,
        hotelBookingStatus = HotelBookingStatus.BOOKED,
    )

    private fun existingRace(): Race = testRace(
        id = "race-id",
        name = "横店马拉松",
        city = "金华",
        raceDate = LocalDate.of(2026, 11, 15),
        details = RoadRunningDetails(RoadRunningCategory.MARATHON, CaaRaceLevel.A1, WorldAthleticsLabel.PLATINUM),
        status = RaceStatus.DRAW_WON,
        travelDistanceKm = 350.5,
        hotelBookingStatus = HotelBookingStatus.BOOKED,
        hotelName = "万豪万枫",
        bookingPlatform = "携程",
        hotelTotalPriceCents = 35_050,
        hotelNotes = "靠近起点",
        raceNotes = "赛前一天领物",
        createdAtEpochMillis = 1_000,
        updatedAtEpochMillis = 2_000,
        recordVersion = 2,
    )
}

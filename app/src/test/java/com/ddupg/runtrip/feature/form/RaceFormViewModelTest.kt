package com.ddupg.runtrip.feature.form

import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceInput
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.WorldAthleticsLabel
import com.ddupg.runtrip.data.repository.RaceMutationResult
import com.ddupg.runtrip.data.repository.RaceRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
    fun draftChangeClearsOnlyErrorsForChangedFields() {
        val viewModel = RaceFormViewModel(FakeRaceRepository(), raceId = null)
        val invalidDraft = viewModel.uiState.value.draft

        viewModel.save()
        assertNotNull(viewModel.uiState.value.errors.name)
        assertNotNull(viewModel.uiState.value.errors.city)

        viewModel.updateDraft(invalidDraft.copy(name = "横店马拉松"))

        assertNull(viewModel.uiState.value.errors.name)
        assertNotNull(viewModel.uiState.value.errors.city)
    }

    @Test
    fun saveCreatesRaceFromCompleteDraft() = runTest(testDispatcher) {
        val repository = FakeRaceRepository()
        val viewModel = RaceFormViewModel(repository, raceId = null)
        viewModel.updateDraft(
            validDraft().copy(
                caaRaceLevel = CaaRaceLevel.A1,
                worldAthleticsLabel = WorldAthleticsLabel.PLATINUM,
                travelDistance = "350.5",
                hotelPrice = "350.50",
            ),
        )

        viewModel.save()
        advanceUntilIdle()

        val createdInput = requireNotNull(repository.createdInput)
        assertEquals("横店马拉松", createdInput.name)
        assertEquals(CaaRaceLevel.A1, createdInput.caaRaceLevel)
        assertEquals(WorldAthleticsLabel.PLATINUM, createdInput.worldAthleticsLabel)
        assertEquals(350.5, createdInput.travelDistanceKm)
        assertEquals(35_050L, createdInput.hotelTotalPriceCents)
        assertTrue(viewModel.uiState.value.isSaveComplete)
    }

    @Test
    fun editLoadsRaceIntoDraftAndUpdatesIt() = runTest(testDispatcher) {
        val repository = FakeRaceRepository(existingRace())
        val viewModel = RaceFormViewModel(repository, raceId = "race-id")

        advanceUntilIdle()

        val loadedState = viewModel.uiState.value
        assertFalse(loadedState.isLoading)
        assertEquals("横店马拉松", loadedState.draft.name)
        assertEquals(CaaRaceLevel.A1, loadedState.draft.caaRaceLevel)
        assertEquals(WorldAthleticsLabel.PLATINUM, loadedState.draft.worldAthleticsLabel)
        assertEquals("350.5", loadedState.draft.travelDistance)
        assertEquals("350.5", loadedState.draft.hotelPrice)

        viewModel.updateDraft(loadedState.draft.copy(name = "横店马拉松 2026"))
        viewModel.save()
        advanceUntilIdle()

        assertEquals("race-id", repository.updatedRaceId)
        assertEquals("横店马拉松 2026", repository.updatedInput?.name)
        assertTrue(viewModel.uiState.value.isSaveComplete)
    }

    @Test
    fun missingRaceKeepsEditFormOpenWithExplicitFailure() = runTest(testDispatcher) {
        val repository = FakeRaceRepository(existingRace()).apply {
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
        val repository = FakeRaceRepository().apply {
            createError = IllegalStateException("write failed")
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
        val repository = FakeRaceRepository().apply {
            createGate = CompletableDeferred()
        }
        val viewModel = RaceFormViewModel(repository, raceId = null)
        viewModel.updateDraft(validDraft())

        viewModel.save()
        viewModel.save()
        runCurrent()

        assertEquals(1, repository.createCalls)
        repository.createGate?.complete(Unit)
        advanceUntilIdle()
    }

    private fun validDraft(): RaceDraft = RaceDraft(
        name = "横店马拉松",
        city = "金华",
        raceDate = LocalDate.of(2026, 11, 15),
        category = RaceCategory.MARATHON,
        status = RaceStatus.DRAW_WON,
        hotelBookingStatus = HotelBookingStatus.BOOKED,
    )

    private fun existingRace(): Race = Race(
        id = "race-id",
        name = "横店马拉松",
        city = "金华",
        raceDate = LocalDate.of(2026, 11, 15),
        category = RaceCategory.MARATHON,
        status = RaceStatus.DRAW_WON,
        caaRaceLevel = CaaRaceLevel.A1,
        worldAthleticsLabel = WorldAthleticsLabel.PLATINUM,
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

private class FakeRaceRepository(
    initialRace: Race? = null,
) : RaceRepository {
    private val race = MutableStateFlow(initialRace)

    var createdInput: RaceInput? = null
    var updatedRaceId: String? = null
    var updatedInput: RaceInput? = null
    var createError: RuntimeException? = null
    var createGate: CompletableDeferred<Unit>? = null
    var createCalls = 0
    var updateResult = RaceMutationResult.APPLIED

    override fun observeRaces(): Flow<List<Race>> =
        race.map { currentRace -> listOfNotNull(currentRace) }

    override fun observeRace(id: String): Flow<Race?> =
        race.map { currentRace -> currentRace?.takeIf { it.id == id } }

    override suspend fun create(input: RaceInput): String {
        createCalls += 1
        createGate?.await()
        createError?.let { throw it }
        createdInput = input
        return "created-id"
    }

    override suspend fun update(id: String, input: RaceInput): RaceMutationResult {
        updatedRaceId = id
        updatedInput = input
        return updateResult
    }

    override suspend fun updateStatus(
        id: String,
        status: RaceStatus,
    ): RaceMutationResult = RaceMutationResult.NOT_FOUND

    override suspend fun delete(id: String): RaceMutationResult = RaceMutationResult.NOT_FOUND
}

package com.ddupg.runtrip.feature.home

import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceInput
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.repository.RaceMutationResult
import com.ddupg.runtrip.data.repository.RaceRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 7, 17)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun dayChangeReprojectsRacesWithoutRepositoryEmission() = runTest(testDispatcher) {
        val repository = FakeHomeRepository(
            listOf(race(id = "today", date = today)),
        )
        val daySource = MutableDaySource(today)
        val viewModel = HomeViewModel(repository, daySource)
        startCollecting(viewModel)
        advanceUntilIdle()

        assertEquals(
            listOf("today"),
            viewModel.uiState.value.monthGroups.flatMap { it.races }.map { it.id },
        )

        daySource.today.value = today.plusDays(1)
        advanceUntilIdle()

        assertEquals(emptyList<RaceMonthGroup>(), viewModel.uiState.value.monthGroups)
        viewModel.selectSection(RaceSection.HISTORY)
        advanceUntilIdle()
        assertEquals(
            listOf("today"),
            viewModel.uiState.value.monthGroups.flatMap { it.races }.map { it.id },
        )
    }

    @Test
    fun quickStatusSelectionAndSavingAreRepresentedInState() = runTest(testDispatcher) {
        val repository = FakeHomeRepository(
            listOf(race(id = "race-id", date = today.plusDays(1))),
        )
        val updateGate = CompletableDeferred<Unit>()
        repository.updateGate = updateGate
        val viewModel = HomeViewModel(repository, MutableDaySource(today))
        startCollecting(viewModel)
        advanceUntilIdle()

        viewModel.openQuickStatus("race-id")
        advanceUntilIdle()
        assertEquals("race-id", viewModel.uiState.value.quickStatusRace?.id)

        viewModel.updateQuickStatus(RaceStatus.DRAW_WON)
        runCurrent()
        assertEquals(
            QuickStatusUpdate.Saving(RaceStatus.DRAW_WON),
            viewModel.uiState.value.quickStatusUpdate,
        )

        viewModel.dismissQuickStatus()
        runCurrent()
        assertNotNull(viewModel.uiState.value.quickStatusRace)

        updateGate.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.quickStatusRace)
        assertEquals(QuickStatusUpdate.Idle, viewModel.uiState.value.quickStatusUpdate)
        assertEquals(
            RaceStatus.DRAW_WON,
            viewModel.uiState.value.monthGroups.single().races.single().status,
        )
    }

    @Test
    fun missingRaceKeepsQuickStatusOpenWithExplicitFailure() = runTest(testDispatcher) {
        val repository = FakeHomeRepository(
            listOf(race(id = "race-id", date = today.plusDays(1))),
        ).apply {
            updateResult = RaceMutationResult.NOT_FOUND
        }
        val viewModel = HomeViewModel(repository, MutableDaySource(today))
        startCollecting(viewModel)
        advanceUntilIdle()

        viewModel.openQuickStatus("race-id")
        viewModel.updateQuickStatus(RaceStatus.DRAW_WON)
        advanceUntilIdle()

        assertEquals("race-id", viewModel.uiState.value.quickStatusRace?.id)
        assertEquals(
            QuickStatusUpdate.Failed("没有找到这条比赛记录"),
            viewModel.uiState.value.quickStatusUpdate,
        )
    }

    @Test
    fun failedQuickStatusCanRetryToSuccess() = runTest(testDispatcher) {
        val repository = FakeHomeRepository(
            listOf(race(id = "race-id", date = today.plusDays(1))),
        ).apply {
            updateError = IllegalStateException("write failed")
        }
        val viewModel = HomeViewModel(repository, MutableDaySource(today))
        startCollecting(viewModel)
        advanceUntilIdle()

        viewModel.openQuickStatus("race-id")
        viewModel.updateQuickStatus(RaceStatus.DRAW_WON)
        advanceUntilIdle()

        assertEquals(
            QuickStatusUpdate.Failed("更新参赛状态失败，请重试"),
            viewModel.uiState.value.quickStatusUpdate,
        )
        assertEquals("race-id", viewModel.uiState.value.quickStatusRace?.id)

        repository.updateError = null
        viewModel.updateQuickStatus(RaceStatus.DRAW_WON)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.quickStatusRace)
        assertEquals(QuickStatusUpdate.Idle, viewModel.uiState.value.quickStatusUpdate)
    }

    private fun TestScope.startCollecting(viewModel: HomeViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
    }
}

private class MutableDaySource(initialDate: LocalDate) : DaySource {
    val today = MutableStateFlow(initialDate)

    override fun observeToday(): Flow<LocalDate> = today
}

private class FakeHomeRepository(
    initialRaces: List<Race>,
) : RaceRepository {
    private val races = MutableStateFlow(initialRaces)

    var updateResult = RaceMutationResult.APPLIED
    var updateError: RuntimeException? = null
    var updateGate: CompletableDeferred<Unit>? = null

    override fun observeRaces(): Flow<List<Race>> = races

    override fun observeRace(id: String): Flow<Race?> =
        races.map { entries -> entries.firstOrNull { it.id == id } }

    override suspend fun create(input: RaceInput): String =
        throw UnsupportedOperationException("Not used by home tests")

    override suspend fun update(id: String, input: RaceInput): RaceMutationResult {
        throw UnsupportedOperationException("Not used by home tests")
    }

    override suspend fun updateStatus(
        id: String,
        status: RaceStatus,
    ): RaceMutationResult {
        updateGate?.await()
        updateError?.let { throw it }
        if (updateResult == RaceMutationResult.NOT_FOUND) {
            return RaceMutationResult.NOT_FOUND
        }

        var found = false
        races.update { entries ->
            entries.map { race ->
                if (race.id == id) {
                    found = true
                    race.copy(
                        status = status,
                        recordVersion = race.recordVersion + 1,
                    )
                } else {
                    race
                }
            }
        }
        return if (found) RaceMutationResult.APPLIED else RaceMutationResult.NOT_FOUND
    }

    override suspend fun delete(id: String): RaceMutationResult {
        throw UnsupportedOperationException("Not used by home tests")
    }
}

private fun race(id: String, date: LocalDate): Race = Race(
    id = id,
    name = id,
    city = "杭州",
    raceDate = date,
    category = RaceCategory.MARATHON,
    status = RaceStatus.DRAW_PENDING,
    caaRaceLevel = null,
    worldAthleticsLabel = null,
    travelDistanceKm = null,
    hotelBookingStatus = HotelBookingStatus.NOT_BOOKED,
    hotelName = null,
    bookingPlatform = null,
    hotelTotalPriceCents = null,
    hotelNotes = null,
    raceNotes = null,
    createdAtEpochMillis = 1_000,
    updatedAtEpochMillis = 1_000,
    recordVersion = 1,
)

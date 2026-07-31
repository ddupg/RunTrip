package com.ddupg.runtrip.feature.home

import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.repository.RaceMutationResult
import com.ddupg.runtrip.testing.TestRaceRepository
import com.ddupg.runtrip.testing.testRace
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
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
        val repository = TestRaceRepository(
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
        val repository = TestRaceRepository(
            listOf(race(id = "race-id", date = today.plusDays(1))),
        )
        val updateGate = CompletableDeferred<Unit>()
        repository.updateStatus.gate = updateGate
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
        val repository = TestRaceRepository(
            listOf(race(id = "race-id", date = today.plusDays(1))),
        ).apply {
            updateStatusResult = RaceMutationResult.NOT_FOUND
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
        val repository = TestRaceRepository(
            listOf(race(id = "race-id", date = today.plusDays(1))),
        ).apply {
            updateStatus.failure = IllegalStateException("write failed")
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

        repository.updateStatus.failure = null
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

private fun race(id: String, date: LocalDate): Race = testRace(
    id = id,
    name = id,
    city = "杭州",
    raceDate = date,
)

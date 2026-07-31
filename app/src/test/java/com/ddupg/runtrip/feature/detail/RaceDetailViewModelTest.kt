package com.ddupg.runtrip.feature.detail

import com.ddupg.runtrip.data.repository.RaceMutationResult
import com.ddupg.runtrip.testing.TestRaceRepository
import com.ddupg.runtrip.testing.testRace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RaceDetailViewModelTest {
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
    fun deleteCompletesWhenRaceWasRemovedOrAlreadyMissing() = runTest(testDispatcher) {
        listOf(
            RaceMutationResult.APPLIED,
            RaceMutationResult.NOT_FOUND,
        ).forEach { result ->
            val viewModel = RaceDetailViewModel(
                repository = TestRaceRepository(listOf(testRace())).apply {
                    deleteResult = result
                },
                raceId = "race-id",
            )
            startCollecting(viewModel)
            advanceUntilIdle()

            viewModel.deleteRace()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isDeleteComplete)
            assertFalse(viewModel.uiState.value.isDeleting)
            assertNull(viewModel.uiState.value.deleteError)
        }
    }

    @Test
    fun duplicateDeleteWhileWritingRunsOnceAndFailureIsVisible() = runTest(testDispatcher) {
        val deleteGate = CompletableDeferred<Unit>()
        val repository = TestRaceRepository(listOf(testRace())).apply {
            delete.failure = IllegalStateException("write failed")
            delete.gate = deleteGate
        }
        val viewModel = RaceDetailViewModel(repository, "race-id")
        startCollecting(viewModel)
        advanceUntilIdle()

        viewModel.deleteRace()
        viewModel.deleteRace()
        runCurrent()

        assertEquals(1, repository.delete.callCount)
        deleteGate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeleting)
        assertFalse(viewModel.uiState.value.isDeleteComplete)
        assertEquals("删除失败，请重试", viewModel.uiState.value.deleteError)
    }

    private fun TestScope.startCollecting(viewModel: RaceDetailViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
    }
}

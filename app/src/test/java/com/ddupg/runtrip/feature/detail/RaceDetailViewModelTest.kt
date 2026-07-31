package com.ddupg.runtrip.feature.detail

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
                repository = FakeDetailRepository(deleteResult = result),
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
        val repository = FakeDetailRepository(
            deleteError = IllegalStateException("write failed"),
            deleteGate = deleteGate,
        )
        val viewModel = RaceDetailViewModel(repository, "race-id")
        startCollecting(viewModel)
        advanceUntilIdle()

        viewModel.deleteRace()
        viewModel.deleteRace()
        runCurrent()

        assertEquals(1, repository.deleteCalls)
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

private class FakeDetailRepository(
    private val deleteResult: RaceMutationResult = RaceMutationResult.APPLIED,
    private val deleteError: RuntimeException? = null,
    private val deleteGate: CompletableDeferred<Unit>? = null,
) : RaceRepository {
    private val race = MutableStateFlow(detailRace())
    var deleteCalls = 0

    override fun observeRaces(): Flow<List<Race>> =
        throw UnsupportedOperationException("Not used by detail tests")

    override fun observeRace(id: String): Flow<Race?> = race

    override suspend fun create(input: RaceInput): String =
        throw UnsupportedOperationException("Not used by detail tests")

    override suspend fun update(id: String, input: RaceInput): RaceMutationResult =
        throw UnsupportedOperationException("Not used by detail tests")

    override suspend fun updateStatus(
        id: String,
        status: RaceStatus,
    ): RaceMutationResult = throw UnsupportedOperationException("Not used by detail tests")

    override suspend fun delete(id: String): RaceMutationResult {
        deleteCalls += 1
        deleteGate?.await()
        deleteError?.let { throw it }
        return deleteResult
    }
}

private fun detailRace(): Race = Race(
    id = "race-id",
    name = "横店马拉松",
    city = "金华",
    raceDate = LocalDate.of(2026, 11, 15),
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

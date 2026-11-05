package com.ddupg.runtrip.feature.form

import com.ddupg.runtrip.data.repository.RaceRepository
import com.ddupg.runtrip.feature.detail.RaceDetailViewModel
import com.ddupg.runtrip.feature.home.HomeViewModel
import com.ddupg.runtrip.feature.home.DaySource
import com.ddupg.runtrip.testing.TestRaceRepository
import com.ddupg.runtrip.testing.testRace
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RaceReliabilityTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun observationFailureCanBeRetriedAcrossScreens() = runTest(dispatcher) {
        val delegate = TestRaceRepository(listOf(testRace()))
        var fail = true
        var allSubscriptions = 0
        var raceSubscriptions = 0
        val repository = object : RaceRepository by delegate {
            override fun observeRaces() = flow {
                allSubscriptions++
                if (fail) error("read failed")
                emit(delegate.observeRaces().first())
            }
            override fun observeRace(id: String) = flow {
                raceSubscriptions++
                if (fail) error("read failed")
                emit(delegate.observeRace(id).first())
            }
        }
        val home = HomeViewModel(repository, object : DaySource {
            override fun observeToday() = flowOf(LocalDate.of(2026, 11, 1))
        })
        val detail = RaceDetailViewModel(repository, "race-id")
        val form = RaceFormViewModel(repository, "race-id")
        backgroundScope.launch { home.uiState.collect {} }
        backgroundScope.launch { detail.uiState.collect {} }
        runCurrent()
        assertNotNull(home.uiState.value.loadError)
        assertNotNull(detail.uiState.value.loadError)
        assertNotNull(form.uiState.value.loadError)
        fail = false
        home.retryLoad(); detail.retryLoad(); form.retryLoad()
        runCurrent()
        assertNull(home.uiState.value.loadError)
        assertEquals(1, home.uiState.value.monthGroups.size)
        assertEquals("race-id", detail.uiState.value.race?.id)
        assertEquals("race-id", form.uiState.value.draft.name)
        assertEquals(2, allSubscriptions)
        assertEquals(4, raceSubscriptions)
    }

    @Test fun savingFreezesDraftAndFailureAllowsEditing() = runTest(dispatcher) {
        val repo = TestRaceRepository()
        val gate = CompletableDeferred<Unit>()
        repo.create.gate = gate
        repo.create.failure = IllegalStateException()
        val vm = RaceFormViewModel(repo, null)
        val draft = vm.uiState.value.draft.copy(name = "原名称", city = "金华")
        vm.updateDraft(draft)
        vm.save()
        runCurrent()
        assertTrue(vm.uiState.value.isSaving)
        vm.updateDraft(draft.copy(name = "新名称"))
        assertEquals("原名称", vm.uiState.value.draft.name)
        gate.complete(Unit)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.saveError)
        vm.updateDraft(draft.copy(name = "新名称"))
        assertEquals("新名称", vm.uiState.value.draft.name)
    }
}


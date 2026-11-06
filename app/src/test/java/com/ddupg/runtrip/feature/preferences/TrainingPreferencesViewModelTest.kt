package com.ddupg.runtrip.feature.preferences

import com.ddupg.runtrip.data.model.TrainingPreferences
import com.ddupg.runtrip.data.repository.TrainingPreferencesRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingPreferencesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private class Repository : TrainingPreferencesRepository {
        override val preferences = MutableStateFlow(TrainingPreferences())
        var failSave = false
        var saves = 0
        override suspend fun update(value: TrainingPreferences) {
            saves++
            if (failSave) throw IOException()
            preferences.value = value
        }
    }

    @Test fun `cancel discards the edited coefficient and reopening shows the saved value`() = runTest(dispatcher) {
        val repository = Repository()
        val model = TrainingPreferencesViewModel(repository)
        model.edit(TrainingPreferenceField.CYCLING_FACTOR)
        model.changeInput("0.4")
        model.dismiss()
        assertNull(model.uiState.value.editing)
        model.edit(TrainingPreferenceField.CYCLING_FACTOR)
        assertEquals("0.25", model.uiState.value.input)
        assertEquals(0, repository.saves)
    }

    @Test fun `save failure preserves input and retry updates the preference without changing other fields`() = runTest(dispatcher) {
        val repository = Repository().apply { failSave = true }
        val model = TrainingPreferencesViewModel(repository)
        val before = repository.preferences.value
        model.edit(TrainingPreferenceField.SWIMMING_FACTOR)
        model.changeInput("3.5")
        model.save()
        advanceUntilIdle()
        assertEquals(before, model.uiState.value.preferences)
        assertEquals("3.5", model.uiState.value.input)
        assertEquals(TrainingPreferenceField.SWIMMING_FACTOR, model.uiState.value.editing)
        assertNotNull(model.uiState.value.error)
        assertFalse(model.uiState.value.saving)

        repository.failSave = false
        model.save()
        advanceUntilIdle()
        assertEquals(before.copy(swimmingFactor = 3.5), model.uiState.value.preferences)
        assertNull(model.uiState.value.editing)
        assertNull(model.uiState.value.error)
    }

    @Test fun `invalid and nonfinite factors are rejected while zero growth is allowed`() = runTest(dispatcher) {
        val repository = Repository()
        val model = TrainingPreferencesViewModel(repository)
        model.edit(TrainingPreferenceField.CYCLING_FACTOR)
        for (input in listOf("", "0", "-1", "NaN", "Infinity", "1e999")) {
            model.changeInput(input)
            model.save()
            assertNotNull(model.uiState.value.error)
        }
        assertEquals(0, repository.saves)
        model.edit(TrainingPreferenceField.RUNNING_WEEKLY_GROWTH)
        model.changeInput("0")
        model.save()
        advanceUntilIdle()
        assertEquals(0.0, repository.preferences.value.runningWeeklyGrowthPercent, 0.0)
        assertEquals(1, repository.saves)
    }
}

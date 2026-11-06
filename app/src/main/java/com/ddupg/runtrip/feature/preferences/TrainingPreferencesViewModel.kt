package com.ddupg.runtrip.feature.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ddupg.runtrip.data.model.TrainingPreferences
import com.ddupg.runtrip.data.repository.TrainingPreferencesRepository
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TrainingPreferenceField(val label: String, val percentage: Boolean) {
    RUNNING_WEEKLY_GROWTH("周增幅", true),
    RUNNING_MONTHLY_GROWTH("月增幅", true),
    CYCLING_FACTOR("骑行换算比例", false),
    SWIMMING_FACTOR("游泳换算比例", false);

    fun value(preferences: TrainingPreferences): Double = when (this) {
        RUNNING_WEEKLY_GROWTH -> preferences.runningWeeklyGrowthPercent
        RUNNING_MONTHLY_GROWTH -> preferences.runningMonthlyGrowthPercent
        CYCLING_FACTOR -> preferences.cyclingFactor
        SWIMMING_FACTOR -> preferences.swimmingFactor
    }

    fun replace(preferences: TrainingPreferences, value: Double): TrainingPreferences = when (this) {
        RUNNING_WEEKLY_GROWTH -> preferences.copy(runningWeeklyGrowthPercent = value)
        RUNNING_MONTHLY_GROWTH -> preferences.copy(runningMonthlyGrowthPercent = value)
        CYCLING_FACTOR -> preferences.copy(cyclingFactor = value)
        SWIMMING_FACTOR -> preferences.copy(swimmingFactor = value)
    }
}

fun trainingPreferenceNumber(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

data class TrainingPreferencesUiState(
    val preferences: TrainingPreferences,
    val editing: TrainingPreferenceField? = null,
    val input: String = "",
    val saving: Boolean = false,
    val error: String? = null,
)

class TrainingPreferencesViewModel(private val repository: TrainingPreferencesRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(TrainingPreferencesUiState(repository.preferences.value))
    val uiState = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.preferences.collect { value -> mutableState.update { it.copy(preferences = value) } }
        }
    }

    fun edit(field: TrainingPreferenceField) {
        if (mutableState.value.saving) return
        mutableState.update {
            it.copy(editing = field, input = trainingPreferenceNumber(field.value(it.preferences)), error = null)
        }
    }

    fun changeInput(value: String) {
        if (!mutableState.value.saving) mutableState.update { it.copy(input = value, error = null) }
    }

    fun dismiss() {
        if (!mutableState.value.saving) mutableState.update { it.copy(editing = null, error = null) }
    }

    fun save() {
        val state = mutableState.value
        val field = state.editing ?: return
        if (state.saving) return
        val value = state.input.trim().toDoubleOrNull()
        if (value == null || !value.isFinite() || if (field.percentage) value < 0 else value <= 0) {
            mutableState.update { it.copy(error = if (field.percentage) "请输入大于或等于 0 的百分比" else "请输入大于 0 的换算比例") }
            return
        }
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                repository.update(field.replace(repository.preferences.value, value))
                mutableState.update { it.copy(editing = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(error = "保存失败，请重试。") }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    class Factory(private val repository: TrainingPreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TrainingPreferencesViewModel(repository) as T
    }
}

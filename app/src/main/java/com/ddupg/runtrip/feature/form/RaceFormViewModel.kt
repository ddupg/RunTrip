package com.ddupg.runtrip.feature.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ddupg.runtrip.data.repository.RaceMutationResult
import com.ddupg.runtrip.data.repository.RaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RaceFormViewModel(
    private val repository: RaceRepository,
    private val raceId: String?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RaceFormUiState(isLoading = raceId != null),
    )
    val uiState: StateFlow<RaceFormUiState> = _uiState.asStateFlow()

    init {
        if (raceId != null) {
            viewModelScope.launch {
                val race = repository.observeRace(raceId).first()
                _uiState.value = if (race == null) {
                    RaceFormUiState(
                        isLoading = false,
                        loadError = "没有找到这条比赛记录",
                    )
                } else {
                    RaceFormUiState(draft = race.toDraft())
                }
            }
        }
    }

    fun updateDraft(draft: RaceDraft) {
        _uiState.update { state -> state.withDraft(draft) }
    }

    fun save() {
        val currentState = _uiState.value
        if (currentState.isSaving || currentState.isSaveComplete) return

        val validation = validateRaceForm(currentState.draft)
        if (validation.input == null) {
            _uiState.update { it.copy(errors = validation.errors) }
            return
        }

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val result = if (raceId == null) {
                    repository.create(validation.input)
                    RaceMutationResult.APPLIED
                } else {
                    repository.update(raceId, validation.input)
                }

                _uiState.update {
                    when (result) {
                        RaceMutationResult.APPLIED -> it.copy(
                            isSaving = false,
                            isSaveComplete = true,
                        )

                        RaceMutationResult.NOT_FOUND -> it.copy(
                            isSaving = false,
                            saveError = "没有找到这条比赛记录",
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = "保存失败，请重试",
                    )
                }
            }
        }
    }

    class Factory(
        private val repository: RaceRepository,
        private val raceId: String?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RaceFormViewModel::class.java))
            return RaceFormViewModel(repository, raceId) as T
        }
    }
}

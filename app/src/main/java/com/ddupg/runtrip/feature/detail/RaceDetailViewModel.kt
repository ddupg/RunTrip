package com.ddupg.runtrip.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.repository.RaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RaceDetailUiState(
    val race: Race? = null,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val isDeleting: Boolean = false,
    val isDeleteComplete: Boolean = false,
    val deleteError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RaceDetailViewModel(
    private val repository: RaceRepository,
    private val raceId: String,
) : ViewModel() {
    private val operationState = MutableStateFlow(RaceDetailUiState())

    private val reload = MutableStateFlow(0)
    private val data = reload.flatMapLatest {
        flow { emitAll(repository.observeRace(raceId).map { DetailData.Ready(it) as DetailData }) }
            .onStart { emit(DetailData.Loading) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(DetailData.Failed)
            }
    }
    val uiState: StateFlow<RaceDetailUiState> = combine(data, operationState) { data, operations ->
        when (data) {
            DetailData.Loading -> operations.copy(isLoading = true)
            DetailData.Failed -> operations.copy(isLoading = false, loadError = "加载比赛失败，请重试")
            is DetailData.Ready -> operations.copy(race = data.race, isLoading = false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RaceDetailUiState())

    fun retryLoad() { reload.update { it + 1 } }

    fun deleteRace() {
        val currentState = operationState.value
        if (currentState.isDeleting || currentState.isDeleteComplete) return

        operationState.update { it.copy(isDeleting = true, deleteError = null) }
        viewModelScope.launch {
            try {
                repository.delete(raceId)
                operationState.update {
                    it.copy(
                        isDeleting = false,
                        isDeleteComplete = true,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                operationState.update {
                    it.copy(
                        isDeleting = false,
                        deleteError = "删除失败，请重试",
                    )
                }
            }
        }
    }

    class Factory(
        private val repository: RaceRepository,
        private val raceId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RaceDetailViewModel::class.java))
            return RaceDetailViewModel(repository, raceId) as T
        }
    }
}

private sealed interface DetailData {
    data object Loading : DetailData
    data object Failed : DetailData
    data class Ready(val race: Race?) : DetailData
}

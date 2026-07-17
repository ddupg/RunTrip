package com.ddupg.runtrip.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.repository.RaceRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: RaceRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val selectedSection = MutableStateFlow(RaceSection.UPCOMING)
    private val selectedStatus = MutableStateFlow<RaceStatus?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeRaces(),
        selectedSection,
        selectedStatus,
    ) { races, section, status ->
        HomeUiState(
            section = section,
            selectedStatus = status,
            monthGroups = buildRaceMonthGroups(
                races = races,
                section = section,
                selectedStatus = status,
                today = today(),
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun selectSection(section: RaceSection) {
        selectedSection.value = section
    }

    fun selectStatus(status: RaceStatus?) {
        selectedStatus.value = status
    }

    fun updateStatus(raceId: String, status: RaceStatus) {
        viewModelScope.launch {
            repository.updateStatus(raceId, status)
        }
    }

    class Factory(
        private val repository: RaceRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java))
            return HomeViewModel(repository) as T
        }
    }
}

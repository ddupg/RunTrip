package com.ddupg.runtrip.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.repository.RaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: RaceRepository,
    daySource: DaySource = SystemDaySource(),
) : ViewModel() {
    private val controls = MutableStateFlow(HomeControls())

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeRaces(),
        daySource.observeToday(),
        controls,
    ) { races, today, currentControls ->
        HomeUiState(
            section = currentControls.section,
            selectedStatus = currentControls.selectedStatus,
            monthGroups = buildRaceMonthGroups(
                races = races,
                section = currentControls.section,
                selectedStatus = currentControls.selectedStatus,
                today = today,
            ),
            quickStatusRace = currentControls.quickStatusRaceId?.let { selectedId ->
                races.firstOrNull { it.id == selectedId }
            },
            quickStatusUpdate = currentControls.quickStatusUpdate,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun selectSection(section: RaceSection) {
        controls.update { it.copy(section = section) }
    }

    fun selectStatus(status: RaceStatus?) {
        controls.update { it.copy(selectedStatus = status) }
    }

    fun openQuickStatus(raceId: String) {
        controls.update { current ->
            if (current.quickStatusUpdate is QuickStatusUpdate.Saving) {
                current
            } else {
                current.copy(
                    quickStatusRaceId = raceId,
                    quickStatusUpdate = QuickStatusUpdate.Idle,
                )
            }
        }
    }

    fun dismissQuickStatus() {
        controls.update { current ->
            if (current.quickStatusUpdate is QuickStatusUpdate.Saving) {
                current
            } else {
                current.copy(
                    quickStatusRaceId = null,
                    quickStatusUpdate = QuickStatusUpdate.Idle,
                )
            }
        }
    }

    fun updateQuickStatus(status: RaceStatus) {
        val current = controls.value
        val raceId = current.quickStatusRaceId ?: return
        if (current.quickStatusUpdate is QuickStatusUpdate.Saving) return

        controls.update {
            it.copy(quickStatusUpdate = QuickStatusUpdate.Saving(status))
        }
        viewModelScope.launch {
            val updateResult = try {
                if (repository.updateStatus(raceId, status)) {
                    QuickStatusResult.Success
                } else {
                    QuickStatusResult.RaceMissing
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                QuickStatusResult.Failed
            }

            controls.update { latest ->
                if (latest.quickStatusRaceId != raceId) {
                    latest
                } else {
                    when (updateResult) {
                        QuickStatusResult.Success -> latest.copy(
                            quickStatusRaceId = null,
                            quickStatusUpdate = QuickStatusUpdate.Idle,
                        )

                        QuickStatusResult.RaceMissing -> latest.copy(
                            quickStatusUpdate = QuickStatusUpdate.Failed(
                                "没有找到这条比赛记录",
                            ),
                        )

                        QuickStatusResult.Failed -> latest.copy(
                            quickStatusUpdate = QuickStatusUpdate.Failed(
                                "更新参赛状态失败，请重试",
                            ),
                        )
                    }
                }
            }
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

private data class HomeControls(
    val section: RaceSection = RaceSection.UPCOMING,
    val selectedStatus: RaceStatus? = null,
    val quickStatusRaceId: String? = null,
    val quickStatusUpdate: QuickStatusUpdate = QuickStatusUpdate.Idle,
)

private enum class QuickStatusResult {
    Success,
    RaceMissing,
    Failed,
}

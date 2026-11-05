package com.ddupg.runtrip.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.repository.RaceMutationResult
import com.ddupg.runtrip.data.repository.RaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: RaceRepository,
    daySource: DaySource = SystemDaySource(),
) : ViewModel() {
    private val controls = MutableStateFlow(HomeControls())

    private val reload = MutableStateFlow(0)
    private val data = reload.flatMapLatest {
        flow {
            emitAll(combine(repository.observeRaces(), daySource.observeToday()) { races, today ->
                HomeData.Ready(races, today) as HomeData
            })
        }.onStart { emit(HomeData.Loading) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(HomeData.Failed)
            }
    }

    val uiState: StateFlow<HomeUiState> = combine(data, controls) { data, currentControls ->
        val base = HomeUiState(
            section = currentControls.section,
            filter = currentControls.filter,
            quickStatusUpdate = currentControls.quickStatusUpdate,
        )
        when (data) {
            HomeData.Loading -> base.copy(isLoading = true)
            HomeData.Failed -> base.copy(loadError = "加载比赛失败，请重试")
            is HomeData.Ready -> base.copy(
                sectionRaceCount = countRacesInSection(data.races, currentControls.section, data.today),
                monthGroups = buildRaceMonthGroups(data.races, currentControls.section, currentControls.filter, data.today),
                quickStatusRace = data.races.firstOrNull { it.id == currentControls.quickStatusRaceId },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(isLoading = true))

    fun retryLoad() { reload.update { it + 1 } }

    fun selectSection(section: RaceSection) {
        controls.update { it.copy(section = section) }
    }

    fun applyFilter(filter: RaceFilter) {
        controls.update { it.copy(filter = filter) }
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
                when (repository.updateStatus(raceId, status)) {
                    RaceMutationResult.APPLIED -> QuickStatusResult.Success
                    RaceMutationResult.NOT_FOUND -> QuickStatusResult.RaceMissing
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
    val filter: RaceFilter = RaceFilter(),
    val quickStatusRaceId: String? = null,
    val quickStatusUpdate: QuickStatusUpdate = QuickStatusUpdate.Idle,
)

private enum class QuickStatusResult {
    Success,
    RaceMissing,
    Failed,
}

private sealed interface HomeData {
    data object Loading : HomeData
    data object Failed : HomeData
    data class Ready(val races: List<com.ddupg.runtrip.data.model.Race>, val today: java.time.LocalDate) : HomeData
}

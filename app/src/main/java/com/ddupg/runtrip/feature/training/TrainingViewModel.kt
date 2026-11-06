package com.ddupg.runtrip.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ddupg.runtrip.data.model.TrainingAccount
import com.ddupg.runtrip.data.model.TrainingActivity
import com.ddupg.runtrip.data.model.TrainingSport
import com.ddupg.runtrip.data.repository.TrainingImportProgress
import com.ddupg.runtrip.data.repository.TrainingReadLimitReached
import com.ddupg.runtrip.data.repository.TrainingRepository
import com.ddupg.runtrip.data.source.SourceAuthorizationExpired
import com.ddupg.runtrip.data.source.TrainingAccountChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrainingUiState(
    val connected: Boolean = false,
    val loading: Boolean = true,
    val records: List<TrainingActivity> = emptyList(),
    val filter: TrainingSport? = null,
    val updating: Boolean = false,
    val progress: TrainingImportProgress? = null,
    val historyComplete: Boolean = false,
    val error: String? = null,
)

class TrainingViewModel(private val repository: TrainingRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(TrainingUiState())
    val uiState = mutableState.asStateFlow()
    private var account: TrainingAccount? = null
    private var observation: Job? = null
    private var check: Job? = null
    private var updating: Job? = null
    private var checkedAccount = false

    fun onResume() {
        check?.cancel()
        check = viewModelScope.launch {
            try {
                val current = repository.connectedAccount()
                if (current != account || !checkedAccount) {
                    updating?.cancelAndJoin()
                    observation?.cancel()
                    account = current
                    checkedAccount = true
                    mutableState.update { it.copy(connected = current != null, loading = current != null,
                        records = emptyList(), updating = false, progress = null, historyComplete = false, error = null) }
                    if (current != null) {
                        observation = viewModelScope.launch {
                            try {
                                repository.observe(current).collect { records ->
                                    mutableState.update { it.copy(records = records, loading = false) }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                mutableState.update { it.copy(loading = false, error = "无法读取本地训练记录，请重试。") }
                                checkedAccount = false
                            }
                        }
                    }
                }
                if (current != null) {
                    val complete = repository.historyComplete(current)
                    mutableState.update { it.copy(historyComplete = complete) }
                    if (updating?.isActive != true && !complete) update()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = "无法读取数据来源，请重试。") }
            }
        }
    }

    fun selectFilter(sport: TrainingSport?) { mutableState.update { it.copy(filter = sport) } }

    fun update() {
        if (updating?.isActive == true) return
        if (!checkedAccount) return onResume()
        val current = account ?: return onResume()
        mutableState.update { it.copy(updating = true, progress = null, error = null) }
        updating = viewModelScope.launch {
            try {
                repository.update(current) { progress -> mutableState.update { it.copy(progress = progress) } }
                val complete = repository.historyComplete(current)
                mutableState.update { it.copy(historyComplete = complete) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SourceAuthorizationExpired) {
                clearAccount("授权已失效，请前往「我」重新连接。")
            } catch (_: TrainingAccountChanged) {
                onResume()
            } catch (_: TrainingReadLimitReached) {
                mutableState.update { it.copy(error = "单日记录达到接口上限，历史尚未获取完整。") }
            } catch (_: Exception) {
                mutableState.update { it.copy(error = "获取未完成，已保存的记录可继续查看。点击更新重试。") }
            } finally {
                mutableState.update { it.copy(updating = false, progress = null) }
            }
        }
    }

    private fun clearAccount(message: String) {
        account = null
        observation?.cancel()
        mutableState.update { it.copy(connected = false, loading = false, records = emptyList(), historyComplete = false, error = message) }
    }

    class Factory(private val repository: TrainingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TrainingViewModel(repository) as T
    }
}

package com.ddupg.runtrip.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ddupg.runtrip.data.source.DataSourceConnector
import com.ddupg.runtrip.data.source.DataSourceId
import com.ddupg.runtrip.data.source.SourceAccount
import com.ddupg.runtrip.data.source.SourceAuthorizationExpired
import com.ddupg.runtrip.data.source.SourceConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SourceRowState(
    val id: DataSourceId,
    val connection: SourceConnection = SourceConnection.Unbound,
)

data class MeUiState(
    val sources: List<SourceRowState>,
    val isWorking: Boolean = false,
    val selectedSource: DataSourceId? = null,
    val error: String? = null,
)

fun sourceLabel(id: DataSourceId): String = when (id) {
    DataSourceId.COROS -> "高驰"
}

fun accountLabel(account: SourceAccount): String = account.name?.trim()?.takeIf { it.isNotEmpty() } ?: "ID ${account.id}"

fun connectionLabel(connection: SourceConnection): String = when (connection) {
    SourceConnection.Unbound -> "未绑定"
    SourceConnection.StorageError -> "连接信息无法读取"
    is SourceConnection.Authorizing -> "等待完成授权"
    is SourceConnection.Bound -> accountLabel(connection.account)
}

class MeViewModel(private val connectors: List<DataSourceConnector>) : ViewModel() {
    private val mutableState = MutableStateFlow(MeUiState(connectors.map { SourceRowState(it.id) }))
    val uiState = mutableState.asStateFlow()
    private var browserRequest: String? = null
    private var failedAction: (suspend () -> Unit)? = null
    private var resumePending = false
    private val browserState = MutableStateFlow<String?>(null)
    val authorizationUrl = browserState.asStateFlow()

    init {
        onResume()
    }

    private fun connector(id: DataSourceId) = connectors.single { it.id == id }

    private suspend fun reload() {
        val rows = connectors.map { SourceRowState(it.id, it.connection()) }
        mutableState.update { it.copy(sources = rows) }
    }

    private fun perform(action: suspend () -> Unit) {
        if (mutableState.value.isWorking) return
        mutableState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                action()
                failedAction = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SourceAuthorizationExpired) {
                mutableState.update { it.copy(error = "授权已失效，请重新连接") }
            } catch (_: Exception) {
                failedAction = action
                mutableState.update { it.copy(error = "连接未完成，请重试") }
            } finally {
                try {
                    reload()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.update { it.copy(error = "无法读取连接信息，请重试") }
                }
                mutableState.update { it.copy(isWorking = false) }
                if (resumePending) onResume()
            }
        }
    }

    fun onResume() {
        if (mutableState.value.isWorking) {
            resumePending = true
            return
        }
        resumePending = false
        perform {
            reload()
            connectors.filter { source -> mutableState.value.sources.single { it.id == source.id }.connection != SourceConnection.StorageError }.forEach { source ->
                try {
                    source.completeAuthorization()
                } catch (expired: SourceAuthorizationExpired) {
                    failedAction = { requestAuthorization(source.id) }
                    throw expired
                }
            }
        }
    }

    fun openSource(id: DataSourceId) {
        if (mutableState.value.isWorking) return
        when (mutableState.value.sources.single { it.id == id }.connection) {
            SourceConnection.Unbound -> authorize(id)
            else -> mutableState.update { it.copy(selectedSource = id) }
        }
    }

    private suspend fun requestAuthorization(id: DataSourceId) {
        mutableState.update { it.copy(selectedSource = null) }
        val url = connector(id).beginAuthorization()
        browserRequest = url
        browserState.value = url
    }

    fun authorize(id: DataSourceId) = perform { requestAuthorization(id) }

    fun takeAuthorizationUrl(): String? {
        val result = browserRequest
        browserRequest = null
        browserState.value = null
        return result
    }

    fun browserUnavailable(url: String) {
        failedAction = {
            browserRequest = url
            browserState.value = url
        }
        mutableState.update { it.copy(error = "无法打开浏览器，请安装或启用浏览器后继续授权") }
    }

    fun retry() {
        failedAction?.let { perform(it) } ?: onResume()
    }

    fun checkAuthorization() {
        dismissSource()
        onResume()
    }

    fun disconnect(id: DataSourceId) = perform {
        connector(id).disconnect()
        mutableState.update { it.copy(selectedSource = null) }
    }

    fun dismissSource() {
        mutableState.update { it.copy(selectedSource = null) }
    }

    class Factory(private val connectors: List<DataSourceConnector>) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MeViewModel::class.java))
            return MeViewModel(connectors) as T
        }
    }
}

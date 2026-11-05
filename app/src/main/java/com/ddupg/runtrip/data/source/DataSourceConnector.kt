package com.ddupg.runtrip.data.source

enum class DataSourceId(val code: String) {
    COROS("coros"),
}

data class SourceAccount(val id: String, val name: String?)

sealed interface SourceConnection {
    data object Unbound : SourceConnection
    data object StorageError : SourceConnection
    data object AuthorizationRestartRequired : SourceConnection
    // A null URL means credentials were received and account information is still pending.
    class Authorizing(val browserUrl: String?) : SourceConnection
    data class Bound(val account: SourceAccount) : SourceConnection
}

interface DataSourceConnector {
    val id: DataSourceId
    suspend fun connection(): SourceConnection
    suspend fun beginAuthorization(): String
    suspend fun completeAuthorization(): SourceConnection
    suspend fun disconnect()
}

class SourceAuthorizationExpired : Exception()
class SourceProtocolException : Exception()

class SourceStoreReadException(cause: Exception) : Exception(cause)

package com.ddupg.runtrip.data.source.coros

import com.ddupg.runtrip.data.source.TrainingDataSource
import com.ddupg.runtrip.data.source.TrainingAccountChanged
import com.ddupg.runtrip.data.source.DataSourceId
import com.ddupg.runtrip.data.source.SourceAccount
import com.ddupg.runtrip.data.source.SourceAuthorizationExpired
import com.ddupg.runtrip.data.source.SourceConnection
import com.ddupg.runtrip.data.source.SourceProtocolException
import com.ddupg.runtrip.data.source.SourceStoreReadException
import com.ddupg.runtrip.data.source.SourceStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class CorosDataSourceConnector(
    private val store: SourceStore,
    requestClient: CorosRequestClient = CorosRequestClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TrainingDataSource {
    override val id = DataSourceId.COROS
    private val api = CorosOAuthApi(requestClient)
    private val mcp = CorosMcpApi(requestClient)
    private val mutex = Mutex()

    private fun snapshot(): CorosAuthorizationState = CorosAuthorizationCodec.decode(store.read(id) ?: JSONObject())
    private fun save(value: CorosAuthorizationState) = store.write(id, CorosAuthorizationCodec.encode(value))

    private fun connection(value: CorosAuthorizationState): SourceConnection {
        if (value.account != null && value.credentials != null) return SourceConnection.Bound(value.account)
        val pending = value.pending
        return when {
            pending != null && listOf(pending.claimStarted, pending.ticketStarted, pending.exchangeStarted).any { it } -> SourceConnection.AuthorizationRestartRequired
            pending != null -> SourceConnection.Authorizing(pending.loginUrl)
            value.credentials != null -> SourceConnection.Authorizing(null)
            else -> SourceConnection.Unbound
        }
    }

    override suspend fun connection(): SourceConnection = withContext(dispatcher) {
        mutex.withLock {
            try { connection(snapshot()) } catch (_: SourceStoreReadException) { SourceConnection.StorageError }
            catch (_: org.json.JSONException) { SourceConnection.StorageError }
        }
    }

    override suspend fun beginAuthorization(): String = withContext(dispatcher) {
        mutex.withLock {
            val value = snapshot()
            value.pending?.let {
                if (it.expiresAt > clock.instant().epochSecond) return@withLock it.loginUrl
            }
            check(value.credentials == null)
            val issuer = api.discoverIssuer()
            val clientId = api.register(issuer)
            val session = api.createSession(issuer, clientId)
            val pending = CorosPendingAuthorization(
                issuer = issuer,
                clientId = clientId,
                verifier = CorosOAuthApi.randomSecret(),
                state = CorosOAuthApi.randomSecret(),
                sessionId = session.getString("sessionId"),
                pollToken = session.getString("pollToken"),
                loginUrl = session.getString("loginUrl"),
                expiresAt = Instant.parse(session.getString("expiresAt")).epochSecond,
            )
            save(CorosAuthorizationState(pending = pending))
            pending.loginUrl
        }
    }

    override suspend fun completeAuthorization(): SourceConnection = withContext(dispatcher) {
        mutex.withLock {
            val value = snapshot()
            try {
                complete(value)
            } catch (expired: SourceAuthorizationExpired) {
                store.clear(id)
                throw expired
            }
        }
    }

    private suspend fun complete(initial: CorosAuthorizationState): SourceConnection {
        var value = initial
        value.pending?.let { initialPending ->
            var pending = initialPending
            if (pending.expiresAt <= clock.instant().epochSecond && pending.code == null) {
                throw SourceAuthorizationExpired()
            }
            if (pending.ticket == null && pending.code == null) {
                currentCoroutineContext().ensureActive()
                if (pending.claimStarted) throw SourceProtocolException()
                val result = withContext(NonCancellable) {
                    pending = pending.copy(claimStarted = true)
                    value = value.copy(pending = pending)
                    save(value)
                    val claimed = api.claim(pending.issuer, pending.sessionId, pending.pollToken)
                    pending = pending.copy(
                        ticket = if (claimed.optString("status").equals("authorized", ignoreCase = true)) claimed.getString("loginTicket") else pending.ticket,
                        claimStarted = false,
                    )
                    value = value.copy(pending = pending)
                    save(value)
                    claimed
                }
                currentCoroutineContext().ensureActive()
                when (result.getString("status").lowercase(java.util.Locale.ROOT)) {
                    "pending" -> return connection(value)
                    "authorized" -> Unit
                    "expired", "failed", "claimed" -> throw SourceAuthorizationExpired()
                    else -> throw SourceProtocolException()
                }
            }
            if (pending.code == null) {
                currentCoroutineContext().ensureActive()
                if (pending.ticketStarted) throw SourceProtocolException()
                withContext(NonCancellable) {
                    pending = pending.copy(ticketStarted = true)
                    value = value.copy(pending = pending)
                    save(value)
                    pending = pending.copy(code = api.authorizationCode(pending, pending.ticket!!), ticket = null, ticketStarted = false)
                    value = value.copy(pending = pending)
                    save(value)
                }
            }
            currentCoroutineContext().ensureActive()
            if (pending.exchangeStarted) throw SourceProtocolException()
            withContext(NonCancellable) {
                pending = pending.copy(exchangeStarted = true)
                value = value.copy(pending = pending)
                save(value)
                val credentials = credentials(api.exchange(pending), pending.issuer, pending.clientId)
                value = value.copy(credentials = credentials, pending = null)
                save(value)
            }
        }
        currentCoroutineContext().ensureActive()
        var credentials = value.credentials ?: return SourceConnection.Unbound
        var refreshed = false
        if (credentials.expiresAt <= clock.instant().epochSecond) {
            credentials = refresh(value, credentials)
            value = value.copy(credentials = credentials)
            refreshed = true
        }
        if (value.account == null) {
            currentCoroutineContext().ensureActive()
            val account = try {
                mcp.account(credentials.issuer, credentials.accessToken)
            } catch (rejected: CorosAccessTokenRejected) {
                if (refreshed) throw rejected
                credentials = refresh(value, credentials)
                value = value.copy(credentials = credentials)
                currentCoroutineContext().ensureActive()
                mcp.account(credentials.issuer, credentials.accessToken)
            }
            value = value.copy(account = account)
            save(value)
        }
        return connection(value)
    }

    private suspend fun refresh(value: CorosAuthorizationState, old: CorosCredentials): CorosCredentials {
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) {
            val updated = credentials(api.refresh(old.issuer, old.clientId, old.refreshToken), old.issuer, old.clientId)
            save(value.copy(credentials = updated))
            updated
        }
    }

    private fun credentials(token: JSONObject, issuer: String, clientId: String) = CorosCredentials(
        issuer = issuer,
        clientId = clientId,
        accessToken = token.getString("access_token"),
        refreshToken = token.getString("refresh_token"),
        expiresAt = clock.instant().epochSecond + token.getLong("expires_in"),
    )

    override suspend fun disconnect() = withContext(dispatcher) {
        mutex.withLock { store.clear(id) }
    }

    override suspend fun readTraining(accountId: String, start: LocalDate, end: LocalDate, limit: Int) = withContext(dispatcher) {
        mutex.withLock {
            val value = snapshot()
            val bound = connection(value) as? SourceConnection.Bound
            if (bound?.account?.id != accountId) throw TrainingAccountChanged()
            try {
                var credentials = value.credentials!!
                var refreshed = false
                if (credentials.expiresAt <= clock.instant().epochSecond) {
                    credentials = refresh(value, credentials)
                    refreshed = true
                }
                currentCoroutineContext().ensureActive()
                try {
                    mcp.training(credentials.issuer, credentials.accessToken, start, end, limit)
                } catch (rejected: CorosAccessTokenRejected) {
                    if (refreshed) throw rejected
                    credentials = refresh(value, credentials)
                    currentCoroutineContext().ensureActive()
                    mcp.training(credentials.issuer, credentials.accessToken, start, end, limit)
                }
            } catch (expired: SourceAuthorizationExpired) {
                store.clear(id)
                throw expired
            }
        }
    }
}

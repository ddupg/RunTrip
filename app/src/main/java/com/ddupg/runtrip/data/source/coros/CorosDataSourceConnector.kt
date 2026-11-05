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
    private val api: CorosOAuthApi = CorosOAuthApi(),
    private val clock: Clock = Clock.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TrainingDataSource {
    override val id = DataSourceId.COROS
    private val mutex = Mutex()

    private fun snapshot(): JSONObject = store.read(id) ?: JSONObject()

    private fun connection(value: JSONObject): SourceConnection {
        val account = value.optJSONObject("account")
        if (account != null && value.has("credentials")) {
            return SourceConnection.Bound(
                SourceAccount(account.getString("id"), if (account.isNull("name")) null else account.getString("name")),
            )
        }
        val pending = value.optJSONObject("pending")
        return when {
            pending != null && listOf("claimStarted", "ticketStarted", "exchangeStarted").any { pending.optBoolean(it) } -> SourceConnection.AuthorizationRestartRequired
            pending != null -> SourceConnection.Authorizing(pending.getString("loginUrl"))
            value.has("credentials") -> SourceConnection.Authorizing(null)
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
            value.optJSONObject("pending")?.let {
                if (it.getLong("expiresAt") > clock.instant().epochSecond) return@withLock it.getString("loginUrl")
            }
            check(!value.has("credentials"))
            val issuer = api.discoverIssuer()
            val clientId = api.register(issuer)
            val session = api.createSession(issuer, clientId)
            val pending = JSONObject()
                .put("issuer", issuer).put("clientId", clientId)
                .put("verifier", CorosOAuthApi.randomSecret()).put("state", CorosOAuthApi.randomSecret())
                .put("sessionId", session.getString("sessionId")).put("pollToken", session.getString("pollToken"))
                .put("loginUrl", session.getString("loginUrl"))
                .put("expiresAt", Instant.parse(session.getString("expiresAt")).epochSecond)
            store.write(id, JSONObject().put("pending", pending))
            pending.getString("loginUrl")
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

    private suspend fun complete(value: JSONObject): SourceConnection {
        val pending = value.optJSONObject("pending")
        if (pending != null) {
            if (pending.getLong("expiresAt") <= clock.instant().epochSecond && !pending.has("code")) {
                throw SourceAuthorizationExpired()
            }
            if (!pending.has("ticket") && !pending.has("code")) {
                currentCoroutineContext().ensureActive()
                if (pending.optBoolean("claimStarted")) throw SourceProtocolException()
                val result = withContext(NonCancellable) {
                    pending.put("claimStarted", true)
                    store.write(id, value)
                    val claimed = api.claim(pending.getString("issuer"), pending.getString("sessionId"), pending.getString("pollToken"))
                    if (claimed.optString("status").equals("authorized", ignoreCase = true)) {
                        pending.put("ticket", claimed.getString("loginTicket"))
                    }
                    pending.remove("claimStarted")
                    store.write(id, value)
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
            if (!pending.has("code")) {
                currentCoroutineContext().ensureActive()
                if (pending.optBoolean("ticketStarted")) throw SourceProtocolException()
                withContext(NonCancellable) {
                    pending.put("ticketStarted", true)
                    store.write(id, value)
                    pending.put("code", api.authorizationCode(pending, pending.getString("ticket")))
                    pending.remove("ticket")
                    pending.remove("ticketStarted")
                    store.write(id, value)
                }
            }
            currentCoroutineContext().ensureActive()
            if (pending.optBoolean("exchangeStarted")) throw SourceProtocolException()
            withContext(NonCancellable) {
                pending.put("exchangeStarted", true)
                store.write(id, value)
                val credentials = credentials(api.exchange(pending), pending.getString("issuer"), pending.getString("clientId"))
                value.put("credentials", credentials)
                value.remove("pending")
                store.write(id, value)
            }
        }
        currentCoroutineContext().ensureActive()
        var credentials = value.optJSONObject("credentials") ?: return SourceConnection.Unbound
        var refreshed = false
        if (credentials.getLong("expiresAt") <= clock.instant().epochSecond) {
            credentials = refresh(value, credentials)
            refreshed = true
        }
        if (!value.has("account")) {
            currentCoroutineContext().ensureActive()
            val account = try {
                api.account(credentials.getString("issuer"), credentials.getString("accessToken"))
            } catch (rejected: CorosAccessTokenRejected) {
                if (refreshed) throw rejected
                credentials = refresh(value, credentials)
                currentCoroutineContext().ensureActive()
                api.account(credentials.getString("issuer"), credentials.getString("accessToken"))
            }
            value.put("account", JSONObject().put("id", account.id).put("name", account.name ?: JSONObject.NULL))
            store.write(id, value)
        }
        return connection(value)
    }

    private suspend fun refresh(value: JSONObject, old: JSONObject): JSONObject {
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) {
            val updated = credentials(
                api.refresh(old.getString("issuer"), old.getString("clientId"), old.getString("refreshToken")),
                old.getString("issuer"), old.getString("clientId"),
            )
            value.put("credentials", updated)
            store.write(id, value)
            updated
        }
    }

    private fun credentials(token: JSONObject, issuer: String, clientId: String): JSONObject = JSONObject()
        .put("issuer", issuer).put("clientId", clientId)
        .put("accessToken", token.getString("access_token")).put("refreshToken", token.getString("refresh_token"))
        .put("expiresAt", clock.instant().epochSecond + token.getLong("expires_in"))

    override suspend fun disconnect() = withContext(dispatcher) {
        mutex.withLock { store.clear(id) }
    }

    override suspend fun readTraining(accountId: String, start: LocalDate, end: LocalDate, limit: Int) = withContext(dispatcher) {
        mutex.withLock {
            val value = snapshot()
            val bound = connection(value) as? SourceConnection.Bound
            if (bound?.account?.id != accountId) throw TrainingAccountChanged()
            try {
                var credentials = value.getJSONObject("credentials")
                var refreshed = false
                if (credentials.getLong("expiresAt") <= clock.instant().epochSecond) {
                    credentials = refresh(value, credentials)
                    refreshed = true
                }
                currentCoroutineContext().ensureActive()
                try {
                    api.training(credentials.getString("issuer"), credentials.getString("accessToken"), start, end, limit)
                } catch (rejected: CorosAccessTokenRejected) {
                    if (refreshed) throw rejected
                    credentials = refresh(value, credentials)
                    currentCoroutineContext().ensureActive()
                    api.training(credentials.getString("issuer"), credentials.getString("accessToken"), start, end, limit)
                }
            } catch (expired: SourceAuthorizationExpired) {
                store.clear(id)
                throw expired
            }
        }
    }
}

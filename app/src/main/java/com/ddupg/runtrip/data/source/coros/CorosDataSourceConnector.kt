package com.ddupg.runtrip.data.source.coros

import com.ddupg.runtrip.data.source.DataSourceConnector
import com.ddupg.runtrip.data.source.DataSourceId
import com.ddupg.runtrip.data.source.SourceAccount
import com.ddupg.runtrip.data.source.SourceAuthorizationExpired
import com.ddupg.runtrip.data.source.SourceConnection
import com.ddupg.runtrip.data.source.SourceProtocolException
import com.ddupg.runtrip.data.source.SourceStore
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class CorosDataSourceConnector(
    private val store: SourceStore,
    private val api: CorosOAuthApi = CorosOAuthApi(),
    private val clock: Clock = Clock.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataSourceConnector {
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
            pending != null -> SourceConnection.Authorizing(pending.getString("loginUrl"))
            value.has("credentials") -> SourceConnection.Authorizing(null)
            else -> SourceConnection.Unbound
        }
    }

    override suspend fun connection(): SourceConnection = withContext(dispatcher) {
        mutex.withLock { connection(snapshot()) }
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

    private fun complete(value: JSONObject): SourceConnection {
        val pending = value.optJSONObject("pending")
        if (pending != null) {
            if (pending.getLong("expiresAt") <= clock.instant().epochSecond && !pending.has("code")) {
                throw SourceAuthorizationExpired()
            }
            if (!pending.has("ticket") && !pending.has("code")) {
                val result = api.claim(pending.getString("issuer"), pending.getString("sessionId"), pending.getString("pollToken"))
                when (result.getString("status").lowercase(java.util.Locale.ROOT)) {
                    "pending" -> return connection(value)
                    "authorized" -> {
                        pending.put("ticket", result.getString("loginTicket"))
                        store.write(id, value)
                    }
                    "expired", "failed", "claimed" -> throw SourceAuthorizationExpired()
                    else -> throw SourceProtocolException()
                }
            }
            if (!pending.has("code")) {
                pending.put("code", api.authorizationCode(pending, pending.getString("ticket")))
                pending.remove("ticket")
                store.write(id, value)
            }
            val credentials = credentials(api.exchange(pending), pending.getString("issuer"), pending.getString("clientId"))
            value.put("credentials", credentials)
            value.remove("pending")
            // Persist before requesting account info: a claimed session/code must not be replayed.
            store.write(id, value)
        }
        var credentials = value.optJSONObject("credentials") ?: return SourceConnection.Unbound
        if (credentials.getLong("expiresAt") <= clock.instant().epochSecond) {
            credentials = credentials(
                api.refresh(credentials.getString("issuer"), credentials.getString("clientId"), credentials.getString("refreshToken")),
                credentials.getString("issuer"), credentials.getString("clientId"),
            )
            value.put("credentials", credentials)
            store.write(id, value)
        }
        if (!value.has("account")) {
            val account = api.account(credentials.getString("issuer"), credentials.getString("accessToken"))
            value.put("account", JSONObject().put("id", account.id).put("name", account.name ?: JSONObject.NULL))
            store.write(id, value)
        }
        return connection(value)
    }

    private fun credentials(token: JSONObject, issuer: String, clientId: String): JSONObject = JSONObject()
        .put("issuer", issuer).put("clientId", clientId)
        .put("accessToken", token.getString("access_token")).put("refreshToken", token.getString("refresh_token"))
        .put("expiresAt", clock.instant().epochSecond + token.getLong("expires_in"))

    override suspend fun disconnect() = withContext(dispatcher) {
        mutex.withLock { store.clear(id) }
    }
}

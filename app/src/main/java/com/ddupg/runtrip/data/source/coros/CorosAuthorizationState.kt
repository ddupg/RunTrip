package com.ddupg.runtrip.data.source.coros

import com.ddupg.runtrip.data.source.SourceAccount
import org.json.JSONObject

internal data class CorosCredentials(
    val issuer: String,
    val clientId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
)

internal data class CorosPendingAuthorization(
    val issuer: String,
    val clientId: String,
    val verifier: String,
    val state: String,
    val sessionId: String,
    val pollToken: String,
    val loginUrl: String,
    val expiresAt: Long,
    val ticket: String? = null,
    val code: String? = null,
    val claimStarted: Boolean? = null,
    val ticketStarted: Boolean? = null,
    val exchangeStarted: Boolean? = null,
)

internal data class CorosAuthorizationState(
    val credentials: CorosCredentials? = null,
    val pending: CorosPendingAuthorization? = null,
    val account: SourceAccount? = null,
)

/** The encrypted SourceStore owns the envelope; this codec preserves its v1 value fields. */
internal object CorosAuthorizationCodec {
    fun decode(value: JSONObject): CorosAuthorizationState {
        val credentials = value.optionalObject("credentials")?.let {
            CorosCredentials(
                issuer = it.getString("issuer"),
                clientId = it.getString("clientId"),
                accessToken = it.getString("accessToken"),
                refreshToken = it.getString("refreshToken"),
                expiresAt = it.getLong("expiresAt"),
            )
        }
        val pending = value.optionalObject("pending")?.let {
            CorosPendingAuthorization(
                issuer = it.getString("issuer"),
                clientId = it.getString("clientId"),
                verifier = it.getString("verifier"),
                state = it.getString("state"),
                sessionId = it.getString("sessionId"),
                pollToken = it.getString("pollToken"),
                loginUrl = it.getString("loginUrl"),
                expiresAt = it.getLong("expiresAt"),
                ticket = it.optionalString("ticket"),
                code = it.optionalString("code"),
                claimStarted = it.optionalBoolean("claimStarted"),
                ticketStarted = it.optionalBoolean("ticketStarted"),
                exchangeStarted = it.optionalBoolean("exchangeStarted"),
            )
        }
        val account = value.optionalObject("account")?.let {
            SourceAccount(id = it.getString("id"), name = if (it.isNull("name")) null else it.getString("name"))
        }
        return CorosAuthorizationState(credentials = credentials, pending = pending, account = account)
    }

    fun encode(value: CorosAuthorizationState): JSONObject = JSONObject().apply {
        value.credentials?.let { put("credentials", encodeCredentials(it)) }
        value.pending?.let { put("pending", encodePending(it)) }
        value.account?.let { put("account", JSONObject().put("id", it.id).put("name", it.name ?: JSONObject.NULL)) }
    }

    private fun encodeCredentials(value: CorosCredentials) = JSONObject()
        .put("issuer", value.issuer)
        .put("clientId", value.clientId)
        .put("accessToken", value.accessToken)
        .put("refreshToken", value.refreshToken)
        .put("expiresAt", value.expiresAt)

    private fun encodePending(value: CorosPendingAuthorization) = JSONObject()
        .put("issuer", value.issuer)
        .put("clientId", value.clientId)
        .put("verifier", value.verifier)
        .put("state", value.state)
        .put("sessionId", value.sessionId)
        .put("pollToken", value.pollToken)
        .put("loginUrl", value.loginUrl)
        .put("expiresAt", value.expiresAt)
        .putOptional("ticket", value.ticket)
        .putOptional("code", value.code)
        .putOptional("claimStarted", value.claimStarted)
        .putOptional("ticketStarted", value.ticketStarted)
        .putOptional("exchangeStarted", value.exchangeStarted)

    private fun JSONObject.optionalObject(key: String): JSONObject? = if (has(key)) getJSONObject(key) else null
    private fun JSONObject.optionalString(key: String): String? = if (has(key)) getString(key) else null
    private fun JSONObject.optionalBoolean(key: String): Boolean? = if (has(key)) optBoolean(key) else null
    private fun JSONObject.putOptional(key: String, value: Any?): JSONObject = apply { if (value != null) put(key, value) }
}

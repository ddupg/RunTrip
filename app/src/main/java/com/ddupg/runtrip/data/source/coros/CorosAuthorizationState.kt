package com.ddupg.runtrip.data.source.coros

import com.ddupg.runtrip.data.source.SourceAccount
import org.json.JSONObject

internal data class CorosCredentials(
    val issuer: String, val clientId: String, val accessToken: String,
    val refreshToken: String, val expiresAt: Long,
)

internal data class CorosPendingAuthorization(
    val issuer: String, val clientId: String, val verifier: String, val state: String,
    val sessionId: String, val pollToken: String, val loginUrl: String, val expiresAt: Long,
    val ticket: String? = null, val code: String? = null,
    val claimStarted: Boolean? = null, val ticketStarted: Boolean? = null, val exchangeStarted: Boolean? = null,
)

internal data class CorosAuthorizationState(
    val credentials: CorosCredentials? = null,
    val pending: CorosPendingAuthorization? = null,
    val account: SourceAccount? = null,
)

/** The encrypted SourceStore owns the envelope; this codec preserves its v1 value fields. */
internal object CorosAuthorizationCodec {
    fun decode(value: JSONObject): CorosAuthorizationState {
        val credentials = value.optJSONObject("credentials")?.let {
            CorosCredentials(it.getString("issuer"), it.getString("clientId"), it.getString("accessToken"),
                it.getString("refreshToken"), it.getLong("expiresAt"))
        }
        val pending = value.optJSONObject("pending")?.let {
            CorosPendingAuthorization(it.getString("issuer"), it.getString("clientId"), it.getString("verifier"),
                it.getString("state"), it.getString("sessionId"), it.getString("pollToken"),
                it.getString("loginUrl"), it.getLong("expiresAt"),
                it.optionalString("ticket"), it.optionalString("code"), it.optionalBoolean("claimStarted"),
                it.optionalBoolean("ticketStarted"), it.optionalBoolean("exchangeStarted"))
        }
        val account = value.optJSONObject("account")?.let {
            SourceAccount(it.getString("id"), if (it.isNull("name")) null else it.getString("name"))
        }
        return CorosAuthorizationState(credentials, pending, account)
    }

    fun encode(value: CorosAuthorizationState): JSONObject = JSONObject().apply {
        value.credentials?.let { put("credentials", JSONObject().put("issuer", it.issuer).put("clientId", it.clientId)
            .put("accessToken", it.accessToken).put("refreshToken", it.refreshToken).put("expiresAt", it.expiresAt)) }
        value.pending?.let { put("pending", JSONObject().put("issuer", it.issuer).put("clientId", it.clientId)
            .put("verifier", it.verifier).put("state", it.state).put("sessionId", it.sessionId)
            .put("pollToken", it.pollToken).put("loginUrl", it.loginUrl).put("expiresAt", it.expiresAt)
            .putOptional("ticket", it.ticket).putOptional("code", it.code)
            .putOptional("claimStarted", it.claimStarted).putOptional("ticketStarted", it.ticketStarted)
            .putOptional("exchangeStarted", it.exchangeStarted)) }
        value.account?.let { put("account", JSONObject().put("id", it.id).put("name", it.name ?: JSONObject.NULL)) }
    }

    private fun JSONObject.optionalString(key: String): String? = if (has(key)) getString(key) else null
    private fun JSONObject.optionalBoolean(key: String): Boolean? = if (has(key)) optBoolean(key) else null
    private fun JSONObject.putOptional(key: String, value: Any?): JSONObject = apply { if (value != null) put(key, value) }
}

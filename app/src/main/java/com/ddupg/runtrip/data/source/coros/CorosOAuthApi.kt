package com.ddupg.runtrip.data.source.coros

import com.ddupg.runtrip.data.source.SourceAuthorizationExpired
import com.ddupg.runtrip.data.source.SourceProtocolException
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal class CorosAccessTokenRejected : Exception()

internal class CorosOAuthApi(private val transport: CorosHttpTransport = UrlConnectionCorosTransport()) {
    companion object {
        const val REDIRECT = "http://127.0.0.1:43123/callback"
        const val SCOPES = "openid offline_access mcp.tools"
        private val issuers = setOf("https://mcpcn.coros.com", "https://mcpeu.coros.com", "https://mcpus.coros.com")

        fun randomSecret(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
            ByteArray(32).also { SecureRandom().nextBytes(it) },
        )

        fun pkceChallenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        private fun form(values: Map<String, String>) = values.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8.name())}=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
    }

    internal suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): CorosHttpResponse {
        val uri = URI(url)
        val origin = "${uri.scheme}://${uri.host}"
        if ((origin !in issuers && origin != "https://mcp.coros.com") || uri.rawUserInfo != null || uri.port != -1) {
            throw SourceProtocolException()
        }
        return transport.request(method, url, headers, body)
    }

    internal fun checked(response: CorosHttpResponse): JSONObject {
        if (response.status !in 200..299) throw SourceProtocolException()
        return JSONObject(response.body)
    }

    private suspend fun postJson(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()) = checked(
        request("POST", url, headers + ("Content-Type" to "application/json"), body.toString()),
    )

    suspend fun discoverIssuer(): String {
        val issuer = checked(request("GET", "https://mcp.coros.com/.well-known/openid-configuration")).getString("issuer")
        if (issuer !in issuers) throw SourceProtocolException()
        return issuer
    }

    suspend fun register(issuer: String): String = postJson(
        "$issuer/connect/register",
        JSONObject().put("client_name", "RunTrip")
            .put("redirect_uris", JSONArray().put(REDIRECT))
            .put("grant_types", JSONArray().put("authorization_code").put("refresh_token"))
            .put("response_types", JSONArray().put("code"))
            .put("scope", SCOPES).put("token_endpoint_auth_method", "none"),
    ).getString("client_id").also { if (it.isBlank()) throw SourceProtocolException() }

    suspend fun createSession(issuer: String, clientId: String): JSONObject = postJson(
        "$issuer/api/v1/cli/login-sessions", JSONObject().put("clientId", clientId),
    ).also {
        val loginUri = URI(it.getString("loginUrl"))
        if ("${loginUri.scheme}://${loginUri.host}" != issuer || loginUri.rawUserInfo != null || loginUri.port != -1) {
            throw SourceProtocolException()
        }
    }

    suspend fun claim(issuer: String, sessionId: String, pollToken: String): JSONObject {
        if (!sessionId.matches(Regex("[A-Za-z0-9_-]+"))) throw SourceProtocolException()
        return checked(request("POST", "$issuer/api/v1/cli/login-sessions/$sessionId/claim", mapOf("X-Poll-Token" to pollToken)))
    }

    suspend fun authorizationCode(pending: CorosPendingAuthorization, ticket: String): String {
        val query = form(
            mapOf(
                "response_type" to "code", "client_id" to pending.clientId,
                "redirect_uri" to REDIRECT, "scope" to SCOPES,
                "code_challenge" to pkceChallenge(pending.verifier),
                "code_challenge_method" to "S256", "resource" to "${pending.issuer}/mcp",
                "state" to pending.state, "login_ticket" to ticket,
            ),
        )
        val response = request("GET", "${pending.issuer}/oauth2/authorize?$query")
        if (response.status !in listOf(302, 303)) throw SourceProtocolException()
        val redirect = URI(response.location ?: throw SourceProtocolException())
        if (redirect.scheme != "http" || redirect.host != "127.0.0.1" || redirect.port != 43123 || redirect.path != "/callback") {
            throw SourceProtocolException()
        }
        val parameters = redirect.rawQuery.orEmpty().split('&').associate { part ->
            val pair = part.split('=', limit = 2)
            java.net.URLDecoder.decode(pair[0], "UTF-8") to java.net.URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
        if (parameters["state"] != pending.state) throw SourceProtocolException()
        return parameters["code"]?.takeIf { it.isNotBlank() } ?: throw SourceProtocolException()
    }

    suspend fun exchange(pending: CorosPendingAuthorization): JSONObject = tokenRequest(
        pending.issuer,
        mapOf("grant_type" to "authorization_code", "client_id" to pending.clientId,
              "code" to pending.code!!, "redirect_uri" to REDIRECT,
              "code_verifier" to pending.verifier),
    )

    suspend fun refresh(issuer: String, clientId: String, refreshToken: String): JSONObject = tokenRequest(
        issuer, mapOf("grant_type" to "refresh_token", "client_id" to clientId, "refresh_token" to refreshToken),
    )

    private suspend fun tokenRequest(issuer: String, values: Map<String, String>): JSONObject {
        val response = request("POST", "$issuer/oauth2/token", mapOf("Content-Type" to "application/x-www-form-urlencoded"), form(values))
        if (values["grant_type"] == "refresh_token" && response.status == 400 && JSONObject(response.body).optString("error") == "invalid_grant") {
            throw SourceAuthorizationExpired()
        }
        val token = checked(response)
        if (token.getString("access_token").isBlank() || token.getString("refresh_token").isBlank() || token.getLong("expires_in") <= 0) {
            throw SourceProtocolException()
        }
        return token
    }

    private val mcp = CorosMcpApi(this)

    suspend fun account(issuer: String, accessToken: String) = mcp.account(issuer, accessToken)

    suspend fun training(issuer: String, accessToken: String, start: LocalDate, end: LocalDate, limit: Int) =
        mcp.training(issuer, accessToken, start, end, limit)
}

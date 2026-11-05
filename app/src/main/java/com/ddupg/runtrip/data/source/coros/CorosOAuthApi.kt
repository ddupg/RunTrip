package com.ddupg.runtrip.data.source.coros

import com.ddupg.runtrip.data.source.SourceAccount
import com.ddupg.runtrip.data.source.SourceAuthorizationExpired
import com.ddupg.runtrip.data.source.SourceProtocolException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONArray
import org.json.JSONObject

internal class CorosHttpResponse(val status: Int, val body: String, val location: String? = null)

internal fun interface CorosHttpTransport {
    suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?): CorosHttpResponse
}

internal class UrlConnectionCorosTransport : CorosHttpTransport {
    override suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?): CorosHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            continuation.invokeOnCancellation { Thread { connection.disconnect() }.apply { isDaemon = true; start() } }
            Thread {
                try {
                    if (!continuation.isActive) return@Thread
                    connection.requestMethod = method
                    connection.instanceFollowRedirects = false
                    headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
                    if (body != null) {
                        connection.doOutput = true
                        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                    val status = connection.responseCode
                    val stream = if (status < 400) connection.inputStream else connection.errorStream
                    val response = CorosHttpResponse(status, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(),
                        connection.getHeaderField("Location"))
                    if (continuation.isActive) continuation.resume(response)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally {
                    connection.disconnect()
                }
            }.apply { isDaemon = true; start() }
        }

}

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

    private suspend fun request(
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

    private fun checked(response: CorosHttpResponse): JSONObject {
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

    suspend fun authorizationCode(pending: JSONObject, ticket: String): String {
        val query = form(
            mapOf(
                "response_type" to "code", "client_id" to pending.getString("clientId"),
                "redirect_uri" to REDIRECT, "scope" to SCOPES,
                "code_challenge" to pkceChallenge(pending.getString("verifier")),
                "code_challenge_method" to "S256", "resource" to "${pending.getString("issuer")}/mcp",
                "state" to pending.getString("state"), "login_ticket" to ticket,
            ),
        )
        val response = request("GET", "${pending.getString("issuer")}/oauth2/authorize?$query")
        if (response.status !in listOf(302, 303)) throw SourceProtocolException()
        val redirect = URI(response.location ?: throw SourceProtocolException())
        if (redirect.scheme != "http" || redirect.host != "127.0.0.1" || redirect.port != 43123 || redirect.path != "/callback") {
            throw SourceProtocolException()
        }
        val parameters = redirect.rawQuery.orEmpty().split('&').associate { part ->
            val pair = part.split('=', limit = 2)
            java.net.URLDecoder.decode(pair[0], "UTF-8") to java.net.URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
        if (parameters["state"] != pending.getString("state")) throw SourceProtocolException()
        return parameters["code"]?.takeIf { it.isNotBlank() } ?: throw SourceProtocolException()
    }

    suspend fun exchange(pending: JSONObject): JSONObject = tokenRequest(
        pending.getString("issuer"),
        mapOf("grant_type" to "authorization_code", "client_id" to pending.getString("clientId"),
              "code" to pending.getString("code"), "redirect_uri" to REDIRECT,
              "code_verifier" to pending.getString("verifier")),
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

    suspend fun account(issuer: String, accessToken: String): SourceAccount {
        val headers = mapOf("Authorization" to "Bearer $accessToken", "Accept" to "application/json, text/event-stream")
        val userResponse = request("GET", "$issuer/userinfo", headers)
        if (userResponse.status == 401) throw CorosAccessTokenRejected()
        val user = checked(userResponse)
        val subject = user.getString("sub").takeIf { it.isNotBlank() } ?: throw SourceProtocolException()
        mcp(issuer, headers, "initialize", JSONObject().put("protocolVersion", "2025-06-18")
            .put("capabilities", JSONObject()).put("clientInfo", JSONObject().put("name", "RunTrip").put("version", "1.0")))
        val profile = mcp(issuer, headers, "tools/call", JSONObject().put("name", "queryUserInfo").put("arguments", JSONObject()))
        if (profile.optBoolean("isError")) throw SourceProtocolException()
        val content = profile.getJSONArray("content")
        var name: String? = null
        for (index in 0 until content.length()) {
            val item = content.getJSONObject(index)
            if (item.optString("type") != "text") continue
            val text = org.json.JSONTokener(item.getString("text")).nextValue() as? String ?: throw SourceProtocolException()
            val match = Regex("(?m)^Nickname:[ \\t]*([^\\r\\n]*)").find(text)
            if (match != null) name = match.groupValues[1].trim().takeIf { it.isNotEmpty() }
        }
        return SourceAccount(id = subject, name = name)
    }

    private suspend fun mcp(issuer: String, headers: Map<String, String>, method: String, params: JSONObject): JSONObject {
        val response = request("POST", "$issuer/mcp", headers + ("Content-Type" to "application/json"),
            JSONObject().put("jsonrpc", "2.0").put("id", if (method == "initialize") 1 else 2)
                .put("method", method).put("params", params).toString())
        if (response.status == 401) throw CorosAccessTokenRejected()
        if (response.status != 200) throw SourceProtocolException()
        // COROS may frame JSON-RPC responses as SSE, as in the official helper.
        val json = if (response.body.trimStart().startsWith("{")) response.body else {
            val events = response.body.replace("\r\n", "\n").split("\n\n")
            events.map { event ->
                event.lineSequence().filter { it.startsWith("data:") }
                    .joinToString("\n") { it.removePrefix("data:").trimStart() }
            }.lastOrNull { it.isNotBlank() } ?: throw SourceProtocolException()
        }
        val envelope = JSONObject(json)
        if (envelope.has("error")) throw SourceProtocolException()
        return envelope.getJSONObject("result")
    }

    suspend fun training(issuer: String, accessToken: String, start: LocalDate, end: LocalDate, limit: Int) = run {
        val headers = mapOf("Authorization" to "Bearer $accessToken", "Accept" to "application/json, text/event-stream")
        mcp(issuer, headers, "initialize", JSONObject().put("protocolVersion", "2025-06-18")
            .put("capabilities", JSONObject()).put("clientInfo", JSONObject().put("name", "RunTrip").put("version", "1.0")))
        val arguments = JSONObject()
            .put("startDate", start.format(DateTimeFormatter.BASIC_ISO_DATE))
            .put("endDate", end.format(DateTimeFormatter.BASIC_ISO_DATE))
            .put("sportTypeCodes", JSONArray(CorosTrainingParser.subtypes.keys.toList())).put("limit", limit)
        CorosTrainingParser.parse(
            mcp(issuer, headers, "tools/call", JSONObject().put("name", "querySportRecords").put("arguments", arguments)),
            start, end,
        )
    }
}

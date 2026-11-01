package com.ddupg.runtrip.data.source.coros

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

internal class TestCorosService : CorosHttpTransport {
    var authorized = false
    var wrongState = false
    var profileFails = false
    var invalidRefresh = false
    var nickname = "Synthetic Runner"
    var sse = false
    var issuer = "https://mcpcn.coros.com"
    var tokenExchanges = 0
    var refreshes = 0
    var claims = 0
    var registrations = 0
    var lastChallenge: String? = null
    var lastVerifier: String? = null

    private fun fields(text: String) = text.split('&').associate {
        val pair = it.split('=', limit = 2)
        URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
    }

    override fun request(method: String, url: String, headers: Map<String, String>, body: String?): CorosHttpResponse {
        val uri = URI(url)
        val json = when (uri.path) {
            "/.well-known/openid-configuration" -> JSONObject().put("issuer", issuer)
            "/connect/register" -> {
                registrations++
                check(method == "POST")
                check(JSONObject(body!!).getString("token_endpoint_auth_method") == "none")
                JSONObject().put("client_id", "synthetic-client")
            }
            "/api/v1/cli/login-sessions" -> JSONObject()
                .put("sessionId", "synthetic-session").put("pollToken", "synthetic-poll-secret")
                .put("loginUrl", "$issuer/cli/login/synthetic-session").put("expiresAt", "2026-11-01T15:00:00Z")
            "/api/v1/cli/login-sessions/synthetic-session/claim" -> {
                claims++
                check(headers["X-Poll-Token"] == "synthetic-poll-secret")
                if (authorized) JSONObject().put("status", "authorized").put("loginTicket", "synthetic-ticket")
                else JSONObject().put("status", "pending")
            }
            "/oauth2/authorize" -> {
                val params = fields(uri.rawQuery)
                lastChallenge = params["code_challenge"]
                val state = if (wrongState) "incorrect-state" else params.getValue("state")
                return CorosHttpResponse(302, "", CorosOAuthApi.REDIRECT + "?code=synthetic-code&state=" + URLEncoder.encode(state, "UTF-8"))
            }
            "/oauth2/token" -> {
                val params = fields(body!!)
                when (params["grant_type"]) {
                    "authorization_code" -> {
                        tokenExchanges++
                        lastVerifier = params["code_verifier"]
                        check(params["code"] == "synthetic-code")
                    }
                    "refresh_token" -> {
                        refreshes++
                        if (invalidRefresh) return CorosHttpResponse(400, "{\"error\":\"invalid_grant\"}")
                    }
                    else -> error("Unexpected grant")
                }
                JSONObject().put("access_token", "synthetic-access-$refreshes")
                    .put("refresh_token", "synthetic-refresh-$refreshes").put("expires_in", 3600)
            }
            "/userinfo" -> {
                check(headers["Authorization"]?.startsWith("Bearer synthetic-access-") == true)
                JSONObject().put("sub", "synthetic-account-id")
            }
            "/mcp" -> {
                val rpc = JSONObject(body!!)
                val result = when (rpc.getString("method")) {
                    "initialize" -> JSONObject().put("protocolVersion", "2025-06-18")
                    "tools/call" -> {
                        check(rpc.getJSONObject("params").getString("name") == "queryUserInfo")
                        if (profileFails) return CorosHttpResponse(503, "")
                        JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text")
                            .put("text", JSONObject.quote("Nickname: $nickname\nHeight: 180 cm\n"))))
                    }
                    else -> error("Unexpected MCP operation")
                }
                val envelope = JSONObject().put("jsonrpc", "2.0").put("id", rpc.getInt("id")).put("result", result).toString()
                return CorosHttpResponse(200, if (sse) "event: message\r\ndata: $envelope\r\n\r\n" else envelope)
            }
            else -> error("Unexpected path: ${uri.path}")
        }
        return CorosHttpResponse(200, json.toString())
    }
}

package com.ddupg.runtrip.data.source.coros

import com.ddupg.runtrip.data.source.SourceAccount
import com.ddupg.runtrip.data.source.SourceProtocolException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

internal class CorosAccessTokenRejected : Exception()

internal class CorosMcpApi(private val client: CorosRequestClient) {
    suspend fun account(issuer: String, accessToken: String): SourceAccount {
        val headers = mapOf("Authorization" to "Bearer $accessToken", "Accept" to "application/json, text/event-stream")
        val userResponse = client.request("GET", "$issuer/userinfo", headers)
        if (userResponse.status == 401) throw CorosAccessTokenRejected()
        val user = client.checked(userResponse)
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
        val response = client.request("POST", "$issuer/mcp", headers + ("Content-Type" to "application/json"),
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

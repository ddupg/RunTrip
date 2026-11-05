package com.ddupg.runtrip.data.source.coros

import com.ddupg.runtrip.data.source.SourceProtocolException
import java.net.URI
import org.json.JSONObject

internal class CorosRequestClient(private val transport: CorosHttpTransport = UrlConnectionCorosTransport()) {
    companion object {
        val issuers = setOf("https://mcpcn.coros.com", "https://mcpeu.coros.com", "https://mcpus.coros.com")
    }

    suspend fun request(
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

    fun checked(response: CorosHttpResponse): JSONObject {
        if (response.status !in 200..299) throw SourceProtocolException()
        return JSONObject(response.body)
    }
}

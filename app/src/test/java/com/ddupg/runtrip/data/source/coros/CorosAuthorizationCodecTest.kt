package com.ddupg.runtrip.data.source.coros

import com.ddupg.runtrip.data.source.DataSourceId
import com.ddupg.runtrip.data.source.SourceConnection
import com.ddupg.runtrip.data.source.SourceProtocolException
import com.ddupg.runtrip.data.source.SourceStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CorosAuthorizationCodecTest {
    private fun legacyPending() = JSONObject()
        .put("issuer", "https://mcpcn.coros.com").put("clientId", "synthetic-client")
        .put("verifier", "verifier").put("state", "state")
        .put("sessionId", "synthetic-session").put("pollToken", "synthetic-poll-secret")
        .put("loginUrl", "https://mcpcn.coros.com/cli/login/synthetic-session")
        .put("expiresAt", Instant.parse("2026-11-01T15:00:00Z").epochSecond)

    private class Store(var json: JSONObject) : SourceStore {
        override fun read(id: DataSourceId) = JSONObject(json.toString())
        override fun write(id: DataSourceId, value: JSONObject) { json = JSONObject(value.toString()) }
        override fun clear(id: DataSourceId) { json = JSONObject() }
    }

    private fun assertJsonEquals(expected: JSONObject, actual: JSONObject) {
        val keys = expected.keys().asSequence().toSet()
        assertEquals(keys, actual.keys().asSequence().toSet())
        keys.forEach { key ->
            val left = expected.get(key)
            val right = actual.get(key)
            if (left is JSONObject && right is JSONObject) assertJsonEquals(left, right)
            else if (left is Number && right is Number) assertEquals(left.toLong(), right.toLong())
            else assertEquals(left, right)
        }
    }

    @Test
    fun `v1 states round trip without adding optional fields`() {
        val pending = legacyPending()
        val states = listOf(
            JSONObject().put("pending", JSONObject(pending.toString())),
            JSONObject().put("pending", JSONObject(pending.toString()).put("ticket", "synthetic-ticket")),
            JSONObject().put("pending", JSONObject(pending.toString()).put("code", "synthetic-code")),
            JSONObject().put("pending", JSONObject(pending.toString()).put("claimStarted", true)),
            JSONObject().put("pending", JSONObject(pending.toString()).put("ticketStarted", false)),
            JSONObject().put("pending", JSONObject(pending.toString()).put("exchangeStarted", true)),
            JSONObject().put("credentials", JSONObject().put("issuer", "issuer").put("clientId", "client")
                .put("accessToken", "access").put("refreshToken", "refresh").put("expiresAt", 123L))
                .put("account", JSONObject().put("id", "id").put("name", JSONObject.NULL)),
        )
        states.forEach { original ->
            assertJsonEquals(original, CorosAuthorizationCodec.encode(CorosAuthorizationCodec.decode(original)))
        }
    }

    @Test
    fun `legacy pending ticket and code resume from saved stage`() = runTest {
        for (stage in listOf("pending", "ticket", "code")) {
            val pending = legacyPending()
            if (stage == "ticket") pending.put("ticket", "synthetic-ticket")
            if (stage == "code") pending.put("code", "synthetic-code")
            val store = Store(JSONObject().put("pending", pending))
            val service = TestCorosService().apply { authorized = true }
            val connector = CorosDataSourceConnector(store, CorosOAuthApi(service),
                Clock.fixed(Instant.parse("2026-11-01T14:00:00Z"), ZoneOffset.UTC), StandardTestDispatcher(testScheduler))
            assertTrue(connector.completeAuthorization() is SourceConnection.Bound)
            assertEquals(if (stage == "pending") 1 else 0, service.claims)
            assertEquals(1, service.tokenExchanges)
        }
    }

    @Test
    fun `started stages require restart and are never replayed`() = runTest {
        for (stage in listOf("claimStarted", "ticketStarted", "exchangeStarted")) {
            val pending = legacyPending().put(stage, true)
            if (stage == "ticketStarted") pending.put("ticket", "synthetic-ticket")
            if (stage == "exchangeStarted") pending.put("code", "synthetic-code")
            val store = Store(JSONObject().put("pending", pending))
            val service = TestCorosService().apply { authorized = true }
            val connector = CorosDataSourceConnector(store, CorosOAuthApi(service),
                Clock.fixed(Instant.parse("2026-11-01T14:00:00Z"), ZoneOffset.UTC), StandardTestDispatcher(testScheduler))
            assertEquals(SourceConnection.AuthorizationRestartRequired, connector.connection())
            try { connector.completeAuthorization(); fail("Started stage must not be replayed") }
            catch (_: SourceProtocolException) { }
            assertEquals(0, service.claims)
            assertEquals(0, service.tokenExchanges)
            assertTrue(store.json.getJSONObject("pending").getBoolean(stage))
        }
    }
}

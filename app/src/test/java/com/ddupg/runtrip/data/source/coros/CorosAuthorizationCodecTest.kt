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
import org.json.JSONArray
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
        var writes = 0
        var clears = 0
        override fun read(id: DataSourceId) = JSONObject(json.toString())
        override fun write(id: DataSourceId, value: JSONObject) {
            writes++
            json = JSONObject(value.toString())
        }
        override fun clear(id: DataSourceId) {
            clears++
            json = JSONObject()
        }
    }

    @Test
    fun `authorization states round trip with stage flags`() {
        val pending = CorosAuthorizationCodec.decode(JSONObject().put("pending", legacyPending())).pending!!
        assertFalse(pending.claimStarted)
        assertFalse(pending.ticketStarted)
        assertFalse(pending.exchangeStarted)
        val states = listOf(
            CorosAuthorizationState(pending = pending),
            CorosAuthorizationState(pending = pending.copy(ticket = "ticket", claimStarted = true)),
            CorosAuthorizationState(pending = pending.copy(code = "code", ticketStarted = true)),
            CorosAuthorizationState(pending = pending.copy(exchangeStarted = true)),
            CorosAuthorizationState(credentials = CorosCredentials("issuer", "client", "access", "refresh", 123L),
                account = com.ddupg.runtrip.data.source.SourceAccount("id", null)),
        )
        states.forEach { state ->
            assertEquals(state, CorosAuthorizationCodec.decode(CorosAuthorizationCodec.encode(state)))
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
            val connector = CorosDataSourceConnector(store, CorosRequestClient(service),
                Clock.fixed(Instant.parse("2026-11-01T14:00:00Z"), ZoneOffset.UTC), StandardTestDispatcher(testScheduler))
            assertTrue(connector.completeAuthorization() is SourceConnection.Bound)
            assertEquals(if (stage == "pending") 1 else 0, service.claims)
            assertEquals(1, service.tokenExchanges)
        }
    }

    @Test
    fun `invalid section shapes remain storage errors without network or mutation`() = runTest {
        for (section in listOf("credentials", "pending", "account")) {
            for (invalid in listOf("damaged", JSONObject.NULL, JSONArray(), true)) {
                val store = Store(JSONObject().put(section, invalid))
                val original = store.json.toString()
                var requests = 0
                val transport = CorosHttpTransport { _, _, _, _ ->
                    requests++
                    error("Invalid stored state must not trigger a request")
                }
                val connector = CorosDataSourceConnector(store, CorosRequestClient(transport),
                    Clock.fixed(Instant.parse("2026-11-01T14:00:00Z"), ZoneOffset.UTC), StandardTestDispatcher(testScheduler))
                assertEquals(SourceConnection.StorageError, connector.connection())
                try { connector.beginAuthorization(); fail("Invalid stored state must not be overwritten") }
                catch (_: org.json.JSONException) { }
                try { connector.completeAuthorization(); fail("Invalid stored state must not be consumed") }
                catch (_: org.json.JSONException) { }
                assertEquals(0, requests)
                assertEquals(0, store.writes)
                assertEquals(0, store.clears)
                assertEquals(original, store.json.toString())
            }
        }
        val emptyStore = Store(JSONObject())
        val connector = CorosDataSourceConnector(emptyStore, CorosRequestClient(TestCorosService()),
            Clock.fixed(Instant.parse("2026-11-01T14:00:00Z"), ZoneOffset.UTC), StandardTestDispatcher(testScheduler))
        assertEquals(SourceConnection.Unbound, connector.connection())
    }

    @Test
    fun `started stages require restart and are never replayed`() = runTest {
        for (stage in listOf("claimStarted", "ticketStarted", "exchangeStarted")) {
            val pending = legacyPending().put(stage, true)
            if (stage == "ticketStarted") pending.put("ticket", "synthetic-ticket")
            if (stage == "exchangeStarted") pending.put("code", "synthetic-code")
            val store = Store(JSONObject().put("pending", pending))
            val service = TestCorosService().apply { authorized = true }
            val connector = CorosDataSourceConnector(store, CorosRequestClient(service),
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

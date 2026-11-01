package com.ddupg.runtrip.data.source.coros

import com.ddupg.runtrip.data.source.DataSourceId
import com.ddupg.runtrip.data.source.SourceAuthorizationExpired
import com.ddupg.runtrip.data.source.SourceConnection
import com.ddupg.runtrip.data.source.SourceProtocolException
import com.ddupg.runtrip.data.source.SourceStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CorosDataSourceConnectorTest {
    private class Store : SourceStore {
        var json: String? = null
        override fun read(id: DataSourceId) = json?.let(::JSONObject)
        override fun write(id: DataSourceId, value: JSONObject) { json = value.toString() }
        override fun clear(id: DataSourceId) { json = null }
    }

    private val clock = Clock.fixed(Instant.parse("2026-11-01T14:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `browser return completes persisted session with PKCE and nickname`() = runTest {
        val service = TestCorosService()
        val store = Store()
        fun connector() = CorosDataSourceConnector(store, CorosOAuthApi(service), clock, StandardTestDispatcher(testScheduler))
        val first = connector()
        assertEquals(SourceConnection.Unbound, first.connection())
        val url = first.beginAuthorization()
        assertTrue(url.startsWith("https://mcpcn.coros.com/cli/login/"))
        assertTrue(first.completeAuthorization() is SourceConnection.Authorizing)
        assertEquals(url, first.beginAuthorization())
        assertEquals(1, service.registrations)
        service.authorized = true
        val restored = connector()
        val result = restored.completeAuthorization() as SourceConnection.Bound
        assertEquals("Synthetic Runner", result.account.name)
        assertEquals("synthetic-account-id", result.account.id)
        assertEquals(CorosOAuthApi.pkceChallenge(service.lastVerifier!!), service.lastChallenge)
        assertFalse(store.json!!.contains("pollToken"))
        assertFalse(store.json!!.contains("verifier"))
        assertEquals(result, connector().connection())
        restored.completeAuthorization()
        assertEquals(1, service.tokenExchanges)
    }

    @Test
    fun `account request failure preserves exchanged credentials and can resume`() = runTest {
        val service = TestCorosService().apply { authorized = true; profileFails = true }
        val store = Store()
        fun connector() = CorosDataSourceConnector(store, CorosOAuthApi(service), clock, StandardTestDispatcher(testScheduler))
        connector().beginAuthorization()
        try {
            connector().completeAuthorization()
            fail("Profile error must remain visible")
        } catch (_: SourceProtocolException) { }
        assertEquals(1, service.tokenExchanges)
        assertTrue(connector().connection() is SourceConnection.Authorizing)
        service.profileFails = false
        assertTrue(connector().completeAuthorization() is SourceConnection.Bound)
        assertEquals(1, service.tokenExchanges)
    }

    @Test
    fun `incorrect state prevents token exchange`() = runTest {
        val service = TestCorosService().apply { authorized = true; wrongState = true }
        val connector = CorosDataSourceConnector(Store(), CorosOAuthApi(service), clock, StandardTestDispatcher(testScheduler))
        connector.beginAuthorization()
        try { connector.completeAuthorization(); fail("State must be verified") } catch (_: SourceProtocolException) { }
        assertEquals(0, service.tokenExchanges)
    }

    @Test
    fun `expired session is cleared and disconnect clears credentials`() = runTest {
        val store = Store()
        val service = TestCorosService().apply { authorized = true }
        val connector = CorosDataSourceConnector(store, CorosOAuthApi(service), clock, StandardTestDispatcher(testScheduler))
        connector.beginAuthorization()
        val later = CorosDataSourceConnector(store, CorosOAuthApi(service), Clock.offset(clock, java.time.Duration.ofHours(2)), StandardTestDispatcher(testScheduler))
        try { later.completeAuthorization(); fail("Expired session") } catch (_: SourceAuthorizationExpired) { }
        assertEquals(SourceConnection.Unbound, connector.connection())
        connector.beginAuthorization()
        connector.completeAuthorization()
        connector.disconnect()
        assertNull(store.json)
        assertEquals(SourceConnection.Unbound, connector.connection())
    }

    @Test
    fun `expired token is rotated and rejected refresh requires new authorization`() = runTest {
        val store = Store()
        val service = TestCorosService().apply { authorized = true }
        val connector = CorosDataSourceConnector(store, CorosOAuthApi(service), clock, StandardTestDispatcher(testScheduler))
        connector.beginAuthorization()
        connector.completeAuthorization()
        val laterClock = Clock.offset(clock, java.time.Duration.ofHours(2))
        val later = CorosDataSourceConnector(store, CorosOAuthApi(service), laterClock, StandardTestDispatcher(testScheduler))
        assertTrue(later.completeAuthorization() is SourceConnection.Bound)
        assertEquals(1, service.refreshes)
        assertTrue(store.json!!.contains("synthetic-refresh-1"))
        service.invalidRefresh = true
        val expired = CorosDataSourceConnector(store, CorosOAuthApi(service), Clock.offset(laterClock, java.time.Duration.ofHours(2)), StandardTestDispatcher(testScheduler))
        try { expired.completeAuthorization(); fail("Revoked refresh") } catch (_: SourceAuthorizationExpired) { }
        assertNull(store.json)
    }

    @Test
    fun `account parser supports official SSE and empty nickname stays absent`() = runTest {
        val service = TestCorosService().apply { sse = true; nickname = "" }
        val account = CorosOAuthApi(service).account(service.issuer, "synthetic-access-0")
        assertNull(account.name)
        assertEquals("synthetic-account-id", account.id)
    }

    @Test
    fun `discovery cannot send credentials to an unrelated host`() {
        val service = TestCorosService().apply { issuer = "https://untrusted.example" }
        try { CorosOAuthApi(service).discoverIssuer(); fail("Untrusted issuer") } catch (_: SourceProtocolException) { }
        assertEquals(0, service.registrations)
    }

    @Test
    fun `PKCE matches RFC example`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", CorosOAuthApi.pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
}

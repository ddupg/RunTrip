package com.ddupg.runtrip.data.source.coros

import com.ddupg.runtrip.data.source.DataSourceId
import com.ddupg.runtrip.data.source.SourceAuthorizationExpired
import com.ddupg.runtrip.data.source.SourceConnection
import com.ddupg.runtrip.data.source.SourceProtocolException
import com.ddupg.runtrip.data.source.SourceStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
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
    fun `discovery cannot send credentials to an unrelated host`() = runTest {
        val service = TestCorosService().apply { issuer = "https://untrusted.example" }
        try { CorosOAuthApi(service).discoverIssuer(); fail("Untrusted issuer") } catch (_: SourceProtocolException) { }
        assertEquals(0, service.registrations)
    }

    @Test
    fun `training reuses rotated credentials and sends all requested subtypes`() = runTest {
        val store = Store()
        val service = TestCorosService().apply {
            authorized = true
            sse = true
            trainingText = "No sport records found from 2026-10-01 to 2026-10-31."
        }
        val connector = CorosDataSourceConnector(store, CorosOAuthApi(service), clock, StandardTestDispatcher(testScheduler))
        connector.beginAuthorization()
        connector.completeAuthorization()
        val later = CorosDataSourceConnector(store, CorosOAuthApi(service), Clock.offset(clock, java.time.Duration.ofHours(2)), StandardTestDispatcher(testScheduler))
        val start = java.time.LocalDate.parse("2026-10-01")
        val end = java.time.LocalDate.parse("2026-10-31")
        assertTrue(later.readTraining("synthetic-account-id", start, end, 1000).isEmpty())
        assertEquals(1, service.refreshes)
        val args = requireNotNull(service.trainingArguments)
        assertEquals("20261001", args.getString("startDate"))
        assertEquals("20261031", args.getString("endDate"))
        val codes = args.getJSONArray("sportTypeCodes")
        assertEquals(CorosTrainingParser.subtypes.keys, (0 until codes.length()).map(codes::getInt).toSet())
        assertEquals(1000, args.getInt("limit"))
        later.disconnect()
        try {
            later.readTraining("synthetic-account-id", start, end, 1000)
            fail("Disconnected account must not read records")
        } catch (_: com.ddupg.runtrip.data.source.TrainingAccountChanged) { }
    }

    @Test
    fun `training unauthorized retries once and preserves connection`() = runTest {
        val store = Store()
        val service = TestCorosService().apply { authorized = true; trainingUnauthorized = true }
        val connector = CorosDataSourceConnector(store, CorosOAuthApi(service), clock, StandardTestDispatcher(testScheduler))
        connector.beginAuthorization()
        connector.completeAuthorization()
        try {
            connector.readTraining("synthetic-account-id", java.time.LocalDate.parse("2026-10-01"), java.time.LocalDate.parse("2026-10-31"), 1000)
            fail("Repeated rejection must be reported")
        } catch (_: CorosAccessTokenRejected) { }
        assertEquals(1, service.refreshes)
        assertTrue(connector.connection() is SourceConnection.Bound)
    }

    @Test
    fun `rejected access token rotates once and retries training`() = runTest {
        val store = Store()
        val service = TestCorosService().apply {
            authorized = true
            rejectOldTrainingToken = true
            trainingText = "No sport records found from 2026-10-01 to 2026-10-31."
        }
        val connector = CorosDataSourceConnector(store, CorosOAuthApi(service), clock, StandardTestDispatcher(testScheduler))
        connector.beginAuthorization()
        connector.completeAuthorization()
        assertTrue(connector.readTraining("synthetic-account-id", java.time.LocalDate.parse("2026-10-01"), java.time.LocalDate.parse("2026-10-31"), 1000).isEmpty())
        assertEquals(1, service.refreshes)
        assertTrue(store.json!!.contains("synthetic-refresh-1"))
    }

    @Test
    fun `corrupt credentials expose reset without silently clearing`() = runTest {
        var cleared = false
        val store = object : SourceStore {
            override fun read(id: DataSourceId): JSONObject? {
                if (!cleared) throw com.ddupg.runtrip.data.source.SourceStoreReadException(IllegalStateException())
                return null
            }
            override fun write(id: DataSourceId, value: JSONObject) = error("must not write")
            override fun clear(id: DataSourceId) { cleared = true }
        }
        val connector = CorosDataSourceConnector(store, CorosOAuthApi(TestCorosService()), clock, StandardTestDispatcher(testScheduler))
        assertEquals(SourceConnection.StorageError, connector.connection())
        assertFalse(cleared)
        connector.disconnect()
        assertEquals(SourceConnection.Unbound, connector.connection())
    }

    @Test
    fun `lost exchange response does not replay one time authorization code`() = runTest {
        val service = TestCorosService().apply { authorized = true; exchangeFails = true }
        val connector = CorosDataSourceConnector(Store(), CorosOAuthApi(service), clock, StandardTestDispatcher(testScheduler))
        connector.beginAuthorization()
        try { connector.completeAuthorization(); fail("exchange should fail") } catch (_: java.io.IOException) { }
        service.exchangeFails = false
        assertEquals(SourceConnection.AuthorizationRestartRequired, connector.connection())
        try { connector.completeAuthorization(); fail("must not replay") } catch (_: SourceProtocolException) { }
        assertEquals(1, service.tokenExchanges)
        connector.disconnect()
        assertEquals(SourceConnection.Unbound, connector.connection())
    }

    @Test
    fun `cancellation during claim persists ticket without starting authorize`() = runTest {
        val store = Store()
        val service = TestCorosService().apply { authorized = true }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var authorizes = 0
        val transport = CorosHttpTransport { method, url, headers, body ->
            if (url.endsWith("/claim")) { entered.complete(Unit); release.await() }
            if (url.contains("/oauth2/authorize")) authorizes++
            service.request(method, url, headers, body)
        }
        val connector = CorosDataSourceConnector(store, CorosOAuthApi(transport), clock, StandardTestDispatcher(testScheduler))
        connector.beginAuthorization()
        val job = launch { connector.completeAuthorization() }
        entered.await()
        job.cancel()
        release.complete(Unit)
        job.join()
        assertEquals(0, authorizes)
        assertEquals(0, service.tokenExchanges)
        assertTrue(JSONObject(store.json!!).getJSONObject("pending").has("ticket"))
        assertTrue(connector.completeAuthorization() is SourceConnection.Bound)
    }

    @Test
    fun `cancellation during ticket exchange persists code without starting token exchange`() = runTest {
        val store = Store()
        val service = TestCorosService().apply { authorized = true }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val transport = CorosHttpTransport { method, url, headers, body ->
            if (url.contains("/oauth2/authorize")) { entered.complete(Unit); release.await() }
            service.request(method, url, headers, body)
        }
        val connector = CorosDataSourceConnector(store, CorosOAuthApi(transport), clock, StandardTestDispatcher(testScheduler))
        connector.beginAuthorization()
        val job = launch { connector.completeAuthorization() }
        entered.await()
        job.cancel()
        release.complete(Unit)
        job.join()
        assertEquals(0, service.tokenExchanges)
        assertTrue(JSONObject(store.json!!).getJSONObject("pending").has("code"))
        assertTrue(connector.completeAuthorization() is SourceConnection.Bound)
    }

    @Test
    fun `PKCE matches RFC example`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", CorosOAuthApi.pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
}

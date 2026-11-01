package com.ddupg.runtrip.feature.me

import com.ddupg.runtrip.data.source.DataSourceConnector
import com.ddupg.runtrip.data.source.DataSourceId
import com.ddupg.runtrip.data.source.SourceAccount
import com.ddupg.runtrip.data.source.SourceConnection
import com.ddupg.runtrip.data.source.SourceAuthorizationExpired
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private class Connector : DataSourceConnector {
        override val id = DataSourceId.COROS
        var state: SourceConnection = SourceConnection.Unbound
        var starts = 0
        var startFailure = false
        var startGate: CompletableDeferred<Unit>? = null
        var authorized = false
        var expired = false
        var connectionGate: CompletableDeferred<Unit>? = null
        var checkGate: CompletableDeferred<Unit>? = null
        var checks = 0
        override suspend fun connection(): SourceConnection {
            connectionGate?.await()
            return state
        }
        override suspend fun beginAuthorization(): String {
            starts++
            startGate?.await()
            if (startFailure) throw IOException()
            state = SourceConnection.Authorizing("https://mcpcn.coros.com/cli/login/synthetic")
            return "https://mcpcn.coros.com/cli/login/synthetic"
        }
        override suspend fun completeAuthorization(): SourceConnection {
            checks++
            checkGate?.await()
            if (expired) {
                state = SourceConnection.Unbound
                throw SourceAuthorizationExpired()
            }
            if (authorized && state is SourceConnection.Authorizing) {
                state = SourceConnection.Bound(SourceAccount("synthetic-id", "Synthetic Runner"))
            }
            return state
        }
        override suspend fun disconnect() { state = SourceConnection.Unbound }
    }

    @Test fun `name replaces ID and absent name uses ID`() {
        assertEquals("Runner", accountLabel(SourceAccount("1234", " Runner ")))
        assertEquals("ID 1234", accountLabel(SourceAccount("1234", "  ")))
        assertEquals("ID 1234", accountLabel(SourceAccount("1234", null)))
    }

    @Test fun `one tap starts authorization once and browser return binds account`() = runTest(dispatcher) {
        val connector = Connector()
        val model = MeViewModel(listOf(connector))
        advanceUntilIdle()
        connector.startGate = CompletableDeferred()
        model.openSource(DataSourceId.COROS)
        model.openSource(DataSourceId.COROS)
        runCurrent()
        assertEquals(1, connector.starts)
        connector.startGate!!.complete(Unit)
        advanceUntilIdle()
        assertNotNull(model.takeAuthorizationUrl())
        assertNull(model.takeAuthorizationUrl())
        connector.authorized = true
        model.onResume()
        advanceUntilIdle()
        val state = model.uiState.value
        assertFalse(state.isWorking)
        assertNull(state.error)
        assertEquals("Synthetic Runner", connectionLabel(state.sources.single().connection))
    }

    @Test fun `failed start is retryable and restored view model completes pending session`() = runTest(dispatcher) {
        val connector = Connector().apply { startFailure = true }
        val model = MeViewModel(listOf(connector))
        advanceUntilIdle()
        model.openSource(DataSourceId.COROS)
        advanceUntilIdle()
        assertNotNull(model.uiState.value.error)
        connector.startFailure = false
        model.retry()
        advanceUntilIdle()
        assertNotNull(model.takeAuthorizationUrl())
        connector.authorized = true
        val restored = MeViewModel(listOf(connector))
        advanceUntilIdle()
        assertTrue(restored.uiState.value.sources.single().connection is SourceConnection.Bound)
        restored.openSource(DataSourceId.COROS)
        assertEquals(DataSourceId.COROS, restored.uiState.value.selectedSource)
        restored.disconnect(DataSourceId.COROS)
        advanceUntilIdle()
        assertNull(restored.uiState.value.selectedSource)
        assertEquals(SourceConnection.Unbound, restored.uiState.value.sources.single().connection)
    }

    @Test fun `retry after expired authorization actually starts a new connection`() = runTest(dispatcher) {
        val connector = Connector()
        val model = MeViewModel(listOf(connector))
        advanceUntilIdle()
        connector.expired = true
        model.onResume()
        advanceUntilIdle()
        assertNotNull(model.uiState.value.error)
        connector.expired = false
        model.retry()
        advanceUntilIdle()
        assertEquals(1, connector.starts)
        assertNotNull(model.takeAuthorizationUrl())
    }

    @Test fun `browser return during final reload is checked after authorization finishes`() = runTest(dispatcher) {
        val connector = Connector()
        val model = MeViewModel(listOf(connector))
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        connector.connectionGate = gate
        model.openSource(DataSourceId.COROS)
        runCurrent()
        assertNotNull(model.takeAuthorizationUrl())
        assertTrue(model.uiState.value.isWorking)

        connector.authorized = true
        repeat(3) { model.onResume() }
        assertEquals(1, connector.checks)
        connector.connectionGate = null
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(model.uiState.value.isWorking)
        assertTrue(model.uiState.value.sources.single().connection is SourceConnection.Bound)
        assertEquals(2, connector.checks)
        assertEquals(1, connector.starts)
        assertNull(model.takeAuthorizationUrl())
    }

    @Test fun `resume events during a check coalesce into one additional check without polling`() = runTest(dispatcher) {
        val connector = Connector()
        val model = MeViewModel(listOf(connector))
        advanceUntilIdle()
        model.openSource(DataSourceId.COROS)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        connector.checkGate = gate
        model.onResume()
        runCurrent()
        assertEquals(2, connector.checks)
        repeat(3) { model.onResume() }
        connector.checkGate = null
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(model.uiState.value.isWorking)
        assertTrue(model.uiState.value.sources.single().connection is SourceConnection.Authorizing)
        assertEquals(3, connector.checks)
        advanceUntilIdle()
        assertEquals(3, connector.checks)
    }
}

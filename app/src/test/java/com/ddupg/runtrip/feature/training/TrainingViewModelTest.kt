package com.ddupg.runtrip.feature.training

import com.ddupg.runtrip.data.model.*
import com.ddupg.runtrip.data.repository.TrainingRepository
import com.ddupg.runtrip.data.repository.TrainingImportProgress
import com.ddupg.runtrip.data.source.DataSourceId
import com.ddupg.runtrip.data.source.SourceAuthorizationExpired
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private class Repository : TrainingRepository {
        var account: TrainingAccount? = TrainingAccount(DataSourceId.COROS, "synthetic-a")
        val data = mutableMapOf<String, MutableStateFlow<List<TrainingActivity>>>()
        var complete = false
        var updates = 0
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        var checkGate: CompletableDeferred<Unit>? = null
        override suspend fun connectedAccount(): TrainingAccount? { checkGate?.await(); return account }
        override fun observe(account: TrainingAccount) = data.getOrPut(account.accountId) { MutableStateFlow(emptyList()) }
        override suspend fun historyComplete(account: TrainingAccount) = complete
        override suspend fun update(account: TrainingAccount, onProgress: (TrainingImportProgress) -> Unit) {
            updates++
            onProgress(TrainingImportProgress(TrainingImportProgress.Phase.RECENT, java.time.LocalDate.parse("2026-01-01"), java.time.LocalDate.parse("2026-01-02")))
            gate?.await()
            failure?.let { throw it }
            complete = true
        }
    }
    private fun activity(id: String, subtype: TrainingSubtype, date: String, timestamp: Long? = null) =
        TrainingActivity(id, subtype, LocalDate.parse(date), timestamp, 5200.0, 2060)

    @Test fun unboundThenConnectedOnResumeStartsImportOnceAndDuplicateTapsDoNotOverlap() = runTest(dispatcher) {
        val repo = Repository().apply { account = null }
        val model = TrainingViewModel(repo)
        model.onResume()
        advanceUntilIdle()
        assertFalse(model.uiState.value.connected)
        assertFalse(model.uiState.value.loading)
        assertEquals(0, repo.updates)
        repo.account = TrainingAccount(DataSourceId.COROS, "synthetic-a")
        repo.gate = CompletableDeferred()
        model.onResume()
        runCurrent()
        model.update()
        model.onResume()
        runCurrent()
        assertEquals(1, repo.updates)
        assertTrue(model.uiState.value.updating)
        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.uiState.value.updating)
        assertNull(model.uiState.value.error)
    }

    @Test fun cachedCompleteHistoryIsShownOfflineAndManualFailurePreservesIt() = runTest(dispatcher) {
        val repo = Repository().apply { complete = true; failure = IOException() }
        val record = activity("run", TrainingSubtype.OUTDOOR_RUN, "2026-11-02")
        repo.observe(repo.account!!).value = listOf(record)
        val model = TrainingViewModel(repo)
        model.onResume()
        advanceUntilIdle()
        assertEquals(0, repo.updates)
        assertEquals(listOf(record), model.uiState.value.records)
        model.update()
        advanceUntilIdle()
        assertEquals(listOf(record), model.uiState.value.records)
        assertNotNull(model.uiState.value.error)
        repo.failure = null
        model.update()
        advanceUntilIdle()
        assertEquals(2, repo.updates)
        assertNull(model.uiState.value.error)
    }

    @Test fun rebindingHidesPreviousAccountAndUsesNewCache() = runTest(dispatcher) {
        val repo = Repository().apply { complete = true }
        repo.observe(repo.account!!).value = listOf(activity("private-a", TrainingSubtype.OUTDOOR_RUN, "2026-11-02"))
        val model = TrainingViewModel(repo)
        model.onResume()
        advanceUntilIdle()
        assertEquals(1, model.uiState.value.records.size)
        repo.account = TrainingAccount(DataSourceId.COROS, "synthetic-b")
        model.onResume()
        advanceUntilIdle()
        assertTrue(model.uiState.value.connected)
        assertTrue(model.uiState.value.records.isEmpty())
        repo.account = null
        model.onResume()
        advanceUntilIdle()
        assertFalse(model.uiState.value.connected)
        assertTrue(model.uiState.value.records.isEmpty())
    }

    @Test fun expiredCredentialsHideCacheAndExplainReconnect() = runTest(dispatcher) {
        val repo = Repository().apply { failure = SourceAuthorizationExpired() }
        val model = TrainingViewModel(repo)
        model.onResume()
        advanceUntilIdle()
        assertFalse(model.uiState.value.connected)
        assertTrue(model.uiState.value.error!!.contains("重新连接"))
        assertFalse(model.uiState.value.updating)
    }

    @Test fun resumeArrivingDuringConnectionReadIsNotLost() = runTest(dispatcher) {
        val repo = Repository().apply { account = null; checkGate = CompletableDeferred() }
        val model = TrainingViewModel(repo)
        model.onResume()
        runCurrent()
        repo.account = TrainingAccount(DataSourceId.COROS, "synthetic-a")
        model.onResume()
        repo.checkGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(model.uiState.value.connected)
        assertEquals(1, repo.updates)
    }

    @Test fun filteringGroupingAndTimeUseSameZoneAndHandleMissingValues() {
        val zone = ZoneId.of("Asia/Shanghai")
        val run = activity("run", TrainingSubtype.OUTDOOR_RUN, "2026-11-01", 1793549400L) // 11/02 00:10 local
        val swim = activity("swim", TrainingSubtype.POOL_SWIM, "2026-11-02").copy(distanceMeters = null)
        val old = activity("old", TrainingSubtype.TRAIL_RUN, "2025-12-31")
        val days = trainingDays(listOf(old, swim, run), null, zone)
        assertEquals(listOf(LocalDate.parse("2026-11-02"), LocalDate.parse("2025-12-31")), days.map { it.date })
        assertEquals(listOf("run", "swim"), days.first().activities.map { it.id })
        assertEquals(listOf("swim"), trainingDays(listOf(run, swim, old), TrainingSport.SWIMMING, zone).single().activities.map { it.id })
        assertEquals("距离未记录", trainingDistance(swim))
        assertEquals("--:--", trainingTime(swim, zone))
        assertEquals("00:10", trainingTime(run, zone))
        assertEquals("00:34:20", trainingDuration(run.durationSeconds))
        assertEquals("5.20 km", trainingDistance(run))
        assertTrue(trainingDateLabel(old.sourceDate, LocalDate.parse("2026-11-02")).startsWith("2025年"))
    }
}

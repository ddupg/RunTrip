package com.ddupg.runtrip.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ddupg.runtrip.data.local.RunTripDatabase
import com.ddupg.runtrip.data.model.*
import com.ddupg.runtrip.data.source.*
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class LocalTrainingRepositoryTest {
    private lateinit var db: RunTripDatabase
    private val clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)
    private val account = TrainingAccount(DataSourceId.COROS, "synthetic-a")
    private class Source : TrainingDataSource {
        override val id = DataSourceId.COROS
        var account = "synthetic-a"
        val reads = mutableListOf<Pair<LocalDate, LocalDate>>()
        var read: suspend (LocalDate, LocalDate, Int) -> List<TrainingActivity> = { _, _, _ -> emptyList() }
        override suspend fun connection() = SourceConnection.Bound(SourceAccount(account, null))
        override suspend fun beginAuthorization(): String = error("Not used")
        override suspend fun completeAuthorization() = connection()
        override suspend fun disconnect() = Unit
        override suspend fun readTraining(accountId: String, start: LocalDate, end: LocalDate, limit: Int): List<TrainingActivity> {
            if (accountId != account) throw TrainingAccountChanged()
            reads += start to end
            return read(start, end, limit)
        }
    }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RunTripDatabase::class.java).build()
    }
    @After fun cleanup() { db.close() }
    private fun record(id: String, date: LocalDate, meters: Double? = 5000.0) =
        TrainingActivity(id, TrainingSubtype.OUTDOOR_RUN, date, null, meters, 1800)

    @Test fun recentFirstEmptyGapsDoNotStopHistoryAndReimportUpserts() = runTest {
        val source = Source()
        source.read = { start, end, _ ->
            listOf(record("new", LocalDate.parse("2026-11-02")), record("old", LocalDate.parse("1980-01-01"), null))
                .filter { it.sourceDate in start..end }
        }
        val repo = LocalTrainingRepository(db.trainingDao(), listOf(source), clock)
        assertFalse(repo.historyComplete(account))
        repo.update(account) { }
        assertEquals(LocalDate.parse("2026-01-01") to LocalDate.parse("2026-11-02"), source.reads.first())
        assertEquals(LocalDate.parse("1970-01-01"), source.reads.last().first)
        assertTrue(repo.historyComplete(account))
        assertEquals(setOf("new", "old"), repo.observe(account).first().map { it.id }.toSet())
        assertNull(repo.observe(account).first().single { it.id == "old" }.distanceMeters)
        source.reads.clear()
        source.read = { _, _, _ -> listOf(record("new", LocalDate.parse("2026-11-02"), 5100.0)) }
        repo.update(account) { }
        assertEquals(1, source.reads.size)
        assertEquals(LocalDate.parse("2026-11-02"), source.reads.single().first)
        assertEquals(2, repo.observe(account).first().size)
        assertEquals(5100.0, repo.observe(account).first().single { it.id == "new" }.distanceMeters!!, 0.0)
    }

    @Test fun interruptedHistoryKeepsRecentBatchAndResumesCheckpointAfterRecreation() = runTest {
        val source = Source()
        source.read = { start, _, _ ->
            if (start.year == 2021) throw IOException()
            listOf(record("recent", LocalDate.parse("2026-11-01")))
        }
        val repo = LocalTrainingRepository(db.trainingDao(), listOf(source), clock)
        try { repo.update(account) { }; fail("Import should report failure") } catch (_: IOException) { }
        assertEquals(1, repo.observe(account).first().size)
        assertFalse(repo.historyComplete(account))
        assertEquals("2025-12-31", db.trainingDao().importState("coros", account.accountId)?.nextHistoryEnd)
        source.reads.clear()
        source.read = { _, _, _ -> emptyList() }
        val restored = LocalTrainingRepository(db.trainingDao(), listOf(source), clock)
        restored.update(account) { }
        assertEquals(LocalDate.parse("2026-11-02"), source.reads.first().first)
        assertEquals(LocalDate.parse("2021-01-01"), source.reads[1].first)
        assertTrue(restored.historyComplete(account))
        assertEquals(1, restored.observe(account).first().size)
    }

    @Test fun fullResponsesSplitRangesWithoutSavingTruncatedData() = runTest {
        val source = Source()
        source.read = { start, end, limit ->
            when {
                start.year != 2026 -> emptyList()
                start < end -> List(limit) { record("truncated-$it", start) }
                else -> listOf(record(start.toString(), start))
            }
        }
        val repo = LocalTrainingRepository(db.trainingDao(), listOf(source), clock)
        repo.update(account) { }
        val records = repo.observe(account).first()
        assertEquals(306, records.size)
        assertFalse(records.any { it.id.startsWith("truncated") })
        assertTrue(repo.historyComplete(account))
    }

    @Test fun fullSingleDayFailsAndDoesNotAdvanceCheckpoint() = runTest {
        val source = Source().apply { read = { start, _, limit -> List(limit) { record("row-$it", start) } } }
        val repo = LocalTrainingRepository(db.trainingDao(), listOf(source), clock)
        try { repo.update(account) { }; fail("A full single day cannot prove completeness") } catch (_: TrainingReadLimitReached) { }
        assertNull(db.trainingDao().importState("coros", account.accountId))
        assertTrue(repo.observe(account).first().isEmpty())
    }

    @Test fun accountsHaveIndependentRecordsAndImportState() = runTest {
        val source = Source().apply { read = { start, _, _ -> if (start.year == 2026) listOf(record("same-id", start)) else emptyList() } }
        val repo = LocalTrainingRepository(db.trainingDao(), listOf(source), clock)
        repo.update(account) { }
        source.account = "synthetic-b"
        val other = account.copy(accountId = source.account)
        assertFalse(repo.historyComplete(other))
        assertTrue(repo.observe(other).first().isEmpty())
        repo.update(other) { }
        assertEquals(1, repo.observe(other).first().size)
        assertEquals(1, repo.observe(account).first().size)
    }

    @Test fun updateAfterLongOfflineGapIncludesWholeGapAndPreviousRefreshDay() = runTest {
        val source = Source()
        val initial = LocalTrainingRepository(db.trainingDao(), listOf(source), clock)
        initial.update(account) { }
        source.reads.clear()
        source.read = { start, end, _ ->
            listOf(record("overlap", LocalDate.parse("2026-11-02")), record("gap", LocalDate.parse("2026-11-15")))
                .filter { it.sourceDate in start..end }
        }
        val later = LocalTrainingRepository(db.trainingDao(), listOf(source), Clock.offset(clock, java.time.Duration.ofDays(30)))
        later.update(account) { }
        assertEquals(listOf(LocalDate.parse("2026-11-02") to LocalDate.parse("2026-12-02")), source.reads)
        assertEquals(setOf("overlap", "gap"), later.observe(account).first().map { it.id }.toSet())
    }

    @Test fun recentSplitFailureResumesAndContinuesHistoryAcrossYear() = runTest {
        val source = Source()
        var failed = false
        source.read = { start, end, limit ->
            when {
                start == LocalDate.parse("2026-01-01") && end == LocalDate.parse("2026-11-02") -> List(limit) { record("truncated-$it", start) }
                start.year == 2026 && start > LocalDate.parse("2026-01-01") && !failed -> { failed = true; throw IOException() }
                else -> listOf(record("leaf-$start", start))
            }
        }
        val repo = LocalTrainingRepository(db.trainingDao(), listOf(source), clock)
        try { repo.update(account) {}; fail() } catch (_: IOException) { }
        val checkpoint = db.trainingDao().importState("coros", account.accountId)!!
        assertEquals("2025-12-31", checkpoint.nextHistoryEnd)
        assertTrue(LocalDate.parse(checkpoint.lastRefreshDay) > LocalDate.parse("2026-01-01"))
        assertEquals(1, repo.observe(account).first().size)
        source.reads.clear()
        val nextYear = Clock.fixed(Instant.parse("2027-01-02T12:00:00Z"), ZoneOffset.UTC)
        val restored = LocalTrainingRepository(db.trainingDao(), listOf(source), nextYear)
        restored.update(account) {}
        assertEquals(LocalDate.parse(checkpoint.lastRefreshDay), source.reads.first().first)
        assertTrue(source.reads.any { it.second == LocalDate.parse("2025-12-31") })
        assertTrue(restored.historyComplete(account))
    }

    @Test fun historySplitFailureKeepsCompletedNewerLeafAndResumesBeforeIt() = runTest {
        val source = Source()
        var failed = false
        source.read = { start, end, limit ->
            when {
                start == LocalDate.parse("2021-01-01") && end == LocalDate.parse("2025-12-31") -> List(limit) { record("truncated-$it", start) }
                start == LocalDate.parse("2021-01-01") && !failed -> { failed = true; throw IOException() }
                else -> listOf(record("leaf-$start", start))
            }
        }
        val repo = LocalTrainingRepository(db.trainingDao(), listOf(source), clock)
        try { repo.update(account) {}; fail() } catch (_: IOException) { }
        val checkpoint = db.trainingDao().importState("coros", account.accountId)!!
        val saved = repo.observe(account).first()
        assertEquals(2, saved.size)
        val newerDate = saved.map { it.sourceDate }.filter { it.year in 2021..2025 }.single()
        assertEquals(newerDate.minusDays(1).toString(), checkpoint.nextHistoryEnd)
        source.reads.clear()
        LocalTrainingRepository(db.trainingDao(), listOf(source), clock).update(account) {}
        assertEquals(LocalDate.parse(checkpoint.nextHistoryEnd), source.reads[1].second)
        assertTrue(source.reads.drop(1).all { it.second < newerDate })
    }

    @Test fun cancellationAfterCompletedLeafKeepsCheckpointForRetry() = runTest {
        val source = Source()
        var cancelled = false
        source.read = { start, end, limit ->
            when {
                start == LocalDate.parse("2026-01-01") && end == LocalDate.parse("2026-11-02") -> List(limit) { record("truncated-$it", start) }
                start.year == 2026 && start > LocalDate.parse("2026-01-01") && !cancelled -> {
                    cancelled = true
                    throw kotlinx.coroutines.CancellationException("interrupted")
                }
                else -> listOf(record("leaf-$start", start))
            }
        }
        val repo = LocalTrainingRepository(db.trainingDao(), listOf(source), clock)
        try { repo.update(account) {}; fail() } catch (_: kotlinx.coroutines.CancellationException) { }
        val checkpoint = db.trainingDao().importState("coros", account.accountId)!!
        assertEquals(1, repo.observe(account).first().size)
        source.reads.clear()
        LocalTrainingRepository(db.trainingDao(), listOf(source), clock).update(account) {}
        assertEquals(LocalDate.parse(checkpoint.lastRefreshDay), source.reads.first().first)
    }

    @Test fun historicalSubtypeLiteralsReadUnchanged() = runTest {
        val dao = db.trainingDao()
        val literals = listOf("OUTDOOR_RUN", "INDOOR_RUN", "TRAIL_RUN", "TRACK_RUN", "OUTDOOR_RIDE", "INDOOR_RIDE", "E_BIKE", "GRAVEL_RIDE", "MOUNTAIN_BIKE", "E_MOUNTAIN_BIKE", "HELMET_RIDE", "POOL_SWIM", "OPEN_WATER_SWIM")
        dao.saveBatch(literals.map { com.ddupg.runtrip.data.local.TrainingActivityEntity("coros", account.accountId, it, it, "2026-01-01", null, null, 1) },
            com.ddupg.runtrip.data.local.TrainingImportEntity("coros", account.accountId, null, "2026-11-02"))
        val repo = LocalTrainingRepository(dao, listOf(Source()), clock)
        assertEquals(literals.toSet(), repo.observe(account).first().map { it.subtype.code }.toSet())
        assertTrue(repo.historyComplete(account))
    }

    @Test fun switchingAccountDuringReadDoesNotSaveToEitherAccount() = runTest {
        val source = Source().apply { read = { start, _, _ -> account = "synthetic-b"; listOf(record("stale", start)) } }
        val repo = LocalTrainingRepository(db.trainingDao(), listOf(source), clock)
        try { repo.update(account) { }; fail("Account changed") } catch (_: TrainingAccountChanged) { }
        assertTrue(repo.observe(account).first().isEmpty())
        assertNull(db.trainingDao().importState("coros", account.accountId))
    }
}

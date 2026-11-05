package com.ddupg.runtrip.data.repository

import com.ddupg.runtrip.data.local.TrainingActivityEntity
import com.ddupg.runtrip.data.local.TrainingDao
import com.ddupg.runtrip.data.local.TrainingImportEntity
import com.ddupg.runtrip.data.model.TrainingAccount
import com.ddupg.runtrip.data.model.TrainingActivity
import com.ddupg.runtrip.data.model.TrainingSubtype
import com.ddupg.runtrip.data.source.SourceConnection
import com.ddupg.runtrip.data.source.TrainingAccountChanged
import com.ddupg.runtrip.data.source.TrainingDataSource
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface TrainingRepository {
    suspend fun connectedAccount(): TrainingAccount?
    fun observe(account: TrainingAccount): Flow<List<TrainingActivity>>
    suspend fun historyComplete(account: TrainingAccount): Boolean
    suspend fun update(account: TrainingAccount, onProgress: (TrainingImportProgress) -> Unit)
}

data class TrainingImportProgress(val phase: Phase, val start: LocalDate, val end: LocalDate) {
    enum class Phase { RECENT, HISTORY }
}

class TrainingReadLimitReached : Exception()

class LocalTrainingRepository(
    private val dao: TrainingDao,
    private val sources: List<TrainingDataSource>,
    private val clock: Clock = Clock.systemDefaultZone(),
) : TrainingRepository {
    private val updateMutex = Mutex()

    override suspend fun connectedAccount(): TrainingAccount? {
        for (source in sources) {
            val connection = source.connection()
            if (connection is SourceConnection.Bound) return TrainingAccount(source.id, connection.account.id)
        }
        return null
    }

    override fun observe(account: TrainingAccount) = dao.observe(account.source.code, account.accountId).map { rows ->
        rows.map { TrainingActivity(it.activityId, TrainingSubtype.fromCode(it.subtypeCode), LocalDate.parse(it.sourceDate),
            it.startEpochSeconds, it.distanceMeters, it.durationSeconds) }
    }

    override suspend fun historyComplete(account: TrainingAccount): Boolean {
        val state = dao.importState(account.source.code, account.accountId)
        return state != null && state.nextHistoryEnd == null
    }

    override suspend fun update(account: TrainingAccount, onProgress: (TrainingImportProgress) -> Unit) = updateMutex.withLock {
        val source = sources.single { it.id == account.source }
        val today = LocalDate.now(clock)
        val previous = dao.importState(account.source.code, account.accountId)
        val recentStart = previous?.lastRefreshDay?.let(LocalDate::parse)?.coerceAtMost(today) ?: today.withDayOfYear(1)
        var state = previous ?: TrainingImportEntity(account.source.code, account.accountId,
            today.withDayOfYear(1).minusDays(1).toString(), recentStart.toString())
        readRange(source, account, recentStart, today, TrainingImportProgress.Phase.RECENT, onProgress) { _, end, records ->
            state = state.copy(lastRefreshDay = end.toString())
            save(account, records, state)
        }
        while (state.nextHistoryEnd != null) {
            currentCoroutineContext().ensureActive()
            val end = LocalDate.parse(state.nextHistoryEnd)
            val start = LocalDate.of((end.year - 4).coerceAtLeast(1970), 1, 1)
            readRange(source, account, start, end, TrainingImportProgress.Phase.HISTORY, onProgress) { leafStart, _, records ->
                state = state.copy(nextHistoryEnd = if (leafStart == LocalDate.of(1970, 1, 1)) null else leafStart.minusDays(1).toString())
                save(account, records, state)
            }
        }
    }

    private suspend fun save(account: TrainingAccount, records: List<TrainingActivity>, state: TrainingImportEntity) {
        currentCoroutineContext().ensureActive()
        if (connectedAccount() != account) throw TrainingAccountChanged()
        dao.saveBatch(records.map {
            TrainingActivityEntity(account.source.code, account.accountId, it.id, it.subtype.code,
                it.sourceDate.toString(), it.startEpochSeconds, it.distanceMeters, it.durationSeconds)
        }, state)
    }

    private suspend fun readRange(
        source: TrainingDataSource, account: TrainingAccount, start: LocalDate, end: LocalDate,
        phase: TrainingImportProgress.Phase, onProgress: (TrainingImportProgress) -> Unit,
        saveLeaf: suspend (LocalDate, LocalDate, List<TrainingActivity>) -> Unit,
    ) {
        if (readAndSaveLeaf(source, account, start, end, phase, onProgress, saveLeaf)) return
        if (start == end) throw TrainingReadLimitReached()
        val middle = start.plusDays(ChronoUnit.DAYS.between(start, end) / 2)
        if (phase == TrainingImportProgress.Phase.RECENT) {
            readRange(source, account, start, middle, phase, onProgress, saveLeaf)
            readRange(source, account, middle.plusDays(1), end, phase, onProgress, saveLeaf)
        } else {
            readRange(source, account, middle.plusDays(1), end, phase, onProgress, saveLeaf)
            readRange(source, account, start, middle, phase, onProgress, saveLeaf)
        }
    }

    private suspend fun readAndSaveLeaf(
        source: TrainingDataSource, account: TrainingAccount, start: LocalDate, end: LocalDate,
        phase: TrainingImportProgress.Phase, onProgress: (TrainingImportProgress) -> Unit,
        saveLeaf: suspend (LocalDate, LocalDate, List<TrainingActivity>) -> Unit,
    ): Boolean {
        currentCoroutineContext().ensureActive()
        onProgress(TrainingImportProgress(phase, start, end))
        val records = source.readTraining(account.accountId, start, end, READ_LIMIT)
        if (records.size >= READ_LIMIT) return false
        saveLeaf(start, end, records)
        return true
    }

    private companion object { const val READ_LIMIT = 1000 }
}

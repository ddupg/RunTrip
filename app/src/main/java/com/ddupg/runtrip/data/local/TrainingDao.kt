package com.ddupg.runtrip.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "training_activities", primaryKeys = ["sourceCode", "accountId", "activityId"])
data class TrainingActivityEntity(
    val sourceCode: String,
    val accountId: String,
    val activityId: String,
    val subtypeCode: String,
    val sourceDate: String,
    val startEpochSeconds: Long?,
    val distanceMeters: Double?,
    val durationSeconds: Long,
)

@Entity(tableName = "training_imports", primaryKeys = ["sourceCode", "accountId"])
data class TrainingImportEntity(
    val sourceCode: String,
    val accountId: String,
    val nextHistoryEnd: String?,
    val lastRefreshDay: String,
)

@Dao
abstract class TrainingDao {
    @Query("SELECT * FROM training_activities WHERE sourceCode = :source AND accountId = :account")
    abstract fun observe(source: String, account: String): Flow<List<TrainingActivityEntity>>

    @Query("SELECT * FROM training_imports WHERE sourceCode = :source AND accountId = :account")
    abstract suspend fun importState(source: String, account: String): TrainingImportEntity?

    @Upsert
    protected abstract suspend fun saveRecords(records: List<TrainingActivityEntity>)

    @Upsert
    protected abstract suspend fun saveImport(state: TrainingImportEntity)

    @Transaction
    open suspend fun saveBatch(records: List<TrainingActivityEntity>, state: TrainingImportEntity) {
        saveRecords(records)
        saveImport(state)
    }
}

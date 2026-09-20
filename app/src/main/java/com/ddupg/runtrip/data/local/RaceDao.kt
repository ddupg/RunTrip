package com.ddupg.runtrip.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RaceDao {
    @Transaction
    @Query("SELECT * FROM races ORDER BY raceDate ASC, createdAtEpochMillis ASC")
    abstract fun observeAll(): Flow<List<RaceRecord>>

    @Transaction
    @Query("SELECT * FROM races WHERE id = :id LIMIT 1")
    abstract fun observeById(id: String): Flow<RaceRecord?>

    @Transaction
    @Query("SELECT * FROM races WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: String): RaceRecord?

    @Upsert
    protected abstract suspend fun upsertCommon(race: RaceEntity)

    @Update
    protected abstract suspend fun updateCommon(race: RaceEntity): Int

    @Upsert
    protected abstract suspend fun upsertRoadRunning(details: RoadRunningEntity)

    @Upsert
    protected abstract suspend fun upsertTriathlon(details: TriathlonEntity)

    @Upsert
    protected abstract suspend fun upsertTrailRunning(details: TrailRunningEntity)

    @Query("DELETE FROM trail_running_details WHERE raceId = :id")
    protected abstract suspend fun deleteTrailRunning(id: String)

    @Query("DELETE FROM road_running_details WHERE raceId = :id")
    protected abstract suspend fun deleteRoadRunning(id: String)

    @Query("DELETE FROM triathlon_details WHERE raceId = :id")
    protected abstract suspend fun deleteTriathlon(id: String)

    @Transaction
    open suspend fun upsert(record: RaceRecord) {
        record.toDomain()
        upsertCommon(record.race)
        replaceDetails(record)
    }

    @Transaction
    open suspend fun updateExisting(record: RaceRecord): Int {
        record.toDomain()
        val count = updateCommon(record.race)
        if (count > 0) replaceDetails(record)
        return count
    }

    private suspend fun replaceDetails(record: RaceRecord) {
        deleteRoadRunning(record.race.id)
        deleteTriathlon(record.race.id)
        deleteTrailRunning(record.race.id)
        record.roadRunning?.let { upsertRoadRunning(it) }
        record.triathlon?.let { upsertTriathlon(it) }
        record.trailRunning?.let { upsertTrailRunning(it) }
    }

    @Query(
        """
        UPDATE races
        SET statusCode = :statusCode,
            updatedAtEpochMillis = :updatedAtEpochMillis,
            recordVersion = recordVersion + 1
        WHERE id = :id
        """,
    )
    abstract suspend fun updateStatusAndAdvanceVersion(
        id: String,
        statusCode: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("DELETE FROM races WHERE id = :id")
    abstract suspend fun deleteById(id: String): Int
}

package com.ddupg.runtrip.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RaceDao {
    @Query("SELECT * FROM races ORDER BY raceDate ASC, createdAtEpochMillis ASC")
    fun observeAll(): Flow<List<RaceEntity>>

    @Query("SELECT * FROM races WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<RaceEntity?>

    @Query("SELECT * FROM races WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RaceEntity?

    @Upsert
    suspend fun upsert(race: RaceEntity)

    @Query(
        """
        UPDATE races
        SET statusCode = :statusCode,
            updatedAtEpochMillis = :updatedAtEpochMillis,
            recordVersion = recordVersion + 1
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(
        id: String,
        statusCode: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("DELETE FROM races WHERE id = :id")
    suspend fun deleteById(id: String)
}

package com.ddupg.runtrip.data.repository

import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceInput
import com.ddupg.runtrip.data.model.RaceStatus
import kotlinx.coroutines.flow.Flow

enum class RaceMutationResult {
    APPLIED,
    NOT_FOUND,
}

interface RaceRepository {
    fun observeRaces(): Flow<List<Race>>

    fun observeRace(id: String): Flow<Race?>

    suspend fun create(input: RaceInput): String

    suspend fun update(id: String, input: RaceInput): RaceMutationResult

    suspend fun updateStatus(id: String, status: RaceStatus): RaceMutationResult

    suspend fun delete(id: String): RaceMutationResult
}

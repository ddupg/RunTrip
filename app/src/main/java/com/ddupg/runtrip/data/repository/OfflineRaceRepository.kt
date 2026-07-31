package com.ddupg.runtrip.data.repository

import com.ddupg.runtrip.data.local.RaceDao
import com.ddupg.runtrip.data.local.toDomain
import com.ddupg.runtrip.data.local.toEntity
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceInput
import com.ddupg.runtrip.data.model.RaceStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineRaceRepository(
    private val raceDao: RaceDao,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : RaceRepository {
    override fun observeRaces(): Flow<List<Race>> =
        raceDao.observeAll().map { races -> races.map { it.toDomain() } }

    override fun observeRace(id: String): Flow<Race?> =
        raceDao.observeById(id).map { it?.toDomain() }

    override suspend fun create(input: RaceInput): String {
        val normalizedInput = input.validatedAndNormalized()
        val timestamp = currentTimeMillis()
        val id = newId()
        val race = normalizedInput.toRace(
            id = id,
            createdAtEpochMillis = timestamp,
            updatedAtEpochMillis = timestamp,
            recordVersion = 1,
        )
        raceDao.upsert(race.toEntity())
        return id
    }

    override suspend fun update(id: String, input: RaceInput): RaceMutationResult {
        val currentRace = raceDao.getById(id)?.toDomain()
            ?: return RaceMutationResult.NOT_FOUND
        val normalizedInput = input.validatedAndNormalized()
        val updatedRace = normalizedInput.toRace(
            id = id,
            createdAtEpochMillis = currentRace.createdAtEpochMillis,
            updatedAtEpochMillis = currentTimeMillis(),
            recordVersion = currentRace.recordVersion + 1,
        )
        return raceDao.updateExisting(updatedRace.toEntity()).toMutationResult()
    }

    override suspend fun updateStatus(id: String, status: RaceStatus): RaceMutationResult {
        val updatedRows = raceDao.updateStatusAndAdvanceVersion(
            id = id,
            statusCode = status.code,
            updatedAtEpochMillis = currentTimeMillis(),
        )
        return updatedRows.toMutationResult()
    }

    override suspend fun delete(id: String): RaceMutationResult =
        raceDao.deleteById(id).toMutationResult()
}

private fun Int.toMutationResult(): RaceMutationResult =
    if (this > 0) RaceMutationResult.APPLIED else RaceMutationResult.NOT_FOUND

private fun RaceInput.toRace(
    id: String,
    createdAtEpochMillis: Long,
    updatedAtEpochMillis: Long,
    recordVersion: Int,
): Race = Race(
    id = id,
    name = name,
    city = city,
    raceDate = raceDate,
    category = category,
    status = status,
    caaRaceLevel = caaRaceLevel,
    worldAthleticsLabel = worldAthleticsLabel,
    travelDistanceKm = travelDistanceKm,
    hotelBookingStatus = hotelBookingStatus,
    hotelName = hotelName,
    bookingPlatform = bookingPlatform,
    hotelTotalPriceCents = hotelTotalPriceCents,
    hotelNotes = hotelNotes,
    raceNotes = raceNotes,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    recordVersion = recordVersion,
)

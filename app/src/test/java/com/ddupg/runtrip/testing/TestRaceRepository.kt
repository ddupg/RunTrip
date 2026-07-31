package com.ddupg.runtrip.testing

import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceInput
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.WorldAthleticsLabel
import com.ddupg.runtrip.data.repository.RaceMutationResult
import com.ddupg.runtrip.data.repository.RaceRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal class TestRaceRepository(
    initialRaces: List<Race> = emptyList(),
) : RaceRepository {
    private val races = MutableStateFlow(initialRaces)

    val create = TestRepositoryOperation()
    val update = TestRepositoryOperation()
    val updateStatus = TestRepositoryOperation()
    val delete = TestRepositoryOperation()

    var updateResult: RaceMutationResult? = null
    var updateStatusResult: RaceMutationResult? = null
    var deleteResult: RaceMutationResult? = null

    val createdInputs = mutableListOf<RaceInput>()
    val updateCalls = mutableListOf<RaceUpdateCall>()

    override fun observeRaces(): Flow<List<Race>> = races

    override fun observeRace(id: String): Flow<Race?> =
        races.map { entries -> entries.firstOrNull { it.id == id } }

    override suspend fun create(input: RaceInput): String {
        createdInputs += input
        create.beforeMutation()
        val id = "created-id"
        races.update { entries -> entries + input.toTestRace(id) }
        return id
    }

    override suspend fun update(id: String, input: RaceInput): RaceMutationResult {
        updateCalls += RaceUpdateCall(id, input)
        update.beforeMutation()
        if (updateResult == RaceMutationResult.NOT_FOUND) {
            return RaceMutationResult.NOT_FOUND
        }

        var found = false
        races.update { entries ->
            entries.map { race ->
                if (race.id == id) {
                    found = true
                    race.withInput(input)
                } else {
                    race
                }
            }
        }
        return updateResult ?: found.toMutationResult()
    }

    override suspend fun updateStatus(
        id: String,
        status: RaceStatus,
    ): RaceMutationResult {
        updateStatus.beforeMutation()
        if (updateStatusResult == RaceMutationResult.NOT_FOUND) {
            return RaceMutationResult.NOT_FOUND
        }

        var found = false
        races.update { entries ->
            entries.map { race ->
                if (race.id == id) {
                    found = true
                    race.copy(
                        status = status,
                        updatedAtEpochMillis = race.updatedAtEpochMillis + 1,
                        recordVersion = race.recordVersion + 1,
                    )
                } else {
                    race
                }
            }
        }
        return updateStatusResult ?: found.toMutationResult()
    }

    override suspend fun delete(id: String): RaceMutationResult {
        delete.beforeMutation()
        if (deleteResult == RaceMutationResult.NOT_FOUND) {
            return RaceMutationResult.NOT_FOUND
        }

        var found = false
        races.update { entries ->
            entries.filterNot { race ->
                if (race.id == id) {
                    found = true
                    true
                } else {
                    false
                }
            }
        }
        return deleteResult ?: found.toMutationResult()
    }
}

internal class TestRepositoryOperation {
    var gate: CompletableDeferred<Unit>? = null
    var failure: RuntimeException? = null
    var callCount: Int = 0
        private set

    internal suspend fun beforeMutation() {
        callCount += 1
        gate?.await()
        failure?.let { throw it }
    }
}

internal data class RaceUpdateCall(
    val id: String,
    val input: RaceInput,
)

internal fun testRace(
    id: String = "race-id",
    name: String = id,
    city: String = "金华",
    raceDate: LocalDate = LocalDate.of(2026, 11, 15),
    category: RaceCategory = RaceCategory.MARATHON,
    status: RaceStatus = RaceStatus.DRAW_PENDING,
    caaRaceLevel: CaaRaceLevel? = null,
    worldAthleticsLabel: WorldAthleticsLabel? = null,
    travelDistanceKm: Double? = null,
    hotelBookingStatus: HotelBookingStatus = HotelBookingStatus.NOT_BOOKED,
    hotelName: String? = null,
    bookingPlatform: String? = null,
    hotelTotalPriceCents: Long? = null,
    hotelNotes: String? = null,
    raceNotes: String? = null,
    createdAtEpochMillis: Long = 1_000,
    updatedAtEpochMillis: Long = 1_000,
    recordVersion: Int = 1,
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

private fun RaceInput.toTestRace(id: String): Race = testRace(
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
)

private fun Race.withInput(input: RaceInput): Race = input.toTestRace(id).copy(
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis + 1,
    recordVersion = recordVersion + 1,
)

private fun Boolean.toMutationResult(): RaceMutationResult =
    if (this) RaceMutationResult.APPLIED else RaceMutationResult.NOT_FOUND

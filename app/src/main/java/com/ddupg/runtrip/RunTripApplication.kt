package com.ddupg.runtrip

import android.app.Application
import com.ddupg.runtrip.data.local.RunTripDatabase
import com.ddupg.runtrip.data.repository.OfflineRaceRepository
import com.ddupg.runtrip.data.repository.RaceRepository

class RunTripApplication : Application() {
    private val database by lazy { RunTripDatabase.create(this) }

    val raceRepository: RaceRepository by lazy {
        OfflineRaceRepository(database.raceDao())
    }
}

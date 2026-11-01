package com.ddupg.runtrip

import android.app.Application
import com.ddupg.runtrip.data.local.RunTripDatabase
import com.ddupg.runtrip.data.repository.OfflineRaceRepository
import com.ddupg.runtrip.data.repository.RaceRepository
import com.ddupg.runtrip.data.region.RegionCatalog

class RunTripApplication : Application() {
    val regionCatalog: RegionCatalog by lazy {
        RegionCatalog.parse(
            assets.open("regions/level.json").bufferedReader().use { it.readText() },
            BuildConfig.CHINA_REGION_DATA_VERSION,
        )
    }

    private val database by lazy { RunTripDatabase.create(this) }

    val raceRepository: RaceRepository by lazy {
        OfflineRaceRepository(database.raceDao())
    }
}

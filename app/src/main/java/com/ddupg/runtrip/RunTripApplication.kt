package com.ddupg.runtrip

import android.app.Application
import com.ddupg.runtrip.data.local.RunTripDatabase
import com.ddupg.runtrip.data.repository.OfflineRaceRepository
import com.ddupg.runtrip.data.repository.RaceRepository
import com.ddupg.runtrip.data.repository.TrainingRepository
import com.ddupg.runtrip.data.repository.LocalTrainingRepository
import com.ddupg.runtrip.data.repository.LocalTrainingPreferencesRepository
import com.ddupg.runtrip.data.region.RegionCatalog
import com.ddupg.runtrip.data.source.DataSourceConnector
import com.ddupg.runtrip.data.source.EncryptedSourceStore
import com.ddupg.runtrip.data.source.androidSourceCipher
import com.ddupg.runtrip.data.source.coros.CorosDataSourceConnector
import java.io.File

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

    private val corosSource by lazy {
        val store = EncryptedSourceStore(File(noBackupFilesDir, "data-sources"), androidSourceCipher())
        CorosDataSourceConnector(store)
    }

    val dataSourceConnectors: List<DataSourceConnector> by lazy { listOf(corosSource) }

    val trainingRepository: TrainingRepository by lazy {
        LocalTrainingRepository(database.trainingDao(), listOf(corosSource))
    }

    val trainingPreferencesRepository by lazy { LocalTrainingPreferencesRepository(this) }
}

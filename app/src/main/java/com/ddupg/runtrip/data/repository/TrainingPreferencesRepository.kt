package com.ddupg.runtrip.data.repository

import android.content.Context
import androidx.core.util.AtomicFile
import com.ddupg.runtrip.data.model.TrainingPreferences
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface TrainingPreferencesRepository {
    val preferences: StateFlow<TrainingPreferences>
    suspend fun update(value: TrainingPreferences)
}

class LocalTrainingPreferencesRepository(private val file: File) : TrainingPreferencesRepository {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, "training-preferences.json"))

    private val mutex = Mutex()
    private val store = AtomicFile(file)
    private val mutablePreferences = MutableStateFlow(read())
    override val preferences = mutablePreferences.asStateFlow()

    override suspend fun update(value: TrainingPreferences) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val bytes = JSONObject()
                .put("running_weekly_growth_percent", value.runningWeeklyGrowthPercent)
                .put("running_monthly_growth_percent", value.runningMonthlyGrowthPercent)
                .put("cycling_factor", value.cyclingFactor)
                .put("swimming_factor", value.swimmingFactor)
                .toString().toByteArray(Charsets.UTF_8)
            val output = store.startWrite()
            try {
                output.write(bytes)
                // AtomicFile logs sync/rename errors rather than returning them to the caller.
                output.fd.sync()
                store.finishWrite(output)
                if (!store.readFully().contentEquals(bytes)) throw IOException("Unable to commit training preferences")
                mutablePreferences.value = value
            } catch (error: Exception) {
                store.failWrite(output)
                throw error
            }
        }
    }

    private fun read(): TrainingPreferences {
        if (!file.exists()) return TrainingPreferences()
        val values = JSONObject(store.readFully().toString(Charsets.UTF_8))
        return TrainingPreferences(
            runningWeeklyGrowthPercent = values.getDouble("running_weekly_growth_percent"),
            runningMonthlyGrowthPercent = values.getDouble("running_monthly_growth_percent"),
            cyclingFactor = values.getDouble("cycling_factor"),
            swimmingFactor = values.getDouble("swimming_factor"),
        )
    }
}

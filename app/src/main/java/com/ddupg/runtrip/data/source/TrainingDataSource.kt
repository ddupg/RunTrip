package com.ddupg.runtrip.data.source

import com.ddupg.runtrip.data.model.TrainingActivity
import java.time.LocalDate

interface TrainingDataSource : DataSourceConnector {
    // The expected account is checked under the same lock as credential refresh and the read.
    suspend fun readTraining(accountId: String, start: LocalDate, end: LocalDate, limit: Int): List<TrainingActivity>
}

class TrainingAccountChanged : Exception()

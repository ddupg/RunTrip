package com.ddupg.runtrip.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object RunTripSchemaMigrations {
    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE races ADD COLUMN caaRaceLevelCode TEXT")
            db.execSQL("ALTER TABLE races ADD COLUMN worldAthleticsLabelCode TEXT")
        }
    }

    val all: List<Migration> = listOf(
        migration1To2,
    )
}

package com.ddupg.runtrip.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RaceEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class RunTripDatabase : RoomDatabase() {
    abstract fun raceDao(): RaceDao

    companion object {
        fun create(context: Context): RunTripDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                RunTripDatabase::class.java,
                "runtrip.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE races ADD COLUMN caaRaceLevelCode TEXT")
                db.execSQL("ALTER TABLE races ADD COLUMN worldAthleticsLabelCode TEXT")
            }
        }
    }
}

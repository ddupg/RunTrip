package com.ddupg.runtrip.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RaceEntity::class, RoadRunningEntity::class, TriathlonEntity::class],
    version = 3,
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
                .addMigrations(MIGRATION_2_3)
                .build()
    }
}

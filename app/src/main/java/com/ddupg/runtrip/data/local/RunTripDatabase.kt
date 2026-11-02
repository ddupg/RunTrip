package com.ddupg.runtrip.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RaceEntity::class, RoadRunningEntity::class, TriathlonEntity::class, TrailRunningEntity::class,
        TrainingActivityEntity::class, TrainingImportEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class RunTripDatabase : RoomDatabase() {
    abstract fun raceDao(): RaceDao
    abstract fun trainingDao(): TrainingDao

    companion object {
        fun create(context: Context): RunTripDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                RunTripDatabase::class.java,
                "runtrip.db",
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}

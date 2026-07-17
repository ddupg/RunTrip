package com.ddupg.runtrip.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RaceEntity::class],
    version = 1,
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
            ).build()
    }
}

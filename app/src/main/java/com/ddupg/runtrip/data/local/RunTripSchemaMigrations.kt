package com.ddupg.runtrip.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `races_new` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `city` TEXT NOT NULL,
                `raceDate` TEXT NOT NULL,
                `sportTypeCode` TEXT NOT NULL,
                `statusCode` TEXT NOT NULL,
                `travelDistanceKm` REAL,
                `hotelBookingStatusCode` TEXT NOT NULL,
                `hotelName` TEXT,
                `bookingPlatform` TEXT,
                `hotelTotalPriceCents` INTEGER,
                `hotelNotes` TEXT,
                `raceNotes` TEXT,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                `recordVersion` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO races_new (`id`,
                `name`,
                `city`,
                `raceDate`,
                `statusCode`,
                `travelDistanceKm`,
                `hotelBookingStatusCode`,
                `hotelName`,
                `bookingPlatform`,
                `hotelTotalPriceCents`,
                `hotelNotes`,
                `raceNotes`,
                `createdAtEpochMillis`,
                `updatedAtEpochMillis`,
                `recordVersion`,
                sportTypeCode)
            SELECT `id`,
                `name`,
                `city`,
                `raceDate`,
                `statusCode`,
                `travelDistanceKm`,
                `hotelBookingStatusCode`,
                `hotelName`,
                `bookingPlatform`,
                `hotelTotalPriceCents`,
                `hotelNotes`,
                `raceNotes`,
                `createdAtEpochMillis`,
                `updatedAtEpochMillis`,
                `recordVersion`,
                'ROAD_RUNNING'
            FROM races
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TEMP TABLE legacy_road_details AS
            SELECT id AS raceId, categoryCode, caaRaceLevelCode, worldAthleticsLabelCode
            FROM races
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE races")
        db.execSQL("ALTER TABLE races_new RENAME TO races")
        db.execSQL(
            """
            CREATE TABLE road_running_details (
                `raceId` TEXT NOT NULL,
                `categoryCode` TEXT NOT NULL,
                `caaRaceLevelCode` TEXT,
                `worldAthleticsLabelCode` TEXT,
                PRIMARY KEY(`raceId`),
                FOREIGN KEY(`raceId`) REFERENCES `races`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE triathlon_details (
                `raceId` TEXT NOT NULL,
                `categoryCode` TEXT NOT NULL,
                PRIMARY KEY(`raceId`),
                FOREIGN KEY(`raceId`) REFERENCES `races`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("INSERT INTO road_running_details SELECT * FROM legacy_road_details")
        db.execSQL("DROP TABLE legacy_road_details")
    }
}

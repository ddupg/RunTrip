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

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE trail_running_details (
                `raceId` TEXT NOT NULL,
                `categoryCode` TEXT NOT NULL,
                `distanceKm` REAL NOT NULL,
                `elevationGainMeters` INTEGER,
                PRIMARY KEY(`raceId`),
                FOREIGN KEY(`raceId`) REFERENCES `races`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 旧自由文本无法可靠推断行政区；原样保留，用户重新选择后再写入结构化地点。
        // TODO(2.0.0): 移除 city 列及兼容逻辑；发布前处理尚未重新选择地点的旧记录。
        listOf("provinceCode", "provinceName", "cityCode", "cityName", "districtCode", "districtName", "dataVersion")
            .forEach { field -> db.execSQL("ALTER TABLE races ADD COLUMN `location_$field` TEXT") }
    }
}

internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE training_activities (
            `sourceCode` TEXT NOT NULL, `accountId` TEXT NOT NULL, `activityId` TEXT NOT NULL,
            `subtypeCode` TEXT NOT NULL, `sourceDate` TEXT NOT NULL, `startEpochSeconds` INTEGER,
            `distanceMeters` REAL, `durationSeconds` INTEGER NOT NULL,
            PRIMARY KEY(`sourceCode`, `accountId`, `activityId`))""")
        db.execSQL("""CREATE TABLE training_imports (
            `sourceCode` TEXT NOT NULL, `accountId` TEXT NOT NULL, `nextHistoryEnd` TEXT,
            `lastRefreshDay` TEXT NOT NULL, PRIMARY KEY(`sourceCode`, `accountId`))""")
    }
}

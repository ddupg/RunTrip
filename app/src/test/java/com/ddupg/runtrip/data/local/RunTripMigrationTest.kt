package com.ddupg.runtrip.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.repository.OfflineRaceRepository
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class RunTripMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: RunTripDatabase? = null

    @Before
    fun deleteExistingDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun closeAndDeleteDatabase() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migration1To2PreservesLegacyRaceThroughProductionOpenPath() = runTest {
        createVersion1Database()

        database = RunTripDatabase.create(context)
        val race = requireNotNull(
            OfflineRaceRepository(requireNotNull(database).raceDao())
                .observeRace("legacy-race")
                .first(),
        )

        assertEquals("横店马拉松", race.name)
        assertEquals("金华", race.city)
        assertEquals(LocalDate.of(2026, 11, 15), race.raceDate)
        assertEquals(RaceCategory.MARATHON, race.category)
        assertEquals(RaceStatus.DRAW_WON, race.status)
        assertEquals(350.5, race.travelDistanceKm)
        assertEquals(HotelBookingStatus.BOOKED, race.hotelBookingStatus)
        assertEquals("万豪万枫", race.hotelName)
        assertEquals("携程", race.bookingPlatform)
        assertEquals(35_050L, race.hotelTotalPriceCents)
        assertEquals("含早", race.hotelNotes)
        assertEquals("赛前一天领物", race.raceNotes)
        assertEquals(1_000L, race.createdAtEpochMillis)
        assertEquals(2_000L, race.updatedAtEpochMillis)
        assertEquals(2, race.recordVersion)
        assertNull(race.caaRaceLevel)
        assertNull(race.worldAthleticsLabel)
    }

    private fun createVersion1Database() {
        val schema = JSONObject(openVersion1Schema().bufferedReader().use { it.readText() })
        val raceTable = schema
            .getJSONObject("database")
            .getJSONArray("entities")
            .getJSONObject(0)
        val createSql = raceTable
            .getString("createSql")
            .replace("\${TABLE_NAME}", raceTable.getString("tableName"))
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(createSql)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = error("Unexpected test setup upgrade from $oldVersion to $newVersion")
                    },
                )
                .build(),
        )

        try {
            helper.writableDatabase.execSQL(
                """
                INSERT INTO races (
                    id,
                    name,
                    city,
                    raceDate,
                    categoryCode,
                    statusCode,
                    travelDistanceKm,
                    hotelBookingStatusCode,
                    hotelName,
                    bookingPlatform,
                    hotelTotalPriceCents,
                    hotelNotes,
                    raceNotes,
                    createdAtEpochMillis,
                    updatedAtEpochMillis,
                    recordVersion
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "legacy-race",
                    "横店马拉松",
                    "金华",
                    "2026-11-15",
                    "MARATHON",
                    "DRAW_WON",
                    350.5,
                    "BOOKED",
                    "万豪万枫",
                    "携程",
                    35_050L,
                    "含早",
                    "赛前一天领物",
                    1_000L,
                    2_000L,
                    2,
                ),
            )
        } finally {
            helper.close()
        }
    }

    private fun openVersion1Schema(): InputStream =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(SCHEMA_1_RESOURCE)) {
            "Missing committed Room schema: $SCHEMA_1_RESOURCE"
        }

    private companion object {
        const val DATABASE_NAME = "runtrip.db"
        const val SCHEMA_1_RESOURCE =
            "com.ddupg.runtrip.data.local.RunTripDatabase/1.json"
    }
}

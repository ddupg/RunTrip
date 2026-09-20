package com.ddupg.runtrip.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.ddupg.runtrip.data.model.*
import com.ddupg.runtrip.data.model.HotelBookingStatus
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
    fun migration2ToLatestPreservesAllProjectsAndFieldsThroughProductionOpenPath() = runTest {
        createVersion2Database()

        database = RunTripDatabase.create(context)
        val race = requireNotNull(
            OfflineRaceRepository(requireNotNull(database).raceDao())
                .observeRace("legacy-race")
                .first(),
        )

        assertEquals("横店马拉松", race.name)
        assertEquals("金华", race.city)
        assertEquals(LocalDate.of(2026, 11, 15), race.raceDate)
        assertEquals(RoadRunningCategory.MARATHON, race.category)
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
        assertEquals(CaaRaceLevel.A1, (race.details as RoadRunningDetails).caaRaceLevel)
        assertEquals(WorldAthleticsLabel.PLATINUM, (race.details as RoadRunningDetails).worldAthleticsLabel)
        val repository = OfflineRaceRepository(requireNotNull(database).raceDao())
        val all = repository.observeRaces().first()
        assertEquals(4, all.size)
        assertEquals(RoadRunningCategory.entries.toSet(), all.map { it.category }.toSet())
        all.filter { it.id != "legacy-race" }.forEach {
            assertEquals(RoadRunningDetails(RoadRunningCategory.fromCode(it.id)), it.details)
            assertNull(it.travelDistanceKm)
            assertNull(it.hotelTotalPriceCents)
        }
        val input = com.ddupg.runtrip.data.model.RaceInput(
            name = race.name, city = race.city, raceDate = race.raceDate,
            status = race.status, details = TriathlonDetails(TriathlonCategory.STANDARD),
        )
        repository.update(race.id, input)
        assertEquals(input.details, repository.observeRace(race.id).first()?.details)
        repository.delete(race.id)
        assertNull(repository.observeRace(race.id).first())
        database?.close()
        database = RunTripDatabase.create(context)
        assertEquals(3, OfflineRaceRepository(requireNotNull(database).raceDao()).observeRaces().first().size)
    }

    @Test
    fun migration3To4PreservesRoadAndTriathlonAndAllowsTrailRecords() = runTest {
        val schemaPath = "com.ddupg.runtrip.data.local.RunTripDatabase/3.json"
        val schema = JSONObject(requireNotNull(javaClass.classLoader?.getResourceAsStream(schemaPath))
            .bufferedReader().use { it.readText() }).getJSONObject("database").getJSONArray("entities")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DATABASE_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        for (index in 0 until schema.length()) {
                            val table = schema.getJSONObject(index)
                            db.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", table.getString("tableName")))
                        }
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
                }).build(),
        )
        val before: Map<String, List<List<String?>>>
        try {
            val db = helper.writableDatabase
            listOf("road" to "ROAD_RUNNING", "tri" to "TRIATHLON").forEach { (id, sport) ->
                db.execSQL(
                    """INSERT INTO races (id, name, city, raceDate, sportTypeCode, statusCode,
                       travelDistanceKm, hotelBookingStatusCode, hotelName, bookingPlatform, hotelTotalPriceCents,
                       hotelNotes, raceNotes, createdAtEpochMillis, updatedAtEpochMillis, recordVersion)
                       VALUES (?, '比赛', '杭州', '2026-11-15', ?, 'REGISTERED', 123.5, 'BOOKED', '酒店', '平台',
                       12345, '酒店备注', '赛事备注', 1000, 2000, 4)""", arrayOf(id, sport),
                )
            }
            db.execSQL("INSERT INTO road_running_details VALUES ('road', 'HALF_MARATHON', 'A1', 'GOLD')")
            db.execSQL("INSERT INTO triathlon_details VALUES ('tri', 'STANDARD')")
            before = snapshot(db)
        } finally {
            helper.close()
        }
        database = RunTripDatabase.create(context)
        val repository = OfflineRaceRepository(requireNotNull(database).raceDao())
        val all = repository.observeRaces().first()
        assertEquals(before, snapshot(requireNotNull(database).openHelper.readableDatabase))
        assertEquals(RoadRunningDetails(RoadRunningCategory.HALF_MARATHON, CaaRaceLevel.A1, WorldAthleticsLabel.GOLD), all.first { it.id == "road" }.details)
        assertEquals(TriathlonDetails(TriathlonCategory.STANDARD), all.first { it.id == "tri" }.details)
        val input = RaceInput("山径赛", "杭州", LocalDate.of(2026, 11, 16), TrailRunningDetails(30.5, 1200), RaceStatus.REGISTERED)
        val id = repository.create(input)
        assertEquals(input.details, repository.observeRace(id).first()?.details)
        database?.close()
        database = RunTripDatabase.create(context)
        assertEquals(input.details, OfflineRaceRepository(requireNotNull(database).raceDao()).observeRace(id).first()?.details)
    }

    private fun snapshot(db: SupportSQLiteDatabase): Map<String, List<List<String?>>> =
        listOf("races", "road_running_details", "triathlon_details").associateWith { table ->
            db.query("SELECT * FROM $table ORDER BY 1").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) })
                }
            }
        }

    private fun createVersion2Database() {
        val schema = JSONObject(openVersion2Schema().bufferedReader().use { it.readText() })
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
                    object : SupportSQLiteOpenHelper.Callback(2) {
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
                    recordVersion,
                    caaRaceLevelCode,
                    worldAthleticsLabelCode
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    "A1",
                    "PLATINUM",
                ),
            )
            listOf("HALF_MARATHON", "TEN_K", "OTHER").forEach { category ->
                helper.writableDatabase.execSQL(
                    """INSERT INTO races (id, name, city, raceDate, categoryCode, statusCode,
                        hotelBookingStatusCode, createdAtEpochMillis, updatedAtEpochMillis, recordVersion)
                        VALUES (?, ?, '杭州', '2026-11-15', ?, 'WATCHING', 'NOT_BOOKED', 1000, 2000, 1)""",
                    arrayOf(category, category, category),
                )
            }
        } finally {
            helper.close()
        }
    }

    private fun openVersion2Schema(): InputStream =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(SCHEMA_2_RESOURCE)) {
            "Missing committed Room schema: $SCHEMA_2_RESOURCE"
        }

    private companion object {
        const val DATABASE_NAME = "runtrip.db"
        const val SCHEMA_2_RESOURCE =
            "com.ddupg.runtrip.data.local.RunTripDatabase/2.json"
    }
}

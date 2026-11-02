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
    fun migration4To5PreservesLegacyTrailAndAllowsReselectionAndReopen() = runTest {
        val schemaPath = "com.ddupg.runtrip.data.local.RunTripDatabase/4.json"
        val entities = JSONObject(requireNotNull(javaClass.classLoader?.getResourceAsStream(schemaPath))
            .bufferedReader().use { it.readText() }).getJSONObject("database").getJSONArray("entities")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DATABASE_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        for (index in 0 until entities.length()) {
                            val table = entities.getJSONObject(index)
                            db.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", table.getString("tableName")))
                        }
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
                }).build(),
        )
        val before: Map<String, List<List<String?>>>
        try {
            val db = helper.writableDatabase
            db.execSQL("""INSERT INTO races (id, name, city, raceDate, sportTypeCode, statusCode,
                hotelBookingStatusCode, createdAtEpochMillis, updatedAtEpochMillis, recordVersion)
                VALUES ('legacy-v4', '山径赛', '朝阳（原始地点）', '2026-11-15', 'TRAIL_RUNNING',
                'REGISTERED', 'NOT_BOOKED', 1000, 2000, 3)""")
            db.execSQL("INSERT INTO trail_running_details VALUES ('legacy-v4', 'CUSTOM', 30.5, 1200)")
            before = snapshot(db)
        } finally {
            helper.close()
        }
        database = RunTripDatabase.create(context)
        var repository = OfflineRaceRepository(requireNotNull(database).raceDao())
        val legacy = requireNotNull(repository.observeRace("legacy-v4").first())
        assertEquals(before, snapshot(requireNotNull(database).openHelper.readableDatabase))
        assertEquals("朝阳（原始地点）", legacy.city)
        assertNull(legacy.location)
        assertEquals(TrailRunningDetails(30.5, 1200), legacy.details)
        assertEquals(3, legacy.recordVersion)
        repository.update(legacy.id, RaceInput(
            name = "只修改名称", city = legacy.city, raceDate = legacy.raceDate,
            details = legacy.details, status = legacy.status,
        ))
        assertEquals(legacy.city, repository.observeRace(legacy.id).first()?.city)
        assertNull(repository.observeRace(legacy.id).first()?.location)

        val location = RaceLocation("110000", "北京市", "110000", "北京市", "110105", "朝阳区", "9.0.0")
        repository.update(legacy.id, RaceInput(
            name = "重新选择地点", location = location, raceDate = legacy.raceDate,
            details = legacy.details, status = legacy.status,
        ))
        database?.close()
        database = RunTripDatabase.create(context)
        repository = OfflineRaceRepository(requireNotNull(database).raceDao())
        val updated = requireNotNull(repository.observeRace(legacy.id).first())
        assertEquals("", updated.city)
        assertEquals(location, updated.location)
        assertEquals(legacy.details, updated.details)
        assertEquals(legacy.createdAtEpochMillis, updated.createdAtEpochMillis)
    }

    @Test
    fun migration3ToLatestPreservesRoadAndTriathlonAndAllowsTrailRecords() = runTest {
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

    @Test
    fun migration5To6PreservesRaceAndStoresTrainingAcrossReopen() = runTest {
        val schemaPath = "com.ddupg.runtrip.data.local.RunTripDatabase/5.json"
        val entities = JSONObject(requireNotNull(javaClass.classLoader?.getResourceAsStream(schemaPath))
            .bufferedReader().use { it.readText() }).getJSONObject("database").getJSONArray("entities")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DATABASE_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        for (index in 0 until entities.length()) {
                            val table = entities.getJSONObject(index)
                            db.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", table.getString("tableName")))
                        }
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
                }).build(),
        )
        try {
            val db = helper.writableDatabase
            db.execSQL("""INSERT INTO races (id, name, city, raceDate, sportTypeCode, statusCode,
                hotelBookingStatusCode, createdAtEpochMillis, updatedAtEpochMillis, recordVersion,
                location_provinceCode, location_provinceName, location_cityCode, location_cityName, location_dataVersion)
                VALUES ('legacy-v5', '既有比赛', '', '2026-11-15', 'ROAD_RUNNING', 'REGISTERED',
                'NOT_BOOKED', 1000, 2000, 3, '330000', '浙江省', '330100', '杭州市', '9.0.0')""")
            db.execSQL("INSERT INTO road_running_details VALUES ('legacy-v5', 'HALF_MARATHON', 'A1', 'GOLD')")
        } finally {
            helper.close()
        }
        database = RunTripDatabase.create(context)
        val before = requireNotNull(OfflineRaceRepository(requireNotNull(database).raceDao()).observeRace("legacy-v5").first())
        assertEquals("既有比赛", before.name)
        assertEquals("杭州市", before.location?.cityName)
        assertEquals(3, before.recordVersion)
        requireNotNull(database).trainingDao().saveBatch(
            listOf(TrainingActivityEntity("coros", "synthetic-account", "synthetic-activity", "POOL_SWIM",
                "2026-11-02", null, null, 2160)),
            TrainingImportEntity("coros", "synthetic-account", "2025-12-31", "2026-11-02"),
        )
        database?.close()
        database = RunTripDatabase.create(context)
        assertEquals(before, OfflineRaceRepository(requireNotNull(database).raceDao()).observeRace("legacy-v5").first())
        val record = requireNotNull(database).trainingDao().observe("coros", "synthetic-account").first().single()
        assertNull(record.distanceMeters)
        assertNull(record.startEpochSeconds)
        assertEquals(2160L, record.durationSeconds)
        assertEquals("2025-12-31", requireNotNull(database).trainingDao().importState("coros", "synthetic-account")?.nextHistoryEnd)
    }

    private fun snapshot(db: SupportSQLiteDatabase): Map<String, List<List<String?>>> =
        listOf("races", "road_running_details", "triathlon_details").associateWith { table ->
            db.query("SELECT * FROM $table ORDER BY 1").use { cursor ->
                buildList {
                    val originalColumns = (0 until cursor.columnCount).filterNot {
                        cursor.getColumnName(it).startsWith("location_")
                    }
                    while (cursor.moveToNext()) add(originalColumns.map { cursor.getString(it) })
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

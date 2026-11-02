package com.ddupg.runtrip.data.source.coros

import com.ddupg.runtrip.data.model.TrainingSport
import com.ddupg.runtrip.data.source.SourceProtocolException
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CorosTrainingParserTest {
    private val start = LocalDate.parse("2026-10-01")
    private val end = LocalDate.parse("2026-10-31")
    private fun result(text: String) = JSONObject().put("content", JSONArray().put(JSONObject()
        .put("type", "text").put("text", JSONObject.quote(text))))
    private fun record(code: Int = 100, fields: String = "Duration: 1:02:03 | Distance: 5.20 km", id: String = "synthetic") =
        "1. Synthetic activity — 2026-10-02\n   Time Window: startTimestamp=1790896200 | endTimestamp=1790899923\n   $fields\n   LabelId: $id | SportType: $code\n"
    private fun header(count: Int = 1) = "Sport Records — $start to $end ($count records)\n========================\n\n"
    private fun parse(text: String) = CorosTrainingParser.parse(result(text), start, end)
    private fun rejects(text: String) {
        try { parse(text); fail("Malformed records must not be saved") } catch (_: SourceProtocolException) { }
    }

    @Test fun allThirteenSubtypesMapToRequestedGroups() {
        val activities = CorosTrainingParser.subtypes.keys.map { parse(header() + record(it)).single() }
        assertEquals(13, activities.size)
        assertEquals(mapOf(TrainingSport.RUNNING to 4, TrainingSport.CYCLING to 7, TrainingSport.SWIMMING to 2),
            activities.groupingBy { it.subtype.sport }.eachCount())
        assertEquals(5200.0, activities.first().distanceMeters!!, 0.0)
        assertEquals(3723L, activities.first().durationSeconds)
        assertEquals(1790896200L, activities.first().startEpochSeconds)
    }

    @Test fun metersMissingDistanceAndMissingTimestampArePreserved() {
        val swim = parse(header() + record(300, "Duration: 36:00 | Distance: 197 m")).single()
        assertEquals(197.0, swim.distanceMeters!!, 0.0)
        assertEquals(2160L, swim.durationSeconds)
        val missing = parse(header() + record(301, "Duration: 36:00")
            .replace(Regex("   Time Window:[^\n]*\n"), "")).single()
        assertNull(missing.distanceMeters)
        assertNull(missing.startEpochSeconds)
    }

    @Test fun emptyResponseMustMatchRequestedRange() {
        assertTrue(parse("No sport records found from $start to $end.").isEmpty())
        rejects("No sport records found from 2025-10-01 to 2025-10-31.")
    }

    @Test fun countOrdinalIdentityAndUnsupportedSportsAreRejected() {
        rejects(header(2) + record())
        rejects(header() + record().replace("1. Synthetic", "2. Synthetic"))
        rejects(header() + record().replace("LabelId:", "MissingId:"))
        rejects(header() + record(104))
        rejects(header() + record().replace("2026-10-02", "2026-11-01"))
        rejects(header() + record().replace("startTimestamp=1790896200", "startTimestamp=unknown"))
    }

    @Test fun invalidFieldsAndDuplicatesAreRejectedRatherThanDefaulted() {
        rejects(header() + record(fields = "Duration: unknown | Distance: 2.0 km"))
        rejects(header() + record(fields = "Duration: 1:60:00 | Distance: 2.0 km"))
        rejects(header() + record(fields = "Duration: 1:00 | Distance: unknown"))
        rejects(header(2) + record() + record().replace("1. Synthetic", "2. Synthetic"))
    }

    @Test fun labelIdentityIncludesSportType() {
        val records = parse(header(2) + record() + record(101).replace("1. Synthetic", "2. Synthetic"))
        assertEquals(2, records.map { it.id }.toSet().size)
    }
}
